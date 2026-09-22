package io.github.dinamo541.idearm.spikes.editor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

/**
 * Spike F0-S5: RichTextFX with a large real Assembly file (loadmap.asm, 104 KB).
 *
 * <p>Measures loading, full highlighting and the latency of simulated edits on the JavaFX thread, from the change
 * until the next layout pulse. It closes itself when done and prints the results.
 */
public class EditorSpike extends Application {

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

    // Assembly is line-oriented (comments never span lines), so highlighting can be recomputed per paragraph.
    private static final Pattern TOKENS = Pattern.compile(
            "(?<COMMENT>;[^\\n]*)"
                    + "|(?<STRING>'[^'\\n]*'|\"[^\"\\n]*\")"
                    + "|(?<DIRECTIVE>\\.(?:MODEL|STACK|DATA|CODE|EXIT|STARTUP|8086|186|286|386)\\b|\\b(?:" + DIRECTIVES + ")\\b)"
                    + "|(?<INSTRUCTION>\\b(?:" + INSTRUCTIONS + ")\\b)"
                    + "|(?<REGISTER>\\b(?:" + REGISTERS + ")\\b)"
                    + "|(?<NUMBER>\\b\\d[0-9A-F]*[HBOQD]?\\b)",
            Pattern.CASE_INSENSITIVE);

    @Override
    public void start(Stage stage) throws Exception {
        List<String> args = getParameters().getRaw();
        if (args.isEmpty() || args.getFirst().isBlank()) {
            throw new IllegalArgumentException("Usage: EditorSpike <file.asm> (Maven: -Dspike.file=<file.asm>)");
        }
        Path file = Path.of(args.getFirst());
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        var area = new CodeArea();
        area.setParagraphGraphicFactory(LineNumberFactory.get(area));
        var scene = new Scene(new VirtualizedScrollPane<>(area), 1100, 800);
        scene.getStylesheets().add(getClass().getResource("editor-spike.css").toExternalForm());
        stage.setTitle("S5 - " + file.getFileName());
        stage.setScene(scene);
        stage.show();

        long started = System.nanoTime();
        area.replaceText(text);
        long loadNanos = System.nanoTime() - started;

        started = System.nanoTime();
        StyleSpans<Collection<String>> spans = highlight(text);
        long computeNanos = System.nanoTime() - started;

        started = System.nanoTime();
        area.setStyleSpans(0, spans);
        long applyNanos = System.nanoTime() - started;

        System.out.printf("File: %s (%d bytes, %d lines)%n", file, Files.size(file), area.getParagraphs().size());
        System.out.printf("replaceText: %.1f ms | full highlighting (compute): %.1f ms | apply styles: %.1f ms%n",
                ms(loadNanos), ms(computeNanos), ms(applyNanos));

        var benchmark = new EditBenchmark(scene, area);
        benchmark.run("Jump across the file and type (per-paragraph highlighting)", 100, true, false,
                () -> benchmark.run("Type in the same paragraph (per-paragraph highlighting)", 100, false, false,
                        () -> benchmark.run("Type with full-file highlighting", 20, false, true, Platform::exit)));
    }

    static StyleSpans<Collection<String>> highlight(String text) {
        Matcher matcher = TOKENS.matcher(text);
        var builder = new StyleSpansBuilder<Collection<String>>();
        int last = 0;
        while (matcher.find()) {
            String style = matcher.group("COMMENT") != null ? "comment"
                    : matcher.group("STRING") != null ? "string"
                    : matcher.group("DIRECTIVE") != null ? "directive"
                    : matcher.group("INSTRUCTION") != null ? "instruction"
                    : matcher.group("REGISTER") != null ? "register"
                    : "number";
            builder.add(Collections.emptyList(), matcher.start() - last);
            builder.add(Collections.singleton(style), matcher.end() - matcher.start());
            last = matcher.end();
        }
        builder.add(Collections.emptyList(), text.length() - last);
        return builder.create();
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

    /** Runs edits one at a time and measures from the change until the next layout pulse. */
    private static final class EditBenchmark {
        private final Scene scene;
        private final CodeArea area;

        EditBenchmark(Scene scene, CodeArea area) {
            this.scene = scene;
            this.area = area;
        }

        void run(String name, int count, boolean jump, boolean fullHighlight, Runnable onDone) {
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
            int fixedParagraph = area.getParagraphs().size() / 2;
            edit[0] = () -> {
                if (done[0] == count) {
                    scene.removePostLayoutPulseListener(afterLayout);
                    System.out.printf("%s: %s%n", name, summarize(samples));
                    onDone.run();
                    return;
                }
                int paragraph = jump
                        ? (int) ((long) done[0] * (area.getParagraphs().size() - 1) / (count - 1))
                        : fixedParagraph;
                int position = area.getAbsolutePosition(paragraph, 0);
                startedAt[0] = System.nanoTime();
                area.insertText(position, "x");
                if (fullHighlight) {
                    area.setStyleSpans(0, highlight(area.getText()));
                } else {
                    area.setStyleSpans(paragraph, 0, highlight(area.getParagraph(paragraph).getText()));
                }
                area.moveTo(position + 1);
                area.requestFollowCaret();
                Platform.requestNextPulse();
            };
            Platform.runLater(edit[0]);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
