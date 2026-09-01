$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$env:JAVA_HOME = Join-Path $root ".tooling\jdk\jdk-17.0.19+10"
$env:ANDROID_HOME = Join-Path $root ".tooling\android-sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = Join-Path $root ".tooling\gradle-user-home"
$env:GDAD_PRODUCTION_RELEASE = "true"

$gradle = Join-Path $root ".tooling\gradle\gradle-9.4.1\bin\gradle.bat"
& $gradle clean testDebugUnitTest lint assembleProductionRelease --no-daemon --max-workers=1 `
    '-Pkotlin.compiler.execution.strategy=in-process'
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$source = Join-Path $root "app\build\outputs\apk\release\app-release.apk"
$target = Join-Path $root "GDAD-BAGS-0.2.0-rc12-13-release.apk"
Copy-Item -LiteralPath $source -Destination $target -Force

$apksigner = Join-Path $env:ANDROID_HOME "build-tools\36.0.0\apksigner.bat"
& $apksigner verify --verbose --print-certs $target
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$artifact = Get-Item -LiteralPath $target
$checksum = Get-FileHash -LiteralPath $target -Algorithm SHA256
Write-Output ("Release APK: {0}" -f $artifact.FullName)
Write-Output ("Bytes: {0}" -f $artifact.Length)
Write-Output ("SHA-256: {0}" -f $checksum.Hash)
