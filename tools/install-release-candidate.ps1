[CmdletBinding()]
param(
    [string]$ApkPath = "",
    [ValidateSet("VerifyOnly", "Fresh", "Upgrade")]
    [string]$InstallMode = "VerifyOnly",
    [string]$Serial = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$expectedPackage = "com.gdad.bags"
$expectedVersionCode = "10"
$expectedVersionName = "0.2.0-rc9"
$expectedSha256 = "99719A389E83FB81CA792DA3832147CB1CD962C867E78DDF7213EF0E8FC3F1CC"
$expectedCertificateSha256 = "C1B015D22B09F79F801B8677CDBC054775322C4A0535064F0AA1DA89160269C9"
$expectedActivity = "com.gdad.bags.MainActivity"

function ConvertTo-NativeArgument([string]$Value) {
    if ($Value.Contains('"')) { throw "Native command arguments may not contain quotation marks." }
    if ($Value -notmatch "\s") { return $Value }
    return '"' + $Value + '"'
}

function Invoke-NativeProcess {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][ValidateRange(1, 300)][int]$TimeoutSeconds
    )

    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $FilePath
    $startInfo.Arguments = (($Arguments | ForEach-Object { ConvertTo-NativeArgument $_ }) -join " ")
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true

    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    try {
        if (-not $process.Start()) { throw "Could not start the native verification command." }
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
            try { $process.Kill() } catch {}
            throw "Native command timed out after $TimeoutSeconds seconds."
        }
        $process.WaitForExit()
        return [PSCustomObject]@{
            ExitCode = $process.ExitCode
            StandardOutput = $stdoutTask.GetAwaiter().GetResult()
            StandardError = $stderrTask.GetAwaiter().GetResult()
        }
    } finally {
        $process.Dispose()
    }
}

function ConvertTo-OutputLines([string]$Value) {
    if ([string]::IsNullOrEmpty($Value)) { return @() }
    return @([Regex]::Split($Value.TrimEnd("`r", "`n"), "\r?\n"))
}

$versionSource = Get-Content -Raw -Encoding utf8 (Join-Path $root "app\build.gradle.kts")
if ($versionSource -notmatch "val appVersionCode = $expectedVersionCode(?:\r?\n)" -or
    $versionSource -notmatch "val appVersionName = `"$([Regex]::Escape($expectedVersionName))`"") {
    throw "This APK has been superseded by the current release source. Build and verify the new signed candidate."
}

if (-not $ApkPath) {
    $ApkPath = Join-Path $root "GDAD-BAGS-0.2.0-rc9-10-release.apk"
}
$resolvedApk = (Resolve-Path -LiteralPath $ApkPath).Path

$buildTools = Join-Path $root ".tooling\android-sdk\build-tools\36.0.0"
$apksigner = Join-Path $buildTools "apksigner.bat"
$aapt = Join-Path $buildTools "aapt.exe"
$bundledJavaHome = Join-Path $root ".tooling\jdk\jdk-17.0.19+10"
if (-not (Test-Path -LiteralPath $apksigner)) { throw "Bundled apksigner is missing." }
if (-not (Test-Path -LiteralPath $aapt)) { throw "Bundled aapt is missing." }
if (Test-Path -LiteralPath (Join-Path $bundledJavaHome "bin\java.exe")) {
    $env:JAVA_HOME = $bundledJavaHome
}

$actualSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $resolvedApk).Hash.ToUpperInvariant()
if ($actualSha256 -ne $expectedSha256) {
    throw "APK SHA-256 does not match the approved release candidate."
}

$signature = & $apksigner verify --verbose --print-certs $resolvedApk 2>&1
if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed." }
if (-not ($signature | Where-Object { $_ -match "Verified using v2 scheme.*true" })) {
    throw "APK Signature Scheme v2 verification did not pass."
}
$certificateLine = $signature |
    Where-Object { $_ -match "certificate SHA-256 digest:" } |
    Select-Object -First 1
if (-not $certificateLine) { throw "APK signer certificate digest is missing." }
$actualCertificateSha256 = (($certificateLine -split ":", 2)[1] -replace "[^0-9A-Fa-f]", "").ToUpperInvariant()
if ($actualCertificateSha256 -ne $expectedCertificateSha256) {
    throw "APK signer certificate does not match the approved production key."
}

$badging = & $aapt dump badging $resolvedApk 2>&1
if ($LASTEXITCODE -ne 0) { throw "APK metadata inspection failed." }
$packageLine = $badging | Where-Object { $_ -match "^package:" } | Select-Object -First 1
$launchLine = $badging | Where-Object { $_ -match "^launchable-activity:" } | Select-Object -First 1
if (-not $packageLine -or
    $packageLine -notmatch "name='$([Regex]::Escape($expectedPackage))'" -or
    $packageLine -notmatch "versionCode='$expectedVersionCode'" -or
    $packageLine -notmatch "versionName='$([Regex]::Escape($expectedVersionName))'") {
    throw "APK package or version metadata does not match the approved release candidate."
}
if (-not $launchLine -or $launchLine -notmatch "name='$([Regex]::Escape($expectedActivity))'") {
    throw "APK launch activity does not match the approved release candidate."
}
if (-not ($badging | Where-Object { $_ -match "^sdkVersion:'31'$" })) {
    throw "APK minimum SDK is not 31."
}
if (-not ($badging | Where-Object { $_ -match "^targetSdkVersion:'36'$" })) {
    throw "APK target SDK is not 36."
}

$result = [ordered]@{
    VerifiedAt = (Get-Date).ToString("o")
    ApkPath = $resolvedApk
    Sha256 = $actualSha256
    CertificateSha256 = $actualCertificateSha256
    Package = $expectedPackage
    VersionCode = $expectedVersionCode
    VersionName = $expectedVersionName
    InstallMode = $InstallMode
    Installed = $false
    DeviceSerial = $null
    LaunchTotalTimeMs = $null
}

if ($InstallMode -ne "VerifyOnly") {
    $bundledAdb = Join-Path $root ".tooling\android-sdk\platform-tools\adb.exe"
    $adb = if (Test-Path -LiteralPath $bundledAdb) {
        $bundledAdb
    } else {
        (Get-Command adb -ErrorAction Stop).Source
    }

    try {
        $deviceResult = Invoke-NativeProcess -FilePath $adb -Arguments @("devices", "-l") -TimeoutSeconds 15
    } catch {
        Get-Process adb -ErrorAction SilentlyContinue |
            Where-Object { $_.Path -eq $adb } |
            Stop-Process -Force -ErrorAction SilentlyContinue
        throw "ADB device discovery did not complete safely. Reconnect the phone, enable USB debugging, accept its authorization prompt, and retry."
    }
    if ($deviceResult.ExitCode -ne 0) {
        $message = @($deviceResult.StandardError, $deviceResult.StandardOutput) |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
        throw (($message -join [Environment]::NewLine).Trim())
    }
    $deviceLines = @(ConvertTo-OutputLines $deviceResult.StandardOutput |
        Select-Object -Skip 1 |
        Where-Object { $_ -match "^\S+\s+device(?:\s|$)" })
    if ($Serial) {
        if (-not ($deviceLines | Where-Object { $_ -match "^$([Regex]::Escape($Serial))\s" })) {
            throw "ADB device '$Serial' is not connected and authorized."
        }
    } elseif ($deviceLines.Count -eq 1) {
        $Serial = ($deviceLines[0] -split "\s+")[0]
    } elseif ($deviceLines.Count -eq 0) {
        throw "No authorized ADB device is connected. Connect one, enable USB debugging, and retry."
    } else {
        throw "Multiple ADB devices are connected. Pass -Serial with the intended device ID."
    }

    function Invoke-Adb {
        param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
        $timeoutSeconds = if ($Arguments.Count -gt 0 -and $Arguments[0] -eq "install") { 180 } else { 30 }
        $commandResult = Invoke-NativeProcess `
            -FilePath $adb `
            -Arguments (@("-s", $Serial) + $Arguments) `
            -TimeoutSeconds $timeoutSeconds
        if ($commandResult.ExitCode -ne 0) {
            $message = @($commandResult.StandardError, $commandResult.StandardOutput) |
                Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
            throw (($message -join [Environment]::NewLine).Trim())
        }
        return @(ConvertTo-OutputLines $commandResult.StandardOutput)
    }

    $installedPath = @(Invoke-Adb shell pm path $expectedPackage |
        Where-Object { $_ -match "^package:" })
    if ($InstallMode -eq "Fresh" -and $installedPath.Count -gt 0) {
        throw "The package is already installed. Uninstall it manually only after preserving any required local data, or use -InstallMode Upgrade."
    }
    if ($InstallMode -eq "Upgrade" -and $installedPath.Count -eq 0) {
        throw "Upgrade mode requires an existing GDAD BAGS installation. Use -InstallMode Fresh."
    }

    if ($InstallMode -eq "Upgrade") {
        Invoke-Adb install -r $resolvedApk | Out-Null
    } else {
        Invoke-Adb install $resolvedApk | Out-Null
    }

    $installedPath = @(Invoke-Adb shell pm path $expectedPackage |
        Where-Object { $_ -match "^package:" })
    if ($installedPath.Count -eq 0) { throw "ADB did not report the installed package." }

    $launch = Invoke-Adb shell am start -W -n "$expectedPackage/$expectedActivity"
    $totalTimeLine = $launch | Where-Object { $_ -match "^TotalTime:\s*(\d+)" } | Select-Object -First 1
    if ($totalTimeLine) {
        $result.LaunchTotalTimeMs = [int]([Regex]::Match($totalTimeLine, "\d+").Value)
    }
    $result.Installed = $true
    $result.DeviceSerial = $Serial
}

[PSCustomObject]$result | ConvertTo-Json -Depth 3
