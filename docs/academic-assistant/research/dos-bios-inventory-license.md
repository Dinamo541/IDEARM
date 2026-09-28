# Research Note: DOS/BIOS Services Inventory & License Assessment (AA-P0-05)

> **Date:** 2026-09-25  
> **Status:** Verified and Closed  
> **Scope:** Licensing clearance and structural mapping of BIOS & DOS interrupt services.

---

## 1. Intellectual Property and Licensing Analysis

1. **Ralf Brown's Interrupt List (RBIL):**
   - *Status:* Freely accessible reference compendium maintained since 1991. The data format allows personal and educational use.
   - *Policy:* IDEARM **does not copy RBIL entries verbatim**. Instead, RBIL is utilized strictly as an index verification tool to cross-reference interrupt numbers, subfunctions, and hardware register contracts.
2. **IBM PC Technical Reference & MS-DOS Programmer's Reference:**
   - *Status:* Industry specifications. Under established legal doctrine (*Feist Publications v. Rural Telephone Service*; *Google v. Oracle*), pure functional interfaces, calling contracts, register usage, and factual hardware tables are non-copyrightable functional facts.
3. **IDEARM Editorial Policy:**
   - All summaries, explanations, failure analyses, and illustrative examples are **original compositions** authored specifically for IDEARM in Spanish and English.
   - Every service entry documents inputs, outputs, buffer layouts, error codes, and emu8086 compatibility notes without third-party textual reproductions.

---

## 2. Interrupt Vector Classification (00h–FFh)

The 256 vector slots are classified into seven discrete architectural states:

| Vector Range | Classification | Dominant Function |
|---|---|---|
| `00h`–`04h` | `ARCHITECTURAL_CPU` | CPU Exceptions: `#DE` (Divide Error), `#DB` (Debug), `NMI`, `#BP` (Breakpoint), `#OF` (Overflow/INTO) |
| `05h`–`1Fh` | `BIOS_STANDARD` | BIOS Hardware & Device Services: INT 10h (Video), INT 13h (Disk), INT 16h (Keyboard), INT 08h (Timer IRQ0), INT 09h (Keyboard IRQ1) |
| `20h`–`3Fh` | `DOS_KERNEL` | MS-DOS Kernel Services: INT 20h (Terminate), INT 21h (Main API Dispatcher), INT 25h/26h (Direct Disk), INT 27h (TSR), INT 2Fh (Multiplex) |
| `40h`–`5Fh` | `BIOS_STANDARD` / `ENVIRONMENT_DEPENDENT` | BIOS Disk Floppy Relocation, NetBIOS, ROM basic |
| `60h`–`67h` | `ENVIRONMENT_DEPENDENT` | User Application Hooks (60h–66h), Expanded Memory Specification (EMS, INT 67h) |
| `68h`–`7Fh` | `RESERVED` | Reserved for DOS & Hardware Extensions |
| `80h` | `ENVIRONMENT_DEPENDENT` | Unix / Linux x86 32-bit Legacy Syscall Handler |
| `81h`–`FFh` | `RESERVED` / `UNDOCUMENTED` | BASIC pointers, dynamic ROM table intercepts |

---

## 3. High-Priority Services for Academic Curriculum

The following 18 functions are prioritized with complete contracts due to presence in the academic curriculum and the built-in emulator (`idearm-emu8086`):

1. **INT 21h Services:**
   - `01h`: Character Input with Echo (AL = char).
   - `02h`: Character Output (DL = char).
   - `06h`: Direct Console I/O (DL = 0FFh input or char output).
   - `07h`: Direct Character Input without Echo (AL = char).
   - `08h`: Character Input without Echo (checks Ctrl-C).
   - `09h`: Print $-Terminated String (DS:DX = address).
   - `0Ah`: Buffered Keyboard Input (DS:DX = buffer).
   - `0Bh`: Get Input Status (AL = status).
   - `25h`: Set Interrupt Vector (AL = int, DS:DX = handler).
   - `2Ah`: Get System Date (CX = year, DH = month, DL = day, AL = day-of-week).
   - `2Ch`: Get System Time (CH = hour, CL = min, DH = sec, DL = 1/100s).
   - `35h`: Get Interrupt Vector (AL = int, returns ES:BX).
   - `3Ch`: Create File (DS:DX = path, CX = attr, returns AX = handle).
   - `3Dh`: Open File (DS:DX = path, AL = mode, returns AX = handle).
   - `3Eh`: Close File Handle (BX = handle).
   - `3Fh`: Read from File / Device (BX = handle, CX = count, DS:DX = buffer).
   - `40h`: Write to File / Device (BX = handle, CX = count, DS:DX = buffer).
   - `4Ch`: Terminate Process with Return Code (AL = exit code).
2. **BIOS INT 10h Video Services:**
   - `00h`: Set Video Mode (AL = mode).
   - `02h`: Set Cursor Position (BH = page, DH = row, DL = col).
   - `0Eh`: Teletype Output (AL = char, BL = color).
3. **BIOS INT 16h Keyboard Services:**
   - `00h`: Read Key (AH = scan code, AL = ASCII char).
   - `01h`: Check Buffer (ZF = 1 if empty, ZF = 0 if key waiting).
