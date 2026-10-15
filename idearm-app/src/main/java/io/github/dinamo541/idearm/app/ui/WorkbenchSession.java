package io.github.dinamo541.idearm.app.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

/**
 * Manages workbench session persistence across application restarts:
 * remembers the last active project, open editor tabs, caret positions,
 * UI layout (sidebar, bottom panel, minimap), theme, and language.
 */
public final class WorkbenchSession {

    private WorkbenchSession() {}

    /**
     * Represents the state of an open file in the editor.
     */
    public record FileState(Path path, int line, int column) {
        public FileState {
            if (line < 1) line = 1;
            if (column < 1) column = 1;
        }
    }

    /**
     * Represents the saved workspace state for a specific project.
     */
    public record ProjectWorkspaceState(boolean exists, List<FileState> openFiles, Path activeFile) {}

    /**
     * Represents the global workbench session state.
     */
    public record WorkbenchSessionState(
            Path lastProject,
            List<FileState> openFiles,
            Path activeFile,
            boolean sidebarVisible,
            double sidebarPosition,
            boolean panelVisible,
            double panelPosition,
            String activePanelTab,
            String theme,
            String language,
            boolean minimapVisible
    ) {
        public static WorkbenchSessionState empty() {
            return new WorkbenchSessionState(
                    null, List.of(), null, true, 0.20, true, 0.73, "OUTPUT", "dark", "en", true
            );
        }
    }

    /**
     * Attaches a listener to the Stage so the session is saved when the window is hidden/closed.
     */
    public static void install(Stage stage, Runnable onHiding) {
        if (stage == null || onHiding == null) return;
        stage.addEventHandler(WindowEvent.WINDOW_HIDING, e -> onHiding.run());
    }

    /**
     * Reads the global session state from the specified file.
     */
    public static WorkbenchSessionState read(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return WorkbenchSessionState.empty();
        }
        try (var in = Files.newInputStream(file)) {
            var props = new Properties();
            props.load(in);

            Path lastProject = null;
            String projStr = props.getProperty("lastProject");
            if (projStr != null && !projStr.isBlank()) {
                lastProject = Path.of(projStr).toAbsolutePath().normalize();
            }

            boolean sidebarVisible = Boolean.parseBoolean(props.getProperty("sidebarVisible", "true"));
            double sidebarPosition = parseDouble(props.getProperty("sidebarPosition"), 0.20);
            if (sidebarPosition < 0.08 || sidebarPosition > 0.50) {
                sidebarPosition = 0.20;
            }
            boolean panelVisible = Boolean.parseBoolean(props.getProperty("panelVisible", "true"));
            double panelPosition = parseDouble(props.getProperty("panelPosition"), 0.73);
            if (panelPosition < 0.15 || panelPosition > 0.85) {
                panelPosition = 0.73;
            }
            String activePanelTab = props.getProperty("activePanelTab", "OUTPUT");
            String theme = props.getProperty("theme", "dark");
            String language = props.getProperty("language", "en");
            boolean minimapVisible = Boolean.parseBoolean(props.getProperty("minimapVisible", "true"));

            Path activeFile = null;
            String activeStr = props.getProperty("activeFile");
            if (activeStr != null && !activeStr.isBlank()) {
                activeFile = Path.of(activeStr).toAbsolutePath().normalize();
            }

            int count = parseInt(props.getProperty("openFiles.count"), 0);
            List<FileState> openFiles = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String pathStr = props.getProperty("openFile." + i + ".path");
                if (pathStr != null && !pathStr.isBlank()) {
                    int line = parseInt(props.getProperty("openFile." + i + ".line"), 1);
                    int col = parseInt(props.getProperty("openFile." + i + ".col"), 1);
                    openFiles.add(new FileState(Path.of(pathStr).toAbsolutePath().normalize(), line, col));
                }
            }

            return new WorkbenchSessionState(
                    lastProject,
                    List.copyOf(openFiles),
                    activeFile,
                    sidebarVisible,
                    sidebarPosition,
                    panelVisible,
                    panelPosition,
                    activePanelTab,
                    theme,
                    language,
                    minimapVisible
            );
        } catch (Exception ignored) {
            return WorkbenchSessionState.empty();
        }
    }

    /**
     * Atomically writes the global session state to the specified file.
     */
    public static void write(Path file, WorkbenchSessionState state) {
        if (file == null || state == null) return;
        Path temporary = null;
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
                temporary = Files.createTempFile(parent, "session-", ".tmp");
            } else {
                temporary = Files.createTempFile("session-", ".tmp");
            }
            var props = new Properties();
            if (state.lastProject() != null) {
                props.setProperty("lastProject", state.lastProject().toAbsolutePath().normalize().toString());
            }
            props.setProperty("sidebarVisible", Boolean.toString(state.sidebarVisible()));
            props.setProperty("sidebarPosition", Double.toString(state.sidebarPosition()));
            props.setProperty("panelVisible", Boolean.toString(state.panelVisible()));
            props.setProperty("panelPosition", Double.toString(state.panelPosition()));
            if (state.activePanelTab() != null) {
                props.setProperty("activePanelTab", state.activePanelTab());
            }
            if (state.theme() != null) {
                props.setProperty("theme", state.theme());
            }
            if (state.language() != null) {
                props.setProperty("language", state.language());
            }
            props.setProperty("minimapVisible", Boolean.toString(state.minimapVisible()));
            if (state.activeFile() != null) {
                props.setProperty("activeFile", state.activeFile().toAbsolutePath().normalize().toString());
            }
            props.setProperty("openFiles.count", Integer.toString(state.openFiles().size()));
            for (int i = 0; i < state.openFiles().size(); i++) {
                FileState fs = state.openFiles().get(i);
                props.setProperty("openFile." + i + ".path", fs.path().toAbsolutePath().normalize().toString());
                props.setProperty("openFile." + i + ".line", Integer.toString(fs.line()));
                props.setProperty("openFile." + i + ".col", Integer.toString(fs.column()));
            }

            try (var out = Files.newOutputStream(temporary)) {
                props.store(out, "IDEARM session state");
            }
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) {
            System.err.println("Could not save session state: " + failure.getMessage());
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) {}
            }
        }
    }

    /**
     * Reads the workspace state for a specific project from .idearm/workspace.properties.
     */
    public static ProjectWorkspaceState readProjectWorkspace(Path projectRoot) {
        if (projectRoot == null || !Files.isDirectory(projectRoot)) {
            return new ProjectWorkspaceState(false, List.of(), null);
        }
        Path file = projectRoot.resolve(".idearm").resolve("workspace.properties");
        if (!Files.isRegularFile(file)) {
            return new ProjectWorkspaceState(false, List.of(), null);
        }
        try (var in = Files.newInputStream(file)) {
            var props = new Properties();
            props.load(in);

            Path activeFile = null;
            String activeStr = props.getProperty("activeFile");
            if (activeStr != null && !activeStr.isBlank()) {
                Path p = Path.of(activeStr);
                activeFile = p.isAbsolute() ? p.normalize() : projectRoot.resolve(p).toAbsolutePath().normalize();
            }

            int count = parseInt(props.getProperty("openFiles.count"), 0);
            List<FileState> list = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String pathStr = props.getProperty("openFile." + i + ".path");
                if (pathStr != null && !pathStr.isBlank()) {
                    Path p = Path.of(pathStr);
                    Path absPath = p.isAbsolute() ? p.normalize() : projectRoot.resolve(p).toAbsolutePath().normalize();
                    int line = parseInt(props.getProperty("openFile." + i + ".line"), 1);
                    int col = parseInt(props.getProperty("openFile." + i + ".col"), 1);
                    list.add(new FileState(absPath, line, col));
                }
            }
            return new ProjectWorkspaceState(true, List.copyOf(list), activeFile);
        } catch (Exception ignored) {
            return new ProjectWorkspaceState(false, List.of(), null);
        }
    }

    /**
     * Atomically writes the workspace state for a specific project to .idearm/workspace.properties.
     */
    public static void writeProjectWorkspace(Path projectRoot, List<FileState> openFiles, Path activeFile) {
        if (projectRoot == null || !Files.isDirectory(projectRoot)) return;
        Path idearmDir = projectRoot.resolve(".idearm");
        Path file = idearmDir.resolve("workspace.properties");
        Path temporary = null;
        try {
            Files.createDirectories(idearmDir);
            temporary = Files.createTempFile(idearmDir, "workspace-", ".tmp");
            var props = new Properties();
            if (activeFile != null) {
                String pathStr = activeFile.isAbsolute() && activeFile.startsWith(projectRoot)
                        ? projectRoot.relativize(activeFile).toString().replace('\\', '/')
                        : activeFile.toString();
                props.setProperty("activeFile", pathStr);
            }
            props.setProperty("openFiles.count", Integer.toString(openFiles.size()));
            for (int i = 0; i < openFiles.size(); i++) {
                FileState fs = openFiles.get(i);
                Path p = fs.path();
                String pathStr = p.isAbsolute() && p.startsWith(projectRoot)
                        ? projectRoot.relativize(p).toString().replace('\\', '/')
                        : p.toString();
                props.setProperty("openFile." + i + ".path", pathStr);
                props.setProperty("openFile." + i + ".line", Integer.toString(fs.line()));
                props.setProperty("openFile." + i + ".col", Integer.toString(fs.column()));
            }
            try (var out = Files.newOutputStream(temporary)) {
                props.store(out, "IDEARM project workspace");
            }
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) {}
            }
        }
    }

    private static double parseDouble(String value, double defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) ? parsed : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static int parseInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
