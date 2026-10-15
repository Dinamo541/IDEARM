/**
 * Fully Reproducible Benchmark Runner for Editor Parity Spike.
 * 
 * Executes:
 * 1. Real Bridge Contract Tests & Server Security Tests
 * 2. Automated Standalone Browser Benchmark (Chromium / Edge Headless)
 * 3. Standalone JavaFX WebView Host Benchmark
 * 
 * Manages local server lifecycle automatically if not already running.
 */

const { execSync, spawn } = require('child_process');
const http = require('http');
const fs = require('fs');
const path = require('path');

const ROOT_DIR = __dirname;
const PORT = 8089;
const browserResultsPath = path.join(ROOT_DIR, 'benchmark-results-browser.json');
const webviewResultsPath = path.join(ROOT_DIR, 'benchmark-results-webview.json');

const args = process.argv.slice(2);
const forceRerun = args.includes('--rerun') || args.includes('-f');

console.log('================================================================');
console.log('  IDEARM Editor Parity Benchmark Harness (Task C Spike)         ');
console.log('================================================================\n');

// Helper to check if server is already responding
function isServerRunning() {
    return new Promise((resolve) => {
        const req = http.get({
            hostname: '127.0.0.1',
            port: PORT,
            path: '/web/index.html',
            timeout: 1000
        }, (res) => {
            resolve(res.statusCode === 200);
        });
        req.on('error', () => resolve(false));
        req.on('timeout', () => { req.destroy(); resolve(false); });
    });
}

function waitForServer(maxAttempts = 15) {
    return new Promise((resolve, reject) => {
        let attempts = 0;
        const interval = setInterval(async () => {
            attempts++;
            const running = await isServerRunning();
            if (running) {
                clearInterval(interval);
                resolve(true);
            } else if (attempts >= maxAttempts) {
                clearInterval(interval);
                reject(new Error(`Server failed to start on 127.0.0.1:${PORT} after ${maxAttempts} attempts`));
            }
        }, 300);
    });
}

async function main() {
    // -------------------------------------------------------------------------
    // Step 1: Run Unit / Contract / Security Tests
    // -------------------------------------------------------------------------
    console.log('--- Step 1: Running Bridge Contract & Server Security Tests ---');
    try {
        execSync('node test-bridge.js', { cwd: ROOT_DIR, stdio: 'inherit' });
        execSync('node test-server.js', { cwd: ROOT_DIR, stdio: 'inherit' });
    } catch (err) {
        console.error('Contract or security tests failed:', err.message);
        process.exit(1);
    }

    // -------------------------------------------------------------------------
    // Server Lifecycle Management
    // -------------------------------------------------------------------------
    let serverProcess = null;
    let startedOwnServer = false;

    const alreadyRunning = await isServerRunning();
    if (!alreadyRunning) {
        console.log(`\nLocal HTTP server not detected on port ${PORT}. Spawning server.js...`);
        serverProcess = spawn('node', ['server.js'], {
            cwd: ROOT_DIR,
            stdio: ['ignore', 'pipe', 'pipe']
        });
        startedOwnServer = true;

        try {
            await waitForServer();
            console.log(`Local server is up and listening on http://127.0.0.1:${PORT}`);
        } catch (e) {
            console.error('Could not start server:', e.message);
            if (serverProcess) serverProcess.kill();
            process.exit(1);
        }
    } else {
        console.log(`\nLocal HTTP server already active on http://127.0.0.1:${PORT}`);
    }

    try {
        // ---------------------------------------------------------------------
        // Step 2: Standalone Browser Benchmark (Chromium / Edge)
        // ---------------------------------------------------------------------
        console.log('\n--- Step 2: Evaluating Standalone Browser Host (Chromium / Edge) ---');
        const shouldRunBrowser = forceRerun || !fs.existsSync(browserResultsPath);

        if (shouldRunBrowser) {
            console.log('Executing fresh browser benchmark via run-browser-benchmark.ps1...');
            try {
                execSync('powershell -ExecutionPolicy Bypass -File run-browser-benchmark.ps1', {
                    cwd: ROOT_DIR,
                    stdio: 'inherit'
                });
            } catch (err) {
                console.warn('Browser benchmark runner failed or Edge not available:', err.message);
            }
        }

        if (fs.existsSync(browserResultsPath)) {
            const raw = fs.readFileSync(browserResultsPath, 'utf8');
            const data = JSON.parse(raw);
            printBenchmarkSummary('Chromium / Edge Host (Measured Data)', data);
        } else {
            console.log('No browser benchmark results recorded.');
        }

        // ---------------------------------------------------------------------
        // Step 3: Standalone JavaFX WebView Host Benchmark
        // ---------------------------------------------------------------------
        console.log('\n--- Step 3: Evaluating Standalone JavaFX WebView Host ---');
        const shouldRunWebview = forceRerun || !fs.existsSync(webviewResultsPath);

        if (shouldRunWebview) {
            console.log('Executing fresh JavaFX WebView benchmark via run-webview-host.ps1 -Benchmark...');
            try {
                execSync('powershell -ExecutionPolicy Bypass -File run-webview-host.ps1 -Benchmark', {
                    cwd: ROOT_DIR,
                    stdio: 'inherit'
                });
            } catch (err) {
                console.warn('JavaFX WebView host could not be run directly:', err.message);
            }
        }

        if (fs.existsSync(webviewResultsPath)) {
            const raw = fs.readFileSync(webviewResultsPath, 'utf8');
            const parsed = JSON.parse(raw);
            const data = parsed.payload || parsed;
            printBenchmarkSummary('JavaFX 23 WebView (WebKit 619.1 - Measured Data)', data);
        } else {
            console.log('No JavaFX WebView benchmark results recorded.');
        }

    } finally {
        if (startedOwnServer && serverProcess) {
            console.log('\nShutting down temporary local HTTP server process...');
            serverProcess.kill();
        }
    }

    console.log('\n================================================================');
    console.log('  Benchmark Evaluation Complete.                                ');
    console.log('================================================================\n');
}

function printBenchmarkSummary(hostName, res) {
    console.log(`\n=== Summary: ${hostName} ===`);
    if (res.load5k) {
        console.log(`  5k Lines Load:     ${res.load5k.totalMs.toFixed(1)} ms (bridge: ${res.load5k.bridgeMs.toFixed(1)} ms)`);
    }
    if (res.typing5k) {
        console.log(`  5k API Edit Time:  p50: ${res.typing5k.p50.toFixed(2)} ms | p95: ${res.typing5k.p95.toFixed(2)} ms | max: ${res.typing5k.max.toFixed(2)} ms`);
    }
    if (res.diag5k) {
        console.log(`  5k Diagnostics:    ${res.diag5k.markerCount} markers in ${res.diag5k.durationMs.toFixed(2)} ms`);
    }
    if (res.load20k) {
        console.log(`  20k Lines Load:    ${res.load20k.totalMs.toFixed(1)} ms (bridge: ${res.load20k.bridgeMs.toFixed(1)} ms)`);
    }
    if (res.typing20k) {
        console.log(`  20k API Edit Time: p50: ${res.typing20k.p50.toFixed(2)} ms | p95: ${res.typing20k.p95.toFixed(2)} ms | max: ${res.typing20k.max.toFixed(2)} ms`);
    }
    if (res.diag20k) {
        console.log(`  20k Diagnostics:   ${res.diag20k.markerCount} markers in ${res.diag20k.durationMs.toFixed(2)} ms`);
    }
    if (res.disposal) {
        console.log(`  Tab Disposal:      ${res.disposal.durationMs.toFixed(1)} ms (retained models in bridge: ${res.disposal.retainedModels})`);
    }
}

main().catch(err => {
    console.error('Fatal error in benchmark runner:', err);
    process.exit(1);
});
