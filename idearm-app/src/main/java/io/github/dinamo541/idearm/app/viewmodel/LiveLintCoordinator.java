package io.github.dinamo541.idearm.app.viewmodel;

import io.github.dinamo541.idearm.app.editor.EditorComponent;
import io.github.dinamo541.idearm.application.editor.LintSource;
import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.language.index.ProjectSymbolIndex;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Validates the document being edited a short while after typing stops, and publishes what it finds to the editor
 * (as underlines) and to the Problems panel.
 *
 * <p>Two things decide the shape of this class. Validation must not run on every keystroke, so each request cancels
 * the one before it and only the last one survives the pause; and it must not run on the JavaFX thread, because
 * parsing a large file there would stutter the typing that ADR-005 budgets at 11 ms p95. The only step that needs
 * the JavaFX thread is reading the text, and that happens once per pause rather than once per key.
 */
final class LiveLintCoordinator implements AutoCloseable {

    /** What a validation pass needs to know about the document it is given. */
    record LintContext(String filePath, String targetCpu, boolean dosTarget, ProjectSymbolIndex index) {
    }

    private final LintSource lintSource;
    private final ScheduledExecutorService executor;
    private final long debounceMillis;
    private final Consumer<List<Diagnostic>> problemsSink;

    /** The pass waiting for typing to pause, per open editor; several documents can be edited in turn. */
    private final Map<EditorComponent, ScheduledFuture<?>> pending = new IdentityHashMap<>();
    /** Counts the requests per editor, so a pass whose text has since changed does not publish stale marks. */
    private final Map<EditorComponent, Long> generations = new IdentityHashMap<>();
    /** The editor whose problems the Problems panel shows. */
    private EditorComponent published;

    /**
     * @param executor     runs the delay and the validation itself; this coordinator owns it and shuts it down
     * @param problemsSink receives every result on the JavaFX thread, to show in the Problems panel
     */
    LiveLintCoordinator(LintSource lintSource, ScheduledExecutorService executor, long debounceMillis,
                        Consumer<List<Diagnostic>> problemsSink) {
        this.lintSource = Objects.requireNonNull(lintSource, "lintSource cannot be null");
        this.executor = Objects.requireNonNull(executor, "executor cannot be null");
        this.problemsSink = Objects.requireNonNull(problemsSink, "problemsSink cannot be null");
        this.debounceMillis = debounceMillis;
    }

    /**
     * Asks for the editor to be validated once typing has paused. Called on every edit, so the work it schedules
     * replaces whatever was scheduled before for the same editor; other editors keep their own pending pass.
     *
     * @param context supplies the file and target at the moment the pass runs, not at the moment it was requested
     */
    synchronized void requestLint(EditorComponent editor, Supplier<LintContext> context) {
        if (editor == null || context == null || executor.isShutdown()) {
            return;
        }
        cancelPending(editor);
        long generation = generations.merge(editor, 1L, Long::sum);
        pending.put(editor, executor.schedule(() -> pass(editor, context, generation), debounceMillis,
                TimeUnit.MILLISECONDS));
    }

    /** Drops the marks of an editor that is going away, so a closed document leaves no problems behind. */
    synchronized void clear(EditorComponent editor) {
        if (editor == null) {
            return;
        }
        cancelPending(editor);
        generations.remove(editor);
        FxDispatch.run(() -> editor.setDiagnostics(List.of()));
        if (published == editor || published == null) {
            published = null;
            FxDispatch.run(() -> problemsSink.accept(List.of()));
        }
    }

    @Override
    public synchronized void close() {
        pending.values().forEach(future -> future.cancel(false));
        pending.clear();
        generations.clear();
        executor.shutdownNow();
    }

    private void cancelPending(EditorComponent editor) {
        ScheduledFuture<?> previous = pending.remove(editor);
        if (previous != null) {
            previous.cancel(false);
        }
    }

    private synchronized boolean isCurrent(EditorComponent editor, long generation) {
        Long current = generations.get(editor);
        return current != null && current == generation;
    }

    private synchronized void publish(EditorComponent editor) {
        published = editor;
    }

    private void pass(EditorComponent editor, Supplier<LintContext> contextSupplier, long generation) {
        // Reading the text is the only step that belongs on the JavaFX thread.
        FxDispatch.run(() -> {
            if (!isCurrent(editor, generation)) {
                return;
            }
            String text = editor.getText();
            LintContext context = contextSupplier.get();
            if (context == null || text == null || text.isBlank()) {
                editor.setDiagnostics(List.of());
                publish(editor);
                problemsSink.accept(List.of());
                return;
            }
            if (!executor.isShutdown()) {
                try {
                    executor.execute(() -> validate(editor, text, context, generation));
                } catch (java.util.concurrent.RejectedExecutionException closing) {
                    // The workbench is closing.
                }
            }
        });
    }

    private void validate(EditorComponent editor, String text, LintContext context, long generation) {
        List<Diagnostic> diagnostics = lintSource.execute(
                text, context.filePath(), context.targetCpu(), context.index(), context.dosTarget());
        FxDispatch.run(() -> {
            // Typing went on, or the document closed, while this pass ran: its marks would sit on the wrong lines.
            if (!isCurrent(editor, generation)) {
                return;
            }
            editor.setDiagnostics(diagnostics);
            publish(editor);
            problemsSink.accept(diagnostics);
        });
    }
}
