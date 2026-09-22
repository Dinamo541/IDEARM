; ERRSYN.ASM - invalid mnemonic (line 12) and missing operand (line 13)
.MODEL small
.STACK 100h

.DATA
message DB 'Hello World', 13, 10, '$'

.CODE
main PROC
    mov ax, @DATA
    mov ds, ax
    movx dx, 5
    mov ah,
    int 21h
    mov ax, 4C00h
    int 21h
main ENDP
END main
