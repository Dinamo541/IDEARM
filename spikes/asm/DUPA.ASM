; DUPA.ASM - module 1: its own main + END main and PUBLIC helper (like Mastermind's Extras.asm)
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
