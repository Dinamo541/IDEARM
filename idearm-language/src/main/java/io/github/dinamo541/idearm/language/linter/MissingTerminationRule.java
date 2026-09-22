package io.github.dinamo541.idearm.language.linter;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Location;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import io.github.dinamo541.idearm.language.index.ProjectSymbolIndex;
import io.github.dinamo541.idearm.language.model.InstructionNode;
import io.github.dinamo541.idearm.language.model.ProcedureNode;
import io.github.dinamo541.idearm.language.model.SourceFileNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Educational lint rule: Warns when an entry procedure (e.g. main/start) does not contain
 * a standard DOS program exit (INT 21h with AH=4Ch, or .EXIT) before ending.
 */
public final class MissingTerminationRule implements LintRule {

    @Override
    public List<Diagnostic> check(SourceFileNode ast, String filePath, String targetCpu, ProjectSymbolIndex index) {
        List<Diagnostic> diagnostics = new ArrayList<>();

        for (ProcedureNode proc : ast.procedures()) {
            String name = proc.name().toLowerCase(Locale.ROOT);
            if (name.equals("main") || name.equals("start") || name.equals("inicio")) {
                if (!hasDosExit(ast, proc)) {
                    Location loc = new Location(filePath, proc.line(), proc.column());
                    diagnostics.add(new Diagnostic(
                            Severity.WARNING,
                            "lint.missing-exit",
                            "Procedure '" + proc.name() + "' does not contain a program exit (e.g. MOV AH, 4Ch / INT 21h or .EXIT). The program will not terminate cleanly.",
                            loc,
                            "idearm-linter",
                            proc.name() + " PROC",
                            List.of(proc.name())
                    ));
                }
            }
        }

        return diagnostics;
    }

    private static boolean hasDosExit(SourceFileNode ast, ProcedureNode proc) {
        boolean preparedAh = false;

        for (InstructionNode inst : ast.instructions()) {
            if (inst.line() >= proc.line() && inst.line() <= proc.endLine()) {
                String mnem = inst.mnemonic();
                List<String> ops = inst.operands();

                if (mnem.equals(".EXIT")) {
                    return true;
                }

                if (mnem.equals("MOV") && ops.size() >= 2) {
                    String dst = ops.get(0).toUpperCase(Locale.ROOT);
                    String src = ops.get(1).toUpperCase(Locale.ROOT).replace("H", "").replace("0X", "");
                    if ((dst.equals("AH") && src.contains("4C")) || (dst.equals("AX") && src.contains("4C"))) {
                        preparedAh = true;
                    }
                }

                if (mnem.equals("INT") && !ops.isEmpty()) {
                    String vector = ops.get(0).toUpperCase(Locale.ROOT).replace("H", "");
                    if (vector.equals("21") && preparedAh) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
