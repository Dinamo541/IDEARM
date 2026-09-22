package io.github.dinamo541.idearm.application.editor;

import io.github.dinamo541.idearm.language.catalog.InstructionCatalog;
import io.github.dinamo541.idearm.language.catalog.InstructionInfo;
import io.github.dinamo541.idearm.language.index.ProjectSymbolIndex;
import io.github.dinamo541.idearm.language.index.SymbolDefinition;

import java.util.*;

/**
 * Use case: Computes contextual code completions based on prefix, target CPU, and project symbols.
 */
public final class QueryCompletion {

    private static final List<String> REGISTERS = List.of(
            "AX", "BX", "CX", "DX", "AH", "AL", "BH", "BL", "CH", "CL", "DH", "DL",
            "SI", "DI", "SP", "BP", "CS", "DS", "ES", "SS", "IP", "FLAGS",
            "EAX", "EBX", "ECX", "EDX", "ESI", "EDI", "ESP", "EBP"
    );

    private static final List<String> DIRECTIVES = List.of(
            ".MODEL", ".STACK", ".DATA", ".CODE", ".STARTUP", ".EXIT",
            "PROC", "ENDP", "END", "SEGMENT", "ENDS", "ASSUME",
            "DB", "DW", "DD", "EQU", "OFFSET", "PTR", "BYTE", "WORD", "DWORD",
            "NEAR", "FAR", "PUBLIC", "EXTRN", "INCLUDE", "MACRO", "ENDM", "DUP"
    );

    public List<CompletionItem> execute(String prefix, String targetCpu, ProjectSymbolIndex index) {
        String p = prefix != null ? prefix.trim().toUpperCase(Locale.ROOT) : "";
        List<CompletionItem> results = new ArrayList<>();

        // 1. Instructions filtered by target CPU
        for (InstructionInfo inst : InstructionCatalog.getForCpu(targetCpu)) {
            if (p.isEmpty() || inst.mnemonic().startsWith(p)) {
                results.add(new CompletionItem(
                        inst.mnemonic(),
                        inst.mnemonic(),
                        inst.summaryEn(),
                        inst.descriptionEn(),
                        CompletionKind.INSTRUCTION,
                        inst.minCpu()
                ));
            }
        }

        // 2. Registers
        for (String reg : REGISTERS) {
            if (p.isEmpty() || reg.startsWith(p)) {
                results.add(CompletionItem.of(reg, CompletionKind.REGISTER, "Register"));
            }
        }

        // 3. Directives
        for (String dir : DIRECTIVES) {
            if (p.isEmpty() || dir.startsWith(p) || dir.replace(".", "").startsWith(p)) {
                results.add(CompletionItem.of(dir, CompletionKind.DIRECTIVE, "Directive"));
            }
        }

        // 4. Project symbols
        if (index != null) {
            for (SymbolDefinition def : index.findDefinitionsStartingWith(p)) {
                CompletionKind kind = switch (def.kind()) {
                    case PROCEDURE -> CompletionKind.PROCEDURE;
                    case LABEL -> CompletionKind.LABEL;
                    case VARIABLE -> CompletionKind.VARIABLE;
                    case CONSTANT -> CompletionKind.CONSTANT;
                    default -> CompletionKind.LABEL;
                };
                results.add(CompletionItem.of(def.name(), kind, def.signature()));
            }
        }

        // Sort: exact matches first, then alphabetically
        results.sort(Comparator.comparing((CompletionItem item) -> !item.label().equalsIgnoreCase(p))
                .thenComparing(CompletionItem::kind)
                .thenComparing(CompletionItem::label));

        return List.copyOf(results);
    }
}
