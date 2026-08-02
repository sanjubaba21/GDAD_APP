[CmdletBinding()]
param(
    [ValidateRange(1, 20)]
    [int]$ColdStartRuns = 5,
    [string]$Serial = "",
    [string]$PackageName = "com.gdad.bags",
    [string]$ActivityName = ".MainActivity",
    [switch]$CaptureCurrentOnly
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$bundledAdb = Join-Path $root ".tooling\android-sdk\platform-tools\adb.exe"
$adb = if (Test-Path -LiteralPath $bundledAdb) {
    $bundledAdb
} else {
    (Get-Command adb -ErrorAction Stop).Source
}

$deviceLines = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\S+\s+device$" }
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
    $output = & $adb -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($output -join [Environment]::NewLine) }
    return $output
}

$component = "$PackageName/$ActivityName"
$starts = @()
if (-not $CaptureCurrentOnly) {
    1..$ColdStartRuns | ForEach-Object {
        Invoke-Adb shell am force-stop $PackageName | Out-Null
        Start-Sleep -Milliseconds 500
        $launch = Invoke-Adb shell am start -W -n $component
        $totalTimeLine = $launch | Where-Object { $_ -match "^TotalTime:\s*(\d+)" } | Select-Object -First 1
        if (-not $totalTimeLine) { throw "ADB did not return TotalTime for cold-start run $_." }
        $totalTime = [int]([Regex]::Match($totalTimeLine, "\d+").Value)
        $starts += [PSCustomObject]@{ Run = $_; TotalTimeMs = $totalTime }
    }
}

$meminfo = Invoke-Adb shell dumpsys meminfo $PackageName
$totalPssLine = $meminfo | Where-Object { $_ -match "TOTAL PSS:\s*[\d,]+" } | Select-Object -First 1
$totalPssKb = if ($totalPssLine) {
    [int](($totalPssLine -replace ".*TOTAL PSS:\s*([\d,]+).*", '$1') -replace ",", "")
} else { $null }

$gfx = Invoke-Adb shell dumpsys gfxinfo $PackageName
$totalFramesLine = $gfx | Where-Object { $_ -match "Total frames rendered:\s*\d+" } | Select-Object -First 1
$jankyFramesLine = $gfx | Where-Object { $_ -match "Janky frames:\s*\d+" } | Select-Object -First 1
$totalFrames = if ($totalFramesLine) { [int]([Regex]::Match($totalFramesLine, "\d+").Value) } else { $null }
$jankyFrames = if ($jankyFramesLine) { [int]([Regex]::Match($jankyFramesLine, "\d+").Value) } else { $null }
$jankPercent = if ($totalFrames -and $null -ne $jankyFrames) {
    [Math]::Round(($jankyFrames * 100.0) / $totalFrames, 2)
} else { $null }

$sortedStarts = @($starts.TotalTimeMs | Sort-Object)
$medianStartMs = if ($sortedStarts.Count) {
    if ($sortedStarts.Count % 2) {
        $sortedStarts[[int][Math]::Floor($sortedStarts.Count / 2)]
    } else {
        [Math]::Round(($sortedStarts[$sortedStarts.Count / 2 - 1] + $sortedStarts[$sortedStarts.Count / 2]) / 2.0, 1)
    }
} else { $null }

[PSCustomObject]@{
    CapturedAt = (Get-Date).ToString("o")
    DeviceSerial = $Serial
    Package = $PackageName
    ColdStarts = $starts
    MedianColdStartMs = $medianStartMs
    TotalPssKb = $totalPssKb
    TotalFrames = $totalFrames
    JankyFrames = $jankyFrames
    JankyFramePercent = $jankPercent
} | ConvertTo-Json -Depth 4
