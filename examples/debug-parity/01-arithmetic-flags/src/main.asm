; 01-arithmetic-flags/src/main.asm
; Original 8086 assembly fixture verifying arithmetic operations and CPU flags:
; CF (Carry), ZF (Zero), SF (Sign), OF (Overflow), AF (Auxiliary), PF (Parity).
.8086
.MODEL small
.STACK 100h

.DATA
val16_a  DW 7FFFh
val16_b  DW 0001h
result16 DW 0

.CODE
main PROC
    mov ax, @DATA
    mov ds, ax

    ; Stop 1: ADD causing signed overflow (7FFFh + 1 = 8000h: OF=1, SF=1, ZF=0, CF=0)
    mov ax, 7FFFh
    add ax, 1

    ; Stop 2: ADD with unsigned carry (FFFFh + 1 = 0000h: CF=1, ZF=1, SF=0, OF=0, PF=1)
    mov ax, 0FFFFh
    add ax, 1

    ; Stop 3: ADC carry propagation (0000h + 0000h + CF=1 -> 0001h: CF=0, ZF=0, SF=0, OF=0)
    mov bx, 0
    adc bx, 0

    ; Stop 4: SUB borrow (0005h - 000Ah -> FFFBh: CF=1, SF=1, ZF=0, OF=0)
    mov cx, 5
    sub cx, 10

    ; Stop 5: SBB multi-byte subtraction borrow chain (1000h - 0001h - CF=1 -> 0FFEh)
    mov dx, 1000h
    sbb dx, 1

    ; Stop 6: CMP signed vs unsigned comparisons
    ; CMP -5 (FBh) with +5 (05h): SF != OF -> JL taken, CF=0 -> JAE taken
    mov al, 0FBh
    cmp al, 5

    ; Stop 7: NEG edge cases
    ; Negating 80h (-128) results in 80h with OF=1, CF=1
    mov al, 80h
    neg al
    ; Negating 0 results in 0 with OF=0, CF=0
    mov bl, 0
    neg bl

    ; Stop 8: Signed division IDIV with negative 32-bit dividend in DX:AX
    ; DX:AX = FFFF:FFFE (-2), divide by 2 -> AX = FFFF (-1), DX = 0 (rem 0)
    mov dx, 0FFFFh
    mov ax, 0FFFEh
    mov bx, 2
    idiv bx

    ; Stop 9: BCD adjust DAA (AL = 38h + 45h = 7Dh -> DAA -> 83h)
    mov al, 38h
    add al, 45h
    daa

    ; Normal exit
    mov ax, 4C00h
    int 21h
main ENDP
END main
