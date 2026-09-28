package io.github.dinamo541.idearm.language.linter;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Location;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import io.github.dinamo541.idearm.language.catalog.CpuLevel;
import io.github.dinamo541.idearm.language.catalog.InstructionCatalog;
import io.github.dinamo541.idearm.language.catalog.InstructionInfo;
import io.github.dinamo541.idearm.language.index.ProjectSymbolIndex;
import io.github.dinamo541.idearm.language.index.SymbolDefinition;
import io.github.dinamo541.idearm.language.index.SymbolKind;
import io.github.dinamo541.idearm.language.knowledge.CpuGeneration;
import io.github.dinamo541.idearm.language.model.ConstantNode;
import io.github.dinamo541.idearm.language.model.InstructionNode;
import io.github.dinamo541.idearm.language.model.SourceFileNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Educational lint rule: Detects instructions and operand combinations exceeding the target CPU baseline.
 * E.g., on an 8086 target, 'SHL AX, 2' or 'PUSH 10h' or 'PUSHA' require 80186+.
 */
public final class CpuBaselineRule implements LintRule {

    /** The value after the {@code EQU} keyword or {@code =} of a constant's signature. */
    private static final Pattern CONSTANT_VALUE = Pattern.compile("(?i)(?:\\bEQU\\b|=)\\s*(.*)$");
    private static final Pattern WORD = Pattern.compile("[A-Za-z0-9_]+");

    private static final Set<String> LONG_MODE_INVALID = Set.of(
            "AAA", "AAS", "AAM", "AAD", "DAA", "DAS", "INTO",
            "PUSHA", "POPA", "BOUND", "ARPL", "LDS", "LES"
    );

    private static final Set<String> REGS_32BIT = Set.of(
            "EAX", "EBX", "ECX", "EDX", "ESI", "EDI", "EBP", "ESP"
    );

    private static final Set<String> SEG_REGS_386 = Set.of(
            "FS", "GS"
    );

    @Override
    public List<Diagnostic> check(SourceFileNode ast, String filePath, String targetCpu, ProjectSymbolIndex index) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        String cpu = targetCpu != null ? targetCpu : "8086";

        // If target CPU is unknown/generic x86, ternary resolution produces UNKNOWN and no warnings
        if (cpu.equalsIgnoreCase("x86") || CpuGeneration.parse(cpu) == CpuGeneration.UNKNOWN) {
            return diagnostics;
        }

        int allowedLevel = CpuLevel.parseLevel(cpu);

        for (InstructionNode inst : ast.instructions()) {
            String mnem = inst.mnemonic().toUpperCase(Locale.ROOT);
            Location loc = new Location(filePath, inst.line(), inst.column());

            // Check 64-bit Long Mode invalid instructions
            if (allowedLevel == 7) {
                if (LONG_MODE_INVALID.contains(mnem)) {
                    diagnostics.add(new Diagnostic(
                            Severity.ERROR,
                            "lint.long-mode-invalid",
                            "Instruction '" + mnem + "' is invalid in 64-bit Long Mode.",
                            loc,
                            "idearm-linter",
                            mnem,
                            List.of(mnem)
                    ));
                    continue;
                }
                if ((mnem.equals("PUSH") || mnem.equals("POP")) && !inst.operands().isEmpty()) {
                    String firstOp = inst.operands().getFirst().trim().toUpperCase(Locale.ROOT);
                    if (firstOp.equals("CS")) {
                        diagnostics.add(new Diagnostic(
                                Severity.ERROR,
                                "lint.long-mode-invalid",
                                "Instruction '" + mnem + " CS' is invalid in 64-bit Long Mode.",
                                loc,
                                "idearm-linter",
                                mnem + " CS",
                                List.of(mnem + " CS")
                        ));
                        continue;
                    }
                }
            }

            // Check form-specific requirements: IMUL
            if (mnem.equals("IMUL")) {
                List<String> ops = inst.operands();
                if (ops.size() >= 3) {
                    if (allowedLevel < 1) { // 3-operand form requires 80186+
                        diagnostics.add(new Diagnostic(
                                Severity.WARNING,
                                "lint.cpu-baseline",
                                "Instruction 'IMUL' with 3 operands requires 80186+ CPU; project target is " + cpu + ".",
                                loc,
                                "idearm-linter",
                                mnem,
                                List.of(mnem, "80186", cpu)
                        ));
                        continue;
                    }
                } else if (ops.size() == 2) {
                    if (allowedLevel < 3) { // 2-operand form requires 80386+
                        diagnostics.add(new Diagnostic(
                                Severity.WARNING,
                                "lint.cpu-baseline",
                                "Instruction 'IMUL' with 2 operands requires 80386+ CPU; project target is " + cpu + ".",
                                loc,
                                "idearm-linter",
                                mnem,
                                List.of(mnem, "80386", cpu)
                        ));
                        continue;
                    }
                }
            }

            // Check form-specific requirements: IRETD
            if (mnem.equals("IRETD")) {
                if (allowedLevel < 3) {
                    diagnostics.add(new Diagnostic(
                            Severity.WARNING,
                            "lint.cpu-baseline",
                            "Instruction 'IRETD' requires 80386+ CPU, but project target is " + cpu + ".",
                            loc,
                            "idearm-linter",
                            mnem,
                            List.of(mnem, "80386", cpu)
                    ));
                    continue;
                }
            }

            // General instruction minCpu check
            Optional<InstructionInfo> infoOpt = InstructionCatalog.find(mnem);
            if (infoOpt.isPresent()) {
                InstructionInfo info = infoOpt.get();
                if (info.minCpu().level() > allowedLevel) {
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
                    String resolvedCount = count;
                    for (ConstantNode c : ast.constants()) {
                        if (c.name().equalsIgnoreCase(count)) {
                            resolvedCount = c.value().trim();
                            break;
                        }
                    }
                    if (resolvedCount.equalsIgnoreCase(count) && index != null) {
                        Optional<SymbolDefinition> sym = index.find(count);
                        if (sym.isPresent() && sym.get().kind() == SymbolKind.CONSTANT) {
                            // The EQU keyword, not the first "EQU" in the text: FREQUENCY EQU 1 contains two.
                            Matcher value = CONSTANT_VALUE.matcher(sym.get().signature());
                            if (value.find()) {
                                resolvedCount = value.group(1).trim();
                            }
                        }
                    }
                    if (!resolvedCount.equalsIgnoreCase("1") && !resolvedCount.equalsIgnoreCase("CL")) {
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
                if (!ops.isEmpty() && isImmediate(ops.getFirst())) {
                    diagnostics.add(new Diagnostic(
                            Severity.WARNING,
                            "lint.cpu-baseline-push",
                            "Pushing an immediate value ('" + ops.getFirst() + "') requires 80186+ CPU; on 8086 load into a register first.",
                            loc,
                            "idearm-linter",
                            "PUSH " + ops.getFirst(),
                            List.of(ops.getFirst())
                    ));
                }
            }

            // Check 32-bit registers & segment registers on < 80386
            if (allowedLevel < 3) {
                for (String op : inst.operands()) {
                    String found32Reg = findRegister(op, REGS_32BIT);
                    if (found32Reg != null) {
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
                    String foundSegReg = findRegister(op, SEG_REGS_386);
                    if (foundSegReg != null) {
                        diagnostics.add(new Diagnostic(
                                Severity.WARNING,
                                "lint.cpu-baseline-segment386",
                                "Segment register '" + foundSegReg + "' requires 80386+ CPU; project target is " + cpu + ".",
                                loc,
                                "idearm-linter",
                                mnem + " " + String.join(", ", inst.operands()),
                                List.of(foundSegReg, cpu)
                        ));
                        break;
                    }
                }
            }
        }

        return diagnostics;
    }

    /** The first word of the operand that is one of {@code targetRegs}, or {@code null}. */
    private static String findRegister(String operand, Set<String> targetRegs) {
        if (operand == null || operand.isBlank()) return null;
        Matcher word = WORD.matcher(operand);
        while (word.find()) {
            String upper = word.group().toUpperCase(Locale.ROOT);
            if (targetRegs.contains(upper)) {
                return upper;
            }
        }
        return null;
    }

    private static boolean isImmediate(String op) {
        if (op == null) return false;
        String clean = op.trim();
        if (clean.isEmpty()) return false;
        String upper = clean.toUpperCase(Locale.ROOT);
        if (upper.startsWith("OFFSET ") || upper.startsWith("OFFSET\t") || upper.equals("OFFSET")) {
            return true;
        }
        if ((clean.startsWith("-") || clean.startsWith("+")) && clean.length() > 1) {
            clean = clean.substring(1).trim(); // push -1
        }
        if (clean.isEmpty()) return false;
        char first = clean.charAt(0);
        return Character.isDigit(first) || first == '\'' || first == '"';
    }
}
