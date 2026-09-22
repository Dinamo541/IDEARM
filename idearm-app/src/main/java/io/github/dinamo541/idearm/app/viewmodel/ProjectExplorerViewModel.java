package io.github.dinamo541.idearm.app.viewmodel;

import io.github.dinamo541.idearm.app.i18n.Message;
import io.github.dinamo541.idearm.app.i18n.Problem;
import io.github.dinamo541.idearm.application.ManageProjectFiles;
import io.github.dinamo541.idearm.domain.files.EntryNames;
import io.github.dinamo541.idearm.domain.files.EntryNames.Kind;
import io.github.dinamo541.idearm.domain.port.ProjectFiles.Deletion;
import io.github.dinamo541.idearm.domain.port.ProjectFiles.Entry;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * The project explorer: what each folder shows and the VS Code style file operations (new file, new folder,
 * rename, delete).
 *
 * <p>The tree view asks for the children of a folder when it expands, and reloads what is expanded whenever
 * {@link #refreshCountProperty()} changes: after an operation here, after a build, or when files change on disk.
 * Other parts of the workbench learn about created, renamed and deleted entries through {@link Listener}, so an
 * open editor follows its file.
 */
public final class ProjectExplorerViewModel {

    /** Told about every change made from the explorer. */
    public interface Listener {
        default void created(Path entry, boolean directory) {
        }

        default void renamed(Path from, Path to) {
        }

        default void deleted(Path entry) {
        }
    }

    /** The outcome of checking a typed name, ready for the view. */
    public record NameFeedback(EntryNames.Severity severity, Message message) {
        public static final NameFeedback NONE = new NameFeedback(EntryNames.Severity.OK, Message.EMPTY);

        public boolean blocking() {
            return severity == EntryNames.Severity.ERROR;
        }

        public boolean visible() {
            return severity != EntryNames.Severity.OK;
        }
    }

    private final ObjectProperty<Path> projectRoot = new SimpleObjectProperty<>();
    private final ObjectProperty<Path> selectedFile = new SimpleObjectProperty<>();
    private final IntegerProperty refreshCount = new SimpleIntegerProperty();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private ManageProjectFiles files;
    private BooleanSupplier dosTarget = () -> true;
    private AutoCloseable watch;

    /**
     * Gives the explorer its file operations.
     *
     * @param dosTarget whether the open project builds for DOS, which decides whether 8.3 warnings are shown
     */
    public void connect(ManageProjectFiles files, BooleanSupplier dosTarget) {
        this.files = Objects.requireNonNull(files, "files");
        this.dosTarget = Objects.requireNonNull(dosTarget, "dosTarget");
        restartWatch();
    }

    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public ObjectProperty<Path> projectRootProperty() {
        return projectRoot;
    }

    public Path getProjectRoot() {
        return projectRoot.get();
    }

    public ObjectProperty<Path> selectedFileProperty() {
        return selectedFile;
    }

    public Path getSelectedFile() {
        return selectedFile.get();
    }

    public void setSelectedFile(Path file) {
        selectedFile.set(file);
    }

    /** Changes whenever the tree should reload what it shows. */
    public ReadOnlyIntegerProperty refreshCountProperty() {
        return refreshCount;
    }

    /** Sets the project folder and starts following changes on disk. */
    public void openProject(Path root) {
        Path normalized = root == null ? null : root.toAbsolutePath().normalize();
        FxDispatch.run(() -> {
            projectRoot.set(normalized);
            selectedFile.set(null);
            refreshCount.set(refreshCount.get() + 1);
        });
        restartWatch(normalized);
    }

    /** Asks the tree to reload; safe from any thread. */
    public void refresh() {
        FxDispatch.run(() -> refreshCount.set(refreshCount.get() + 1));
    }

    /** Stops following the file system; the workbench calls it when the window closes. */
    public void dispose() {
        stopWatch();
    }

    /** The children of a folder, folders first; empty until a project is open. */
    public List<Entry> children(Path directory) {
        Path root = projectRoot.get();
        if (files == null || root == null || directory == null) {
            return List.of();
        }
        try {
            return files.children(root, directory);
        } catch (RuntimeException unreadable) {
            return List.of();
        }
    }

    /** Where a new entry goes for the current selection: into a selected folder, next to a selected file. */
    public Path targetFolder(Path selected, boolean selectedIsDirectory) {
        Path root = projectRoot.get();
        if (selected == null || root == null || !selected.startsWith(root)) {
            return root;
        }
        return selectedIsDirectory ? selected : selected.getParent();
    }

    public boolean canEdit() {
        return files != null && projectRoot.get() != null;
    }

    public boolean trashAvailable() {
        return files != null && files.trashAvailable();
    }

    /**
     * Checks a name while it is typed.
     *
     * @param renamed the entry being renamed, or {@code null} when creating
     */
    public NameFeedback check(Path parent, String typed, Kind kind, Path renamed) {
        if (!canEdit()) {
            return NameFeedback.NONE;
        }
        try {
            EntryNames.Check check = files.check(projectRoot.get(), parent, typed, kind, dosTarget.getAsBoolean(),
                    renamed);
            if (check.severity() == EntryNames.Severity.OK) {
                return NameFeedback.NONE;
            }
            return new NameFeedback(check.severity(), new Message(check.code(), check.arguments()));
        } catch (RuntimeException failure) {
            return new NameFeedback(EntryNames.Severity.ERROR, Message.of("explorer.error", reason(failure)));
        }
    }

    /** Creates a file (or a folder, when the name ends with a slash) and reports it. */
    public Path createFile(Path parent, String typed) {
        Path created = requireFiles().createFile(projectRoot.get(), parent, typed, dosTarget.getAsBoolean());
        boolean directory = typed.endsWith("/") || typed.endsWith("\\");
        announce(listener -> listener.created(created, directory));
        return created;
    }

    public Path createFolder(Path parent, String typed) {
        Path created = requireFiles().createFolder(projectRoot.get(), parent, typed, dosTarget.getAsBoolean());
        announce(listener -> listener.created(created, true));
        return created;
    }

    public Path rename(Path entry, String typed) {
        Path renamed = requireFiles().rename(projectRoot.get(), entry, typed, dosTarget.getAsBoolean());
        if (!renamed.equals(entry) || !renamed.getFileName().toString().equals(entry.getFileName().toString())) {
            announce(listener -> listener.renamed(entry, renamed));
        }
        return renamed;
    }

    public Deletion delete(Path entry, boolean permanently) {
        Deletion deletion = requireFiles().delete(projectRoot.get(), entry, permanently);
        if (entry.equals(selectedFile.get())) {
            selectedFile.set(null);
        }
        announce(listener -> listener.deleted(entry));
        return deletion;
    }

    /** A project-relative, slash-separated path, as VS Code's "Copy Relative Path" gives it. */
    public String relativePath(Path entry) {
        Path root = projectRoot.get();
        if (root == null || entry == null || !entry.startsWith(root)) {
            return entry == null ? "" : entry.toString();
        }
        return root.relativize(entry).toString().replace('\\', '/');
    }

    /** Explains a failed operation to the user. */
    public static Message failureMessage(RuntimeException failure) {
        return Message.of("explorer.error", reason(failure));
    }

    private void announce(java.util.function.Consumer<Listener> event) {
        for (Listener listener : new ArrayList<>(listeners)) {
            event.accept(listener);
        }
        refresh();
    }

    private ManageProjectFiles requireFiles() {
        if (!canEdit()) {
            throw new IllegalStateException("No project is open.");
        }
        return files;
    }

    private void restartWatch() {
        restartWatch(projectRoot.get());
    }

    private synchronized void restartWatch(Path root) {
        stopWatch();
        if (files == null || root == null) {
            return;
        }
        try {
            watch = files.watch(root, this::refresh);
        } catch (RuntimeException unavailable) {
            // Without a watcher the tree still refreshes after IDE operations and on demand.
            watch = null;
        }
    }

    private synchronized void stopWatch() {
        if (watch != null) {
            try {
                watch.close();
            } catch (Exception ignored) {
                // The watcher is being discarded.
            }
            watch = null;
        }
    }

    private static Problem reason(RuntimeException failure) {
        return Problem.of(failure);
    }
}
