; main.asm - reads a name from the keyboard and greets it.
; Start Debugging (F5) runs it in the built-in 8086 emulator: type the name in the Output panel.
.8086
.MODEL small
.STACK 100h

.DATA
ask      DB 'What is your name? $'
hello    DB 13, 10, 'Hello, $'
bye      DB '!', 13, 10, '$'
; INT 21h function 0Ah: maximum length, characters read, then the characters and a carriage return.
buffer   DB 20
count    DB 0
typed    DB 21 DUP('$')

.CODE
main PROC
    mov ax, @DATA
    mov ds, ax

    mov dx, OFFSET ask          ; ask for the name
    mov ah, 09h
    int 21h

    mov dx, OFFSET buffer       ; read a line from the keyboard
    mov ah, 0Ah
    int 21h

    mov bl, count               ; replace the carriage return with '$' to print the name
    mov bh, 0
    mov typed[bx], '$'

    mov dx, OFFSET hello
    mov ah, 09h
    int 21h
    mov dx, OFFSET typed
    mov ah, 09h
    int 21h
    mov dx, OFFSET bye
    mov ah, 09h
    int 21h

    mov ax, 4C00h               ; return to DOS
    int 21h
main ENDP
END main
