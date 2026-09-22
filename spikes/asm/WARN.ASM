; WARN.ASM - constructs expected to produce warnings rather than errors (assemble with all warnings enabled)
.MODEL small
.MODEL small              ; line 3: repeated .MODEL directive
.STACK 100h

.DATA
length   DW 0             ; line 7: reserved word used as a symbol name

.CODE
main PROC
    mov ax, @DATA
    mov ds, ax
    jmp later             ; line 13: forward jump that could be SHORT
    nop
later:
    mov ax, 4C00h
    int 21h
main ENDP
END main
