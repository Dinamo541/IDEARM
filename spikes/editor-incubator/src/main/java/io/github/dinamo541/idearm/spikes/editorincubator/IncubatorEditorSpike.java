package io.github.dinamo541.idearm.spikes.editorincubator;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import jfx.incubator.scene.control.richtext.CodeArea;
import jfx.incubator.scene.control.richtext.RichTextArea;
import jfx.incubator.scene.control.richtext.SyntaxDecorator;
import jfx.incubator.scene.control.richtext.TextPos;
import jfx.incubator.scene.control.richtext.model.CodeTextModel;
import jfx.incubator.scene.control.richtext.model.RichParagraph;

/**
 * Spike F0-S5 (alternative): the JavaFX incubator {@code CodeArea} (module {@code jfx.incubator.richtext}) with the
 * same large Assembly file and the same edit benchmark as the RichTextFX spike, so both can be compared.
 */
public class IncubatorEditorSpike extends Application {

    private static final String DIRECTIVES = String.join("|",
            "PROC", "ENDP", "END", "SEGMENT", "ENDS", "ASSUME", "DB", "DW", "DD", "DQ", "DT", "EQU", "OFFSET", "PTR",
            "BYTE", "WORD", "DWORD", "NEAR", "FAR", "PUBLIC", "EXTRN", "INCLUDE", "MACRO", "ENDM", "LOCAL", "DUP",
            "SEG", "ORG");
    private static final String INSTRUCTIONS = String.join("|",
            "MOV", "PUSH", "POP", "XCHG", "LEA", "ADD", "ADC", "SUB", "SBB", "INC", "DEC", "NEG", "CMP", "MUL",
            "IMUL", "DIV", "IDIV", "AND", "OR", "XOR", "NOT", "TEST", "SHL", "SHR", "SAL", "SAR", "ROL", "ROR",
            "RCL", "RCR", "JMP", "JE", "JNE", "JZ", "JNZ", "JA", "JAE", "JB", "JBE", "JG", "JGE", "JL", "JLE", "JC",
            "JNC", "JCXZ", "LOOP", "LOOPE", "LOOPNE", "CALL", "RET", "RETF", "INT", "IRET", "CLC", "STC", "CLD",
            "STD", "CLI", "STI", "NOP", "HLT", "IN", "OUT", "LODSB", "LODSW", "STOSB", "STOSW", "MOVSB", "MOVSW",
            "REP", "REPE", "REPNE", "CBW", "CWD", "PUSHF", "POPF");
    private static final String REGISTERS = String.join("|",
            "AX", "BX", "CX", "DX", "AH", "AL", "BH", "BL", "CH", "CL", "DH", "DL", "SI", "DI", "SP", "BP", "CS",
            "DS", "ES", "SS", "IP");

    private static final Pattern TOKENS = Pattern.compile(
            "(?<COMMENT>;.*)"
                    + "|(?<STRING>'[^']*'|\"[^\"]*\")"
                    + "|(?<DIRECTIVE>\\.(?:MODEL|STACK|DATA|CODE|EXIT|STARTUP|8086|186|286|386)\\b|\\b(?:" + DIRECTIVES + ")\\b)"
                    + "|(?<INSTRUCTION>\\b(?:" + INSTRUCTIONS + ")\\b)"
                    + "|(?<REGISTER>\\b(?:" + REGISTERS + ")\\b)"
                    + "|(?<NUMBER>\\b\\d[0-9A-F]*[HBOQD]?\\b)",
            Pattern.CASE_INSENSITIVE);

    @Override
    public void start(Stage stage) throws Exception {
        List<String> args = getParameters().getRaw();
        if (args.isEmpty() || args.getFirst().isBlank()) {
            throw new IllegalArgumentException("Usage: IncubatorEditorSpike <file.asm> (Maven: -Dspike.file=<file.asm>)");
        }
        Path file = Path.of(args.getFirst());
        // Paragraph text must not contain control characters other than TAB, so CR is dropped.
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).replace("\r", "");

        var area = new CodeArea();
        area.setLineNumbersEnabled(true);
        area.setFont(Font.font("Consolas", 14));
        var decorator = new AssemblyDecorator();
        area.setSyntaxDecorator(decorator);
        var scene = new Scene(area, 1100, 800);
        scene.getStylesheets().add(getClass().getResource("editor-incubator-spike.css").toExternalForm());
        stage.setTitle("S5b - " + file.getFileName());
        stage.setScene(scene);
        stage.show();

        long started = System.nanoTime();
        area.setText(text);
        long loadNanos = System.nanoTime() - started;

        var model = (CodeTextModel) area.getModel();
        started = System.nanoTime();
        for (int index = 0; index < model.size(); index++) {
            decorator.createRichParagraph(model, index);
        }
        long decorateNanos = System.nanoTime() - started;

        Method scrollToCaret = findScrollToCaret();
        System.out.printf("File: %s (%d bytes, %d paragraphs)%n", file, Files.size(file), area.getParagraphCount());
        System.out.printf("setText: %.1f ms | decorating every paragraph once: %.1f ms (the control decorates lazily)%n",
                ms(loadNanos), ms(decorateNanos));
        System.out.println("Scroll-to-caret method found by reflection: "
                + (scrollToCaret == null ? "none (edits in the jump scenario may happen off-screen)" : scrollToCaret.getName()));

        var benchmark = new EditBenchmark(scene, area, scrollToCaret);
        benchmark.run("Jump across the file and type", 100, true,
                () -> benchmark.run("Type in the same paragraph", 100, false, Platform::exit));
    }

    /** Looks for a public no-argument method whose name mentions both "scroll" and "caret". */
    private static Method findScrollToCaret() {
        return Arrays.stream(RichTextArea.class.getMethods())
                .filter(method -> method.getParameterCount() == 0)
                .filter(method -> {
                    String name = method.getName().toLowerCase(Locale.ROOT);
                    return name.contains("scroll") && name.contains("caret");
                })
                .findFirst()
                .orElse(null);
    }

    static String styleOf(Matcher matcher) {
        return matcher.group("COMMENT") != null ? "comment"
                : matcher.group("STRING") != null ? "string"
                : matcher.group("DIRECTIVE") != null ? "directive"
                : matcher.group("INSTRUCTION") != null ? "instruction"
                : matcher.group("REGISTER") != null ? "register"
                : "number";
    }

    static String summarize(long[] samples) {
        long[] sorted = samples.clone();
        Arrays.sort(sorted);
        return "p50=%.2f ms  p95=%.2f ms  max=%.2f ms  (n=%d)".formatted(
                ms(sorted[sorted.length / 2]), ms(sorted[(int) Math.ceil(sorted.length * 0.95) - 1]),
                ms(sorted[sorted.length - 1]), sorted.length);
    }

    static double ms(long nanos) {
        return nanos / 1_000_000.0;
    }

    /** Decorates each paragraph independently: Assembly comments and strings never span lines. */
    private static final class AssemblyDecorator implements SyntaxDecorator {

        @Override
        public RichParagraph createRichParagraph(CodeTextModel model, int index) {
            String text = model.getPlainText(index);
            RichParagraph.Builder builder = RichParagraph.builder();
            Matcher matcher = TOKENS.matcher(text);
            int last = 0;
            while (matcher.find()) {
                if (matcher.start() > last) {
                    builder.addSegment(text.substring(last, matcher.start()));
                }
                builder.addWithStyleNames(matcher.group(), styleOf(matcher));
                last = matcher.end();
            }
            if (last < text.length()) {
                builder.addSegment(text.substring(last));
            }
            return builder.build();
        }

        @Override
        public void handleChange(CodeTextModel model, TextPos start, TextPos end, int charsTop, int linesAdded, int charsBottom) {
            // Nothing is cached: every paragraph is decorated on demand.
        }
    }

    /** Runs edits one at a time and measures from the change until the next layout pulse. */
    private static final class EditBenchmark {
        private final Scene scene;
        private final CodeArea area;
        private final Method scrollToCaret;

        EditBenchmark(Scene scene, CodeArea area, Method scrollToCaret) {
            this.scene = scene;
            this.area = area;
            this.scrollToCaret = scrollToCaret;
        }

        void run(String name, int count, boolean jump, Runnable onDone) {
            long[] samples = new long[count];
            int[] done = {0};
            long[] startedAt = {0};
            Runnable[] edit = new Runnable[1];
            Runnable afterLayout = () -> {
                if (startedAt[0] == 0) {
                    return;
                }
                samples[done[0]++] = System.nanoTime() - startedAt[0];
                startedAt[0] = 0;
                Platform.runLater(edit[0]);
            };
            scene.addPostLayoutPulseListener(afterLayout);
            // Without a scroll-to-caret method, type near the top so the edited paragraph is visible.
            int fixedParagraph = scrollToCaret == null ? 20 : area.getParagraphCount() / 2;
            edit[0] = () -> {
                if (done[0] == count) {
                    scene.removePostLayoutPulseListener(afterLayout);
                    System.out.printf("%s: %s%n", name, summarize(samples));
                    onDone.run();
                    return;
                }
                int paragraph = jump
                        ? (int) ((long) done[0] * (area.getParagraphCount() - 1) / (count - 1))
                        : fixedParagraph;
                TextPos position = TextPos.ofLeading(paragraph, 0);
                startedAt[0] = System.nanoTime();
                area.replaceText(position, position, "x", true);
                area.select(TextPos.ofLeading(paragraph, 1));
                if (scrollToCaret != null) {
                    try {
                        scrollToCaret.invoke(area);
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException(e);
                    }
                }
                Platform.requestNextPulse();
            };
            Platform.runLater(edit[0]);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
