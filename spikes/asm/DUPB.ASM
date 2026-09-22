; DUPB.ASM - module 2: repeats main + END main and PUBLIC helper (duplicate public symbol)
.MODEL small
.STACK 100h

.CODE
PUBLIC helper
helper PROC
    ret
helper ENDP

main PROC
    mov ax, 4C00h
    int 21h
main ENDP
END main
