/**
 * Lightweight local HTTP server for the Monaco Editor isolated prototype.
 * Serves Monaco assets, web test harness, and fixtures without external server dependencies.
 * Also provides an API endpoint to receive benchmark telemetry from browser hosts.
 */

const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = process.env.PORT || 8089;
const ROOT_DIR = path.resolve(__dirname);

const MIME_TYPES = {
    '.html': 'text/html; charset=utf-8',
    '.js': 'application/javascript; charset=utf-8',
    '.css': 'text/css; charset=utf-8',
    '.json': 'application/json; charset=utf-8',
    '.asm': 'text/plain; charset=utf-8',
    '.ttf': 'font/ttf',
    '.woff': 'font/woff',
    '.woff2': 'font/woff2'
};

const server = http.createServer((req, res) => {
    // API endpoint for receiving benchmark telemetry from browser runner
    if (req.method === 'POST' && req.url.startsWith('/api/benchmark-results')) {
        let body = '';
        req.on('data', chunk => { body += chunk; });
        req.on('end', () => {
            const outPath = path.join(ROOT_DIR, 'benchmark-results-browser.json');
            fs.writeFileSync(outPath, body, 'utf8');
            console.log('[IDEARM-SPIKE] Saved browser benchmark results to benchmark-results-browser.json');
            res.writeHead(200, { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
            res.end(JSON.stringify({ status: 'saved' }));
        });
        return;
    }

    let reqUrl = req.url.split('?')[0];
    if (reqUrl === '/' || reqUrl === '') {
        reqUrl = '/web/index.html';
    }

    // Guard 1: Handle malformed URI encoding
    let decodedPath;
    try {
        decodedPath = decodeURIComponent(reqUrl);
    } catch (err) {
        res.writeHead(400, { 'Content-Type': 'text/plain' });
        res.end('400 Bad Request: Malformed URI encoding');
        return;
    }

    // Guard 2: Prevent directory traversal and sibling prefix traversal via path.relative containment
    const filePath = path.resolve(ROOT_DIR, '.' + decodedPath);
    const rel = path.relative(ROOT_DIR, filePath);
    const isContained = !rel.startsWith('..') && !path.isAbsolute(rel);

    if (!isContained) {
        res.writeHead(403, { 'Content-Type': 'text/plain' });
        res.end('403 Forbidden: Access outside spike directory is prohibited');
        return;
    }

    fs.stat(filePath, (err, stats) => {
        if (err || !stats.isFile()) {
            res.writeHead(404, { 'Content-Type': 'text/plain' });
            res.end(`404 Not Found: ${reqUrl}`);
            return;
        }

        const ext = path.extname(filePath).toLowerCase();
        const contentType = MIME_TYPES[ext] || 'application/octet-stream';

        res.writeHead(200, {
            'Content-Type': contentType,
            'Content-Length': stats.size,
            'Access-Control-Allow-Origin': '*'
        });

        fs.createReadStream(filePath).pipe(res);
    });
});

if (require.main === module) {
    server.listen(PORT, '127.0.0.1', () => {
        console.log(`[IDEARM-SPIKE] Monaco Editor Host running at: http://127.0.0.1:${PORT}/web/index.html`);
    });
}

if (typeof module !== 'undefined' && module.exports) {
    module.exports = { server, PORT, ROOT_DIR };
}
