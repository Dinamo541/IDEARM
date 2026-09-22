package io.github.dinamo541.idearm.app.viewmodel;

import javafx.application.Platform;

/**
 * Sends property updates to the JavaFX thread.
 *
 * <p>View models are written from build and run tasks on background threads, and observable state may only be
 * touched on the JavaFX thread. Outside a running toolkit (unit tests) the action runs where it is called, so a
 * view model stays testable headless.
 */
final class FxDispatch {

    private FxDispatch() {
    }

    static void run(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        try {
            Platform.runLater(action);
        } catch (IllegalStateException toolkitNotRunning) {
            action.run();
        }
    }
}
