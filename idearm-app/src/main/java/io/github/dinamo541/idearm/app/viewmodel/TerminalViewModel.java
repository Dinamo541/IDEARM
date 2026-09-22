package io.github.dinamo541.idearm.app.viewmodel;

import io.github.dinamo541.idearm.domain.port.TerminalRunner;
import io.github.dinamo541.idearm.domain.port.TerminalSession;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel managing the interactive host terminal session and its command history.
 */
public final class TerminalViewModel {

    private final TerminalRunner terminalRunner;
    private final StringProperty terminalText = new SimpleStringProperty("");
    private final BooleanProperty running = new SimpleBooleanProperty(false);
    private final StringProperty workingDirectory = new SimpleStringProperty("");

    private final List<String> commandHistory = new ArrayList<>();
    private int historyIndex = -1;

    private TerminalSession currentSession;
    private Path currentWorkingDir;

    public TerminalViewModel(TerminalRunner terminalRunner) {
        this.terminalRunner = terminalRunner;
    }

    public StringProperty terminalTextProperty() {
        return terminalText;
    }

    public String getTerminalText() {
        return terminalText.get();
    }

    public BooleanProperty runningProperty() {
        return running;
    }

    public boolean isRunning() {
        return running.get();
    }

    public StringProperty workingDirectoryProperty() {
        return workingDirectory;
    }

    public String getWorkingDirectory() {
        return workingDirectory.get();
    }

    public void startSession(Path cwd) {
        stop();
        this.currentWorkingDir = cwd != null ? cwd : Path.of(System.getProperty("user.dir", "."));
        this.workingDirectory.set(currentWorkingDir.toAbsolutePath().toString());

        if (terminalRunner == null) {
            appendOutput("Terminal runner not available.\n");
            return;
        }

        try {
            this.currentSession = terminalRunner.startSession(currentWorkingDir, this::appendOutput);
            this.running.set(true);
            currentSession.onExit().thenAccept(code -> FxDispatch.run(() -> {
                running.set(false);
                appendOutput("\n[Process exited with code " + code + "]\n");
            }));
        } catch (Exception e) {
            running.set(false);
            appendOutput("Failed to launch terminal: " + e.getMessage() + "\n");
        }
    }

    public void sendCommand(String command) {
        if (command == null || command.isBlank()) return;
        commandHistory.add(command);
        historyIndex = commandHistory.size();

        if (currentSession != null && currentSession.isAlive()) {
            currentSession.sendInput(command);
        } else {
            appendOutput("No active terminal session.\n");
        }
    }

    public String getPreviousHistory() {
        if (commandHistory.isEmpty()) return "";
        if (historyIndex > 0) {
            historyIndex--;
        }
        return commandHistory.get(historyIndex);
    }

    public String getNextHistory() {
        if (commandHistory.isEmpty()) return "";
        if (historyIndex < commandHistory.size() - 1) {
            historyIndex++;
            return commandHistory.get(historyIndex);
        } else {
            historyIndex = commandHistory.size();
            return "";
        }
    }

    public void restart() {
        startSession(currentWorkingDir);
    }

    public void clear() {
        FxDispatch.run(() -> terminalText.set(""));
    }

    public void stop() {
        if (currentSession != null) {
            currentSession.terminate();
            currentSession = null;
        }
        running.set(false);
    }

    private void appendOutput(String chunk) {
        if (chunk == null) return;
        FxDispatch.run(() -> {
            String current = terminalText.get();
            // Limit buffer size to ~100k characters to prevent unbounded memory growth
            if (current.length() > 100_000) {
                current = current.substring(current.length() - 50_000);
            }
            terminalText.set(current + chunk);
        });
    }
}
