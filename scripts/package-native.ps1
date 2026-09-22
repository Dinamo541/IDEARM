# Packages IDEARM as a standalone native Windows application via jpackage
[CmdletBinding()]
param(
    [ValidateSet('auto', 'app-image', 'msi', 'exe')]
    [string]$Type = 'auto'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$appTarget = Join-Path $projectRoot 'idearm-app/target'
$appJar = Join-Path $appTarget 'idearm-app-0.1.0-SNAPSHOT.jar'

if (-not (Test-Path -LiteralPath $appJar)) {
    Write-Host "Building project first (mvn package -DskipTests)..." -ForegroundColor Cyan
    & mvn package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Maven build failed."
        exit 1
    }
}

# Resolve packaging type
$hasWix = (Get-Command candle -ErrorAction SilentlyContinue) -ne $null
if ($Type -eq 'auto') {
    if ($hasWix) {
        $Type = 'msi'
        Write-Host "WiX Toolset detected. Target installer: MSI" -ForegroundColor Green
    } else {
        $Type = 'app-image'
        Write-Host "WiX Toolset not detected. Target packaging: Portable Application Image (app-image)" -ForegroundColor Yellow
    }
} elseif (($Type -eq 'msi' -or $Type -eq 'exe') -and -not $hasWix) {
    Write-Error "WiX Toolset (candle.exe, light.exe) is required for $Type installers. Install WiX Toolset 3.11+ or use -Type app-image."
    exit 2
}

# Prepare input directory with jars
$stagingDir = Join-Path $projectRoot 'target/jpackage-staging'
if (Test-Path $stagingDir) {
    Remove-Item -Recurse -Force $stagingDir
}
New-Item -ItemType Directory -Path $stagingDir | Out-Null

# Copy dependency jars and app jar
Write-Host "Gathering application modules and dependencies..." -ForegroundColor Cyan
Get-ChildItem -Path $projectRoot -Filter "*.jar" -Recurse | Where-Object {
    $_.FullName -match "target[\\/][^\\/]+\.jar$" -and
    $_.FullName -notmatch "original-" -and
    $_.FullName -notmatch "test-"
} | ForEach-Object {
    Copy-Item $_.FullName -Destination $stagingDir -Force
}

$destDir = Join-Path $projectRoot 'dist/native'
if (-not (Test-Path $destDir)) {
    New-Item -ItemType Directory -Path $destDir -Force | Out-Null
}

Write-Host "Invoking jpackage ($Type)..." -ForegroundColor Cyan
$jpackageArgs = @(
    '--input', $stagingDir,
    '--name', 'idearm',
    '--main-jar', 'idearm-app-0.1.0-SNAPSHOT.jar',
    '--main-class', 'io.github.dinamo541.idearm.app.App',
    '--type', $Type,
    '--dest', $destDir,
    '--app-version', '0.8.0',
    '--vendor', 'IDEARM Project',
    '--description', 'Educational Assembly IDE for x86 and x86-64',
    '--icon', (Join-Path $projectRoot 'idearm-app/src/main/resources/io/github/dinamo541/idearm/app/branding/idearm.ico'),
    '--java-options', '--enable-native-access=ALL-UNNAMED'
)

& jpackage @jpackageArgs

if ($LASTEXITCODE -eq 0) {
    Write-Host "Native packaging completed successfully in: $destDir" -ForegroundColor Green
} else {
    Write-Error "jpackage failed with exit code $LASTEXITCODE"
    exit $LASTEXITCODE
}
