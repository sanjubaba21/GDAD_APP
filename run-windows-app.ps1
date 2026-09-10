$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$env:JAVA_HOME = Join-Path $root ".tooling\jdk\jdk-17.0.19+10"
$env:GRADLE_USER_HOME = Join-Path $root ".tooling\gradle-user-home"

& (Join-Path $root "gradlew.bat") :desktopApp:run '-Pkotlin.compiler.execution.strategy=in-process' --no-daemon
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
