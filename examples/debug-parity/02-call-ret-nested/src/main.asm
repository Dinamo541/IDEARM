; 02-call-ret-nested/src/main.asm
; Original 8086 assembly fixture verifying nested CALL/RET behavior,
; stack frame chaining via BP, recursive countdown, and step into/over/out boundaries.
.8086
.MODEL small
.STACK 100h

.CODE
main PROC
    mov ax, 0010h
    push ax

    ; Stop 1: About to call level1 (call depth 0 -> 1)
    call proc_level1

    ; Stop 2: Returned from level1 (call depth 0)
    mov bx, ax

    ; Stop 3: Recursive countdown call (initial CX = 3)
    mov cx, 3
    call proc_countdown

    ; Stop 4: End of execution
    mov ax, 4C00h
    int 21h
main ENDP

proc_level1 PROC
    push bp
    mov bp, sp

    ; Inside level1 (call depth 1)
    mov ax, [bp+4]     ; Parameter 0010h passed on stack
    add ax, 5

    ; Call level2 (call depth 1 -> 2)
    call proc_level2

    pop bp
    ret 2              ; Return and pop parameter
proc_level1 ENDP

proc_level2 PROC
    push bp
    mov bp, sp

    ; Inside level2 (call depth 2)
    add ax, 10

    ; Call leaf procedure (call depth 2 -> 3)
    call proc_leaf

    pop bp
    ret
proc_level2 ENDP

proc_leaf PROC
    ; Leaf procedure with no further calls (call depth 3)
    add ax, 20
    ret
proc_leaf ENDP

proc_countdown PROC
    ; Recursive countdown procedure
    push bp
    mov bp, sp

    cmp cx, 0
    jle countdown_done

    dec cx
    call proc_countdown

countdown_done:
    pop bp
    ret
proc_countdown ENDP

END main
