; CPU186.ASM - 80186 instructions (lines 8 and 9) with an 8086 CPU baseline
.MODEL small
.8086
.STACK 100h

.CODE
main PROC
    push 5
    shl ax, 3
    mov ax, 4C00h
    int 21h
main ENDP
END main
