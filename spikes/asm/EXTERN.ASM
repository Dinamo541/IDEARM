; EXTERN.ASM - assembles fine but fails to link: unresolved external (line 13)
.MODEL small
.STACK 100h

.DATA
message DB 'Hello World', 13, 10, '$'

.CODE
EXTRN printNumber:NEAR
main PROC
    mov ax, @DATA
    mov ds, ax
    call printNumber
    mov ax, 4C00h
    int 21h
main ENDP
END main
