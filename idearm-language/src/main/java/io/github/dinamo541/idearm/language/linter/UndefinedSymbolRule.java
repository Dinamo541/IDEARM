package io.github.dinamo541.idearm.language.linter;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Location;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import io.github.dinamo541.idearm.language.index.ProjectSymbolIndex;
import io.github.dinamo541.idearm.language.model.InstructionNode;
import io.github.dinamo541.idearm.language.model.LabelNode;
import io.github.dinamo541.idearm.language.model.ProcedureNode;
import io.github.dinamo541.idearm.language.model.SourceFileNode;

import java.util.*;

/**
 * Educational lint rule: Checks for calls or jumps referencing undefined labels or procedures.
 */
public final class UndefinedSymbolRule implements LintRule {

    private static final Set<String> BRANCH_INSTRUCTIONS = Set.of(
            "JMP", "CALL", "JE", "JZ", "JNE", "JNZ", "JA", "JNBE", "JAE", "JNB", "JNC",
            "JB", "JNAE", "JC", "JBE", "JNA", "JG", "JNLE", "JGE", "JNL",
            "JL", "JNGE", "JLE", "JNG", "JS", "JNS", "JO", "JNO", "JP", "JPE", "JNP", "JPO",
            "JCXZ", "JECXZ", "LOOP", "LOOPE", "LOOPZ", "LOOPNE", "LOOPNZ"
    );

    @Override
    public List<Diagnostic> check(SourceFileNode ast, String filePath, String targetCpu, ProjectSymbolIndex index) {
        List<Diagnostic> diagnostics = new ArrayList<>();

        // Local known symbols in this file
        Set<String> localSymbols = new HashSet<>();
        for (ProcedureNode proc : ast.procedures()) {
            localSymbols.add(proc.name().toUpperCase(Locale.ROOT));
        }
        for (LabelNode lbl : ast.labels()) {
            localSymbols.add(lbl.name().toUpperCase(Locale.ROOT));
        }

        for (InstructionNode inst : ast.instructions()) {
            String mnem = inst.mnemonic().toUpperCase(Locale.ROOT);
            if (BRANCH_INSTRUCTIONS.contains(mnem) && !inst.operands().isEmpty()) {
                String target = label(inst.operands().get(0));
                // Only a plain label can be checked: registers, memory ([BX], WORD PTR x), segment prefixes
                // (ES:[DI]), the current location ($+2) and expressions (tabla+2) are left alone.
                if (target == null || isRegister(target)) {
                    continue;
                }

                String upperTarget = target.toUpperCase(Locale.ROOT);
                if (!localSymbols.contains(upperTarget)) {
                    // Check if defined in project index
                    boolean foundInProject = index != null && index.findDefinition(target).isPresent();
                    if (!foundInProject) {
                        Location loc = new Location(filePath, inst.line(), inst.column());
                        diagnostics.add(new Diagnostic(
                                Severity.WARNING,
                                "lint.undefined-target",
                                "Target label or procedure '" + target + "' does not appear to be defined.",
                                loc,
                                "idearm-linter",
                                mnem + " " + target,
                                List.of(target)
                        ));
                    }
                }
            }
        }

        return diagnostics;
    }

    /** Distance and size keywords written before a jump target: {@code JMP SHORT fin}, {@code CALL FAR PTR sub}. */
    private static final Set<String> TARGET_KEYWORDS = Set.of("SHORT", "NEAR", "FAR", "PTR", "WORD", "DWORD");
    private static final java.util.regex.Pattern NAME = java.util.regex.Pattern.compile("[A-Za-z_@?.][A-Za-z0-9_@?$.]*");

    /** The label a jump names, or {@code null} when the operand is something else. */
    private static String label(String operand) {
        var words = new ArrayList<>(List.of(operand.trim().split("\\s+")));
        boolean memory = false;
        while (!words.isEmpty() && TARGET_KEYWORDS.contains(words.getFirst().toUpperCase(Locale.ROOT))) {
            String keyword = words.removeFirst().toUpperCase(Locale.ROOT);
            memory |= keyword.equals("WORD") || keyword.equals("DWORD");
        }
        if (memory || words.size() != 1) {
            return null;
        }
        String target = words.getFirst();
        return NAME.matcher(target).matches() ? target : null;
    }

    private static boolean isRegister(String op) {
        String clean = op.toUpperCase(Locale.ROOT);
        return Set.of("AX", "BX", "CX", "DX", "SI", "DI", "BP", "SP", "AL", "AH", "BL", "BH", "CL", "CH", "DL", "DH",
                "CS", "DS", "ES", "SS", "EAX", "EBX", "ECX", "EDX", "ESI", "EDI", "EBP", "ESP").contains(clean);
    }
}
