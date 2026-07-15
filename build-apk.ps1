$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$env:JAVA_HOME = Join-Path $root ".tooling\jdk\jdk-17.0.19+10"
$env:ANDROID_HOME = Join-Path $root ".tooling\android-sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = Join-Path $root ".tooling\gradle-user-home"

$gradle = Join-Path $root ".tooling\gradle\gradle-9.4.1\bin\gradle.bat"
& $gradle testDebugUnitTest assembleDebug --no-daemon
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$source = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
$target = Join-Path $root "GDAD-BAGS-test.apk"
Copy-Item -LiteralPath $source -Destination $target -Force
Write-Output "APK ready: $target"
