package io.github.dinamo541.idearm.app.viewmodel;

import io.github.dinamo541.idearm.app.editor.EditorComponent;
import io.github.dinamo541.idearm.domain.files.TextDecoding;
import io.github.dinamo541.idearm.domain.files.TextDecoding.TextFormat;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * ViewModel for a single open file in the editor area.
 *
 * <p>The document remembers how its file was stored (charset, byte order mark, line separator) and writes it back
 * the same way, so opening and saving an ANSI file with accents in its comments changes nothing but the edits.
 */
public final class EditorDocumentViewModel {

    private final ObjectProperty<Path> filePath = new SimpleObjectProperty<>();
    private final ObjectProperty<TextFormat> format = new SimpleObjectProperty<>(TextFormat.DEFAULT);
    private final EditorComponent editor;
    private final StringBinding titleBinding;

    public EditorDocumentViewModel(Path file, EditorComponent editor) {
        this(file, editor, TextFormat.DEFAULT);
    }

    public EditorDocumentViewModel(Path file, EditorComponent editor, TextFormat format) {
        this.filePath.set(Objects.requireNonNull(file, "file cannot be null"));
        this.editor = Objects.requireNonNull(editor, "editor cannot be null");
        this.format.set(Objects.requireNonNull(format, "format cannot be null"));

        this.titleBinding = Bindings.createStringBinding(() -> {
            Path p = filePath.get();
            String name = p != null ? p.getFileName().toString() : "Untitled";
            return editor.modifiedProperty().get() ? name + " *" : name;
        }, filePath, editor.modifiedProperty());
    }

    public Path getFilePath() {
        return filePath.get();
    }

    public ObjectProperty<Path> filePathProperty() {
        return filePath;
    }

    public EditorComponent getEditor() {
        return editor;
    }

    public String getTitle() {
        return titleBinding.get();
    }

    public StringBinding titleProperty() {
        return titleBinding;
    }

    public BooleanProperty modifiedProperty() {
        return editor.modifiedProperty();
    }

    public boolean isModified() {
        return editor.modifiedProperty().get();
    }

    public ReadOnlyIntegerProperty caretLineProperty() {
        return editor.caretLineProperty();
    }

    public ReadOnlyIntegerProperty caretColumnProperty() {
        return editor.caretColumnProperty();
    }

    /** How the file is stored on disk; it changes to UTF-8 when an edit adds a character its charset lacks. */
    public ObjectProperty<TextFormat> formatProperty() {
        return format;
    }

    public TextFormat getFormat() {
        return format.get();
    }

    /**
     * Saves the editor buffer in the file's own format.
     *
     * <p>The text is written to a temporary file next to the original and moved over it, so a crash or a full disk
     * never leaves the student's source half written. A character the file's single-byte charset cannot hold (a
     * Greek letter in a Windows-1252 file) switches the file to UTF-8 instead of being lost.
     */
    public void save() throws IOException {
        Path path = filePath.get();
        if (path == null) {
            return;
        }
        // A file deleted from the explorer while its tab had unsaved changes is written back, folders included.
        Path folder = path.toAbsolutePath().getParent();
        if (folder != null) {
            Files.createDirectories(folder);
        }
        String text = editor.getText();
        TextFormat target = format.get();
        byte[] bytes;
        try {
            bytes = TextDecoding.encode(text, target);
        } catch (CharacterCodingException unmappable) {
            target = target.withCharset(StandardCharsets.UTF_8);
            bytes = TextDecoding.encode(text, target);
        }
        Path temporary = Files.createTempFile(folder, "." + path.getFileName() + "-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException notAtomic) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        format.set(target);
        editor.modifiedProperty().set(false);
    }

    /** Reloads file contents from disk. */
    public void reload() throws IOException {
        Path path = filePath.get();
        if (path != null && Files.exists(path)) {
            var decoded = TextDecoding.decode(Files.readAllBytes(path));
            format.set(decoded.format());
            editor.setText(decoded.text());
        }
    }
}
