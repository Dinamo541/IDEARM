package io.github.dinamo541.idearm.language.linter;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Location;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import io.github.dinamo541.idearm.language.catalog.CpuLevel;
import io.github.dinamo541.idearm.language.catalog.InstructionCatalog;
import io.github.dinamo541.idearm.language.catalog.InstructionInfo;
import io.github.dinamo541.idearm.language.index.ProjectSymbolIndex;
import io.github.dinamo541.idearm.language.model.InstructionNode;
import io.github.dinamo541.idearm.language.model.SourceFileNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Educational lint rule: Detects instructions and operand combinations exceeding the target CPU baseline.
 * E.g., on an 8086 target, 'SHL AX, 2' or 'PUSH 10h' or 'PUSHA' require 80186+.
 */
public final class CpuBaselineRule implements LintRule {

    @Override
    public List<Diagnostic> check(SourceFileNode ast, String filePath, String targetCpu, ProjectSymbolIndex index) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        String cpu = targetCpu != null ? targetCpu : "8086";
        int allowedLevel = CpuLevel.parseLevel(cpu);

        for (InstructionNode inst : ast.instructions()) {
            String mnem = inst.mnemonic().toUpperCase(Locale.ROOT);
            Optional<InstructionInfo> infoOpt = InstructionCatalog.find(mnem);

            if (infoOpt.isPresent()) {
                InstructionInfo info = infoOpt.get();
                if (info.minCpu().level() > allowedLevel) {
                    Location loc = new Location(filePath, inst.line(), inst.column());
                    diagnostics.add(new Diagnostic(
                            Severity.WARNING,
                            "lint.cpu-baseline",
                            "Instruction '" + mnem + "' requires " + info.minCpu().displayName() + "+ CPU, but project target is " + cpu + ".",
                            loc,
                            "idearm-linter",
                            mnem,
                            List.of(mnem, info.minCpu().displayName(), cpu)
                    ));
                    continue;
                }
            }

            // Check shift with immediate count > 1 on 8086
            if (allowedLevel == 0 && (mnem.equals("SHL") || mnem.equals("SHR") || mnem.equals("ROL") || mnem.equals("ROR") || mnem.equals("SAR"))) {
                List<String> ops = inst.operands();
                if (ops.size() >= 2) {
                    String count = ops.get(1).trim();
                    if (!count.equalsIgnoreCase("1") && !count.equalsIgnoreCase("CL")) {
                        Location loc = new Location(filePath, inst.line(), inst.column());
                        diagnostics.add(new Diagnostic(
                                Severity.WARNING,
                                "lint.cpu-baseline-shift",
                                "Shifting by immediate count '" + count + "' requires 80186+ CPU; 8086 only allows 1 or CL.",
                                loc,
                                "idearm-linter",
                                mnem + " " + String.join(", ", ops),
                                List.of(count)
                        ));
                    }
                }
            }

            // Check PUSH immediate on 8086
            if (allowedLevel == 0 && mnem.equals("PUSH")) {
                List<String> ops = inst.operands();
                if (!ops.isEmpty() && isImmediate(ops.get(0))) {
                    Location loc = new Location(filePath, inst.line(), inst.column());
                    diagnostics.add(new Diagnostic(
                            Severity.WARNING,
                            "lint.cpu-baseline-push",
                            "Pushing an immediate value ('" + ops.get(0) + "') requires 80186+ CPU; on 8086 load into a register first.",
                            loc,
                            "idearm-linter",
                            "PUSH " + ops.get(0),
                            List.of(ops.get(0))
                    ));
                }
            }

            // Check 32-bit registers on < 80386
            if (allowedLevel < 3) {
                for (String op : inst.operands()) {
                    String found32Reg = find32BitRegister(op);
                    if (found32Reg != null) {
                        Location loc = new Location(filePath, inst.line(), inst.column());
                        diagnostics.add(new Diagnostic(
                                Severity.WARNING,
                                "lint.cpu-baseline-register32",
                                "Register '" + found32Reg + "' is a 32-bit register requiring 80386+ CPU; project target is " + cpu + ".",
                                loc,
                                "idearm-linter",
                                mnem + " " + String.join(", ", inst.operands()),
                                List.of(found32Reg, cpu)
                        ));
                        break;
                    }
                }
            }
        }

        return diagnostics;
    }

    private static final java.util.Set<String> REGS_32BIT = java.util.Set.of(
            "EAX", "EBX", "ECX", "EDX", "ESI", "EDI", "EBP", "ESP", "FS", "GS"
    );

    private static String find32BitRegister(String operand) {
        if (operand == null || operand.isBlank()) return null;
        String upper = operand.toUpperCase(Locale.ROOT);
        // Direct match or word boundary match
        for (String reg : REGS_32BIT) {
            if (upper.equals(reg) || upper.matches(".*\\b" + reg + "\\b.*")) {
                return reg;
            }
        }
        return null;
    }

    private static boolean isImmediate(String op) {
        String clean = op.trim();
        if (clean.isEmpty()) return false;
        char first = clean.charAt(0);
        return Character.isDigit(first) || first == '\'' || first == '"';
    }
}
