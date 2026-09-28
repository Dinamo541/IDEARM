# Annex A — Reproducible audit and inventory of the catalog

> Part of [`expansion-plan.md`](expansion-plan.md). Covers phase 1 §1–§3 of the assignment.
> Everything marked **[code]** was checked in this session against the working tree with its uncommitted
> changes. Nothing was modified.

---

## A.1 Method: how the counts were obtained

The numbers were not estimated by reading the 1823-line file. The procedure was:

```bash
# 1. Compile the catalog package as it stands in the working tree, into a temporary folder.
javac -d <temp>/cat-classes \
      idearm-language/src/main/java/io/github/dinamo541/idearm/language/catalog/*.java

# 2. Compile and run the inventory program against those classes.
javac -cp <temp>/cat-classes -d <temp> docs/academic-assistant/tools/Inventory.java
java  -cp "<temp>/cat-classes;<temp>" Inventory > inventory.txt
```

The `catalog` package depends only on the JDK, so it compiles on its own without the other modules. The program
is [`tools/Inventory.java`](tools/Inventory.java) and it queries only the public API
(`getAll`, `knownMnemonics`, `find`, `getForCpu`, `filter`, `suggest`), so it measures what the real consumers
measure.

A second tool, `FalsePositives.java` (reproduced in §A.4), checks which real x86 mnemonics the catalog does not
know and what `suggest()` proposes for each one.

A methodological warning the assignment asks us to respect: **these counts measure entries and fields, not
understanding.** §A.5 explains why 214 entries are not 214 well-documented instructions.

---

## A.2 Inventory: full output

```
## Totals
primary_entries=214
known_mnemonics=266
variant_aliases=52

## By category
DATA_TRANSFER=33      ARITHMETIC=24      LOGIC=14          CONTROL_FLOW=50
STRINGS=21            FLAGS_CONTROL=13   STACK_PROCEDURES=14
SYSTEM_INTERRUPTS=12  IO_PORTS=4         BIT_MANIPULATION=6  FLOATING_POINT=23

## By minCpu
CPU_8086=127   CPU_80186=7   CPU_80286=3   CPU_80386=45
CPU_80486=3    CPU_PENTIUM=21   CPU_X86_64=8

## Syntax forms
total_syntax_variant_strings=485
entries_with_zero_variants=0
entries_with_one_variant=68
max_variants=6

## Flag tables            (order O D I T S Z A P C)
distinct_flag_tables=20
  [- - - - - - - - -]=158     [M - - - M M M M M]=24     [0 - - - M M U M 0]=4
  [M - - - - - - - M]=4       [M M M M M M M M M]=3      [M - - - M M M M -]=2
  [M - - - U U U U M]=2       [U - - - M M M M M]=2      [U - - - M M U M U]=2
  [U - - - U U M U M]=2       [U - - - U U U U U]=2      [- - - - - - - - 0]=1
  [- - - - - - - - 1]=1       [- - - - - - - - M]=1      [- - - - M M M M M]=1
  [- - 0 - - - - - -]=1       [- - 0 0 - - - - -]=1      [- - 1 - - - - - -]=1
  [- 0 - - - - - - -]=1       [- 1 - - - - - - -]=1
entries_with_no_flag_effect=158

## Examples
entries_with_empty_example=0
entries_with_single_line_example=108
entries_with_empty_descriptionEn=0
entries_with_empty_descriptionEs=0

## Alias mnemonics (known minus primary) — 52
CMOVE CMOVNA CMOVNAE CMOVNB CMOVNBE CMOVNE CMOVNG CMOVNGE CMOVNL CMOVNLE CMOVPE CMOVPO FNSTSW FWAIT
INSB INSD INSTRUCTION INSW IRETD IRETQ JNA JNAE JNB JNBE JNG JNGE JNL JNLE JPE JPO LOOPNZ LOOPZ
OUTSB OUTSD OUTSW REPE REPNE REPNZ REPZ RETN SAL SETNA SETNAE SETNB SETNBE SETNG SETNGE SETNL SETNLE
SETPE SETPO XLATB
```

### Alias resolution

`INSTRUCTION → LOCK` is an alias invented by the generator (see [A-01](#a-01)). The others are legitimate:

```
CMOVE→CMOVZ    CMOVNA→CMOVBE   CMOVNAE→CMOVB   CMOVNB→CMOVAE   CMOVNBE→CMOVA   CMOVNE→CMOVNZ
CMOVNG→CMOVLE  CMOVNGE→CMOVL   CMOVNL→CMOVGE   CMOVNLE→CMOVG   CMOVPE→CMOVP    CMOVPO→CMOVNP
FNSTSW→FSTSW   FWAIT→WAIT      INSB/INSD/INSW→INS   IRETD→IRET   IRETQ→IRET
JNA→JBE  JNAE→JB  JNB→JAE  JNBE→JA  JNG→JLE  JNGE→JL  JNL→JGE  JNLE→JG  JPE→JP  JPO→JNP
LOOPNZ→LOOPNE  LOOPZ→LOOPE   OUTSB/OUTSD/OUTSW→OUTS   REPE/REPNE/REPNZ/REPZ→REP
RETN→RET  SAL→SHL  SETNA→SETBE  SETNAE→SETB  SETNB→SETAE  SETNBE→SETA  SETNG→SETLE
SETNGE→SETL  SETNL→SETGE  SETNLE→SETG  SETPE→SETP  SETPO→SETNP  XLATB→XLAT
INSTRUCTION→LOCK        ← defect
```

### The 214 primary mnemonics

```
AAA AAD AAM AAS ADC ADD AND ARPL BOUND BSF BSR BSWAP BT BTC BTR BTS CALL CBW CDQ CDQE CLC CLD CLI CMC
CMOVA CMOVAE CMOVB CMOVBE CMOVC CMOVG CMOVGE CMOVL CMOVLE CMOVNC CMOVNO CMOVNP CMOVNS CMOVNZ CMOVO
CMOVP CMOVS CMOVZ CMP CMPS CMPSB CMPSD CMPSW CMPXCHG CPUID CQO CWD CWDE DAA DAS DEC DIV ENTER FABS
FADD FCHS FCOM FCOMP FDIV FILD FINIT FIST FISTP FLD FLD1 FLDPI FLDZ FMUL FNINIT FSQRT FST FSTP FSTSW
FSUB FTST FXCH HLT IDIV IMUL IN INC INS INT INTO IRET JA JAE JB JBE JC JCXZ JE JECXZ JG JGE JL JLE
JMP JNC JNE JNO JNP JNS JNZ JO JP JRCXZ JS JZ LAHF LAR LDS LEA LEAVE LES LFS LGS LOCK LODS LODSB
LODSD LODSW LOOP LOOPE LOOPNE LSL LSS MOV MOVABS MOVS MOVSB MOVSD MOVSW MOVSX MOVZX MUL NEG NOP NOT
OR OUT OUTS POP POPA POPAD POPF POPFD POPFQ PUSH PUSHA PUSHAD PUSHF PUSHFD PUSHFQ RCL RCR RDTSC REP
RET RETF ROL ROR SAHF SAR SBB SCAS SCASB SCASD SCASW SETA SETAE SETB SETBE SETC SETE SETG SETGE SETL
SETLE SETNC SETNE SETNO SETNP SETNS SETNZ SETO SETP SETS SETZ SHL SHLD SHR SHRD STC STD STI STOS
STOSB STOSD STOSW SUB SYSCALL SYSRET TEST UD2 WAIT XADD XCHG XLAT XOR
```

The eight x86-64 entries are: `CDQE`, `CQO`, `JRCXZ`, `MOVABS`, `POPFQ`, `PUSHFQ`, `SYSCALL`, `SYSRET`.

---

## A.3 Measured behavior of the compatibility filter

```
target=8086     parseLevel=0  getForCpu=127
target=80286    parseLevel=2  getForCpu=137
target=80386    parseLevel=3  getForCpu=182
target=i386     parseLevel=3  getForCpu=182
target=80486    parseLevel=4  getForCpu=185
target=pentium  parseLevel=5  getForCpu=206
target=x86-64   parseLevel=6  getForCpu=214      ← includes AAA, DAA, PUSHA, INTO, BOUND, LDS, LES, ARPL
target=x64      parseLevel=6  getForCpu=214
target=amd64    parseLevel=6  getForCpu=214
target=x86      parseLevel=0  getForCpu=127      ← downgraded to 8086
target=32       parseLevel=0  getForCpu=127      ← downgraded to 8086
target=64       parseLevel=6  getForCpu=214
target=unknown  parseLevel=0  getForCpu=127      ← downgraded to 8086
getForCpu(null)=214
```

The field that feeds this is `cpu` in `[target]` of `idearm.toml`, a free-form `String`
(`TargetSelection(String profile, String cpu)`); `CreateProject` sets it to `"8086"` when it is empty, and the
catalog profiles use `"8086"`, `"80386"` and `"x86-64"`, which are recognized. A plausible value typed by hand
(`"x86"`, `"386"`, `"i686"`, `"32"`) downgrades the filter to 8086 **[code]**.

### Search: what it finds today

The queries are the Spanish words a student would type; the English gloss is in parentheses.

```
query="dos puntos"  (colon)          hits=0
query="corchetes"   (brackets)       hits=0
query="segmento extra" (extra segment) hits=0
query="interrupcion 21h"             hits=0
query="@data"                        hits=0     ← even though the MOV example contains "mov ax, @data"
query=":"                            hits=46    ← accidental matches inside other strings
query="["                            hits=38
query="acarreo"     (carry)          hits=20    query="carry"  hits=19
query="stack"                        hits=34    query="pila" (stack)  hits=37
query="int 21h"                      hits=2     (INT, SYSCALL)
query="JNBE"                         hits=1     (JA)
query="sal"                          hits=39    ← substring of "salto", "señal"…, not the SAL alias
query="offset"                       hits=4     query="ptr"  hits=13
```

`InstructionInfo.matches` searches the mnemonic, both summaries, both descriptions and the syntax variants; it
does **not** search the example, and there is no concept, symbol or service entity that could answer "colon" or
"extra segment" **[code]**. Acceptance case 9 of the assignment fails completely today.

### `suggest()` applied to words that are not mnemonics

```
MVO → [MOV]        MUV → [MOV, MUL]      INTT → [INT, INTO]     PUSHH → [PUSH, PUSHA, PUSHF]
DATA → [DAA]       END → [AND]           DB → [JB]              LEAA → [LEA]
SEGMENT → []       PROC → []             OFFSET → []            EQU → []      AX → []
```

The first three items of the second line are the problem: `DATA`, `END` and `DB` get instruction suggestions. In
practice the analyzer does not flag them because the lexer classifies them as directives before they reach the
rule, but the suggestion function does not distinguish the symbol class, and any three- or four-letter user
identifier (`sum`, `dir`, `tot`) can get an instruction suggestion when it does reach the rule **[code]**.

---

## A.4 Real mnemonics the catalog does not know

Program [`tools/FalsePositives.java`](tools/FalsePositives.java) (same compilation procedure; a list of 122 real
x86 and NASM mnemonics and directives). Result: **110 unknown out of 122**. The ones that matter, grouped:

| Family | Unknown examples | Produces a false error? |
|---|---|---|
| SSE / SSE2 scalar and packed | `MOVAPS` `MOVUPS` `ADDPS` `MULPS` `DIVPS` `SQRTPS` `XORPS` `ANDPS` `MOVSS` `ADDSD` `MULSD` `COMISD` `UCOMISD` `CVTSI2SD` `CVTSD2SI` `MOVDQA` `MOVDQU` `LDMXCSR` `STMXCSR` | **Yes** |
| MMX | `PADDB` `PADDW` `PADDD` `PXOR` `PCMPEQB` `PSHUFB` `EMMS` `MOVD` `MOVQ` | **Yes** |
| AVX | `VADDPS` `VMOVAPS` `VZEROUPPER` `VPXOR` | **Yes** |
| System and tables | `LGDT` `LIDT` `LMSW` `SMSW` `SLDT` `STR` `VERR` `VERW` `CLTS` `INVLPG` `RDMSR` `WRMSR` `RSM` `SWAPGS` | **Yes** |
| Fast calls | `SYSENTER` `SYSEXIT` `RDTSCP` | **Yes** |
| 64-bit strings | `MOVSQ` `STOSQ` `LODSQ` `SCASQ` `CMPSQ` | **Yes** |
| Bit manipulation | `POPCNT` `LZCNT` `TZCNT` `ANDN` `BEXTR` | **Yes** |
| Fences and synchronization | `SFENCE` `LFENCE` `MFENCE` `PAUSE` `CLFLUSH` `MONITOR` `MWAIT` `PREFETCHT0` | **Yes** |
| Wide atomics | `CMPXCHG8B` `CMPXCHG16B` | **Yes** |
| Randomness and cryptography | `RDRAND` `RDSEED` `AESENC` `SHA1RNDS4` | **Yes** |
| Missing x87 | `FSIN` `FCOS` `FPTAN` `FPATAN` `F2XM1` `FYL2X` `FSCALE` `FRNDINT` `FPREM` `FSTCW` `FLDCW` `FSAVE` `FRSTOR` `FCOMI` `FCMOVB` `FUCOM` `FUCOMI` `FLDL2E` `FLDLG2` `FLDLN2` `FLDL2T` | **Yes** |
| Undocumented | `SALC` `ICEBP` `INT1` | Yes (they must be documented **as undocumented**, not omitted) |
| NASM directives | `TIMES` `RESB` `SECTION` `GLOBAL` `EXTERN` `DEFAULT` `STRUC` `ENDSTRUC` | No: the lexer treats them as directives before they reach the rule (except `STRUC`/`ENDSTRUC`, which are also missing from the directive set) |

Actively misleading suggestions a student would see today:

```
PAUSE   → [PUSH]                FSAVE   → [LEAVE]           SALC   → [SAL]
MWAIT   → [FWAIT, WAIT]         EXTERN  → [ENTER]           STRUC  → [STC]
MOVSQ   → [MOVS, MOVSB, MOVSD]  FSTCW   → [FSTSW, FNSTSW]   BEXTR  → [BTR]
ANDN    → [AND]                 RDMSR   → [RDTSC]           FCOS   → [FCHS, FCOM]
```

---

## A.5 Why 214 entries are not 214 documented instructions

Three measurements over the same representative sample the assignment asks for:

**1. Instructions with several forms share a single semantics.** `IMUL` declares three strings
(`"IMUL reg"`, `"IMUL mem"`, `"IMUL reg, reg, imm"`), one `minCpu` (8086) and one flag table. The source
distinguishes **ten** real forms grouped into three families, and says the two- and three-operand ones do not
exist on the 8086/8088 **[source: Irvine 5th ed. (Spanish), §7.4.2, printed 205–206 / PDF 237–238]**. Besides,
the three-operand string the catalog writes matches no form: the real ones are `IMUL r16, r/m16, imm8|imm16` and
`IMUL r32, r/m32, imm8|imm32`.

**2. Implicit operands are not modeled.** `MUL r/m8` reads AL and writes AX; `MUL r/m16` reads AX and writes
DX:AX; `DIV r/m16` reads DX:AX and writes AX and DX **[source: Irvine §7.4.1 and §7.4.4, printed 204 and 208]**.
None of that is in the model: the student reads the prose or does not know it. The same goes for strings
(`DS:[SI]`, `ES:[DI]`, CX, DF) and for `XLAT` (`DS:[BX+AL]`).

**3. Conditional effects are declared as absolute.** `SHL`/`SHR`/`SAR` declare the nine flags with a fixed table
that marks AF as *modified*. The source says three things the table cannot express: "if the count is 0, the flags
are not affected"; "the OF flag is affected only for 1-bit shifts; otherwise it is undefined"; "for a non-zero
count, the AF flag is undefined" **[source: HTML rendering of the SDM, `sal:sar:shl:shr`, consulted 2026-09-25;
to be confirmed against volume 2 of SDM 093 → task AA-P0-03]**.

Full audit sample, with the table each entry declares (order O D I T S Z A P C):

| Instruction | Declared | Correct? | Note |
|---|---|---|---|
| `MOV`, `XCHG`, `LEA`, `PUSH`, `POP`, `CALL`, `RET`, `NOT`, `CBW`, `CWD` | `- - - - - - - - -` | Yes | correct |
| `ADD`, `ADC`, `SUB`, `SBB`, `CMP`, `NEG`, `CMPS`, `SCAS`, `XADD`, `CMPXCHG` | `M - - - M M M M M` | Yes | correct |
| `INC`, `DEC` | `M - - - M M M M -` | Yes | CF preserved: correct and well handled |
| `TEST` | `0 - - - M M U M 0` | Yes | AF undefined: good |
| `MUL`, `IMUL` | `M - - - U U U U M` | Yes for the one-operand form | the per-form requirement is missing |
| `DIV`, `IDIV` | `U - - - U U U U U` | Yes | but the divide exception is not modeled |
| `AAA`, `AAS` | `U - - - U U M U M` | Yes | correct |
| `AAD`, `AAM` | `U - - - M M U M U` | Yes | correct |
| `DAA`, `DAS` | `U - - - M M M M M` | Yes | correct |
| `POPF` | `M M M M M M M M M` | Yes | correct |
| `SAHF` | `- - - - M M M M M` | Yes | correct |
| `STI`/`CLI`/`STD`/`CLD`/`STC`/`CLC`/`CMC` | a single bit | Yes | correct |
| `SHL`, `SHR`, `SAR`, `SHLD`, `SHRD`, `BSF`, `BSR` | `M - - - M M M M M` | **No** | AF must be `UNDEFINED`; OF conditional on a count of 1; no effect when the count is 0 → [A-11](#a-11) |
| `ROL`, `ROR`, `RCL`, `RCR` | `M - - - - - - - M` | **Partial** | OF defined only for 1-bit rotations → [A-12](#a-12) |
| `BT`, `BTS`, `BTR`, `BTC` | `- - - - - - - - -` | **No** | CF receives the selected bit → [A-06](#a-06) |
| `IRET` (and aliases `IRETD`, `IRETQ`) | `M - - - M M M M M` | **No** | pops all of EFLAGS: D, I and T too → [A-07](#a-07) |
| `INT` | `- - 0 0 - - - - -` | **Partial** | depends on the mode and the gate type → [A-13](#a-13) |
| `MOVS`, `LODS`, `STOS` (and suffixes) | `- - - - - - - - -` | Yes | correct |
| 23 x87 entries | `- - - - - - - - -` | **Misleading** | they do not touch FLAGS but do touch the x87 status word; the UI shows "does not modify any flag" → [A-28](#a-28) |

---

## A.6 The ceiling of the catalog as Java code, measured

```
javap -c -p InstructionCatalog.class
→ static {}: last bytecode offset          = 9406
             bytecode instructions          = 3158
   class-file limit per method              = 65535
   size of the .class                       = 100 642 bytes
```

9406 B / 214 entries ≈ **44 B of bytecode per entry**. At the same rate, the 65 535 B per-method limit is reached
around **1490 entries** **[code]**. The sheet this plan proposes has forms, typed operands, per-form flags,
requirements, sources and several examples: each entry will cost several times more bytecode, so the wall comes
well before 1490. The realistic order of magnitude is between 300 and 500 entries, and it is an **extrapolation
[assumption]**, not a measurement. That is why the migration to data (AA-P1-01) comes before any content
expansion.

---

## A.7 The 30 findings

Severity: **A** the product gives false information or marks correct code as wrong · **B** the model prevents
meeting a requirement of the assignment · **C** localized defect, direct fix.

### Correctness of the content and the model

#### A-01
**Severity A · `INSTRUCTION` is a valid mnemonic.**
`reg()` derives aliases from the first token of each syntax variant; `LOCK` declares `"LOCK instruction"`, so
`ALIASES` gets `INSTRUCTION → LOCK`
([`InstructionCatalog.java:1313-1314`](../../idearm-language/src/main/java/io/github/dinamo541/idearm/language/catalog/InstructionCatalog.java)).
Consequence: the word `instruction` in source code is colored as an instruction and the analyzer accepts it.
**Fix:** declare `aliases[]` in the data and delete `extractMnemonicFromVariant`. → AA-P1-02.

#### A-02
**Severity A · Alias resolution loses the CPU requirement.**
`find("IRETD")` and `find("IRETQ")` return the `IRET` entry with `minCpu = 8086`. `IRETD` requires the 80386 and
`IRETQ` **exists only in 64-bit mode and needs the REX.W prefix**
**[source: HTML rendering of the SDM, `iret:iretd:iretq`]**. An 8086 project gets no warning.
The same applies to `INSB/INSD/INSW`. **Fix:** an alias is an entry with its own requirement, pointing at the
form, not at the mnemonic. → AA-P2-02.

#### A-03
**Severity B · `CMOVcc` declared as Pentium, and `CpuLevel` has no P6.**
The 12 `CMOV*` entries declare `CPU_PENTIUM`; `CMOVcc` appears in the P6 (Pentium Pro), not in the P5. The
`CpuLevel` scale has no value for P6, so the correct fact cannot be represented
([`CpuLevel.java:9-15`](../../idearm-language/src/main/java/io/github/dinamo541/idearm/language/catalog/CpuLevel.java)).
**[assumption, to be confirmed against SDM vol. 2 → AA-P0-03]**. **Fix:** extend the generation scale and also
declare the matching CPUID bit. → AA-P2-01.

#### A-04
**Severity B · One form, one requirement, one flag table.**
Described in §A.5. **Fix:** `InstructionForm` as an entity. → AA-P2-02.

#### A-05
**Severity B · Implicit operands do not exist in the model.**
`MUL`, `IMUL`, `DIV`, `IDIV`, the string instructions, `XLAT`, `LOOP`, `PUSHA`, `ENTER`, `CWD` operate on
registers that appear in no queryable structure. **Fix:** `Operand` with `role ∈ {EXPLICIT, IMPLICIT}` and
`access ∈ {READ, WRITE, READ_WRITE}`. → AA-P2-02.

#### A-06
**Severity A · `BT`, `BTS`, `BTR`, `BTC` declare no flag effect and their own prose says otherwise.**
`BT` describes "copies the selected bit of the first operand into the carry flag (CF)" and declares
`FlagSummary.none()` ([`InstructionCatalog.java:1337-1342`](../../idearm-language/src/main/java/io/github/dinamo541/idearm/language/catalog/InstructionCatalog.java)).
The source: "The CF flag contains the value of the selected bit. The ZF flag is unaffected. The OF, SF, AF, and
PF flags are undefined" **[source: HTML rendering of the SDM, `bt`]**. The catalog's own example (`bt eax,5` /
`jc bit_activo`) only works if CF changes. **Fix:** correct table + test. → AA-P1-03.

#### A-07
**Severity A · `IRET` declares D, I and T as preserved.**
`IRET` pops the whole flags word: "All the flags and fields in the EFLAGS register are potentially modified,
depending on the mode of operation" **[source: HTML rendering of the SDM, `iret:iretd:iretq`]**. The catalog
declares `M - - - M M M M M`, while `POPF`, which does the same to the flags, correctly declares all nine. An
internal inconsistency. **Fix:** correct table + test. → AA-P1-03.

#### A-08
**Severity A · `MOVABS` is declared as an x86-64 instruction and no IDEARM assembler accepts it.**
`movabs` is a **GNU as** mnemonic: "In 64-bit code, `movabs` can be used to encode the `mov` instruction with
the 64-bit displacement or immediate operand" **[source: GNU as documentation, i386-Variations]**. It is not an
Intel mnemonic and does not appear in the NASM 3.02 manual; NASM writes `mov rax, qword <imm>`. IDEARM assembles
with TASM, MASM and NASM: none of them accepts it. Today the editor colors it as valid and the analyzer approves
it, and the error appears later, in the assembler. **Fix:** remove it as an instruction and document it as a
mnemonic of another dialect (a syntax entry with `dialect = gas`, `recognizedBy = []`). → AA-P1-04.

#### A-09
**Severity B · The 23 x87 entries declare `minCpu = 8086`.**
The scale has no coprocessor or extension axis, so the only way to express "needs an 8087/287/387 or a 486 with an
integrated FPU" is to lie downwards. Measurable effect: an 8086 project gets no warning at all, and the built-in
emulator **fails** with an invalid opcode because it does not implement `D8`–`DF` (§A.8).
**Fix:** a `features` axis with `X87` and a per-backend `availability`. → AA-P2-01.

#### A-10
**Severity B · Whole families are missing.**
No MMX, no SSE/SSE2/SSE3/SSSE3/SSE4, no AVX, no BMI, no system-table instructions (`LGDT`, `LIDT`, `LMSW`,
`SLDT`…), no `SYSENTER`/`SYSEXIT`, no 64-bit strings (`MOVSQ`…), most of the transcendental x87 missing (`FSIN`,
`FPATAN`, `F2XM1`…), no undocumented instructions (`SALC`, `ICEBP`). Full list in §A.4. **Fix:** an inventory
per family with a declared treatment level. → AA-P5.

#### A-11
**Severity A · Shifts: AF declared modified; the conditions are missing.**
Details in §A.5. Affects `SHL`, `SHR`, `SAR` (and `SAL` through its alias), `SHLD`, `SHRD`.
**Fix:** `FlagEffectSpec` with a condition. → AA-P1-03 (values) and AA-P2-03 (conditions).

#### A-12
**Severity C · Rotations: OF without a condition.**
`ROL`, `ROR`, `RCL`, `RCR` declare OF modified without saying it is defined only for 1-bit rotations.
**Fix:** a condition. → AA-P2-03.

#### A-13
**Severity C · `INT` declares IF and TF cleared without context.**
That is what happens in real mode; in protected mode it depends on whether the gate is an interrupt or a trap
gate. **Fix:** an effect with a per-mode condition. → AA-P2-03.

#### A-14
**Severity B · `MOVSD` and `CMPSD` are homonyms and only one reading is documented.**
The catalog documents the 32-bit string instruction (80386). In SSE2 the same mnemonics mean "move scalar
double-precision" and "compare scalar double-precision". A student who reads the sheet with SSE code in front of
them gets the wrong explanation. **Fix:** `Instruction` allows several readings, told apart by extension and by
operand form, and the sheet presents them as alternatives. → AA-P2-02.

### Compatibility and analyzer

#### A-15
**Severity A · `parseLevel` downgrades the unknown to 8086.**
Data in §A.3. **Fix:** the context is derived from the `TargetProfile` and the unknown is `UNKNOWN`. → AA-P2-04.

#### A-16
**Severity A · A 64-bit project gets all 214 entries.**
It includes **at least 15** entries that are invalid in long mode: `AAA`, `AAD`, `AAM`, `AAS`, `DAA`, `DAS`,
`PUSHA`, `PUSHAD`, `POPA`, `POPAD`, `INTO`, `BOUND`, `LDS`, `LES`, `ARPL`. Two more cases remain to be confirmed:
`JCXZ` (whose 16-bit address size does not exist in 64-bit mode) and the `PUSH`/`POP` forms with a segment
register, which are not entries of the catalog but operand forms. **The final list is closed in AA-P0-03**; this
plan does not fix it on its own. **Fix:** `invalidModes`. → AA-P2-04.

#### A-17
**Severity B · `CpuBaselineRule`: false negatives and a wrong label.**
It does not detect `IMUL ax, bx, 3` on the 8086 (the mnemonic's `minCpu` is 8086), nor `IRETD`, nor
`push offset msg` (`isImmediate` only checks whether the first character is a digit or a quote). It calls `FS`
and `GS` a "32-bit register that requires the 80386+", while they are 16-bit segment registers introduced with
the 386: the requirement is right, the concept is not
([`CpuBaselineRule.java:112-114`](../../idearm-language/src/main/java/io/github/dinamo541/idearm/language/linter/CpuBaselineRule.java)).
Also, the warning for a shift with an immediate count fires on a symbol (`shl ax, CUENTA`) even when it equals 1.
**Fix:** per-form requirements and typed operands. → AA-P2-05.

#### A-18
**Severity A · On 32/64 bits the only active rule is the one that produces the false errors.**
`LintSource` builds `portableLinter = new AssemblyLinter(List.of(new UnknownInstructionRule()))`
([`LintSource.java:29`](../../idearm-application/src/main/java/io/github/dinamo541/idearm/application/editor/LintSource.java)).
A double consequence: the mnemonics of §A.4 are marked as errors, and no other warning works (the CPU baseline is
not checked in native projects). **Fix:** AA-P1-04 (boundary and severity) and AA-P2-05 (rules per profile).

#### A-19
**Severity C · Duplicated i18n key.**
`dialog.dictionary.description` is defined twice in `messages_es.properties` (lines 551 and 606) and in
`messages_en.properties` (551 and 606). `Properties` keeps the last one, so the title of the sheet section,
"Operational explanation and behavior", is shown as "Explore x86 instructions, syntax, flags and code examples".
`MessageBundlesTest` checks parity, used keys and defined keys, but **not** duplicates **[code]**. **Fix:** rename
one key and add the test. → AA-P1-06.

### Contextual help

#### A-20
**Severity A · The catalog is consulted before the project's symbols.**
`QueryHover` resolves number → catalog → symbol index
([`QueryHover.java:39-63`](../../idearm-application/src/main/java/io/github/dinamo541/idearm/application/editor/QueryHover.java)),
and `UnknownInstructionRule` checks `isKnownInstruction` **before** looking at the declared symbols
([`UnknownInstructionRule.java:57-60`](../../idearm-language/src/main/java/io/github/dinamo541/idearm/language/linter/UnknownInstructionRule.java)).
A user macro or label named like an instruction is explained as an instruction. Acceptance case 10. **Fix:**
reverse the order and offer the instruction as a secondary card. → AA-P1-05.

#### A-21
**Severity A · The help cannot see punctuation and explains `21h` out of context.**
`getWordAt` requires `isWordChar` (letter, digit, `_ @ $ ? .`), so `:` , `[` , `]` , `,` return an empty string
([`RichTextFxEditorComponent.java:512-541`](../../idearm-app/src/main/java/io/github/dinamo541/idearm/app/editor/RichTextFxEditorComponent.java)).
And `21h` in `int 21h` produces a numeric conversion card announcing "ASCII '!'", because 0x21 = 33 is in the
printable range ([`QueryHover.java:111-113`](../../idearm-application/src/main/java/io/github/dinamo541/idearm/application/editor/QueryHover.java)).
Acceptance cases 1, 2 and 4. **Fix:** `QueryExplain(file, line, column)` with an operand parser and awareness of
the instruction around the token. → AA-P4.

#### A-22
**Severity B · Completion encodes its own world, in a single language.**
`QueryCompletion` carries 30 registers and 30 directives written in the class, and uses `summaryEn()` and
`descriptionEn()` without looking at the UI language
([`QueryCompletion.java:15-44`](../../idearm-application/src/main/java/io/github/dinamo541/idearm/application/editor/QueryCompletion.java)).
With the UI in Spanish, completion explains in English. It offers no aliases (`JNBE`) either and does not know
`R8`–`R15`. **Fix:** a `CompletionView` over the corpus, with a language. → AA-P3-04.

#### A-23
**Severity B · Three register lists and a directive set without a dialect.**
`AssemblyLexer.REGISTERS` has 68 entries (including `R8B`, `SIL`, `RFLAGS`); `QueryCompletion.REGISTERS` has 30
(no 64-bit register at all, no `FS`/`GS`); `CpuBaselineRule.REGS_32BIT` has 10. `DIRECTIVES` mixes
`.MODEL`/`ASSUME`/`PROC` with `SECTION`/`RESB`/`TIMES`/`BITS`/`GLOBAL`/`DEFAULT`/`REL`, without marking a
dialect, and the `%` character is silently dropped, so `%macro` tokenizes as the `MACRO` directive
([`AssemblyLexer.java:12-37,156-158`](../../idearm-language/src/main/java/io/github/dinamo541/idearm/language/lexer/AssemblyLexer.java)).
**Fix:** registers and syntax as corpus entities; a lexer parameterized by dialect. → AA-P2-06 and AA-P3-01.

#### A-24
**Severity A · Search does not answer the student's questions.**
Data in §A.3: "colon", "brackets", "extra segment", "interrupt 21h" and "@data" give zero results. `matches` does
not look at the example and there are no concept, symbol or service entities. Acceptance case 9.
**Fix:** a search index over every entity, with synonyms and with punctuation marks as entries of their own.
→ AA-P3-02.

### Emulator and debugger

#### A-25
**Severity B · The emulator is "8086 plus a few 80186 opcodes", and it does not say so.**
`Cpu8086.executeOpcode` implements 174 cases. `C0`/`C1` (shift with an immediate count) and `68`/`6A`
(`PUSH imm`), which are 80186, are there; **missing** are `60`–`67` (`PUSHA`/`POPA`), `69`/`6B` (`IMUL` with an
immediate), `6C`–`6F` (`INS`/`OUTS`), `C8`/`C9` (`ENTER`/`LEAVE`) and the whole x87 range `D8`–`DF`, which falls
into `default → fail("emu.invalid-opcode")`
([`Cpu8086.java:196-614`](../../idearm-emu8086/src/main/java/io/github/dinamo541/idearm/emu8086/cpu/Cpu8086.java)).
The `LOCK` prefix (`F0`) is ignored; `F2`/`F3` only apply to `A6`, `A7`, `AE`, `AF` for `REPE`/`REPNE`.
**Fix:** the corpus declares `availability.emu8086` per form, from a table generated from the emulator itself (a
test compares the two lists so they cannot drift apart). → AA-P2-07.

#### A-26
**Severity B · `INT 21h` function 0Ah accepts one character more than the documented contract.**
`DosInterruptHandler` loops `while (sb.length() < maxLen)` and writes the carriage return at
`off + 2 + sb.length()`
([`DosInterruptHandler.java:168-188`](../../idearm-emu8086/src/main/java/io/github/dinamo541/idearm/emu8086/dos/DosInterruptHandler.java)).
The contract says: "The maxInput field specifies the maximum number of characters the user can enter,
**including the Enter key**" **[source: Irvine 5th ed. (Spanish), §13.2.3, printed 443 / PDF 475]**. With
`maxInput = 10` DOS accepts 9 characters plus the return; the emulator accepts 10 and writes 11 bytes after the
counter. **Fix:** document the contract in the corpus and open an emulator fix task with a test. → AA-P0-06
(confirm with a second source) and an emulator task in AA-P2-07.

#### A-27
**Severity C · The unimplemented `INT 10h` and `INT 16h` services give no warning; those of `INT 21h` do.**
`handleInt21` reports once per function with the code `emu.dos.unsupported`; `handleInt10` and `handleInt16`
have `default -> return true`, that is, they do nothing and do not say so
([`DosInterruptHandler.java:218-282`](../../idearm-emu8086/src/main/java/io/github/dinamo541/idearm/emu8086/dos/DosInterruptHandler.java)).
A program that uses `INT 10h` function 13h for graphics "works" without drawing anything. **Fix:** the same
warning mechanism, with the corpus as the source of the service name. → AA-P3-05.

#### A-28
**Severity C · "Does not modify any flag" is misleading for the x87.**
The sheet shows `dialog.dictionary.flags.none` when `isAnyAffected()` is false
([`MnemonicsDictionaryDialog.java:488-497`](../../idearm-app/src/main/java/io/github/dinamo541/idearm/app/view/MnemonicsDictionaryDialog.java)),
and the 23 x87 entries fall there. They do not touch FLAGS but they do touch the x87 status word (C0–C3), which is
exactly what `FSTSW AX` + `SAHF` moves into FLAGS, a sequence the catalog itself uses in its examples.
**Fix:** the model tells the "flags register" apart from the "x87 status word", and the sheet says so. → AA-P2-03.

#### A-29
**Severity B · The debugger registers do not reach the x87 or SIMD, and the native panel shows a fixed 24.**
`RegisterState` holds the 13 16-bit registers of the 8086 plus an `extended` map, and masks `flags` to 16 bits,
so the EFLAGS bits above bit 15 (`AC`, `VM`, `RF`, `ID`…) are lost
([`RegisterState.java`](../../idearm-domain/src/main/java/io/github/dinamo541/idearm/domain/debug/RegisterState.java)).
`GdbProcessDebugSession` deliberately selects `GENERAL_64` (24 names) or `GENERAL_32` (16), although GDB lists 92
on amd64 **[code + PLAN.md §8]**. **Fix:** the corpus marks, per register, whether any backend exposes it; the
sheet says so instead of promising it. Widening the panel is a separate decision (D-AA-13). → AA-P2-06.

#### A-30
**Severity B · The catalog tests check presence, not semantics.**
`InstructionCatalogTest` has 22 tests; the only semantic assertion is that `ADD` modifies ZF and CF. The rest
check that there are entries, that the filters filter and that the strings are not null **[code]**. None of the
wrong tables of §A.5 would have failed. **Fix:** a semantic accuracy suite with an independent oracle.
→ AA-P1-03 and AA-P5-04.

---

## A.8 Table of the opcodes the emulator implements

Extracted with `grep -o "case 0x.." Cpu8086.java | sort -u` **[code]**. It is included because it feeds the
`debuggable` column of the matrix in annex B and the `availability.emu8086` mark in the corpus.

```
00 01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E        ← 0F missing (two-byte prefix / POP CS on the 8086)
10 11 12 13 14 15 16 17 18 19 1A 1B 1C 1D 1E 1F
20 21 22 23 24 25 27 28 29 2A 2B 2C 2D 2F
30 31 32 33 34 35 37 38 39 3A 3B 3C 3D 3F
40 48 50 58                                         ← per-register INC/DEC/PUSH/POP ranges
68 6A                                               ← 80186: PUSH imm16 / imm8
70…7F                                               ← all conditional jumps
80 81 83 84 85 86 87 88 89 8A 8B 8C 8D 8E 8F
90 91 98 99 9A 9B 9C 9D 9E 9F
A0…AF                                               ← direct MOV and strings
B0 B8 C0 C1 C2 C3 C4 C5 C6 C7 CA CB CC CD CE CF     ← C0/C1 are 80186
D0 D1 D2 D3 D4 D5 D7
E0 E1 E2 E3 E4 E5 E6 E7 E8 E9 EA EB EC ED EE EF
F4 F5 F6 F7 F8 F9 FA FB FC FD FE FF
prefixes: 26 2E 36 3E (segment) · F2 F3 (REP, only with A6/A7/AE/AF) · F0 (LOCK, ignored)
relevant missing: 0F · 60–67 (PUSHA/POPA) · 69 6B (IMUL imm) · 6C–6F (INS/OUTS) ·
                  C8 C9 (ENTER/LEAVE) · D6 (SALC) · D8–DF (the whole x87) · F1 (ICEBP)
```

## A.9 Services the emulator implements

| Vector | Implemented functions | The rest |
|---|---|---|
| `INT 20h` | terminate program | — |
| `INT 21h` | `00h` `01h` `02h` `06h` `07h` `08h` `09h` `0Ah` `0Bh` `25h` `30h` `35h` `4Ch` (**13**) | warns once with `emu.dos.unsupported`, sets AL=0 and continues |
| `INT 10h` | `00h` (video mode, does nothing) · `0Eh` (teletype) · `0Fh` (current mode: returns AL=3, AH=80, BH=0) | **silence**: does nothing and gives no warning ([A-27](#a-27)) |
| `INT 16h` | `00h` (reads a key, scan code always 0) · `01h` (checks for a key) | **silence** |
| any other | — | `handleInterrupt` returns `false` and the CPU reports `emu.interrupt.unsupported` |

Contract details the corpus will have to record, verified in the code **[code]**: function `09h` returns
`AL = '$'` (0x24) although Irvine documents "Returns: nothing" **[source: Irvine §13.2.1, printed 440]**, a real
discrepancy between sources that the sheet must explain, not hide; function `09h` aborts with
`emu.string.unterminated` after 64 KiB without finding `$`; `0Ah` has the mismatch of [A-26](#a-26); `30h`
declares DOS 5.0; `0Bh` returns `0FFh`/`00h` depending on whether a key is pending.
