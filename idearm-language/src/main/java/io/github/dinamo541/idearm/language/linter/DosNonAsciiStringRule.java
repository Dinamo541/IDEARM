package io.github.dinamo541.idearm.language.linter;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Location;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import io.github.dinamo541.idearm.language.index.ProjectSymbolIndex;
import io.github.dinamo541.idearm.language.model.ConstantNode;
import io.github.dinamo541.idearm.language.model.DataNode;
import io.github.dinamo541.idearm.language.model.InstructionNode;
import io.github.dinamo541.idearm.language.model.SourceFileNode;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Educational lint rule: Warns when a DOS string literal contains non-ASCII characters (> 127, e.g. 'ñ', 'á', '¿')
 * in a UTF-8 encoded file, which causes DOS to display corrupted characters (e.g. "a├▒o"), and suggests saving
 * with CP437 encoding instead.
 */
public final class DosNonAsciiStringRule implements LintRule {

    public static final String CODE = "lint.dos-non-ascii-string";

    /** Matches single-quoted or double-quoted string literals, supporting escaped quotes ('' or ""). */
    private static final Pattern QUOTED_STRING = Pattern.compile("'((?:[^']|'')*)'|\"((?:[^\"]|\"\")*)\"");

    private final Predicate<String> isUtf8Predicate;

    public DosNonAsciiStringRule() {
        this(path -> true);
    }

    public DosNonAsciiStringRule(Predicate<String> isUtf8Predicate) {
        this.isUtf8Predicate = Objects.requireNonNull(isUtf8Predicate, "isUtf8Predicate cannot be null");
    }

    public static DosNonAsciiStringRule forCharsetProvider(Function<String, Charset> charsetProvider) {
        Objects.requireNonNull(charsetProvider, "charsetProvider cannot be null");
        return new DosNonAsciiStringRule(path -> {
            Charset cs = charsetProvider.apply(path);
            return cs == null || StandardCharsets.UTF_8.equals(cs);
        });
    }

    @Override
    public List<Diagnostic> check(SourceFileNode ast, String filePath, String targetCpu, ProjectSymbolIndex index) {
        return check(ast, filePath, targetCpu, index, null);
    }

    @Override
    public List<Diagnostic> check(SourceFileNode ast, String filePath, String targetCpu, ProjectSymbolIndex index,
                                   Charset charset) {
        if (ast == null) {
            return List.of();
        }
        boolean isUtf8 = (charset != null) ? StandardCharsets.UTF_8.equals(charset) : isUtf8Predicate.test(filePath);
        if (!isUtf8) {
            return List.of();
        }

        List<Diagnostic> diagnostics = new ArrayList<>();
        checkDataDefinitions(ast, filePath, diagnostics);
        checkInstructions(ast, filePath, diagnostics);
        checkConstants(ast, filePath, diagnostics);
        return List.copyOf(diagnostics);
    }

    private void checkDataDefinitions(SourceFileNode ast, String filePath, List<Diagnostic> diagnostics) {
        for (DataNode data : ast.dataDefinitions()) {
            Matcher m = QUOTED_STRING.matcher(data.initialValue());
            while (m.find()) {
                String literal = m.group();
                if (containsNonAscii(literal)) {
                    Location loc = new Location(filePath, data.line(), data.column());
                    diagnostics.add(createDiagnostic(literal, loc));
                }
            }
        }
    }

    private void checkInstructions(SourceFileNode ast, String filePath, List<Diagnostic> diagnostics) {
        for (InstructionNode inst : ast.instructions()) {
            for (String operand : inst.operands()) {
                Matcher m = QUOTED_STRING.matcher(operand);
                while (m.find()) {
                    String literal = m.group();
                    if (containsNonAscii(literal)) {
                        Location loc = new Location(filePath, inst.line(), inst.column());
                        diagnostics.add(createDiagnostic(literal, loc));
                    }
                }
            }
        }
    }

    private void checkConstants(SourceFileNode ast, String filePath, List<Diagnostic> diagnostics) {
        for (ConstantNode constant : ast.constants()) {
            Matcher m = QUOTED_STRING.matcher(constant.value());
            while (m.find()) {
                String literal = m.group();
                if (containsNonAscii(literal)) {
                    Location loc = new Location(filePath, constant.line(), constant.column());
                    diagnostics.add(createDiagnostic(literal, loc));
                }
            }
        }
    }

    private static Diagnostic createDiagnostic(String literal, Location loc) {
        return new Diagnostic(
                Severity.WARNING,
                CODE,
                "String literal " + literal + " contains non-ASCII characters in a UTF-8 file; DOS will display corrupted characters. Save the file with CP437 encoding instead.",
                loc,
                "idearm-linter",
                literal,
                List.of(literal)
        );
    }

    private static boolean containsNonAscii(String str) {
        if (str == null) {
            return false;
        }
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) > 127) {
                return true;
            }
        }
        return false;
    }
}
