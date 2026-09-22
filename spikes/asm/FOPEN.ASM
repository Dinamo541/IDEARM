; FOPEN.ASM - tries to open a file with a long (non 8.3) name through two DOS APIs
.MODEL small
.STACK 100h

.DATA
longName   DB 'inv_bottom.spr', 0
msgClassic DB 'Classic API 3Dh: $'
msgLfn     DB 'LFN API 716Ch:   $'
msgOk      DB 'OK', 13, 10, '$'
msgFail    DB 'FAIL', 13, 10, '$'

.CODE
main PROC
    mov ax, @DATA
    mov ds, ax

    mov dx, OFFSET msgClassic
    call print
    mov ax, 3D00h            ; open file, read-only
    mov dx, OFFSET longName
    int 21h
    call report

    mov dx, OFFSET msgLfn
    call print
    mov ax, 716Ch            ; create or open with a long name
    mov bx, 0                ; access mode: read-only
    xor cx, cx               ; attributes
    mov dx, 1                ; action: open if it exists
    mov si, OFFSET longName
    int 21h
    call report

    mov ax, 4C00h
    int 21h
main ENDP

; Prints the $-terminated string pointed to by DX
print PROC
    mov ah, 09h
    int 21h
    ret
print ENDP

; Prints OK when CF=0 (closing the handle returned in AX) or FAIL when CF=1
report PROC
    jc  failed
    mov bx, ax
    mov ah, 3Eh
    int 21h
    mov dx, OFFSET msgOk
    call print
    ret
failed:
    mov dx, OFFSET msgFail
    call print
    ret
report ENDP
END main
