/**
 * Deterministic generator for original 5k-line and 20k-line assembly fixtures.
 * Produces structured 8086 MASM/TASM/NASM assembly code with realistic procedures,
 * data definitions, comments, directives, and control flow for benchmark evaluation.
 * 
 * 100% original code — no proprietary course assets or external reference code.
 */

const fs = require('fs');
const path = require('path');

const FIXTURES_DIR = path.join(__dirname, 'fixtures');
if (!fs.existsSync(FIXTURES_DIR)) {
    fs.mkdirSync(FIXTURES_DIR, { recursive: true });
}

function generateAssemblyFile(targetLines, filename) {
    const lines = [];

    // Header
    lines.push('; ==============================================================================');
    lines.push(`; IDEARM Benchmark Fixture: ${filename}`);
    lines.push(`; Generated line count target: ${targetLines} lines`);
    lines.push('; Architecture: x86 / 8086 Real Mode (MASM / TASM compatible)');
    lines.push('; Original synthetic workload for editor syntax, editing, and diagnostic stress');
    lines.push('; ==============================================================================');
    lines.push('');
    lines.push('.model small');
    lines.push('.stack 2048h');
    lines.push('');
    lines.push('.data');
    lines.push('    ; Global configuration and lookup tables');
    lines.push('    sys_banner      db "IDEARM Synthetic Assembly Test Suite v2.0", 0Dh, 0Ah, "$"');
    lines.push('    err_overflow    db "FATAL: Arithmetic overflow detected", 0Dh, 0Ah, "$"');
    lines.push('    err_bounds      db "ERROR: Memory index out of bounds", 0Dh, 0Ah, "$"');
    lines.push('    lookup_crc16    dw 0000h, 1021h, 2042h, 3063h, 4084h, 50A5h, 60C6h, 70E7h');
    lines.push('                    dw 8108h, 9129h, 0A14Ah, 0B16Bh, 0C18Ch, 0D1ADh, 0E1CEh, 0F1EFh');
    lines.push('    buffer_capacity dw 1024');
    lines.push('    active_session  db 01h');
    lines.push('    scratch_pad     db 256 dup(0)');
    lines.push('');

    let procIndex = 0;
    const modules = [
        'matrix_transform', 'string_lexer', 'memory_heap', 'crc_checksum',
        'uart_driver', 'vga_renderer', 'interrupt_service', 'sort_quicksort',
        'fixed_point_math', 'ring_buffer', 'hash_table', 'event_dispatcher'
    ];

    lines.push('.code');
    lines.push('main proc far');
    lines.push('    mov ax, @data');
    lines.push('    mov ds, ax');
    lines.push('    mov es, ax');
    lines.push('    lea dx, sys_banner');
    lines.push('    mov ah, 09h');
    lines.push('    int 21h');
    lines.push('    call proc_init_subsystem');
    lines.push('    mov ax, 4C00h');
    lines.push('    int 21h');
    lines.push('main endp');
    lines.push('');

    // Generate procedures until targetLines is reached
    while (lines.length < targetLines - 50) {
        procIndex++;
        const modName = modules[procIndex % modules.length];
        const procName = `${modName}_routine_${String(procIndex).padStart(4, '0')}`;
        
        lines.push(`; --- Procedure: ${procName} (module: ${modName}) ---`);
        lines.push(`${procName} proc near`);
        lines.push('    push bp');
        lines.push('    mov bp, sp');
        lines.push('    push si');
        lines.push('    push di');
        lines.push('    push bx');
        lines.push('    push cx');
        lines.push('    push dx');
        lines.push('');
        lines.push('    ; Load parameters from stack frame');
        lines.push('    mov si, [bp + 4]        ; Source pointer');
        lines.push('    mov di, [bp + 6]        ; Destination pointer');
        lines.push('    mov cx, [bp + 8]        ; Word counter');
        lines.push('    cmp cx, 0');
        lines.push(`    jle @@${procName}_exit`);
        lines.push('');
        lines.push(`@@${procName}_loop:`);
        lines.push('    lodsw                   ; Load word from DS:SI into AX');
        lines.push(`    xor ax, 0A5A5h          ; Routine mask operation`);
        lines.push(`    add ax, ${procIndex % 256}`);
        lines.push('    rol ax, 3               ; Permute bit pattern');
        lines.push('    test ax, 0001h');
        lines.push(`    jz @@${procName}_even`);
        lines.push('    sub ax, 17h');
        lines.push(`    jmp @@${procName}_store`);
        lines.push(`@@${procName}_even:`);
        lines.push('    xor ax, 0FFh');
        lines.push(`@@${procName}_store:`);
        lines.push('    stosw                   ; Store AX to ES:DI');
        lines.push(`    loop @@${procName}_loop`);
        lines.push('');
        lines.push(`@@${procName}_exit:`);
        lines.push('    pop dx');
        lines.push('    pop cx');
        lines.push('    pop bx');
        lines.push('    pop di');
        lines.push('    pop si');
        lines.push('    pop bp');
        lines.push('    ret 6');
        lines.push(`${procName} endp`);
        lines.push('');
    }

    // Epilogue
    lines.push('proc_init_subsystem proc near');
    lines.push('    xor ax, ax');
    lines.push('    mov bx, 0100h');
    lines.push('    ret');
    lines.push('proc_init_subsystem endp');
    lines.push('');
    lines.push('end main');

    // Trim or pad exactly to targetLines
    while (lines.length < targetLines) {
        lines.splice(lines.length - 2, 0, '; Pad line for precise benchmark line count matching');
    }
    if (lines.length > targetLines) {
        lines.splice(targetLines - 1, lines.length - targetLines + 1);
        lines.push('end main');
    }

    const content = lines.join('\r\n');
    const outPath = path.join(FIXTURES_DIR, filename);
    fs.writeFileSync(outPath, content, 'utf8');
    const stats = fs.statSync(outPath);
    console.log(`Generated ${filename}: ${lines.length} lines, ${stats.size} bytes (${(stats.size / 1024).toFixed(1)} KB)`);
}

generateAssemblyFile(5000, 'load5k.asm');
generateAssemblyFile(20000, 'load20k.asm');
