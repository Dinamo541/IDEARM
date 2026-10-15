/**
 * IDEARM Monaco Editor Bridge Contract Implementation.
 * 
 * Manages bi-directional communication between host (JavaFX WebView / Test Harness)
 * and Monaco Editor instance.
 * 
 * Features:
 * - Strict monotonic document versioning & stale/future diagnostic drop guards
 * - Async autocompletion request/response protocol with stale completion drop guards
 * - Per-document debounce tracking and automatic flush on document switching
 * - Breakpoint gutter decorations (active/disabled) & click toggles
 * - Execution line pointer decoration & smooth scrolling
 * - Assembly syntax support, hover cards & autocompletion integration
 * - Caret tracking & word/prefix extraction
 * - Comprehensive lifecycle disposal of models and language providers without leaks
 */

(function (root, factory) {
    if (typeof module !== 'undefined' && module.exports) {
        // Node.js environment
        module.exports = factory();
    } else {
        // Browser environment
        root.MonacoEditorBridge = factory().MonacoEditorBridge;
        root.monacoBridge = new root.MonacoEditorBridge();
    }
})(typeof self !== 'undefined' ? self : this, function () {

    class MonacoEditorBridge {
        constructor() {
            this.editor = null;
            this.models = new Map(); // uri -> { model, version, isDirty, debounceTimer, pendingChanges, disposables }
            this.activeUri = null;
            this.breakpointDecorationIds = [];
            this.executionDecorationIds = [];
            this.providerDisposables = []; // Track language provider registrations for clean disposal
            this.changeDebounceMs = 50; // Responsive edit debounce
            this.javaBridge = (typeof window !== 'undefined' && window.idearmJavaBridge) ? window.idearmJavaBridge : null;
            this.metrics = {
                loadTimes: [],
                typingLatencies: [],
                diagnosticLatencies: []
            };

            // Pending request callbacks for async language features
            this.pendingCompletions = new Map();
            this.pendingHovers = new Map();
            this.requestIdCounter = 0;
        }

        /**
         * Initialize the Monaco editor container with IDEARM theme and configurations.
         * Sets model: null by default to avoid leaking an initial anonymous model.
         */
        init(containerElement, options = {}, monacoInstance = (typeof monaco !== 'undefined' ? monaco : null)) {
            const m = monacoInstance;
            if (!m) {
                this.log('ERROR', 'Monaco instance not provided or loaded');
                return null;
            }

            const defaultOptions = {
                model: null, // Critical: prevent leaked initial anonymous model
                language: 'asm8086',
                theme: 'idearm-dark',
                fontSize: 13,
                fontFamily: "Consolas, 'Courier New', monospace",
                lineHeight: 21,
                lineNumbers: 'on',
                glyphMargin: true,
                minimap: { enabled: false },
                wordWrap: 'off',
                scrollBeyondLastLine: false,
                renderWhitespace: 'selection',
                automaticLayout: true,
                tabSize: 4,
                insertSpaces: true,
                cursorBlinking: 'smooth',
                cursorSmoothCaretAnimation: 'on',
                smoothScrolling: true,
                fixedOverflowWidgets: true
            };

            const merged = Object.assign({}, defaultOptions, options);

            // Define IDEARM dark theme matching VS Code / Workbench specifications
            if (m.editor && typeof m.editor.defineTheme === 'function') {
                m.editor.defineTheme('idearm-dark', {
                    base: 'vs-dark',
                    inherit: true,
                    rules: [
                        { token: 'keyword.instruction', foreground: '4EC9B0', fontStyle: 'bold' },
                        { token: 'variable.register', foreground: '9CDCFE', fontStyle: 'bold' },
                        { token: 'keyword.directive', foreground: 'C586C0' },
                        { token: 'type.identifier', foreground: 'DCDCAA' },
                        { token: 'comment', foreground: '6A9955', fontStyle: 'italic' },
                        { token: 'string', foreground: 'CE9178' },
                        { token: 'number.hex', foreground: 'B5CEA8' },
                        { token: 'number.binary', foreground: 'B5CEA8' },
                        { token: 'number', foreground: 'B5CEA8' }
                    ],
                    colors: {
                        'editor.background': '#1E1E1E',
                        'editor.foreground': '#D4D4D4',
                        'editor.lineHighlightBackground': '#2A2D2E',
                        'editorLineNumber.foreground': '#858585',
                        'editorLineNumber.activeForeground': '#C6C6C6',
                        'editorGutter.background': '#1E1E1E',
                        'editorCursor.foreground': '#AEAFAD',
                        'editor.selectionBackground': '#264F78',
                        'editor.inactiveSelectionBackground': '#3A3D41'
                    }
                });
            }

            this.monaco = m;
            this.editor = m.editor.create(containerElement, merged);
            this.registerProviders(m);
            this.setupEditorListeners(m);
            this.setupKeybindings(m);

            this.emit('onReady', { version: 'monaco-loaded' });
            return this.editor;
        }

        /**
         * Set up editor events for changes, caret, and gutter clicks.
         */
        setupEditorListeners(m = this.monaco) {
            if (!this.editor) return;

            // Caret movement tracking
            if (typeof this.editor.onDidChangeCursorPosition === 'function') {
                this.editor.onDidChangeCursorPosition(e => {
                    const pos = e.position;
                    const model = this.editor.getModel();
                    if (!model) return;

                    const lineContent = model.getLineContent(pos.lineNumber);
                    const offset = model.getOffsetAt ? model.getOffsetAt(pos) : 0;
                    const wordInfo = model.getWordAtPosition ? model.getWordAtPosition(pos) : null;
                    const word = wordInfo ? wordInfo.word : '';
                    
                    // Prefix immediately preceding caret (for autocompletion matching)
                    const textBefore = lineContent ? lineContent.substring(0, pos.column - 1) : '';
                    const prefixMatch = textBefore.match(/([a-zA-Z_?@][a-zA-Z0-9_?@]*)$/);
                    const prefix = prefixMatch ? prefixMatch[1] : '';

                    this.emit('onCaretChanged', {
                        uri: this.activeUri,
                        line: pos.lineNumber,
                        column: pos.column,
                        offset: offset,
                        word: word,
                        prefix: prefix
                    });
                });
            }

            // Gutter mouse click for breakpoints
            if (typeof this.editor.onMouseDown === 'function') {
                this.editor.onMouseDown(e => {
                    if (e.target && e.target.type === m.editor.MouseTargetType.GUTTER_GLYPH_MARGIN) {
                        const line = e.target.position.lineNumber;
                        this.emit('onBreakpointToggled', {
                            uri: this.activeUri,
                            line: line
                        });
                    }
                });
            }
        }

        /**
         * Register shortcuts: F9 (toggle breakpoint), F12 (definition), Shift+F12 (references), Shift+F1 (academic center)
         */
        setupKeybindings(m = this.monaco) {
            if (!this.editor || typeof this.editor.addCommand !== 'function') return;

            // F9: Toggle breakpoint
            this.editor.addCommand(m.KeyCode.F9, () => {
                const pos = this.editor.getPosition();
                if (pos) {
                    this.emit('onBreakpointToggled', { uri: this.activeUri, line: pos.lineNumber });
                }
            });

            // F12: Go to Definition
            this.editor.addCommand(m.KeyCode.F12, () => {
                this.requestSymbolAction('onDefinitionRequested');
            });

            // Shift+F12: Find References
            this.editor.addCommand(m.KeyMod.Shift | m.KeyCode.F12, () => {
                this.requestSymbolAction('onReferencesRequested');
            });

            // Shift+F1: Academic Center documentation
            this.editor.addCommand(m.KeyMod.Shift | m.KeyCode.F1, () => {
                this.requestSymbolAction('onAcademicCenterRequested');
            });
        }

        requestSymbolAction(eventName) {
            if (!this.editor) return;
            const pos = this.editor.getPosition();
            const model = this.editor.getModel();
            if (!pos || !model) return;
            const wordInfo = model.getWordAtPosition ? model.getWordAtPosition(pos) : null;
            const word = wordInfo ? wordInfo.word : '';
            this.emit(eventName, {
                uri: this.activeUri,
                line: pos.lineNumber,
                column: pos.column,
                word: word
            });
        }

        /**
         * Register language providers (Hover, Completion) with tracked disposables.
         */
        registerProviders(m = this.monaco) {
            if (!m || !m.languages) return;
            const self = this;

            // Autocompletion Provider
            const compDisp = m.languages.registerCompletionItemProvider('asm8086', {
                triggerCharacters: ['.', '@', ' '],
                provideCompletionItems: function (model, position) {
                    const wordInfo = model.getWordUntilPosition ? model.getWordUntilPosition(position) : { startColumn: 1, endColumn: 1 };
                    const range = {
                        startLineNumber: position.lineNumber,
                        endLineNumber: position.lineNumber,
                        startColumn: wordInfo.startColumn,
                        endColumn: wordInfo.endColumn
                    };

                    const suggestions = [
                        { label: 'mov', kind: m.languages.CompletionItemKind.Keyword, insertText: 'mov ${1:dest}, ${2:src}', insertTextRules: m.languages.CompletionItemInsertTextRule ? m.languages.CompletionItemInsertTextRule.InsertAsSnippet : 4, detail: 'Move byte or word', documentation: 'Copies data from source to destination operand.' },
                        { label: 'add', kind: m.languages.CompletionItemKind.Keyword, insertText: 'add ${1:dest}, ${2:src}', insertTextRules: m.languages.CompletionItemInsertTextRule ? m.languages.CompletionItemInsertTextRule.InsertAsSnippet : 4, detail: 'Integer addition', documentation: 'Adds destination and source, updating flags.' },
                        { label: 'sub', kind: m.languages.CompletionItemKind.Keyword, insertText: 'sub ${1:dest}, ${2:src}', insertTextRules: m.languages.CompletionItemInsertTextRule ? m.languages.CompletionItemInsertTextRule.InsertAsSnippet : 4, detail: 'Integer subtraction', documentation: 'Subtracts source from destination operand.' },
                        { label: 'int 21h', kind: m.languages.CompletionItemKind.Snippet, insertText: 'int 21h', detail: 'DOS Interrupt 21h', documentation: 'Invokes DOS system service.' },
                        { label: 'ax', kind: m.languages.CompletionItemKind.Variable, insertText: 'ax', detail: 'Accumulator Register (16-bit)' },
                        { label: 'bx', kind: m.languages.CompletionItemKind.Variable, insertText: 'bx', detail: 'Base Register (16-bit)' }
                    ].map(s => Object.assign(s, { range }));

                    return { suggestions: suggestions };
                }
            });

            // Hover Provider
            const hoverDisp = m.languages.registerHoverProvider('asm8086', {
                provideHover: function (model, position) {
                    const wordInfo = model.getWordAtPosition ? model.getWordAtPosition(position) : null;
                    if (!wordInfo) return null;

                    const word = wordInfo.word ? wordInfo.word.toLowerCase() : '';
                    const range = m.Range ? new m.Range(
                        position.lineNumber, wordInfo.startColumn,
                        position.lineNumber, wordInfo.endColumn
                    ) : null;

                    // Numeric inspector
                    const rawWord = wordInfo.word;
                    let numVal = null;
                    if (/^[0-9][0-9a-fA-F]*h$/i.test(rawWord)) {
                        numVal = parseInt(rawWord.slice(0, -1), 16);
                    } else if (/^0x[0-9a-fA-F]+$/i.test(rawWord)) {
                        numVal = parseInt(rawWord, 16);
                    } else if (/^[01]+b$/i.test(rawWord)) {
                        numVal = parseInt(rawWord.slice(0, -1), 2);
                    } else if (/^\d+$/.test(rawWord)) {
                        numVal = parseInt(rawWord, 10);
                    }

                    if (numVal !== null && !isNaN(numVal)) {
                        return {
                            range: range,
                            contents: [
                                { value: '**Numeric Conversion (IDEARM Base Inspector)**' },
                                { value: `* **Hexadecimal:** \`0x${numVal.toString(16).toUpperCase().padStart(4, '0')}h\`\n* **Decimal:** \`${numVal.toString(10)}\`` }
                            ]
                        };
                    }

                    if (word === 'mov') {
                        return {
                            range: range,
                            contents: [
                                { value: '**MOV — Move Data**' },
                                { value: '`MOV dest, src`\n\nCopies value from src to dest.' }
                            ]
                        };
                    }

                    return null;
                }
            });

            this.providerDisposables.push(compDisp, hoverDisp);
        }

        /**
         * Cleanly unregisters language providers to prevent listener leaks.
         */
        disposeProviders() {
            this.providerDisposables.forEach(d => {
                if (d && typeof d.dispose === 'function') {
                    d.dispose();
                }
            });
            this.providerDisposables = [];
            this.log('INFO', 'Disposed all registered language providers');
        }

        // =========================================================================
        // Host-to-Editor Actions (Inbound API)
        // =========================================================================

        /**
         * Open document: creates/sets model, resets undo, sets monotonic version.
         * Flushes any pending debounced edits for the previously active document.
         */
        openDocument({ uri, text, version = 1, languageId = 'asm8086' }) {
            const start = (typeof performance !== 'undefined') ? performance.now() : Date.now();
            const m = this.monaco;

            // Flush pending debounced edits on previous document before switching
            if (this.activeUri && this.activeUri !== uri) {
                this.flushPendingChanges(this.activeUri);
            }

            let entry = this.models.get(uri);

            if (!entry) {
                const monacoUri = (m && m.Uri) ? m.Uri.parse(uri) : uri;
                let model = (m && m.editor && m.editor.getModel) ? m.editor.getModel(monacoUri) : null;
                if (!model && m && m.editor && m.editor.createModel) {
                    model = m.editor.createModel(text, languageId, monacoUri);
                } else if (model) {
                    model.setValue(text);
                }

                entry = {
                    model: model,
                    version: version,
                    isDirty: false,
                    debounceTimer: null,
                    pendingChanges: null,
                    disposables: []
                };

                // Subscribe to text changes with per-entry debouncing
                if (model && typeof model.onDidChangeContent === 'function') {
                    const changeSub = model.onDidChangeContent(e => {
                        entry.version++;
                        entry.isDirty = true;
                        entry.pendingChanges = (entry.pendingChanges || []).concat(e.changes || []);

                        clearTimeout(entry.debounceTimer);
                        entry.debounceTimer = setTimeout(() => {
                            this.flushPendingChanges(uri);
                        }, this.changeDebounceMs);
                    });

                    entry.disposables.push(changeSub);
                }

                this.models.set(uri, entry);
            } else {
                entry.version = version;
                entry.isDirty = false;
                if (entry.model && typeof entry.model.setValue === 'function') {
                    entry.model.setValue(text);
                }
            }

            this.activeUri = uri;
            if (this.editor && typeof this.editor.setModel === 'function') {
                this.editor.setModel(entry.model);
            }

            const end = (typeof performance !== 'undefined') ? performance.now() : Date.now();
            const duration = end - start;
            const lineCount = (entry.model && typeof entry.model.getLineCount === 'function') ? entry.model.getLineCount() : (text.split('\n').length);
            this.recordMetric('openDocument', duration, { uri, lines: lineCount });

            return {
                uri: uri,
                version: entry.version,
                lineCount: lineCount,
                durationMs: duration
            };
        }

        /**
         * Flushes any debounced change events immediately for the given URI.
         */
        flushPendingChanges(uri) {
            const targetUri = uri || this.activeUri;
            if (!targetUri) return;
            const entry = this.models.get(targetUri);
            if (!entry) return;

            if (entry.debounceTimer) {
                clearTimeout(entry.debounceTimer);
                entry.debounceTimer = null;
            }

            if (entry.pendingChanges) {
                const changes = entry.pendingChanges;
                entry.pendingChanges = null;
                const fullText = (entry.model && typeof entry.model.getValue === 'function') ? entry.model.getValue() : '';
                this.emit('onDocumentChanged', {
                    uri: targetUri,
                    version: entry.version,
                    isDirty: entry.isDirty,
                    changes: changes,
                    fullText: fullText
                });
            }
        }

        /**
         * Sets text directly without marking as dirty (e.g. file reloaded from disk).
         */
        setText(text) {
            if (!this.activeUri) return;
            const entry = this.models.get(this.activeUri);
            if (entry) {
                if (entry.model && typeof entry.model.setValue === 'function') {
                    entry.model.setValue(text);
                }
                entry.isDirty = false;
            }
        }

        getText() {
            if (!this.activeUri) return '';
            const entry = this.models.get(this.activeUri);
            return (entry && entry.model && typeof entry.model.getValue === 'function') ? entry.model.getValue() : '';
        }

        /**
         * Applies diagnostics markers.
         * Enforces strict versioning:
         * - Drops stale results if version < current model version
         * - Rejects future results if version > current model version (desync)
         */
        setDiagnostics({ uri, version, diagnostics = [] }) {
            const start = (typeof performance !== 'undefined') ? performance.now() : Date.now();
            const targetUri = uri || this.activeUri;
            const entry = this.models.get(targetUri);
            if (!entry) {
                this.log('WARN', `setDiagnostics failed: unknown uri ${targetUri}`);
                return false;
            }

            // Stale check
            if (version !== undefined && version < entry.version) {
                this.log('WARN', `Rejected stale diagnostics for ${targetUri}: received v${version} < current v${entry.version}`);
                this.emit('onStaleDiagnosticsDropped', {
                    uri: targetUri,
                    receivedVersion: version,
                    currentVersion: entry.version,
                    droppedCount: diagnostics.length
                });
                return false;
            }

            // Future version desync check
            if (version !== undefined && version > entry.version) {
                this.log('WARN', `Rejected future-version diagnostics for ${targetUri}: received v${version} > current v${entry.version}`);
                this.emit('onFutureDiagnosticsDropped', {
                    uri: targetUri,
                    receivedVersion: version,
                    currentVersion: entry.version,
                    droppedCount: diagnostics.length
                });
                return false;
            }

            const m = this.monaco;
            const markers = diagnostics.map(d => {
                const sev = (m && m.MarkerSeverity) ?
                    (d.severity === 'ERROR' ? m.MarkerSeverity.Error :
                     d.severity === 'WARNING' ? m.MarkerSeverity.Warning :
                     d.severity === 'INFO' ? m.MarkerSeverity.Info : m.MarkerSeverity.Hint) : d.severity;

                return {
                    severity: sev,
                    startLineNumber: d.line || 1,
                    startColumn: d.column || 1,
                    endLineNumber: d.endLine || d.line || 1,
                    endColumn: d.endColumn || (d.column ? d.column + (d.length || 1) : 100),
                    message: d.message,
                    source: 'IDEARM Linter'
                };
            });

            if (m && m.editor && typeof m.editor.setModelMarkers === 'function') {
                m.editor.setModelMarkers(entry.model, 'idearm-diagnostics', markers);
            }

            const end = (typeof performance !== 'undefined') ? performance.now() : Date.now();
            const duration = end - start;
            this.recordMetric('setDiagnostics', duration, { uri: targetUri, count: markers.length });
            return true;
        }

        /**
         * Asynchronously requests completions from host with version tracking.
         */
        requestCompletions({ uri, line, column, prefix }) {
            const targetUri = uri || this.activeUri;
            const entry = this.models.get(targetUri);
            const currentVersion = entry ? entry.version : 1;
            const requestId = ++this.requestIdCounter;

            let resolveFn, rejectFn;
            const promise = new Promise((resolve, reject) => {
                resolveFn = resolve;
                rejectFn = reject;
            });

            this.pendingCompletions.set(requestId, {
                requestId: requestId,
                uri: targetUri,
                version: currentVersion,
                resolve: resolveFn,
                reject: rejectFn,
                timestamp: Date.now()
            });

            this.emit('onCompletionRequested', {
                requestId: requestId,
                uri: targetUri,
                version: currentVersion,
                line: line,
                column: column,
                prefix: prefix
            });

            return { requestId, promise };
        }

        /**
         * Provides asynchronous completion candidates response from host.
         * Enforces strict versioning: rejects stale responses if version < current model version.
         */
        provideCompletionsResponse({ requestId, version, items = [] }) {
            const pending = this.pendingCompletions.get(requestId);
            if (!pending) {
                this.log('WARN', `Received completion response for unknown or expired requestId ${requestId}`);
                return false;
            }

            this.pendingCompletions.delete(requestId);
            const entry = this.models.get(pending.uri);
            const currentVersion = entry ? entry.version : pending.version;

            // Stale completion check
            if (version !== undefined && version < currentVersion) {
                this.log('WARN', `Rejected stale completion response for req#${requestId}: received v${version} < current v${currentVersion}`);
                this.emit('onStaleCompletionDropped', {
                    requestId: requestId,
                    uri: pending.uri,
                    receivedVersion: version,
                    currentVersion: currentVersion,
                    itemCount: items.length
                });
                pending.resolve([]);
                return false;
            }

            // Future completion check (desync)
            if (version !== undefined && version > currentVersion) {
                this.log('WARN', `Rejected future-version completion response for req#${requestId}: received v${version} > current v${currentVersion}`);
                this.emit('onFutureCompletionDropped', {
                    requestId: requestId,
                    uri: pending.uri,
                    receivedVersion: version,
                    currentVersion: currentVersion,
                    itemCount: items.length
                });
                pending.resolve([]);
                return false;
            }

            pending.resolve(items);
            return true;
        }

        /**
         * Sets gutter decorations for active and disabled breakpoints.
         */
        setBreakpoints({ uri, activeLines = [], disabledLines = [] }) {
            if (!this.editor || typeof this.editor.deltaDecorations !== 'function') return;
            const m = this.monaco;
            const newDecorations = [];

            activeLines.forEach(line => {
                newDecorations.push({
                    range: m.Range ? new m.Range(line, 1, line, 1) : { startLineNumber: line, startColumn: 1, endLineNumber: line, endColumn: 1 },
                    options: {
                        isWholeLine: false,
                        glyphMarginClassName: 'glyph-breakpoint-active',
                        glyphMarginHoverMessage: { value: `**Breakpoint** (Active on line ${line})` }
                    }
                });
            });

            disabledLines.forEach(line => {
                newDecorations.push({
                    range: m.Range ? new m.Range(line, 1, line, 1) : { startLineNumber: line, startColumn: 1, endLineNumber: line, endColumn: 1 },
                    options: {
                        isWholeLine: false,
                        glyphMarginClassName: 'glyph-breakpoint-disabled',
                        glyphMarginHoverMessage: { value: `**Breakpoint** (Disabled on line ${line})` }
                    }
                });
            });

            this.breakpointDecorationIds = this.editor.deltaDecorations(
                this.breakpointDecorationIds,
                newDecorations
            );
        }

        /**
         * Sets execution line (instruction pointer).
         * If line is null, decoration is removed.
         */
        setExecutionLine({ uri, line = null, reveal = true }) {
            if (!this.editor || typeof this.editor.deltaDecorations !== 'function') return;
            const m = this.monaco;
            const newDecorations = [];

            if (line !== null && line > 0) {
                newDecorations.push({
                    range: m.Range ? new m.Range(line, 1, line, 1) : { startLineNumber: line, startColumn: 1, endLineNumber: line, endColumn: 1 },
                    options: {
                        isWholeLine: true,
                        className: 'line-execution-highlight',
                        glyphMarginClassName: 'glyph-execution-line',
                        glyphMarginHoverMessage: { value: `**Execution Line** (Instruction Pointer at line ${line})` }
                    }
                });

                if (reveal && typeof this.editor.revealLineInCenter === 'function') {
                    this.editor.revealLineInCenter(line);
                }
            }

            this.executionDecorationIds = this.editor.deltaDecorations(
                this.executionDecorationIds,
                newDecorations
            );
        }

        /**
         * Navigates caret to specific line and scrolls into view.
         */
        goToLine({ line, column = 1 }) {
            if (!this.editor) return;
            if (typeof this.editor.setPosition === 'function') {
                this.editor.setPosition({ lineNumber: line, column: column });
            }
            if (typeof this.editor.revealLineInCenter === 'function') {
                this.editor.revealLineInCenter(line);
            }
            if (typeof this.editor.focus === 'function') {
                this.editor.focus();
            }
        }

        /**
         * Replaces word at current caret with replacement text.
         */
        replaceWordAtCaret(replacement) {
            if (!this.editor) return;
            const pos = this.editor.getPosition();
            const model = this.editor.getModel();
            if (!pos || !model) return;

            const m = this.monaco;
            const wordInfo = model.getWordAtPosition ? model.getWordAtPosition(pos) : null;
            const range = (wordInfo && m.Range) ?
                new m.Range(pos.lineNumber, wordInfo.startColumn, pos.lineNumber, wordInfo.endColumn) :
                (m.Range ? new m.Range(pos.lineNumber, pos.column, pos.lineNumber, pos.column) : { startLineNumber: pos.lineNumber, startColumn: pos.column, endLineNumber: pos.lineNumber, endColumn: pos.column });

            if (typeof this.editor.executeEdits === 'function') {
                this.editor.executeEdits('idearm-bridge', [{
                    range: range,
                    text: replacement,
                    forceMoveMarkers: true
                }]);
            }
        }

        /**
         * Disposes a document model, canceling pending debounced timers and in-flight completions.
         */
        disposeDocument(uri) {
            const entry = this.models.get(uri);
            if (entry) {
                if (entry.debounceTimer) {
                    clearTimeout(entry.debounceTimer);
                    entry.debounceTimer = null;
                }
                entry.disposables.forEach(d => {
                    if (d && typeof d.dispose === 'function') d.dispose();
                });
                if (entry.model && typeof entry.model.dispose === 'function') {
                    entry.model.dispose();
                }
                this.models.delete(uri);
                if (this.activeUri === uri) {
                    this.activeUri = null;
                }

                // Cancel in-flight completions for this document
                this.pendingCompletions.forEach((pending, reqId) => {
                    if (pending.uri === uri) {
                        this.pendingCompletions.delete(reqId);
                        this.emit('onCompletionCancelled', { requestId: reqId, uri: uri, reason: 'Document disposed' });
                        pending.resolve([]);
                    }
                });

                this.log('INFO', `Disposed model for ${uri}`);
            }
        }

        /**
         * Completely disposes the editor, all models, pending requests, and all language providers.
         */
        disposeEditor() {
            this.models.forEach((entry, uri) => {
                if (entry.debounceTimer) {
                    clearTimeout(entry.debounceTimer);
                    entry.debounceTimer = null;
                }
                entry.disposables.forEach(d => {
                    if (d && typeof d.dispose === 'function') d.dispose();
                });
                if (entry.model && typeof entry.model.dispose === 'function') {
                    entry.model.dispose();
                }
            });
            this.models.clear();
            this.activeUri = null;

            // Cancel and clear all pending async completions and hovers
            this.pendingCompletions.forEach((pending, reqId) => {
                this.emit('onCompletionCancelled', { requestId: reqId, uri: pending.uri, reason: 'Editor disposed' });
                pending.resolve([]);
            });
            this.pendingCompletions.clear();
            this.pendingHovers.clear();

            // Dispose registered language providers
            this.disposeProviders();

            if (this.editor) {
                if (typeof this.editor.dispose === 'function') {
                    this.editor.dispose();
                }
                this.editor = null;
            }
            this.log('INFO', 'Editor, models, and providers successfully disposed');
        }

        // =========================================================================
        // Communication / Logging / Telemetry
        // =========================================================================

        emit(eventType, payload) {
            const message = { type: eventType, payload: payload, timestamp: Date.now() };

            // 1. If running inside JavaFX WebView with registered bridge
            if (this.javaBridge && typeof this.javaBridge.postMessage === 'function') {
                try {
                    this.javaBridge.postMessage(JSON.stringify(message));
                } catch (err) {
                    console.error('Failed to post to JavaFX bridge:', err);
                }
            } else if (typeof window !== 'undefined' && window.idearmJavaBridge && typeof window.idearmJavaBridge.postMessage === 'function') {
                try {
                    window.idearmJavaBridge.postMessage(JSON.stringify(message));
                } catch (err) {
                    console.error('Failed to post to window.idearmJavaBridge:', err);
                }
            }

            // 2. Browser window event
            if (typeof window !== 'undefined' && typeof window.dispatchEvent === 'function') {
                try {
                    window.dispatchEvent(new CustomEvent('idearm-editor-event', { detail: message }));
                } catch (e) {
                    // Ignored in non-browser env
                }
            }

            // 3. Callback hook for automated tests
            if (typeof this.onEvent === 'function') {
                this.onEvent(message);
            }
            if (typeof window !== 'undefined' && typeof window.onEditorBridgeEvent === 'function') {
                window.onEditorBridgeEvent(message);
            }
        }

        log(level, msg) {
            this.emit('onLog', { level: level, message: msg });
        }

        recordMetric(name, durationMs, details = {}) {
            const record = { name, durationMs, details, timestamp: Date.now() };
            this.emit('onBenchmarkMetric', record);
        }
    }

    return { MonacoEditorBridge };
});
