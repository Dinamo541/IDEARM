/**
 * Comprehensive Automated Test Suite for IDEARM Monaco Bridge Contract.
 * 
 * Directly exercises the actual exported MonacoEditorBridge class from web/bridge.js
 * using a clean Monaco API stub.
 * 
 * Verifies:
 * 1. Benchmark Fixtures (load5k.asm, load20k.asm)
 * 2. Monarch Assembly Tokenizer Rules
 * 3. Leaked Model Prevention: Verifies init sets model: null
 * 4. Real Bridge Versioning: Stale & future-version diagnostics rejection
 * 5. Real Bridge Autocompletion: Async completion tracking & stale response rejection
 * 6. Real Bridge Debounce Lifecycle: Switching documents flushes pending debounced edits
 * 7. Real Bridge Debug Decorations: Breakpoints (active/disabled) & execution line pointer
 * 8. Real Bridge Disposal: Model cleanup and language provider unregistration
 */

const fs = require('fs');
const path = require('path');
const assert = require('assert');

// 1. Import the ACTUAL exported MonacoEditorBridge class
const { MonacoEditorBridge } = require('./web/bridge.js');

console.log('================================================================');
console.log('  IDEARM Monaco Editor Bridge Contract Test Suite               ');
console.log('================================================================\n');

// -----------------------------------------------------------------------------
// Monaco API Stub for Headless Node.js Execution
// -----------------------------------------------------------------------------
function createMonacoStub() {
    const models = new Map();
    const markers = new Map();
    const providerDisposables = [];

    class MockModel {
        constructor(text, languageId, uri) {
            this.text = text;
            this.languageId = languageId;
            this.uri = uri;
            this.disposed = false;
            this.changeListeners = [];
        }

        getValue() { return this.text; }
        setValue(t) { this.text = t; }
        getLineCount() { return this.text.split(/\r?\n/).length; }
        getLineContent(line) {
            const lines = this.text.split(/\r?\n/);
            return lines[line - 1] || '';
        }
        getWordAtPosition(pos) {
            const line = this.getLineContent(pos.lineNumber);
            const match = line.slice(0, pos.column).match(/([a-zA-Z_?@][a-zA-Z0-9_?@]*)$/);
            return match ? { word: match[1], startColumn: pos.column - match[1].length, endColumn: pos.column } : null;
        }
        getOffsetAt(pos) { return 0; }
        onDidChangeContent(listener) {
            this.changeListeners.push(listener);
            return {
                dispose: () => {
                    const idx = this.changeListeners.indexOf(listener);
                    if (idx >= 0) this.changeListeners.splice(idx, 1);
                }
            };
        }
        // Helper to simulate text edit event
        simulateEdit(newText, changes = [{ text: newText }]) {
            this.text = newText;
            this.changeListeners.forEach(fn => fn({ changes: changes }));
        }
        dispose() {
            this.disposed = true;
            this.changeListeners = [];
        }
    }

    class MockEditor {
        constructor(container, options) {
            this.container = container;
            this.options = options;
            this.currentModel = null;
            this.decorations = [];
            this.commands = new Map();
            this.disposed = false;
        }

        getModel() { return this.currentModel; }
        setModel(m) { this.currentModel = m; }
        getPosition() { return { lineNumber: 1, column: 1 }; }
        setPosition(pos) {}
        revealLineInCenter(line) {}
        focus() {}
        addCommand(key, handler) { this.commands.set(key, handler); }
        onDidChangeCursorPosition(listener) {}
        onMouseDown(listener) {}
        deltaDecorations(oldIds, newDecs) {
            this.decorations = newDecs;
            return newDecs.map((_, i) => 'dec_' + (i + 1));
        }
        executeEdits(source, edits) {
            if (this.currentModel && edits && edits[0]) {
                this.currentModel.setValue(this.currentModel.getValue() + edits[0].text);
            }
        }
        dispose() {
            this.disposed = true;
            this.currentModel = null;
        }
    }

    return {
        editor: {
            create: (container, options) => new MockEditor(container, options),
            createModel: (text, lang, uri) => {
                const m = new MockModel(text, lang, uri);
                models.set(uri.toString ? uri.toString() : uri, m);
                return m;
            },
            getModel: (uri) => models.get(uri.toString ? uri.toString() : uri),
            setModelMarkers: (model, owner, markerList) => {
                markers.set(model, markerList);
            },
            defineTheme: () => {}
        },
        languages: {
            registerCompletionItemProvider: (lang, provider) => {
                let disposed = false;
                const d = {
                    dispose: () => { disposed = true; },
                    isDisposed: () => disposed,
                    provider: provider
                };
                providerDisposables.push(d);
                return d;
            },
            registerHoverProvider: (lang, provider) => {
                let disposed = false;
                const d = {
                    dispose: () => { disposed = true; },
                    isDisposed: () => disposed,
                    provider: provider
                };
                providerDisposables.push(d);
                return d;
            },
            CompletionItemKind: { Keyword: 14, Variable: 4, Snippet: 27 },
            CompletionItemInsertTextRule: { InsertAsSnippet: 4 }
        },
        Uri: {
            parse: (u) => ({ toString: () => u })
        },
        Range: class {
            constructor(startLineNumber, startColumn, endLineNumber, endColumn) {
                this.startLineNumber = startLineNumber;
                this.startColumn = startColumn;
                this.endLineNumber = endLineNumber;
                this.endColumn = endColumn;
            }
        },
        MarkerSeverity: { Error: 8, Warning: 4, Info: 2, Hint: 1 },
        KeyCode: { F9: 67, F12: 70 },
        KeyMod: { Shift: 1024 },
        _getModels: () => models,
        _getMarkers: () => markers,
        _getProviderDisposables: () => providerDisposables
    };
}

// -----------------------------------------------------------------------------
// Test 1: Validate Benchmark Fixtures
// -----------------------------------------------------------------------------
console.log('[Test 1] Validating Benchmark Fixtures...');
const fix5kPath = path.join(__dirname, 'fixtures', 'load5k.asm');
const fix20kPath = path.join(__dirname, 'fixtures', 'load20k.asm');

assert(fs.existsSync(fix5kPath), 'load5k.asm must exist');
assert(fs.existsSync(fix20kPath), 'load20k.asm must exist');

const content5k = fs.readFileSync(fix5kPath, 'utf8');
const lines5k = content5k.split(/\r?\n/).length;
assert.strictEqual(lines5k, 5000, '5k fixture must have exactly 5000 lines');
console.log(`  ✓ 5k fixture: ${lines5k} lines (${(content5k.length / 1024).toFixed(1)} KB)`);

const content20k = fs.readFileSync(fix20kPath, 'utf8');
const lines20k = content20k.split(/\r?\n/).length;
assert.strictEqual(lines20k, 20000, '20k fixture must have exactly 20000 lines');
console.log(`  ✓ 20k fixture: ${lines20k} lines (${(content20k.length / 1024).toFixed(1)} KB)`);

// -----------------------------------------------------------------------------
// Test 2: Monarch Assembly Tokenizer Rules
// -----------------------------------------------------------------------------
console.log('\n[Test 2] Validating Monarch Assembly Tokenizer Rules...');
const mockWindow = {};
const syntaxCode = fs.readFileSync(path.join(__dirname, 'web', 'assembly-syntax.js'), 'utf8');
const vm = require('vm');
vm.runInNewContext(syntaxCode, { window: mockWindow });

const asmDef = mockWindow.ASM_LANGUAGE_DEFINITION;
assert(asmDef, 'ASM_LANGUAGE_DEFINITION must be exported');
assert(asmDef.instructions.includes('mov'), 'Instructions must include mov');
assert(asmDef.instructions.includes('int'), 'Instructions must include int');
assert(asmDef.registers.includes('ax'), 'Registers must include ax');
assert(asmDef.directives.includes('.model'), 'Directives must include .model');
console.log(`  ✓ Monarch syntax verified: ${asmDef.instructions.length} instructions, ${asmDef.registers.length} registers, ${asmDef.directives.length} directives`);

// -----------------------------------------------------------------------------
// Test 3: Initialize Real MonacoEditorBridge & Guard Against Leaked Anonymous Model
// -----------------------------------------------------------------------------
console.log('\n[Test 3] Exercising MonacoEditorBridge.init() & Anonymous Model Leak Prevention...');
const monacoStub = createMonacoStub();
const bridge = new MonacoEditorBridge();
const events = [];
bridge.onEvent = (ev) => events.push(ev);

const container = {};
bridge.init(container, {}, monacoStub);

// Verify Monaco was called with model: null
assert.strictEqual(bridge.editor.options.model, null, 'Editor init must pass model: null to prevent leaking an anonymous model');
assert.strictEqual(bridge.providerDisposables.length, 2, 'Must track completion and hover provider registrations');
console.log('  ✓ Bridge initialized with model: null (no leaked initial anonymous model)');
console.log(`  ✓ Tracked ${bridge.providerDisposables.length} language provider disposables`);

// -----------------------------------------------------------------------------
// Test 4: Real Bridge Versioning (Stale & Future-Version Diagnostics)
// -----------------------------------------------------------------------------
console.log('\n[Test 4] Testing Monotonic Versioning (Stale & Future Diagnostics on Real Bridge)...');
const uri = 'file:///test-main.asm';
bridge.openDocument({ uri: uri, text: 'mov ax, @data\nint 21h\n', version: 1 });

const entry = bridge.models.get(uri);
assert(entry, 'Model entry must exist for opened document');
assert.strictEqual(entry.version, 1, 'Initial document version must be 1');

// Case A: Diagnostic matching current version (v1) -> Accepted
const v1Applied = bridge.setDiagnostics({
    uri: uri,
    version: 1,
    diagnostics: [{ line: 1, column: 1, length: 3, severity: 'INFO', message: 'Valid v1 lint' }]
});
assert.strictEqual(v1Applied, true, 'Diagnostic matching current version (v1) must be applied');

// Simulate user edits -> model advances to version 2
entry.model.simulateEdit('mov ax, 4C00h\nint 21h\n');
assert.strictEqual(entry.version, 2, 'Model version must increment to 2 upon edit');

// Case B: Stale diagnostic from slow linter computed against v1 -> Must be dropped
const v1StaleApplied = bridge.setDiagnostics({
    uri: uri,
    version: 1,
    diagnostics: [{ line: 1, column: 1, length: 3, severity: 'ERROR', message: 'Stale v1 lint' }]
});
assert.strictEqual(v1StaleApplied, false, 'Stale diagnostic (v1 < v2) must be dropped by real bridge');
const staleDropEv = events.find(e => e.type === 'onStaleDiagnosticsDropped');
assert(staleDropEv, 'Bridge must emit onStaleDiagnosticsDropped event');
assert.strictEqual(staleDropEv.payload.receivedVersion, 1);
assert.strictEqual(staleDropEv.payload.currentVersion, 2);
console.log('  ✓ Stale diagnostic (received v1 < current v2) correctly dropped with telemetry');

// Case C: Future desync diagnostic (received v5 > current v2) -> Must be rejected
const v5FutureApplied = bridge.setDiagnostics({
    uri: uri,
    version: 5,
    diagnostics: [{ line: 1, column: 1, length: 3, severity: 'ERROR', message: 'Future v5 desync lint' }]
});
assert.strictEqual(v5FutureApplied, false, 'Future-version diagnostic (v5 > v2) must be rejected by real bridge');
const futureDropEv = events.find(e => e.type === 'onFutureDiagnosticsDropped');
assert(futureDropEv, 'Bridge must emit onFutureDiagnosticsDropped event');
assert.strictEqual(futureDropEv.payload.receivedVersion, 5);
assert.strictEqual(futureDropEv.payload.currentVersion, 2);
console.log('  ✓ Future-version diagnostic (received v5 > current v2) correctly rejected');

// Case D: Fresh diagnostic matching current version (v2) -> Accepted
const v2Applied = bridge.setDiagnostics({
    uri: uri,
    version: 2,
    diagnostics: [{ line: 1, column: 1, length: 3, severity: 'WARNING', message: 'Fresh v2 lint' }]
});
assert.strictEqual(v2Applied, true, 'Fresh diagnostic matching current version (v2) must be applied');
console.log('  ✓ Fresh diagnostic (v2 == v2) correctly applied');

// -----------------------------------------------------------------------------
// Test 5: Real Bridge Async Autocompletion & Stale Response Drop Guard
// -----------------------------------------------------------------------------
console.log('\n[Test 5] Testing Async Autocompletion & Stale Completion Response Guard...');
// Document is currently at v2
const compReq = bridge.requestCompletions({ uri: uri, line: 1, column: 5, prefix: 'mo' });
assert.strictEqual(compReq.requestId, 1);
assert(bridge.pendingCompletions.has(1), 'Bridge must store pending completion request');

// Simulate document edit while request is in flight -> version increments to v3
entry.model.simulateEdit('mov bx, 10h\n');
assert.strictEqual(entry.version, 3, 'Document version advanced to 3');

// Incoming completion response was computed against v2 -> Stale!
let resolvedItems = null;
compReq.promise.then(items => { resolvedItems = items; });

const staleCompAccepted = bridge.provideCompletionsResponse({
    requestId: 1,
    version: 2, // Older than current v3
    items: ['mov', 'movzx']
});
assert.strictEqual(staleCompAccepted, false, 'Stale completion response (v2 < v3) must be rejected');
const staleCompEv = events.find(e => e.type === 'onStaleCompletionDropped');
assert(staleCompEv, 'Bridge must emit onStaleCompletionDropped event');
assert.strictEqual(staleCompEv.payload.requestId, 1);
assert.strictEqual(staleCompEv.payload.receivedVersion, 2);
assert.strictEqual(staleCompEv.payload.currentVersion, 3);
console.log('  ✓ Stale autocompletion response correctly dropped when document version has advanced');

// Test Future completion response (version > current) -> Must be rejected
const futureReq = bridge.requestCompletions({ uri: uri, line: 1, column: 5, prefix: 'mo' });
const futureCompAccepted = bridge.provideCompletionsResponse({
    requestId: futureReq.requestId,
    version: 99, // Future version ahead of current v3
    items: ['future_item']
});
assert.strictEqual(futureCompAccepted, false, 'Future-version completion response (v99 > v3) must be rejected');
const futureCompEv = events.find(e => e.type === 'onFutureCompletionDropped');
assert(futureCompEv, 'Bridge must emit onFutureCompletionDropped event');
assert.strictEqual(futureCompEv.payload.requestId, futureReq.requestId);
assert.strictEqual(futureCompEv.payload.receivedVersion, 99);
assert.strictEqual(futureCompEv.payload.currentVersion, 3);
console.log('  ✓ Future-version autocompletion response correctly rejected when version is ahead of model');

// Valid completion response matching version
const freshReq = bridge.requestCompletions({ uri: uri, line: 1, column: 5, prefix: 'mo' });
const freshCompAccepted = bridge.provideCompletionsResponse({
    requestId: freshReq.requestId,
    version: 3,
    items: ['mov', 'movsb']
});
assert.strictEqual(freshCompAccepted, true, 'Fresh completion response matching version 3 must be accepted');
console.log('  ✓ Fresh autocompletion response matching current version correctly resolved');

// -----------------------------------------------------------------------------
// Test 6: Switching Documents While Debounced Edits are Pending
// -----------------------------------------------------------------------------
console.log('\n[Test 6] Testing Switching Documents While Debounced Edits are Pending...');
const docA = 'file:///docA.asm';
const docB = 'file:///docB.asm';

bridge.openDocument({ uri: docA, text: '; Document A initial\n', version: 1 });
const entryA = bridge.models.get(docA);

// Trigger an edit on Doc A. This sets pendingChanges and schedules debounceTimer
entryA.model.simulateEdit('; Document A modified\n');
assert(entryA.debounceTimer !== null, 'Doc A must have a pending debounce timer scheduled');
assert(entryA.pendingChanges !== null, 'Doc A must have pending changes buffered');

// Before timer fires, immediately switch to Doc B
const switchStartIdx = events.length;

bridge.openDocument({ uri: docB, text: '; Document B initial\n', version: 1 });

// Real bridge must have automatically flushed Doc A's pending changes!
assert.strictEqual(entryA.debounceTimer, null, 'Doc A debounce timer must be cleared after flush');
assert.strictEqual(entryA.pendingChanges, null, 'Doc A pending changes must be cleared after flush');

const flushedChange = events.slice(switchStartIdx).find(e => e.type === 'onDocumentChanged' && e.payload.uri === docA);
assert(flushedChange, 'Bridge must emit onDocumentChanged for Doc A upon switching to Doc B');
assert.strictEqual(flushedChange.payload.fullText, '; Document A modified\n');
assert.strictEqual(bridge.activeUri, docB, 'Active URI must now be Doc B');
console.log('  ✓ Switching documents cleanly and immediately flushed pending debounced edits for previous document');

// -----------------------------------------------------------------------------
// Test 7: Debug Breakpoints & Execution Pointer Decorations
// -----------------------------------------------------------------------------
console.log('\n[Test 7] Testing Debug Breakpoints & Execution Line Decorations...');
bridge.setBreakpoints({
    uri: docB,
    activeLines: [5, 12],
    disabledLines: [8]
});
assert.strictEqual(bridge.editor.decorations.length, 3, 'Must render 3 breakpoint decorations');
assert.strictEqual(bridge.editor.decorations[0].options.glyphMarginClassName, 'glyph-breakpoint-active');
assert.strictEqual(bridge.editor.decorations[2].options.glyphMarginClassName, 'glyph-breakpoint-disabled');
console.log('  ✓ Active and disabled breakpoint glyph margin classes mapped correctly');

bridge.setExecutionLine({ uri: docB, line: 5, reveal: true });
assert.strictEqual(bridge.editor.decorations.length, 1, 'Must render 1 execution line decoration');
assert.strictEqual(bridge.editor.decorations[0].options.glyphMarginClassName, 'glyph-execution-line');
assert.strictEqual(bridge.editor.decorations[0].options.className, 'line-execution-highlight');

bridge.setExecutionLine({ uri: docB, line: null });
assert.strictEqual(bridge.editor.decorations.length, 0, 'Null line must clear execution line decoration');
console.log('  ✓ Execution pointer decoration sets and clears properly');

// -----------------------------------------------------------------------------
// Test 8: Disposal & Language Provider Cleanup
// -----------------------------------------------------------------------------
console.log('\n[Test 8] Testing Document Disposal & Language Provider Unregistration...');
assert.strictEqual(bridge.models.size, 3, 'Bridge currently holds 3 models (test-main, docA, docB)');

// Test in-flight completion cancellation on document disposal
const inFlightA = bridge.requestCompletions({ uri: docA, line: 1, column: 5, prefix: 'in' });
assert(bridge.pendingCompletions.has(inFlightA.requestId), 'Pending completion must be registered for Doc A');

let inFlightAResolved = null;
inFlightA.promise.then(res => { inFlightAResolved = res; });

bridge.disposeDocument(docA);
assert.strictEqual(bridge.models.has(docA), false, 'Doc A model must be removed from bridge models');
assert.strictEqual(entryA.model.disposed, true, 'Doc A model must have its dispose() method invoked');
assert.strictEqual(bridge.pendingCompletions.has(inFlightA.requestId), false, 'In-flight completion for Doc A must be removed from pending map');
const cancelEvA = events.find(e => e.type === 'onCompletionCancelled' && e.payload.requestId === inFlightA.requestId);
assert(cancelEvA, 'Must emit onCompletionCancelled when document is disposed');
console.log('  ✓ In-flight completion cancelled cleanly when document is disposed');

// Test in-flight completion cancellation on full editor disposal
const inFlightB = bridge.requestCompletions({ uri: docB, line: 1, column: 5, prefix: 'in' });
assert(bridge.pendingCompletions.has(inFlightB.requestId));

// Dispose entire editor
const registeredProviders = monacoStub._getProviderDisposables();
assert.strictEqual(registeredProviders.length, 2);
assert.strictEqual(registeredProviders[0].isDisposed(), false, 'Provider must not be disposed before editor disposal');

bridge.disposeEditor();
assert.strictEqual(bridge.models.size, 0, 'All models must be cleared from bridge registry');
assert.strictEqual(bridge.providerDisposables.length, 0, 'Provider disposables list must be cleared');
assert.strictEqual(bridge.pendingCompletions.size, 0, 'All pending completions must be cleared on editor disposal');
assert.strictEqual(registeredProviders[0].isDisposed(), true, 'Completion provider must have .dispose() called');
assert.strictEqual(registeredProviders[1].isDisposed(), true, 'Hover provider must have .dispose() called');
assert.strictEqual(bridge.editor, null, 'Editor instance must be reset to null');
console.log('  ✓ Full editor disposal: models destroyed, language providers unregistered, in-flight requests cancelled');

console.log('\n================================================================');
console.log('  >>> ALL 8 REAL BRIDGE CONTRACT TESTS PASSED SUCCESSFULLY! <<< ');
console.log('================================================================\n');
