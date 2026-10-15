/**
 * Monarch Language Definition for 8086 / x86 Assembly (MASM / TASM / NASM).
 * Provides rich syntax highlighting for registers, instructions, directives,
 * memory operators, strings, numbers, and comments.
 */

(function () {
    const asmDefinition = {
        defaultToken: '',
        tokenPostfix: '.asm',
        ignoreCase: true,

        instructions: [
            'mov', 'movzx', 'movsx', 'push', 'pop', 'pusha', 'popa', 'pushf', 'popf',
            'xchg', 'xlat', 'in', 'out', 'lea', 'lds', 'les', 'lahf', 'sahf',
            'add', 'adc', 'sub', 'sbb', 'mul', 'imul', 'div', 'idiv', 'inc', 'dec', 'neg', 'cmp',
            'daa', 'das', 'aaa', 'aas', 'aam', 'aad', 'cbw', 'cwd',
            'and', 'or', 'xor', 'not', 'test', 'shl', 'shr', 'sal', 'sar', 'rol', 'ror', 'rcl', 'rcr',
            'jmp', 'je', 'jne', 'jz', 'jnz', 'js', 'jns', 'jc', 'jnc', 'jo', 'jno',
            'jp', 'jpe', 'jnp', 'jpo', 'jl', 'jnge', 'jnl', 'jge', 'jle', 'jng', 'jnle', 'jg',
            'jb', 'jnae', 'jnb', 'jae', 'jbe', 'jna', 'jnbe', 'ja', 'jcxz', 'jecxz',
            'loop', 'loope', 'loopz', 'loopne', 'loopnz',
            'call', 'ret', 'retn', 'retf', 'iret',
            'clc', 'stc', 'cmc', 'cld', 'std', 'cli', 'sti',
            'wait', 'nop', 'hlt', 'lock', 'int', 'into',
            'lodsb', 'lodsw', 'lodsd', 'stosb', 'stosw', 'stosd',
            'movsb', 'movsw', 'movsd', 'cmpsb', 'cmpsw', 'cmpsd', 'scasb', 'scasw', 'scasd',
            'rep', 'repe', 'repz', 'repne', 'repnz'
        ],

        registers: [
            'al', 'cl', 'dl', 'bl', 'ah', 'ch', 'dh', 'bh',
            'ax', 'cx', 'dx', 'bx', 'sp', 'bp', 'si', 'di',
            'eax', 'ecx', 'edx', 'ebx', 'esp', 'ebp', 'esi', 'edi',
            'cs', 'ds', 'ss', 'es', 'fs', 'gs',
            'cr0', 'cr2', 'cr3', 'dr0', 'dr1', 'dr2', 'dr3', 'dr6', 'dr7'
        ],

        directives: [
            '.model', '.stack', '.data', '.data?', '.code', '.const',
            'segment', 'ends', 'proc', 'endp', 'macro', 'endm',
            'org', 'assume', 'end', 'equ', 'label', 'include', 'includelib',
            'public', 'extrn', 'extern', 'global',
            'db', 'dw', 'dd', 'dq', 'dt', 'dup',
            'near', 'far', 'ptr', 'byte', 'word', 'dword', 'qword', 'tbyte',
            'short', 'offset', 'seg', 'type', 'length', 'size'
        ],

        operators: [
            '+', '-', '*', '/', '%', '==', '!=', '<', '<=', '>', '>=',
            '&', '|', '^', '~', '<<', '>>', '!', ':'
        ],

        tokenizer: {
            root: [
                // Comments
                [/;.*$/, 'comment'],

                // Directives with leading dot
                [/\.[a-zA-Z_?][a-zA-Z0-9_?]*/, {
                    cases: {
                        '@directives': 'keyword.directive',
                        '@default': 'identifier'
                    }
                }],

                // Labels: name:
                [/^[ \t]*[a-zA-Z_?@][a-zA-Z0-9_?@]*:/, 'type.identifier'],
                [/@@[a-zA-Z0-9_?@]+:/, 'type.identifier'],

                // Strings
                [/"([^"\\]|\\.)*"/, 'string'],
                [/'([^'\\]|\\.)*'/, 'string'],

                // Numbers: Hex, binary, decimal
                [/0[xX][0-9a-fA-F]+h?/, 'number.hex'],
                [/[0-9][0-9a-fA-F]*[hH]/, 'number.hex'],
                [/[01]+[bB]/, 'number.binary'],
                [/[0-7]+[oOqQ]/, 'number.octal'],
                [/\d+/, 'number'],

                // Identifiers, instructions, directives, registers
                [/[a-zA-Z_?@][a-zA-Z0-9_?@]*/, {
                    cases: {
                        '@instructions': 'keyword.instruction',
                        '@registers': 'variable.register',
                        '@directives': 'keyword.directive',
                        '@default': 'identifier'
                    }
                }],

                // Whitespace
                { include: '@whitespace' },

                // Delimiters and operators
                [/[{}()\[\]]/, '@brackets'],
                [/[,]/, 'delimiter'],
                [/[-+*\/=<>:&|^~]/, 'operator']
            ],

            whitespace: [
                [/[ \t\r\n]+/, 'white']
            ]
        }
    };

    window.ASM_LANGUAGE_DEFINITION = asmDefinition;

    window.registerAssemblyLanguage = function (monacoInstance) {
        if (!monacoInstance) return;
        monacoInstance.languages.register({ id: 'asm8086' });
        monacoInstance.languages.setMonarchTokensProvider('asm8086', asmDefinition);
        monacoInstance.languages.setLanguageConfiguration('asm8086', {
            comments: {
                lineComment: ';'
            },
            brackets: [
                ['[', ']'],
                ['(', ')']
            ],
            autoClosingPairs: [
                { open: '[', close: ']' },
                { open: '(', close: ')' },
                { open: '"', close: '"' },
                { open: "'", close: "'" }
            ],
            surroundingPairs: [
                { open: '[', close: ']' },
                { open: '(', close: ')' },
                { open: '"', close: '"' },
                { open: "'", close: "'" }
            ]
        });
    };
})();
