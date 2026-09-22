; UTF8.ASM - non-ASCII text stored as UTF-8 in comments and strings: naïve café ñ
.MODEL small
.STACK 100h

.DATA
message DB 'naïve café ñ', 13, 10, '$'   ; string with multi-byte characters

.CODE
main PROC
    mov ax, @DATA        ; initialize the data segment
    mov ds, ax
    mov dx, OFFSET message
    mov ah, 09h
    int 21h
    mov ax, 4C00h
    int 21h
main ENDP
END main
