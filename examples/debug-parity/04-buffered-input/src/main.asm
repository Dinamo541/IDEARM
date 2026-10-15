; 04-buffered-input/src/main.asm
; Original 8086 assembly fixture verifying DOS INT 21h buffered keyboard input,
; line editing with Backspace, string pasting, and input termination with Enter.
.8086
.MODEL small
.STACK 100h

.DATA
prompt   DB 'Input: $'
echo_msg DB 13, 10, 'Echo: $'

; Buffer structure for INT 21h AH=0Ah:
; Byte 0: Maximum length (10 characters)
; Byte 1: Actual count read (written by DOS)
; Bytes 2..11: Character buffer terminated by CR (0Dh)
in_buf   DB 10
in_count DB 0
in_data  DB 11 DUP(0)

.CODE
main PROC
    mov ax, @DATA
    mov ds, ax

    ; Print prompt
    mov dx, OFFSET prompt
    mov ah, 09h
    int 21h

    ; Read buffered input line
    mov dx, OFFSET in_buf
    mov ah, 0Ah
    int 21h

    ; Replace trailing CR with '$' to echo using AH=09h
    mov bl, in_count
    mov bh, 0
    mov in_data[bx], '$'

    ; Print echo prefix
    mov dx, OFFSET echo_msg
    mov ah, 09h
    int 21h

    ; Print the entered string
    mov dx, OFFSET in_data
    mov ah, 09h
    int 21h

    ; Exit
    mov ax, 4C00h
    int 21h
main ENDP
END main
