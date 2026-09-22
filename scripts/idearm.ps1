# Launch the modular CLI after "mvn package". Tool installations stay outside the project file.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$cliJar = Get-ChildItem -Path (Join-Path $projectRoot 'idearm-cli/target') -Filter 'idearm-cli-*.jar' -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch '-(sources|javadoc|tests)\.jar$' } | Select-Object -First 1 -ExpandProperty FullName
$libraries = Join-Path $projectRoot 'idearm-cli/target/lib'
if (-not $cliJar -or -not (Test-Path -LiteralPath $libraries)) {
    Write-Error 'Build IDEARM first: mvn package'
    exit 2
}
$modulePath = $cliJar + [IO.Path]::PathSeparator + $libraries
& java '--enable-native-access=io.github.dinamo541.idearm.infrastructure' '--module-path' $modulePath '-m' 'io.github.dinamo541.idearm.cli/io.github.dinamo541.idearm.cli.Main' @args
exit $LASTEXITCODE
