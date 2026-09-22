; NOEND.ASM - the END directive is missing (unexpected end of file)
.MODEL small
.STACK 100h

.DATA
message DB 'Hello World', 13, 10, '$'

.CODE
main PROC
    mov ax, @DATA
    mov ds, ax
    mov dx, OFFSET message
    mov ah, 09h
    int 21h
    mov ax, 4C00h
    int 21h
main ENDP
