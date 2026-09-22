; ==============================================================================
; Project: HELLO64
; Target:  win-pe64-console (x86-64)
; Prints a greeting with the Windows API and exits with code 0.
; ==============================================================================

default rel

global main
extern GetStdHandle
extern WriteFile
extern ExitProcess

section .data
    message     db "Hello, 64-bit World from NASM!", 13, 10
    message_len equ $ - message

section .bss
    written     resd 1

section .text
main:
    sub rsp, 40                 ; shadow space for the callee, keeps the stack aligned
    mov ecx, -11                ; STD_OUTPUT_HANDLE
    call GetStdHandle
    mov rcx, rax                ; handle
    lea rdx, [message]          ; text
    mov r8d, message_len        ; length
    lea r9, [written]           ; receives the number of bytes written
    mov qword [rsp + 32], 0     ; fifth argument: no overlapped structure
    call WriteFile
    xor ecx, ecx                ; exit code 0
    call ExitProcess
