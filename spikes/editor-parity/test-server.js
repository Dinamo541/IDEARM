/**
 * Automated security and path containment tests for spikes/editor-parity/server.js.
 */

const http = require('http');
const assert = require('assert');
const { server } = require('./server.js');

console.log('Testing server.js security and containment guards on isolated test port...');

server.listen(0, '127.0.0.1', async () => {
    const testPort = server.address().port;
    console.log(`Test server bound to 127.0.0.1:${testPort}`);

    function makeRequest(path) {
        return new Promise((resolve, reject) => {
            const req = http.get({
                hostname: '127.0.0.1',
                port: testPort,
                path: path
            }, (res) => {
                let data = '';
                res.on('data', chunk => data += chunk);
                res.on('end', () => resolve({ statusCode: res.statusCode, data }));
            });
            req.on('error', reject);
        });
    }

    try {
        // Test 1: Normal valid file -> 200
        const validRes = await makeRequest('/web/index.html');
        assert.strictEqual(validRes.statusCode, 200, 'Valid file must return 200');
        console.log('✓ Normal request returns 200 OK');

        // Test 2: Malformed URI encoding -> 400
        const malformedRes = await makeRequest('/%E0%A4%A');
        assert.strictEqual(malformedRes.statusCode, 400, 'Malformed URI must return 400 Bad Request');
        console.log('✓ Malformed URI encoding returns 400 Bad Request (handled safely without crash)');

        // Test 3: Sibling prefix / directory traversal -> 403
        const traversalRes = await makeRequest('/../../pom.xml');
        assert.strictEqual(traversalRes.statusCode, 403, 'Directory traversal must return 403 Forbidden');
        console.log('✓ Directory traversal returns 403 Forbidden (path.relative containment enforced)');

        console.log('>>> All server.js security tests passed! <<<');
    } catch (err) {
        console.error('Server test error:', err);
        process.exit(1);
    } finally {
        server.close();
    }
});
