# Standalone JavaFX WebView Host Execution Script
param(
    [switch]$Benchmark,
    [string]$Url = "http://127.0.0.1:8089/web/index.html"
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$binDir = Join-Path $scriptDir "bin"

$m2 = "$env:USERPROFILE\.m2\repository\org\openjfx"
$modulePath = "$m2\javafx-base\23.0.2\javafx-base-23.0.2-win.jar;$m2\javafx-graphics\23.0.2\javafx-graphics-23.0.2-win.jar;$m2\javafx-controls\23.0.2\javafx-controls-23.0.2-win.jar;$m2\javafx-media\23.0.2\javafx-media-23.0.2-win.jar;$m2\javafx-web\23.0.2\javafx-web-23.0.2-win.jar"

Write-Host "=== Running Standalone JavaFX WebView Host ===" -ForegroundColor Cyan
Write-Host "Target URL: $Url"
Write-Host "Benchmark Mode: $Benchmark"
Write-Host "Classpath: $binDir"

$javaArgs = @(
    "--module-path", $modulePath,
    "--add-modules", "javafx.controls,javafx.web",
    "-Dspike.url=$Url",
    "-Dspike.auto=$($Benchmark.IsPresent.ToString().ToLower())",
    "-Dspike.out=$scriptDir\benchmark-results-webview.json",
    "-cp", $binDir,
    "spikes.editor.StandaloneWebViewHost"
)

if ($Benchmark) {
    $javaArgs += "--benchmark"
}

java @javaArgs
