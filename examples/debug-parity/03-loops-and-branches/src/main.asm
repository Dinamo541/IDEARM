; 03-loops-and-branches/src/main.asm
; Original 8086 assembly fixture verifying conditional jumps, counting loops,
; repeated breakpoints inside loop bodies, and loop termination.
.8086
.MODEL small
.STACK 100h

.CODE
main PROC
    ; --- Part 1: Conditional Branching ---
    mov ax, 10
    cmp ax, 10
    je branch_equal
    mov bx, 0DEADh     ; Should NOT be executed

branch_equal:
    mov bx, 1          ; BX = 1 confirms JE taken

    cmp ax, 20
    jl branch_less
    mov bx, 0BEEFh     ; Should NOT be executed

branch_less:
    mov bx, 2          ; BX = 2 confirms JL taken

    ; --- Part 2: Counting Loop (LOOP instruction) ---
    ; Repeated breakpoint will be placed on line 28 (add dx, ax)
    mov dx, 0          ; Accumulator
    mov ax, 5          ; Value added each iteration
    mov cx, 4          ; 4 loop iterations

loop_start:
    add dx, ax         ; Breakpoint target: hit on iteration 1, 2, 3, 4
    loop loop_start

    ; DX should now equal 20 (4 * 5)
    mov bx, dx

    ; --- Part 3: LOOPZ / LOOPE ---
    ; Loops while CX != 0 and ZF == 1
    mov cx, 3
    xor ax, ax         ; Set ZF=1

loop_zero:
    dec cx
    cmp ax, 0          ; Keeps ZF=1
    loopz loop_zero

    ; --- Part 4: JCXZ Check ---
    mov cx, 0
    jcxz jcxz_taken
    mov ax, 0FFFFh     ; Should NOT be executed

jcxz_taken:
    mov ax, 4C00h
    int 21h
main ENDP
END main
