; 05-faults-and-traps/src/main.asm
; Original 8086 assembly fixture demonstrating software breakpoint traps (INT 3),
; interrupt vectors, division error handling, and invalid opcode fault detection.
.8086
.MODEL small
.STACK 100h

.CODE
main PROC
    mov ax, 1000h

    ; 1. Software Breakpoint (INT 3)
    ; Must pause debugger like a breakpoint and continue without corrupting flow
    int 3

    mov bx, 2000h

    ; 2. Division by zero check
    ; When BL = 0, DIV BL triggers "emu.divide-error"
    mov al, 10
    mov bl, 0
    div bl

    ; Normal return (unreached if division error pauses and terminates)
    mov ax, 4C00h
    int 21h
main ENDP
END main
