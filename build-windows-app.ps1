[CmdletBinding()]
param([switch]$Production)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$env:JAVA_HOME = Join-Path $root ".tooling\jdk\jdk-17.0.19+10"
$env:GRADLE_USER_HOME = Join-Path $root ".tooling\gradle-user-home"
if ($Production) { $env:GDAD_DESKTOP_PRODUCTION_RELEASE = "true" }

$tasks = @(
    ":desktopApp:clean",
    ":desktopApp:check",
    ":desktopApp:createDistributable"
)
if ($Production) { $tasks += ":desktopApp:verifyDesktopProductionReady" }

& (Join-Path $root "gradlew.bat") @tasks '-Pkotlin.compiler.execution.strategy=in-process' --no-daemon
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$application = Join-Path $root "desktopApp\build\compose\binaries\main\app\GDAD BAGS"
if (-not (Test-Path -LiteralPath $application -PathType Container)) {
    throw "The Windows application image was not produced."
}
Write-Output $application
