; ERRSYM.ASM - undefined symbol on line 12
.MODEL small
.STACK 100h

.DATA
message DB 'Hello World', 13, 10, '$'

.CODE
main PROC
    mov ax, @DATA
    mov ds, ax
    mov dx, OFFSET printNumber
    mov ah, 09h
    int 21h
    mov ax, 4C00h
    int 21h
main ENDP
END main
