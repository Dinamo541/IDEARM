# Standalone Browser Host Benchmark Runner (Microsoft Edge / Chromium)
Write-Host "=== Running Standalone Chromium/Edge Monaco Benchmark ===" -ForegroundColor Cyan

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$edgePath = "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
if (-not (Test-Path $edgePath)) {
    Write-Error "Edge browser not found at: $edgePath"
    exit 1
}

$url = "http://127.0.0.1:8089/web/index.html?auto=true"
Write-Host "Launching Headless Edge at: $url"

$resultFile = Join-Path $scriptDir "benchmark-results-browser.json"
if (Test-Path $resultFile) {
    Remove-Item $resultFile -Force
}

$proc = Start-Process -FilePath $edgePath -ArgumentList @(
    "--headless=new",
    "--disable-gpu",
    "--no-first-run",
    "--no-default-browser-check",
    $url
) -PassThru

Write-Host "Process started with PID: $($proc.Id). Waiting for benchmark telemetry..."

$timeoutSec = 30
$elapsed = 0
while ($elapsed -lt $timeoutSec) {
    Start-Sleep -Seconds 1
    $elapsed++
    if (Test-Path $resultFile) {
        Write-Host "Received benchmark results in $elapsed seconds!" -ForegroundColor Green
        break
    }
}

# Stop the headless browser process
if (-not $proc.HasExited) {
    Stop-Process -Id $proc.Id -Force
}

if (Test-Path $resultFile) {
    Get-Content $resultFile
} else {
    Write-Error "Timed out waiting for browser benchmark results."
    exit 1
}
