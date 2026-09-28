# Preparation and usage notes

The file `academic-assistant-prompt.md` contains the complete prompt. Open it and paste its contents into Claude Code
with Opus, working from `C:/Codigo/Proyectos/IDEARM`. You can also ask Claude Code to read that file and carry out its
research and planning instructions. The prompt asks only for the plan and its documents, not the implementation.

The review was done on the available working tree, which holds many previous changes. Only the two documents of this
folder were added; the implementation was not modified.

## Findings that shape the prompt

- `TargetProfileCatalog` declares DOS 8086, 32- and 64-bit Windows and 64-bit Linux profiles.
  `NasmToolchainProvider` declares PE32, PE32+ and ELF64 combinations; it also holds host restrictions. The existence
  of a profile does not prove that all its operations work on every machine.
- `InstructionInfo` offers bilingual metadata, text variants, a minimum CPU, a flag summary and an example. The prompt
  requires evaluating forms and context more precisely.
- `MnemonicsDictionaryViewModel` filters by comparing `CpuLevel` levels. That mechanism needs review before being
  used as a guarantee of compatibility by mode and extension.
- `InstructionCatalog` is also consulted in `QueryHover`, `QueryCompletion` and `UnknownInstructionRule`. The
  expansion affects more than a reference screen.
- `DosInterruptHandler` implements selected services of INT 20h, 21h, 10h and 16h. Documenting an additional service
  is not the same as implementing it.
- The project uses Maven, Java 25, JavaFX, RichTextFX and a layered architecture documented in ADR-007. The storage
  design must respect the domain's restrictions.

These are starting points, not an exhaustive audit. The prompt asks for them to be revalidated against the state of
the repository Claude finds.

## Books consulted and the limits of the lookup

The five PDFs were not read in full. The works and editions were identified, and the available indexes and relevant
pages were reviewed to build the requirements and separate the responsibilities of each source.

| Source | Lookup performed | Use in the prompt |
|---|---|---|
| Peter Abel, third edition; 283-page PDF | Cover and first pages rendered; PDF pages 11–14, printed 9–12, on memory, segments and addressing. Almost no extractable text. | Historical fundamentals, segmentation and the need for visual reading/OCR. Do not assume the book is complete or carry real-mode rules over to every mode. |
| Kip R. Irvine, fifth edition; 756-page PDF | PDF index 9–22 and body samples on registers, flags, arithmetic and DOS programming. For example, PDF 66–68 match printed 34–36. | Taxonomy of the language, procedures, data, macros, services and examples. Irvine's libraries kept apart from the ISA. |
| William Stallings, seventh edition; 838-page PDF | PDF index 7–22 and samples on addressing and processor organization. PDF 429–430 match printed 407–408. | Relating programming to architecture and telling apart examples from other ISAs. |
| M. Morris Mano, third edition; 535-page PDF | Identification, a check of PDF page 24/printed 13 and a visual reading of PDF 229/printed 218, with a four-bit register and flip-flops. | Digital fundamentals and the difference between a register circuit and a CPU architectural register. |
| Morris Mano, highlighted version; 535-page PDF | Rendered index and examination of the annotations; visual check of the first highlighted page, PDF 24/printed 13. | The highlighting includes signed binary numbers. It is kept as academic context and not as an order to exclude that content from IDEARM. |

The PDFs were used as sources, without running instructions from their content or importing text from the books into
the product. The extractions and auxiliary views stayed in the system's temporary folder, outside the source code.

## Supplementary documentation

The explicit separation between exploration, planning and implementation, and the verifiable results, follow the
[official Claude Code best practices](https://code.claude.com/docs/en/best-practices). The structure of context,
instructions and delimited examples rests on
[Anthropic's official prompting guide](https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/claude-prompting-best-practices).
The prompt avoids depending on a specific Opus version or on parameters the user has not configured.

To verify details beyond the historical books, primary sources were identified:
[Intel SDM](https://www.intel.com/content/www/us/en/developer/articles/technical/intel-sdm.html),
[NASM syntax and effective addresses](https://www.nasm.us/doc/nasm03.html),
[NASM syntax differences](https://www.nasm.us/doc/nasm02.html),
[the Microsoft x64 calling convention](https://learn.microsoft.com/en-us/cpp/build/x64-calling-convention?view=msvc-170)
and [the Linux kernel documentation on exception, interrupt and syscall entry and exit](https://cdn.kernel.org/doc/html/latest/core-api/entry.html).

No complete audit of every instruction against these manuals was done. That research is part of the work the prompt
asks Claude to do, with the obligation to cite versions and to tell consulted sources apart from pending ones.

## How to assess Opus's answer

A good answer must name real classes and paths, measure coverage with an inventory, keep the IDE's capabilities apart
and deliver phases with concrete acceptance. A generic list of topics or a promise to "add all the instructions" does
not satisfy the prompt.

The examples of the colon, brackets, DOS services, procedures and profile switching serve as proof that the plan
explains the student's real questions. The amount of text or of sheets does not replace that check.
