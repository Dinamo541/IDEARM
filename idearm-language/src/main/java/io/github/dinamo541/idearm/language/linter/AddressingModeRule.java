package io.github.dinamo541.idearm.language.linter;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Location;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import io.github.dinamo541.idearm.language.catalog.CpuLevel;
import io.github.dinamo541.idearm.language.index.ProjectSymbolIndex;
import io.github.dinamo541.idearm.language.knowledge.AddressingMode;
import io.github.dinamo541.idearm.language.model.InstructionNode;
import io.github.dinamo541.idearm.language.model.MemoryOperand;
import io.github.dinamo541.idearm.language.model.ParsedOperand;
import io.github.dinamo541.idearm.language.model.SourceFileNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Educational lint rule verifying the validity of memory addressing modes (AA-P4-03).
 * Catches invalid combinations (e.g., [bx+bp] "dos registros base", [ax] in 16-bit addressing,
 * or ESP used as index register in 32-bit SIB).
 */
public final class AddressingModeRule implements LintRule {

    @Override
    public List<Diagnostic> check(SourceFileNode ast, String filePath, String targetCpu, ProjectSymbolIndex index) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        if (ast == null || ast.instructions() == null) {
            return diagnostics;
        }

        String cpu = targetCpu != null ? targetCpu : "8086";
        // If unknown or generic x86 without specific CPU profile, assume at least 386 to avoid false positives
        int allowedLevel = cpu.equalsIgnoreCase("x86") ? 3 : CpuLevel.parseLevel(cpu);

        for (InstructionNode inst : ast.instructions()) {
            for (ParsedOperand op : inst.parsedOperands()) {
                if (op instanceof MemoryOperand mem && mem.expression() != null) {
                    AddressingMode.Validation val = AddressingMode.validate(mem.expression(), allowedLevel);
                    if (!val.isValid()) {
                        Location loc = new Location(filePath, inst.line(), op.startColumn());
                        String reasonEn = val.reasonEn() != null ? val.reasonEn() : "";
                        String reasonEs = val.reasonEs() != null ? val.reasonEs() : reasonEn;
                        // The message is English; the arguments carry both reasons so each UI language picks its own.
                        diagnostics.add(new Diagnostic(
                                Severity.ERROR,
                                "lint.addressing.invalid",
                                "Invalid addressing mode: " + reasonEn,
                                loc,
                                "idearm-linter",
                                mem.rawText(),
                                List.of(reasonEn, reasonEs)
                        ));
                    }
                }
            }
        }

        return List.copyOf(diagnostics);
    }
}
