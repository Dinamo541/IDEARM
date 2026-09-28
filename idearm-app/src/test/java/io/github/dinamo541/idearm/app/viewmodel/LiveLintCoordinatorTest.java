package io.github.dinamo541.idearm.app.viewmodel;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dinamo541.idearm.app.editor.FakeEditorComponent;
import io.github.dinamo541.idearm.application.editor.LintSource;
import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.language.index.ProjectSymbolIndex;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LiveLintCoordinatorTest {

    private static final String MISTYPED = """
            .code
            main proc
              MUV bx, 1
            main endp
            end main
            """;

    private LiveLintCoordinator.LintContext context() {
        return new LiveLintCoordinator.LintContext("main.asm", "8086", true, new ProjectSymbolIndex());
    }

    @Test
    void doesNotValidateWhileTypingIsStillGoing() throws Exception {
        // A delay no test can reach proves the coalescing without waiting for a clock: ten edits, nothing published.
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        var editor = new FakeEditorComponent();
        editor.setText(MISTYPED);

        try (var coordinator = new LiveLintCoordinator(new LintSource(), executor, 600_000, diagnostics -> { })) {
            for (int edit = 0; edit < 10; edit++) {
                coordinator.requestLint(editor, this::context);
            }

            assertEquals(0, editor.getDiagnosticsSetCount(), "nothing should be validated until typing pauses");
        }
    }

    @Test
    void validatesOnceTypingPausesAndPublishesWhatItFound() throws Exception {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        var editor = new FakeEditorComponent();
        editor.setText(MISTYPED);

        var published = new AtomicReference<List<Diagnostic>>(List.of());
        var arrived = new CountDownLatch(1);

        try (var coordinator = new LiveLintCoordinator(new LintSource(), executor, 40, diagnostics -> {
            published.set(diagnostics);
            arrived.countDown();
        })) {
            coordinator.requestLint(editor, this::context);

            assertTrue(arrived.await(10, SECONDS), "the validation pass never published anything");
            assertTrue(published.get().stream().anyMatch(d -> d.code().equals("lint.unknown-instruction-suggestion")));
            assertEquals(published.get(), editor.getDiagnostics(), "the editor and the panel show the same problems");
        }
    }

    @Test
    void clearingAnEditorDropsItsMarksAndItsProblems() throws Exception {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        var editor = new FakeEditorComponent();
        editor.setText(MISTYPED);

        var published = new AtomicReference<List<Diagnostic>>(null);

        try (var coordinator = new LiveLintCoordinator(new LintSource(), executor, 600_000, published::set)) {
            coordinator.requestLint(editor, this::context);
            coordinator.clear(editor);

            assertTrue(editor.getDiagnostics().isEmpty());
            assertEquals(List.of(), published.get());
        }
    }

    @Test
    void publishesNothingForAnEmptyDocument() throws Exception {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        var editor = new FakeEditorComponent();
        editor.setText("");

        var arrived = new CountDownLatch(1);
        var published = new AtomicReference<List<Diagnostic>>(null);

        try (var coordinator = new LiveLintCoordinator(new LintSource(), executor, 40, diagnostics -> {
            published.set(diagnostics);
            arrived.countDown();
        })) {
            coordinator.requestLint(editor, this::context);

            assertTrue(arrived.await(10, SECONDS));
            assertEquals(List.of(), published.get());
        }
    }

    /** One pending pass was shared by every document, so editing B right after A left A's marks out of date. */
    @Test
    void editingAnotherDocumentDoesNotCancelTheFirstOnesValidation() throws Exception {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        var first = new FakeEditorComponent();
        first.setText(MISTYPED);
        var second = new FakeEditorComponent();
        second.setText(MISTYPED);
        var arrived = new CountDownLatch(2);

        try (var coordinator = new LiveLintCoordinator(new LintSource(), executor, 40, diagnostics -> arrived.countDown())) {
            coordinator.requestLint(first, this::context);
            coordinator.requestLint(second, this::context);

            assertTrue(arrived.await(10, SECONDS), "both documents must be validated");
            assertFalse(first.getDiagnostics().isEmpty());
            assertFalse(second.getDiagnostics().isEmpty());
        }
    }
}
