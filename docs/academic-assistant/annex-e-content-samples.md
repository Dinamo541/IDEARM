# Annex E — Written content samples

> Part of [`expansion-plan.md`](expansion-plan.md). Covers deliverable 11.
>
> Six samples to validate the model, **not** the complete encyclopedia. Each one declares what it covers, what it
> leaves out and its sources. The text is written as it would be published to the student (this is the English
> version, the original of the corpus according to §D.4; the Spanish version is its translation). The "Sample
> coverage" and "Verification state" sections are plan metadata, not part of the sheet.

Index: [E.1 IMUL](#e1-an-instruction-with-several-forms-imul) · [E.2 AX](#e2-a-register-with-subregisters-ax) ·
[E.3 Segments](#e3-segments-sheet-how-many-segments-does-a-program-have) ·
[E.4 `:` and `[ ]`](#e4-signs-sheet-the-colon-and-the-brackets) ·
[E.5 INT 21h/0Ah](#e5-service-function-int-21h-function-0ah) ·
[E.6 Procedure with a stack and an ABI](#e6-a-procedure-with-a-stack-and-an-abi)

---

## E.1 An instruction with several forms: IMUL

> **Sample coverage:** the 13 encodings the reference documents, grouped into three families of forms; flags per
> form family; implicit operands; requirement per form; availability in the built-in emulator; two examples (one
> fragment, one runnable); comparison with `MUL`.
> **Outside the sample:** the 64-bit forms are listed but not illustrated; the bit-by-bit ModRM encoding is not
> developed.

### IMUL — Signed integer multiplication

`x86.instr.imul` · Family F-02 Integer arithmetic · Intermediate level
Prerequisites: signed integers and two's complement · `x86.reg.ax` · `x86.instr.mul`

**Summary.** Multiplies two integers **interpreting them as signed values** and keeps the sign of the result.

**What it does and what it is for.** Multiplying two *n*-bit numbers can need up to 2*n* bits for the result. IMUL
solves that in two different ways, which is why it has several forms:

- The **one-operand form** stores the whole product in a pair of registers, so it **never loses information**. It
  is the only one that exists on the 8086 and the one to use when the result can be large.
- The **two- and three-operand forms** store the product in the destination register, the same size as the
  factors, so they **can truncate**. They are easier to read and write, and they report truncation through the
  flags. They appear from the 80186 (the ones with an immediate) and the 80386 (the one that multiplies by a
  register or a memory location).

The difference with `MUL` is not performance: it is the **interpretation** of the operands. `MUL` treats the bits
as an unsigned number; `IMUL` treats them as a signed number. With `AL = 0FFh`, `MUL` sees 255 and `IMUL` sees −1.

#### Forms

**Family 1 — one operand: the product does not fit in one register, so it uses two.**

| Syntax | Implicit multiplicand | Product | Requirement |
|---|---|---|---|
| `IMUL r/m8` | `AL` | `AX` | 8086 |
| `IMUL r/m16` | `AX` | `DX:AX` | 8086 |
| `IMUL r/m32` | `EAX` | `EDX:EAX` | 80386 |
| `IMUL r/m64` | `RAX` | `RDX:RAX` | x86-64, **64-bit mode only** |

**Family 2 — two operands: destination and source, the product is truncated to the destination.**

| Syntax | Requirement |
|---|---|
| `IMUL r16, r/m16` · `IMUL r32, r/m32` | 80386 |
| `IMUL r64, r/m64` | x86-64, **64-bit mode only** |

**Family 3 — three operands: destination, source and immediate.**

| Syntax | Requirement |
|---|---|
| `IMUL r16, r/m16, imm8` · `IMUL r16, r/m16, imm16` | 80186 |
| `IMUL r32, r/m32, imm8` · `IMUL r32, r/m32, imm32` | 80386 |
| `IMUL r64, r/m64, imm8` · `IMUL r64, r/m64, imm32` | x86-64, **64-bit mode only** |

**Dialect note.** When you write `imul bx, 5`, with two operands and an immediate, the assembler does **not** use
family 2: it generates family 3 with the destination repeated as the source, that is `imul bx, bx, 5`. That is why
`imul bx, 5` works on an 80186, while `imul bx, cx` needs an 80386.

**No form accepts an immediate as its only operand**, and family 1 **accepts no immediate operand at all**:
`imul 5` is not valid.

#### Operands

| Form | Explicit operands | Implicit operands |
|---|---|---|
| `IMUL r/m16` | 1: the multiplier, **read** (register or memory; DS segment by default) | `AX` is **read** (multiplicand) · `DX` and `AX` are **written** (product) |
| `IMUL r16, r/m16` | 1: destination, **read and written** · 2: source, **read** | none |
| `IMUL r16, r/m16, imm8` | 1: destination, **written** · 2: source, **read** · 3: immediate, **read** and sign-extended to 16 bits | none |

That the one-operand form reads AX **without AX appearing in the code** is the most common source of mistakes: if
you forget to load AX, you multiply whatever was there.

#### Flags

| Flag | Family 1 (one operand) | Families 2 and 3 |
|---|---|---|
| **CF** | 1 if the high half of the product is **not** the sign extension of the low half; 0 if the product fits entirely in the low half | 1 if the product **had to be truncated** to fit in the destination; 0 if it fit exactly |
| **OF** | same as CF | same as CF |
| SF, ZF, AF, PF | **undefined** | **undefined** |

"Undefined" means exactly that: **it can be 0 or 1 and must not be consulted**. It does not mean "is 0". A
conditional jump that depends on ZF right after an IMUL is a mistake, even if it sometimes works.

Correct use: after a two- or three-operand form, check CF (or OF) to know whether the result in the destination is
the true one or a truncated version. After the one-operand form, CF tells you whether you can ignore the high half
without losing anything.

#### Operation

```
Family 1 (16 bits):   DX:AX := AX * SignExtend(source)
Family 2:             destination := Truncate(destination * source)
Family 3:             destination := Truncate(source * SignExtend(immediate))
```

In words: family 1 places the whole result split across two registers, the high half in DX and the low half in AX.
The other two compute the same product and **keep only the low bits**, setting CF and OF if something was lost on
the way.

#### Requirements and availability

| Context | Family 1 (8/16 bits) | Family 2 | Family 3 |
|---|---|---|---|
| `dos-exe-16` project, `cpu = 8086` | available | requires 80386 | requires 80186 |
| `dos-exe-16` project, `cpu = 80386` | available | available | available |
| `win-pe64-console` project | available (plus the 64-bit forms) | available | available |
| **IDEARM's built-in emulator** | available | **no**: it does not implement opcode `0F AF` | **no**: it implements neither `69h` nor `6Bh` |
| GDB on a native program | available | available | available |

The emulator row is not a mistake in your code: the instruction exists on your processor and the assembler accepts
it; it is the built-in emulator that does not run it. To study it while it runs, use a native project with GDB, or
Turbo Debugger through DOSBox.

#### Example 1 — fragment: the whole product and what CF says

Context: an illustrative fragment, any 16-bit profile. It is not a complete program.
Initial state: `AX = 0FFFEh` (−2), `BX = 3`.

```asm
mov  ax, -2         ; AX = FFFE
mov  bx, 3          ; BX = 0003
imul bx             ; DX:AX = FFFF:FFFA  →  -6
                    ; CF = 0 and OF = 0: FFFF is the sign extension of FFFA,
                    ; so the result fits entirely in AX and DX adds nothing
```

Final state: `DX = 0FFFFh`, `AX = 0FFFAh` (together, −6), `CF = 0`, `OF = 0`. `SF`, `ZF`, `AF` and `PF` are
**undefined** and nothing is claimed about them.

Contrast, with the same code and other data: with `AX = 300` and `BX = 200`, the product is 60 000, which does not
fit in 16 signed bits. `DX:AX` is `0000:EA60` and **CF = OF = 1**, because `DX = 0` is not the sign extension of
`EA60` (whose high bit is 1, that is, a negative). The whole result is correct; what CF tells you is that **you
cannot keep only AX**.

#### Example 2 — complete program: truncation detected

Declared context: profile `dos-exe-16`, `cpu = 80186` or higher, dialect TASM 4.1 or MASM 6.11, toolchain
`borland-tasm`, run in DOSBox. **It cannot be debugged in the built-in emulator** because it uses family 3.

```asm
.186                          ; the three-operand form needs an 80186
.MODEL small
.STACK 100h

.DATA
    fits      DB 'The result fits in 16 bits', 13, 10, '$'
    truncated DB 'WARNING: the result was truncated', 13, 10, '$'
    value     DW 1000

.CODE
main PROC
    mov  ax, @data
    mov  ds, ax

    imul bx, value, 40        ; BX = 1000 * 40 = 40000, which does not fit as signed
    jc   was_truncated        ; CF reports the truncation

    lea  dx, fits
    jmp  print
was_truncated:
    lea  dx, truncated
print:
    mov  ah, 09h
    int  21h

    mov  ax, 4C00h            ; ends with exit code 0
    int  21h
main ENDP
END main
```

Checkable result: it prints the warning, because 40 000 exceeds the range of a signed 16-bit integer (−32 768 to
32 767). `BX` ends up as `9C40h`, which as a signed value is −25 536: truncation in action. Change `40` to `30` and
the product (30 000) fits, CF stays 0 and the program prints the other message.

#### Common mistakes

1. **Forgetting to load AX** before the one-operand form. The assembler does not warn: the instruction is valid.
2. **Checking ZF or SF** after an IMUL. They are undefined; use `CMP` or `TEST` if you need to compare.
3. **Using the three-operand form in an 8086 project.** TASM does not reject it: **it expands it into an equivalent
   sequence**, so the program works in DOSBox and would fail on a real 8086. IDEARM does warn about it.
4. **Assuming `imul bx, cx` is as old as `imul bx, 5`.** The first needs an 80386; the second, an 80186.
5. **Confusing `IMUL` with `MUL`** when the data is negative: with `AL = 0FFh` and `BL = 2`, `MUL BL` gives 510 and
   `IMUL BL` gives −2.

#### Related

`MUL` (contrast: unsigned) · `IDIV` (pair: signed division) · `SHL` (alternative: multiplying by a power of two) ·
`CBW`/`CWD` (sign extension, needed before dividing) · `CF` and `OF` (flags).

#### Sources

- Kip R. Irvine, *Lenguaje ensamblador para computadoras basadas en Intel* (Spanish translation of *Assembly
  Language for Intel-Based Computers*), 5th ed., §7.4.2 "The IMUL instruction", printed pages 205–206 (PDF
  237–238). From here: the three families of forms, the fact that the 8086/8088 processors only support the
  one-operand format, the truncation of the two- and three-operand forms, and the advice to check CF or OF.
- Intel SDM, volume 2, `IMUL` entry. From here: the table of 13 encodings, the validity in 64-bit mode and the
  exact wording of the flags: "For the one operand form … the CF and OF flags are set when significant bits are
  carried into the upper half of the result … The SF, ZF, AF, and PF flags are undefined".
  **Consulted through an unofficial HTML rendering of the SDM (felixcloutier.com/x86/imul, 2026-09-25); to be
  confirmed against the PDF of volume 2 of revision 093 → task AA-P0-03.**
- Assigning each encoding to a specific generation (`0F AF` → 80386, `69`/`6B` → 80186) **is an assumption of this
  plan**: the SDM table has no generation column. It is closed in AA-P0-03.
- Availability in the emulator: reading `Cpu8086.executeOpcode` (annex A §A.8) — opcodes `69h`, `6Bh` and the `0Fh`
  prefix are not implemented.

> **Verification state of the examples:** `NOT_RUN`. Nothing was assembled or run in this session. Example 2 is
> written for the declared profile and its verification is part of AA-P3-06.

---

## E.2 A register with subregisters: AX

> **Sample coverage:** AX with its views AH and AL, its relation to EAX and RAX, conventional uses versus uses
> imposed by the ISA, partial-write semantics, and which debugger shows it.
> **Outside the sample:** the other general registers (BX, CX, DX) reuse this structure with their own list of
> imposed uses; R8–R15 have a sheet of their own because of their encoding restrictions.

### AX — 16-bit accumulator

`x86.reg.ax` · Group G-1 General · 16 bits · Basic level

**What it is.** A 16-bit store inside the CPU, much faster than memory. It is called the "accumulator" by
tradition: it is the register many instructions use by default.

**Views: a register inside another.**

```
 63                              31              15       8 7        0
┌────────────────────────────────┬───────────────┬─────────┬─────────┐
│                                │               │   AH    │   AL    │
│                                │               └─────────┴─────────┘
│                                │               └────── AX ─────────┘
│                                └───────────────────── EAX ─────────┘
└──────────────────────────────────────────────── RAX ───────────────┘
```

AX is the low half of EAX, which in turn is the low half of RAX. And AX splits into two 8-bit halves: AH (high)
and AL (low). The same relation exists in BX, CX and DX **[source: Irvine 5th ed. (es), §2.2.2, printed 34–35]**.

**What exactly happens when you write to a part.** This is the rule that causes the most trouble:

| You write to | AH | AL | AX | EAX (bits 31–16) | RAX (bits 63–32) |
|---|---|---|---|---|---|
| `AL` | unchanged | new value | its low half changes | **unchanged** | unchanged |
| `AH` | new value | unchanged | its high half changes | **unchanged** | unchanged |
| `AX` | changes | changes | new value | **unchanged** | unchanged |
| `EAX` | changes | changes | changes | new value | **zeroed** |

The last row is the surprise of 64-bit mode: `mov eax, 5` leaves RAX at exactly 5, not "5 in the low half and
whatever was above". 8- and 16-bit writes do **not** do that.

A minimal example, with the state before and after:

```asm
mov ax, 1234h      ; AX = 1234h  →  AH = 12h, AL = 34h
mov al, 0FFh       ; AX = 12FFh  →  AH is still 12h
mov ah, 0          ; AX = 00FFh
```

**Conventional uses.** Accumulator for arithmetic and destination of returned values. These are habits: you can
use BX or CX just as well.

**Uses imposed by the ISA.** Here there is no choice; the instruction requires AX (or AL, or AH):

| Instruction | Role of AX or of its views |
|---|---|
| `MUL r/m8` / `IMUL r/m8` | `AL` is the multiplicand; `AX` receives the product |
| `MUL r/m16` / `IMUL r/m16` | `AX` is the multiplicand; `DX:AX` receives the product |
| `DIV r/m8` / `IDIV r/m8` | `AX` is the dividend; `AL` receives the quotient and `AH` the remainder |
| `DIV r/m16` / `IDIV r/m16` | `DX:AX` is the dividend; `AX` receives the quotient and `DX` the remainder |
| `CBW` | extends the sign of `AL` into `AH` |
| `CWD` | extends the sign of `AX` into `DX` |
| `XLAT` | `AL` is the index into the table and receives the result |
| `IN` / `OUT` | `AL`, `AX` or `EAX` is the only possible destination or source |
| `LAHF` / `SAHF` | `AH` is where the low byte of the flags travels |
| `INT 21h` and other services | `AH` selects the function; it is a **convention of the service**, not of the CPU |

The last row matters: `AH` selecting the function of `INT 21h` is not a property of the processor, it is the
contract MS-DOS defined. Another system uses another register.

**Access restrictions.** None by privilege: AX is reachable in every mode and level. A single encoding restriction,
in 64-bit mode: an instruction that needs the REX prefix (because it uses R8–R15, or new 8-bit registers such as
`SIL`) **cannot refer to AH, BH, CH or DH** in the same instruction; the codes that named those registers now name
`SPL`, `BPL`, `SIL` and `DIL` **[assumption of this plan, to be confirmed against SDM vol. 2 → AA-P0-03]**.

**What the debugger shows.**

| Backend | Does it show AX? |
|---|---|
| Built-in emulator (`emu8086`) | yes, and AH and AL separately too |
| GDB on a native program | yes, as part of RAX or EAX; the IDEARM panel lists `RAX` on 64 bits and `EAX` on 32 |
| Turbo Debugger / CodeView | yes, in its own window |

**Related.** `AH`, `AL` (views) · `EAX`, `RAX` (registers that contain it) · `BX`, `CX`, `DX` (same group,
different imposed uses) · `MUL`, `DIV`, `CBW`, `XLAT` (instructions that impose it).

**Sources.** Irvine 5th ed. (es), §2.2.2 "Basic execution environment", printed 34–35 (PDF 66–67): the overlap
figure, the eight general-purpose registers and six segment registers, and the statement that the same overlapping
relationship exists for EAX, EBX, ECX and EDX · Irvine §7.4.1 and §7.4.4 for the roles imposed in multiplication and
division (printed 204 and 208) · the zeroing when writing a 32-bit register in 64-bit mode and the REX prefix
restriction are assigned to AA-P0-03 against SDM 093.

**A note on fundamentals, not architecture.** At the circuit level, a CPU register is built from flip-flops: Mano
presents it with a four-bit register made of clocked D flip-flops with a clear input **[source: M. Morris Mano,
*Diseño digital* (Spanish translation of *Digital Design*), 3rd ed., figure 6-1, printed 218 / PDF 229]**. That
explains **how** a register can exist, not **what AX is**: AX is a specification of the x86 architecture (16 bits,
a name, some views and some imposed uses) that has been implemented with very different circuits in each
generation. Do not confuse the two levels.

---

## E.3 Segments sheet: how many segments does a program have?

> **Sample coverage:** the distinction segment register / logical region / object section, the six registers, the
> real-mode address calculation and the default segment.
> **Outside the sample:** protected-mode selectors and descriptors, paging, A20 and DOS memory models, which have
> linked sheets of their own.

### Segments: three different things with the same name

`concept.segments-three-meanings` · Block C · Basic→intermediate level

Much of the confusion around segments comes from the word naming three things that are related but **not
interchangeable**.

**1. Segment register: a CPU register.** There are **six**: `CS`, `DS`, `SS`, `ES` since the 8086, plus `FS` and
`GS` since the 80386 **[source: Irvine §2.2.2, printed 34; Stallings §11.2, printed 416: "There are six segment
registers"]**. Each holds the base of an area of memory, and its role is to say **which areas are reachable right
now**:

| Register | Role |
|---|---|
| `CS` | the code being executed; together with IP it forms the address of the next instruction |
| `DS` | the data, and the default segment of almost every memory access |
| `SS` | the stack; it is the default segment when the address is computed with `BP` or `SP` |
| `ES` | a second data segment; mandatory as the destination of the string instructions |
| `FS`, `GS` | two more segments with no fixed role; the operating system usually uses them for its own data |

**2. Logical region: an area you declare in the source code.** MASM and TASM `.DATA`, `.CODE`, `.STACK`,
`MY_SEGMENT SEGMENT … ENDS` in the classic form, `section .data` in NASM. It is an organization decision, and the
assembler translates it into addresses.

**3. Object file section: an entry of the binary format.** `.text`, `.data`, `.bss` in an ELF; equivalent sections
in OMF and COFF. Here "segment" is no longer a register or a declaration: it is a part of the file, with its size,
its permissions and its relocations.

#### The conclusion that is often taught wrong

> **A program is not limited to four segments.**

You can declare as many logical regions as you like. What is limited is how many can be **reachable at once**: six,
one per segment register. Working with more means loading another base into a register when you need it, which is
exactly what `mov ax, @data` / `mov ds, ax` do at the start of a DOS program, or `LES` when preparing a string
destination.

In long mode (64 bits) the question almost disappears: `CS`, `DS`, `ES` and `SS` have base 0 and all of memory is
seen as a flat space; only `FS` and `GS` keep a useful base.

#### From the calculation to the address, in real mode

Four names, in order:

1. **Effective address** — what the addressing mode computes from the operands: for `[BX+SI+4]`, it is
   `BX + SI + 4`.
2. **Logical address** — the pair `segment:offset`, for example `DS:0014h`. It is written with a colon, which here is
   **notation**, not an operator (see [E.4](#e4-signs-sheet-the-colon-and-the-brackets)).
3. **Linear address** — the segment base plus the offset.
4. **Physical address** — in real mode the same as the linear one; in protected mode with paging, the result of
   translating the linear one.

In real mode the base is obtained by multiplying the segment register by 16:

```
physical = (segment register × 16) + offset
```

With `DS = 0710h` and offset `0014h`: `0710h × 16 = 07100h`, and `07100h + 0014h = 07114h`.

Consequences worth knowing: segments start every 16 bytes, so they **overlap** (address `07114h` is also
`0711h:0004h`); the 16-bit offset limits each segment to 64 KiB; and the highest reachable address is
`0FFFFh × 16 + 0FFFFh = 10FFEFh`, a little over 1 MiB, which historically forced a decision about what happened
when the megabyte overflowed (the A20 gate, with a sheet of its own).

In protected mode the segment register is not multiplied: it is a **selector** that indexes a table of
descriptors, and the descriptor holds the base, the limit and the permissions **[source: Stallings §11.2 and
figure 11.2, printed 415–416]**.

#### Default segment and override

| Situation | Segment used if you say nothing |
|---|---|
| a normal data read or write | `DS` |
| the address is computed with `BP` or `SP` | **`SS`** |
| destination of a string instruction (`[DI]`) | **`ES`** |
| fetching the current instruction | `CS` |

To use another one, write it in front: `mov al, ES:[BX]`, `mov ax, SS:[SI]`. That is a **segment override**, and it
is real assemblable syntax, not notation.

**Related.** Registers `CS`, `DS`, `SS`, `ES`, `FS`, `GS` · effective address · addressing modes · DOS memory
models · `near` and `far` pointers · directives `.MODEL`, `SEGMENT`, `ASSUME`, `section` · executable format.

**Sources.** Irvine §2.2.2 (printed 34) and §2.3.1 "Real-address mode" (printed 39–41) · Stallings §11.2 "Pentium
addressing modes", printed 415–416 and figure 11.2 (segment registers, descriptors, base + effective = linear) ·
DOS memory models and the A20 gate are documented from Abel, pending his PDF becoming readable (AA-P0-02).

---

## E.4 Signs sheet: the colon and the brackets

> **Sample coverage:** the four entries of `:` that acceptance case 2 asks for, and `[ ]` in the three dialects
> with the `mov ax, var` difference.
> **Outside the sample:** the rest of the glossary (`$`, `$$`, `%`, `?`, `@`, `< >`, `&`, arithmetic operators),
> with the same structure.

### The colon `:` — four meanings, and only two are code

`syntax.common.colon.*` · Block D · Basic level

**There is no single meaning.** The colon is not "concatenation" or a "separator" in general: it means something
different in each context, and two of those uses **are never written in an instruction**.

#### 1. Label definition — `loop_top:`

```asm
loop_top:
    dec cx
    jnz loop_top
```

Marks this point of the program with a name. The name then stands for the address of what follows.
**It is assemblable syntax**, and it works in MASM, TASM and NASM. A detail: there is no colon in `MAIN PROC`,
because `PROC` is another way of declaring a named point.

#### 2. Segment override — `ES:[DI]`

```asm
mov al, ES:[DI]      ; reads from ES, not from DS
mov ax, SS:[BX]      ; reads from SS, even though the base is BX
```

In front of a memory operand, it says **which segment register to use** instead of the default one. **It is
assemblable syntax** and produces a prefix byte in the machine code.

#### 3. Logical address notation — `CS:IP`, `DS:DX`, `SS:SP`

This **is not code**. It is how the documentation writes a *segment:offset* pair:

- `CS:IP` — "where the next instruction is".
- `SS:SP` — "where the top of the stack is".
- `DS:DX` — "the address of the buffer", as the documentation of `INT 21h` function 09h says.

When you read "pass the address of the string in `DS:DX`", what you have to write is:

```asm
mov ax, @data
mov ds, ax           ; DS points to the data segment
lea dx, message      ; DX = offset of the message
```

Two different instructions, one per register. There is **no** `mov ds:dx, message` instruction.

#### 4. Register pair notation — `DX:AX`, `EDX:EAX`

Not code either. It describes **a single value split across two registers**, with the first one as the high half:

```asm
mov ax, -2
mov bx, 3
imul bx              ; the documentation writes DX:AX := AX * BX
                     ; in the machine these are two registers holding one half each
```

To work with that value, the two registers are handled separately. `DX:AX` is shorthand in the explanation, not an
operand.

#### How to tell them apart when reading

| You see this | It is |
|---|---|
| a name and `:` at the start of the line | label definition (code) |
| `REG:` right before a `[` or a variable name, inside an operand | segment override (code) |
| a segment register (`CS`, `DS`, `SS`, `ES`) and `:` with a register that is not a segment register, in a text | logical address notation (not code) |
| two registers of the same kind, such as `DX:AX`, in a text | pair notation (not code) |

---

### The brackets `[ ]` — "the contents of", with a dialect trap

`syntax.common.brackets` · Block D · Basic level

In an instruction, brackets around an expression mean **"the contents of memory at that address"** instead of the
address itself:

```asm
mov ax, [bx]         ; AX = the 16 bits at the address held in BX
mov ax, bx           ; AX = the value of BX
```

Inside the brackets goes an address expression, with rules of its own depending on the address size (addressing
modes sheet). With a 16-bit address: base `BX` or `BP`, index `SI` or `DI`, and an optional displacement.
`[BX+SI+4]` is valid; `[BX+BP]` is not, because it has two bases; `[AX]` is not, because AX cannot be a base.

#### The trap: `mov ax, variable`

Here the dialects **do not agree**, and assuming otherwise is the most common portability mistake:

| Dialect | `mov ax, var` | `mov ax, [var]` | How the address is asked for |
|---|---|---|---|
| **NASM 3.02** | loads **the address** of `var` | loads **the contents** | `mov ax, var` already gives it; there is no `OFFSET` |
| **MASM 6.11 / TASM in MASM mode** | loads **the contents** | loads **the contents** | `mov ax, OFFSET var` |

The NASM manual states it as a rule: every access to the **contents** of a memory location requires brackets, and
every access to the **address** of a variable goes without them; and it explains that it does so to avoid MASM's
ambiguity, where the same syntax could generate different code depending on how the symbol had been declared
**[source: NASM 3.02 manual, ch. 2]**.

That is why this plan **does not offer the same example as valid in all three dialects**: a fragment written for
MASM that uses `mov ax, variable` expecting the contents means something else in NASM, and the other way round.

#### `LEA` versus reading memory

With the same bracketed operand, two different instructions:

```asm
lea dx, [bx+si+4]    ; DX = BX + SI + 4      (computes the address; does not touch memory)
mov dx, [bx+si+4]    ; DX = contents at that address  (reads 16 bits from memory)
```

`LEA` is an address calculator that uses the addressing hardware; it does not access memory and modifies no flags.

**Related.** `:` (the four uses) · `OFFSET`, `SEG`, `PTR` · effective address · addressing modes · `LEA` · `MOV`.

**Sources.** NASM 3.02 manual, ch. 2 "NASM versus MASM" (the bracket rule and the absence of `OFFSET`) and ch. 3
"Effective addresses" · Irvine §4.3.1 "OFFSET operator", printed 94 · Irvine §4.4 "Indirect addressing", printed
99–103 · Stallings figure 11.2 (base, index, scale and displacement).

---

## E.5 Service function: INT 21h function 0Ah

> **Sample coverage:** the complete contract of a function with an input and output buffer, the relation to
> `DS:DX`, availability in the built-in emulator with its documented divergence, and the contrast with function
> 09h.
> **Outside the sample:** the other input functions (`01h`, `06h`, `07h`, `08h`, `3Fh`), which share the structure.

### INT 21h, function 0Ah — Read a line from the keyboard into a buffer

`dos.int21.0ah` · Block E · Intermediate level
Prerequisites: `INT` (instruction) · the `DS:DX` notation · declaring data with `DB` and `DUP`

**What it is and what it is not.** It is an **MS-DOS service**, not a CPU instruction. The `INT 21h` instruction
raises a software interrupt; the program that handles that interrupt is MS-DOS, and it looks at `AH` to know what is
being asked. `AH = 0Ah` means "read a whole line from the keyboard and store it where I tell you".

**How it is selected.** `AH = 0Ah`. That "0Ah" is **not the interrupt number** (which is 21h) nor a vector: it is
the function number within the service.

#### Inputs

| Register | Contents |
|---|---|
| `AH` | `0Ah` |
| `DS:DX` | the address of the input structure (see below) |

Remember that `DS:DX` is **notation**: the two registers have to be loaded separately.

#### The input structure

Three parts, in this order:

| Offset | Size | Direction | Meaning |
|---|---|---|---|
| 0 | 1 byte | you write it **before** | maximum number of characters, **the Enter key included** |
| 1 | 1 byte | DOS writes it | how many characters were typed, **not counting Enter** |
| 2 | *n* bytes | DOS writes them | the characters, terminated with `0Dh` (carriage return) |

**The first field counts the Enter key.** If you write 10, the user can type at most **9** characters: the tenth
slot is taken by the carriage return **[source: Irvine §13.2.3, printed 443: the maxInput field specifies the
maximum number of characters the user can enter, including the Enter key]**. Reserving one extra byte in the buffer
is the prudent habit.

While typing, the user can correct with Backspace. They finish with Enter. Keys that produce no ASCII character
(F1, PgDn…) are discarded and never reach the buffer.

#### Outputs

The structure is filled in: field 1 with the count, and from field 2 on the characters plus the final `0Dh`. The
documented contract **promises nothing about the flags** or other registers, so this material claims nothing about
them: if your program needs to keep a value, save it yourself.

#### Errors and edge cases

| Situation | What happens |
|---|---|
| maximum = 0 | nothing is read |
| the user types more than the maximum | DOS stops accepting characters; it does not overflow the buffer |
| the user presses Enter without typing anything | the count stays at 0 and the first data byte is `0Dh` |
| you forget to write the maximum before calling | the byte holds garbage and the behavior is unpredictable |

#### Availability in IDEARM

| Backend | State | Detail |
|---|---|---|
| Built-in emulator (`emu8086`) | **available, with a known divergence** | accepts **one character more** than the contract: it checks "typed < maximum" instead of "typed < maximum − 1". A program that fills the buffer right to the limit behaves differently here and on real DOS |
| Turbo Debugger / CodeView in DOSBox | available | it is DOSBox's real DOS |
| Native Windows or Linux | **does not apply** | `INT 21h` is an MS-DOS service; it does not exist in a modern native program. The equivalent is a call to the system API or a syscall, with another contract |

The emulator's divergence is recorded as a defect to fix (finding A-26 of annex A); as long as it exists, the sheet
shows it, because a student who does not know about it will blame their own code.

#### Example — complete program

Declared context: profile `dos-exe-16`, `cpu = 8086`, dialect TASM 4.1 or MASM 6.11, toolchain `borland-tasm`, run
in DOSBox, **debuggable in the built-in emulator** (it only uses implemented functions: `09h`, `0Ah`, `4Ch`).

```asm
.8086
.MODEL small
.STACK 100h

.DATA
    question  DB  'What is your name? $'
    greeting  DB  13, 10, 'Hello, $'
    ending    DB  '!', 13, 10, '$'

    ; Structure for function 0Ah: the maximum counts the Enter key,
    ; so with 21 there is room for 20 typed characters.
    maximum   DB  21
    typed     DB  ?
    yourname  DB  21 DUP(?)

.CODE
main PROC
    mov  ax, @data
    mov  ds, ax

    lea  dx, question         ; DS:DX points to the '$'-terminated string
    mov  ah, 09h
    int  21h

    lea  dx, maximum          ; DS:DX points to the FIRST byte of the structure
    mov  ah, 0Ah
    int  21h

    ; DOS left the carriage return inside the buffer; replace it with '$'
    ; so the name can be printed with function 09h.
    mov  bl, typed            ; how many characters the user typed
    mov  bh, 0
    mov  yourname[bx], '$'    ; replaces the 0Dh that sits right after them

    lea  dx, greeting
    mov  ah, 09h
    int  21h

    lea  dx, yourname
    mov  ah, 09h
    int  21h

    lea  dx, ending
    mov  ah, 09h
    int  21h

    mov  ax, 4C00h
    int  21h
main ENDP
END main
```

An explanation of the two lines that usually go wrong:

- `lea dx, maximum` points to the **first** byte of the structure, the maximum, not to the character area. The
  three parts must be contiguous and in that order, which is why they are declared together.
- `mov yourname[bx], '$'` writes the `$` **exactly where DOS left the `0Dh`**, because `typed` does not count the
  Enter key. Using `typed + 1` would eat one byte too many.

Checkable result: with the input `Ana` the output is `What is your name? Ana` and, on the next line,
`Hello, Ana!`. In the built-in debugger, after the second `int 21h`, `typed` is 3 and the first three bytes of
`yourname` are `41h 6Eh 61h`.

#### Comparison with function 09h

| | `AH = 09h` | `AH = 0Ah` |
|---|---|---|
| Direction | output | input |
| `DS:DX` points to | a `$`-terminated string | the three-part structure |
| End of the data | the `$` character, which is **not** printed | the `0Dh` DOS writes, and the count in field 1 |
| Returns | nothing, according to Irvine | the filled-in structure |
| Classic mistake | forgetting the `$`: DOS prints until it finds one, and may print garbage | forgetting the maximum, or confusing the maximum field with the data area |

**A discrepancy between sources, explained on purpose.** Irvine documents that function 09h returns nothing
**[source: Irvine §13.2.1, printed 440]**, while IDEARM's built-in emulator leaves `AL = 24h`, the code of `$`,
which is what the specialized interrupt lists document. It is not a source contradicting reality: it is one source
**omitting** a detail another one records. The rule for your code: do not depend on `AL` after function 09h,
because the contract you can rely on is the one both sources share.

**Related.** `INT` (instruction) · `dos.int21.09h` · `dos.int21.01h` (read a character) · `dos.int21.3fh` (read from
a handle) · `dos.int21.4ch` (terminate) · `DS:DX` notation · `DB`, `DUP` · vector index.

**Sources.** Irvine 5th ed. (es), §13.2.3 "Selected input functions", printed 443–444 (PDF 475–476): the
three-field structure, the "including the Enter key", the filtering of non-ASCII keys and the use of Backspace ·
Irvine §13.2.1, printed 440, for function 09h · `DosInterruptHandler.handleInt21`, case `0x0A`, for what the
emulator does · A specialized interrupt list, with its version and its documented character, remains to be pinned
and its license checked (AA-P0-05).

> **Verification state of the example:** `NOT_RUN`.

---

## E.6 A procedure with a stack and an ABI

> **Sample coverage:** a DOS procedure with parameters passed on the stack and a BP frame, the distinction between
> what the instruction does and what the convention imposes, and the same problem solved with the Microsoft x64
> ABI.
> **Outside the sample:** 32-bit `cdecl`/`stdcall` and System V AMD64, with the same structure; `far` calls;
> dynamic local variables.

### A procedure with parameters on the stack

`concept.stack-frame` · Block F · Intermediate level
Prerequisites: the stack · `PUSH`/`POP` · `CALL`/`RET` · `BP` and `SP`

#### What the instruction does, and what you decide

It is worth separating from the start, because it is the usual confusion:

| The CPU does it | The calling convention decides it |
|---|---|
| `CALL` pushes the return address on the stack and jumps | **where the parameters go** (registers, stack, memory) |
| `RET` pops that address and returns | **who cleans the stack** on return |
| `RET n` also adds *n* to SP | **which registers the procedure must preserve** |
| `PUSH` subtracts and writes; `POP` reads and adds | **where the returned value ends up** |

The ISA imposes no convention. What imposes it is the agreement between the caller and the callee; when one of the
two is the operating system or a compiler, that agreement is an ABI and it is not negotiable.

#### The stack, step by step

The convention chosen for this example (common practice in a DOS course): **parameters on the stack, left to right;
the procedure cleans up with `RET n`; the result in AX; BP and the registers the procedure uses are preserved**.

```asm
    push  first              ; SP -> [first]
    push  second             ; SP -> [second][first]
    call  add_two            ; SP -> [ret][second][first]
```

Inside the procedure:

```asm
add_two PROC
    push  bp                 ; SP -> [bp][ret][second][first]
    mov   bp, sp             ; BP fixes the frame: from here on everything is measured from BP
    ; [bp+0] = saved BP
    ; [bp+2] = return address
    ; [bp+4] = second   (the last one pushed is the closest)
    ; [bp+6] = first
    push  bx                 ; this procedure uses BX, so it preserves it

    mov   ax, [bp+6]
    mov   bx, [bp+4]
    add   ax, bx             ; result in AX, per the convention

    pop   bx
    pop   bp
    ret   4                  ; returns and removes the 4 bytes of the two parameters
add_two ENDP
```

Three things to read in that code:

1. **`mov bp, sp` is what makes the frame useful.** SP moves with every `PUSH`; BP does not. Measuring the
   parameters from BP lets you push more without recalculating anything.
2. **The offsets are fixed and computable:** on 16 bits the return address takes 2 bytes and the saved BP another
   2, so the first parameter sits at `[bp+6]`. With a `far` call the return address takes 4 bytes and everything
   shifts.
3. **`ret 4` is the convention, not the instruction.** A plain `ret` would also return; the `4` is the agreement
   that the procedure cleans up. If the caller cleaned up as well, the stack would be unbalanced and the program
   would fail later, somewhere that has nothing to do with it.

Observable state in the built-in debugger, with `first = 7` and `second = 5`: right after `mov bp, sp`, `SP = BP`,
the word at `[BP+2]` is the address of the instruction after the `CALL`, `[BP+4]` is 5 and `[BP+6]` is 7. On
return, `AX = 12` and SP is back to the value it had before the two `PUSH`es.

**Recursion.** It works with nothing special, because each call creates its own frame: the saved BP forms a chain
that lets you walk the frames backwards, and that is what a debugger uses to show the call stack.

**`ENTER` and `LEAVE`.** From the 80186, `enter n, 0` does `push bp` + `mov bp, sp` + reserves *n* bytes of
locals, and `leave` undoes the first two. They are a convenience, not magic; and `ENTER` is **not** implemented in
IDEARM's built-in emulator (opcode `C8h`, annex A §A.8).

#### The same procedure with the Microsoft x64 ABI

In `win-pe64-console` you no longer choose the agreement. The Microsoft x64 ABI says:

| Rule | Contents |
|---|---|
| Parameters | the first four integers in `RCX`, `RDX`, `R8`, `R9`; floating point ones in `XMM0`–`XMM3`; the fifth and following on the stack |
| Shadow space | the caller reserves **32 bytes** on the stack before the call, **even if the function does not have four parameters** |
| Alignment | `RSP` must be aligned to **16 bytes** in all code that is not prologue or epilogue |
| Return | in `RAX` if it fits in 64 bits; in `XMM0` if it is floating point |
| Volatile | `RAX`, `RCX`, `RDX`, `R8`–`R11`, `XMM0`–`XMM5`: the callee may destroy them |
| Non-volatile | `RBX`, `RBP`, `RDI`, `RSI`, `RSP`, `R12`–`R15`, `XMM6`–`XMM15`: they must be saved and restored |

**[source: Microsoft Learn, "x64 calling convention", revision 2026-05-21]**

The same `add_two`, now in the NASM dialect:

```asm
; add_two(int a, int b) -> int   per the Microsoft x64 ABI
add_two:
    mov   eax, ecx            ; first parameter
    add   eax, edx            ; second parameter
    ret                       ; result in EAX (low half of RAX)
```

And the caller:

```asm
    sub   rsp, 40             ; 32 of shadow space + 8 to leave RSP aligned to 16
    mov   ecx, 7
    mov   edx, 5
    call  add_two             ; EAX = 12
    add   rsp, 40
```

Five differences the student must be able to name:

1. The parameters go in registers, not on the stack.
2. **The caller reserves 32 bytes that nobody may use.** It is not arbitrary waste: it is where the callee can spill
   its register parameters if it needs their address.
3. The `sub rsp, 40` is not 32: it is 32 plus 8. The `call` pushed 8 bytes of return address and broke the 16-byte
   alignment; the extra 8 restore it. The template IDEARM generates for this profile does exactly that.
4. **There is no `RET n`**: the caller cleans up.
5. `RCX` and `RDX` are volatile, so if you need them after the call, save them before.

The four conventions are **not compared here** all at once: only those of the relevant profiles, each in its own
sheet, because mixing them is exactly what makes a student write a Windows prologue in a DOS program. 32-bit
`cdecl` and `stdcall` have a sheet of their own with name decoration (`_WriteFile@20`), and System V AMD64 has one
with the difference between the **function ABI** (`RDI`, `RSI`, `RDX`, `RCX`, `R8`, `R9`) and the **syscall ABI**
(`RDI`, `RSI`, `RDX`, `R10`, `R8`, `R9`, with the number in `RAX`), which are not the same contract even though
they look alike.

#### Common mistakes

1. Forgetting `pop bp` before `ret`: it returns to the wrong address.
2. Miscounting the frame offsets and reading the return address as a parameter.
3. Both sides cleaning the stack, or neither.
4. Modifying a non-volatile register without restoring it, in a program that calls the system API.
5. Reserving the shadow space and forgetting the 16-byte alignment on x64: the failure usually appears inside a
   system function, far from the cause.

**Related.** `CALL`, `RET`, `PUSH`, `POP`, `ENTER`, `LEAVE` · registers `SP`, `BP`, `SS` · the stack · calling
conventions per profile · libraries and the system API (where it is explained why Irvine32 does **not** come with
IDEARM).

**Sources.** Irvine 5th ed. (es), ch. 5 "Procedures" (printed 111–149) and ch. 8 "Advanced procedures" (printed
224–268), which in this edition was completely redesigned to explain the low-level details of stack frames
(activation records) first, according to its own preface · Microsoft Learn, "x64 calling convention", revision
2026-05-21 · For System V AMD64, the maintained psABI, with the version to be pinned (AA-P0-04) · IDEARM's 64-bit
template (`CreateProject.generateStarterAssembly`) as a real example of the `sub rsp, 40`.

> **Verification state of the examples:** `NOT_RUN`.
