# Launch the modular CLI after "mvn package". Tool installations stay outside the project file.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$cliJar = Join-Path $projectRoot 'idearm-cli/target/idearm-cli-0.1.0-SNAPSHOT.jar'
$libraries = Join-Path $projectRoot 'idearm-cli/target/lib'
if (-not (Test-Path -LiteralPath $cliJar) -or -not (Test-Path -LiteralPath $libraries)) {
    Write-Error 'Build IDEARM first: mvn package'
    exit 2
}
$modulePath = $cliJar + [IO.Path]::PathSeparator + $libraries
& java '--enable-native-access=io.github.dinamo541.idearm.infrastructure' '--module-path' $modulePath '-m' 'io.github.dinamo541.idearm.cli/io.github.dinamo541.idearm.cli.Main' @args
exit $LASTEXITCODE
