; NOSTK.ASM - no .STACK directive: assembles cleanly, the linker is expected to warn about the missing stack
.MODEL small

.DATA
message DB 'No stack', 13, 10, '$'

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
END main
