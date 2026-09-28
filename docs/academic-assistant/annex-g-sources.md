# Annex G — Source catalog, page mappings and editorial workflow

> Part of [`expansion-plan.md`](expansion-plan.md). Covers phase 1 §6 and deliverable 8.
>
> This annex separates three things that must not be confused: **what was really consulted in this session**,
> **what remains pending** and **what could not be consulted**. No claim of the plan rests on a source of the
> second or third category.

---

## G.1 Lookup tools available in this session

| Tool | State | Consequence |
|---|---|---|
| `pdftotext` 4.00 (mingw64) | **available** | extracts text from PDFs that have a text layer, with page ranges and `-layout` |
| `pdftoppm` / page renderer | **missing** | a page cannot be seen as an image; the visual reading the assignment recommends for doubtful extractions was not possible |
| Ghostscript, `mutool`, ImageMagick | **missing** | same |
| `tesseract` or another OCR | **missing** | **no OCR**: a scanned PDF is unreadable in this session |
| Python PDF libraries (`pypdf`, `PyMuPDF`, `pdfminer`) | **missing**, and `pip` is not available in the system Python | nothing was installed: changing the machine's environment is outside the assignment |
| Web access (searching and downloading pages) | available | it allowed pinning versions and consulting official documentation |

The temporary files of this lookup (extractions, compiled classes, inventory output) stayed in the session's
temporary folder, outside the repository.

---

## G.2 Local sources: what was read and with what limit

The books are the course's own copies, kept in a local folder outside the repository; they are never
redistributed. The titles below are the Spanish editions the course uses.

### G.2.1 Kip R. Irvine — *Lenguaje ensamblador para computadoras basadas en Intel*, 5th ed. (Spanish translation of *Assembly Language for Intel-Based Computers*)

756 PDF pages · **extractable text** · **body offset: PDF page = printed page + 32** (verified at two independent
points: PDF 126 → printed 94, and PDF 236 → printed 204). The index has a different offset, because the Roman
numbering at the start does not follow the same count.

| Consulted in this session | PDF | Printed | What it brings to the plan |
|---|---|---|---|
| Full table of contents | 9–24 | vii–xx | structure of the 17 chapters and the 4 appendices |
| §4.3 "Data-related operators and directives" | 126–130 | 94–98 | `OFFSET`, `ALIGN`, `PTR`, `TYPE`, `LENGTHOF`, `SIZEOF`, `LABEL`; the statement that `LENGTH` and `SIZE` are the legacy forms; that operators "are not executable instructions" |
| §7.4 "Multiplication and division instructions" | 236–242 | 204–210 | the three families of `IMUL` forms; that the 8086/8088 processors only support the one-operand format; the multiplicand/product tables of `MUL`; dividend/quotient/remainder of `DIV`; `CBW`/`CWD`/`CDQ` as preparation for `IDIV` |
| §13.2 "MS-DOS function calls (INT 21h)" | 471–477 | 439–445 | the contracts of functions 02h, 05h, 06h, 09h, 0Ah, 0Bh, 3Fh, 40h; the three-field structure of 0Ah and the "including the Enter key"; "Returns: nothing" for 09h |
| §2.2.2 "Basic execution environment" | 66–67 | 34–35 | "eight general-purpose registers, six segment registers"; the EAX/AX/AH/AL overlap figure and the statement that the same overlap exists for EAX, EBX, ECX and EDX |
| Preface | 24 | xx–xxii | that the 5th ed. added the two- and three-operand IMUL instructions; that ch. 8 was redesigned to explain stack frames first; that Irvine32.lib and Irvine16.lib are **libraries the book supplies**, not part of the ISA |

**Structure of the appendices** (relevant to the editorial workflow): A "MASM reference" (printed 600), B "The IA-32
instruction set" (619), C "BIOS and MS-DOS interrupts" (650), D "Answers to review questions" (659).

**An erratum detected in the source itself.** The body of §4.3 refers to "appendix D" for the full list of MASM
operators, but appendix D of this edition holds the answers to the review questions; the list is in appendix A. It
is an **internal discrepancy of the edition or the translation**, not a reading error. It is recorded as an example
of the treatment the assignment requires: when a source contradicts itself, the contradiction is cited and resolved
against the index, not silently.

**Limit of the extraction.** The book's code listings come out **scrambled** by the reconstruction of two-column
text: for example, a loop appears as `L1: ah,6 / mov dl,0FFh / mov 21h`. Therefore **no example in this plan was
copied from the book**: all are original, written from the contracts and rules the book explains in prose, which do
extract correctly. This matches the assignment's instruction to use our own explanations and examples.

### G.2.2 William Stallings — *Organización y arquitectura de computadores*, 7th ed. (Spanish translation of *Computer Organization and Architecture*)

838 pages · **extractable text** · **offset: PDF = printed + 22** (verified: PDF 437 → printed 415).

| Consulted | PDF | Printed | Brings |
|---|---|---|---|
| Contents | 7–22 | — | structure of the 18 chapters; the two-column TOC extracts with misalignments, so the chapter numbers were checked one by one |
| §11.2 "Pentium and PowerPC addressing modes" | 437–439 | 415–417 | virtual/effective address → linear → physical; "There are six segment registers"; descriptors not visible to the programmer; base register, index register, scale 1/2/4/8, displacement of 0/8/32 bits (figure 11.2); that Table 11.2 lists the twelve Pentium addressing modes; stack addressing as implicit addressing |
| Chapter index 9–11 and 15 | — | 301, 347, 407, 563 | computer arithmetic; instruction sets (characteristics and functions); addressing modes and formats; **ch. 15 "The IA-64 architecture"** |

**Use and limit.** Stallings supports the fundamentals of architecture, memory, I/O, arithmetic and processor
organization. His PowerPC and IA-64 examples are **not** within IDEARM's scope, and ch. 15 is precisely the one the
assignment warns not to confuse with x86-64: IA-64 (Itanium) is a different architecture, not the 64-bit extension
of x86.

### G.2.3 M. Morris Mano — *Diseño digital*, 3rd ed. (Spanish translation of *Digital Design*)

535 pages · **extractable text** · the offset **varies by section** (PDF 229 → printed 218, that is +11 in that
area; at the start of the book the difference is smaller), so each citation is verified individually.

| Consulted | PDF | Printed | Brings |
|---|---|---|---|
| Ch. 6 "Registers and counters", figure 6-1 "Four-bit register" and §6-2 | 229–230 | 218–219 | a register built from D flip-flops, with a clock and a clear input; the master clock generator |
| Ch. 1 "Binary systems" | 8 onwards | 2 onwards | binary numbers, powers of two, base conversion |

**Use and explicit limit.** Mano is a **conceptual foundation, not a specification of the x86 instruction set**. A
four-bit register made of flip-flops explains how a register can exist; it is **not** the architectural description
of AX, which is an x86 specification (16 bits, a name, views, imposed uses) implemented in very different ways in
each generation. Sample [E.2](annex-e-content-samples.md#e2-a-register-with-subregisters-ax) includes that warning as
content for the student.

### G.2.4 M. Morris Mano — copy with the excluded topics highlighted

Same book, 535 pages, same text extraction (byte-identical on the pages checked). **The highlights cannot be read**
without a renderer: they are graphic annotations in the PDF.

From the assignment, the context fact is kept that the highlighting includes a mark on signed binary numbers. **It
is not read as an order to exclude that topic from the product**: it is information about the course syllabus, and
the user's goal is to extend coverage. Reading the annotations is assigned to AA-P0-02.

### G.2.5 Peter Abel — *Lenguaje ensamblador y programación para IBM PC y compatibles*, 3rd ed. (Spanish translation of *IBM PC Assembly Language and Programming*)

283 pages · **a scanned PDF without a text layer**. A concrete measurement: extracting 20 pages produces **20 bytes**
of text. Without a renderer and without OCR, **the book is unreadable in this session**.

**The consequence, stated plainly:** no technical claim of this plan cites Abel. His planned contribution (the
historical environment of the PC, real-mode segmentation, DOS memory models, keyboard and screen services, the A20
gate) is **assigned to task AA-P0-02**, not taken for granted. If OCR of an old scan turns out poor, it is recorded
as a permanent limit and the historical content leans on Irvine and Stallings.

### G.2.6 Books present in the folder but not listed in the assignment

The folder also holds `COMPUTER_ORGANIZATION_AND_DESIGN_THE_HAR.pdf` (Patterson and Hennessy) and
`Compuertas Lógicas.pdf` (logic gates). **They were not consulted**, because the assignment delimits five sources.
They are recorded here because they are available: Patterson and Hennessy could cover the instruction cycle and the
datapath from another perspective, but its reference ISA is MIPS or RISC-V, **explicitly outside IDEARM's scope**,
so its use would have to be limited to fundamentals and carry the corresponding warning.

---

## G.3 Web sources: consulted and pending

| Source | URL | Consulted | Revision / date | What was obtained |
|---|---|---|---|---|
| Intel SDM (official page) | `intel.com/content/www/us/en/developer/articles/technical/intel-sdm.html` | **yes** | document 767375, **revision 093**, page updated 2026-09-21; consulted 2026-09-25 | the reference revision and the list of volumes (1; 2A–2D; 3A–3D; 4) |
| Intel SDM, volume 2 (PDF) | same | **no** | — | **pending**: AA-P0-03. The plan's flag claims rest on the rendering of the next row |
| Unofficial HTML rendering of the SDM | `felixcloutier.com/x86/{bt, sal:sar:shl:shr, iret:iretd:iretq, imul}` | **yes** | consulted 2026-09-25 | the "Flags Affected" sections of `BT`, the shifts and `IRET`; the table of 13 `IMUL` encodings with their validity in 64-bit mode. The page itself declares itself an "UNOFFICIAL, mechanically-separated, non-verified reference", so **every fact taken from it is marked for confirmation against the official PDF** |
| NASM manual | `nasm.us/doc/nasmdoc2.html`, `nasmdoc3.html` | **yes** | **NASM 3.02** manual; consulted 2026-09-25 | the bracket rule: "any access to the *contents* of a memory location requires square brackets around the address, and any access to the *address* of a variable doesn't"; that NASM needs no `OFFSET`; effective address algebra and `NOSPLIT` |
| GNU as, i386 variations | `sourceware.org/binutils/docs/as/i386_002dVariations.html` | **yes** (through search) | consulted 2026-09-25 | that `movabs` is a **GNU as** mnemonic for encoding `mov` with a 64-bit immediate or displacement, and that it is neither an Intel nor a NASM mnemonic |
| Microsoft x64 ABI | `learn.microsoft.com/en-us/cpp/build/x64-calling-convention` | **yes** | `ms.date` 2025-03-19, updated 2026-05-21; consulted 2026-09-25 | parameter registers, a 32 B shadow space reserved by the caller, 16 B alignment, return in RAX/XMM0, full lists of volatile and non-volatile registers, FPCSR and MXCSR |
| System V AMD64 psABI | `gitlab.com/x86-psABIs/x86-64-ABI` | **located, not read** | — | **pending**: AA-P0-04 (exact version, function and syscall register table, *red zone*) |
| Linux kernel documentation | `docs.kernel.org` | **no** | — | pending, together with AA-P0-04 |
| AMD manuals | — | **no** | — | pending, only for vendor differences (AA-P0-03) |
| MASM 6.11 and TASM 3.2/4.1 manuals | — | **no** | — | **pending**: AA-P0-01. A firm rule: **modern MASM documentation is not extrapolated to the historical version the IDE invokes** |
| IBM BIOS and MS-DOS references; specialized interrupt lists | — | **no** | — | **pending**: AA-P0-05, with a license check before deriving any inventory |

---

## G.4 Editorial workflow

### Cycle per corpus entry

```
1. INVENTORY       the entry is born from a list (SDM index, the assembler's mnemonics,
                   vector index), never from the writer's memory
2. LOOKUP          the primary source is located and the work, edition, section and page
                   (or URL and revision) are noted. Without a source the entry is not written
3. WRITING         original text in English. Nothing is copied: it is explained
4. REVIEW          a second independent source confirms every semantic claim.
                   If the two sources disagree, the discrepancy and its origin are explained
                   (CPU, mode, dialect, OS version, translation or erratum)
5. TRANSLATION     into Spanish. If it is missing, the entry is published with the "not translated" mark
6. EXAMPLES        written by us; verified with the real toolchain if there is one;
                   if not, they stay marked NOT_RUN
7. VERSIONING      the entry stores corpusVersion and sourceRevision
8. COVERAGE        the report is regenerated and publishes the state per family
```

### How discrepancies between sources are handled

The assignment asks that they never be silently mixed. Real examples this plan already treats this way:

| Discrepancy | Origin | Treatment |
|---|---|---|
| Irvine says `INT 21h`/09h returns nothing; IDEARM's emulator and the specialized lists leave `AL = 24h` | an **omission** in a teaching source versus a reference source | the sheet shows both and gives the practical rule: do not depend on `AL` |
| The body of Irvine §4.3 points to "appendix D"; the operators appendix is A | **erratum or translation** | the correct appendix is cited and the erratum recorded |
| `movabs` is a valid instruction in GNU as and does not exist in Intel, NASM, MASM or TASM | **dialect** | it is removed as an instruction and documented as another assembler's mnemonic |
| Three NASM versions in play (2.16.01, 3.01, 3.02) | **version** | the requirement is expressed per version; the dialect+version axis is mandatory in the corpus |

### Intellectual property rules the plan adopts

1. **The PDFs are not redistributed** and no chapters are copied into the product.
2. **No complete table is copied** from a source: our own are written from the semantics, citing where they were
   checked.
3. **Always cite**: work, edition, section and printed page (plus the PDF page when it can be verified), or URL and
   revision.
4. **Before importing any external catalog** (an interrupt list, an opcode table) its license is checked
   (AA-P0-05), and if it does not allow it, the inventory is built from the official sources with whatever coverage
   results, declared.
5. The literal quotations of the plan and the corpus are short, in quotation marks and attributed.

### Schema of a reference in the corpus

```json
{
  "id": "src.irvine.5e-es",
  "kind": "BOOK",
  "author": "Kip R. Irvine",
  "title": "Lenguaje ensamblador para computadoras basadas en Intel",
  "edition": "5th edition, Spanish",
  "notes": "the body offset is PDF page = printed page + 32"
}
```

and the citation in an entry: `"sources": ["src.irvine.5e-es#7.4.2:205"]`, that is section and **printed page**;
the PDF page is kept in the source's record, not in each citation, because it depends on the file and not on the
work.

---

## G.5 An honest summary of the state of the sources

| | How many | Which |
|---|---|---|
| **Consulted and verified in this session** | 7 | Irvine (6 sections), Stallings (2 sections), Mano (2 sections), the Intel SDM page, the NASM 3.02 manual, the Microsoft x64 ABI, the GNU as documentation |
| **Consulted with a declared reservation** | 1 | the unofficial HTML rendering of the SDM: useful and well known, but **not authoritative**; every fact is marked for confirmation |
| **Located, not read** | 2 | System V AMD64 psABI, Linux kernel documentation |
| **To be located** | 4 | the SDM 093 PDF, AMD manuals, MASM 6.11 and TASM manuals, BIOS/DOS references and interrupt lists |
| **Unreadable in this session** | 2 | Abel (scanned, no OCR) and the highlights of the annotated Mano |
| **Available but outside the assignment** | 2 | Patterson and Hennessy, "Compuertas Lógicas" |

No claim marked **[source]** in the plan comes from the last three rows. Those that depend on the second row are
also marked with the task that confirms them.
