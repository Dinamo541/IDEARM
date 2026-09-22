package io.github.dinamo541.idearm.app.viewmodel;

import io.github.dinamo541.idearm.app.editor.EditorComponent;
import io.github.dinamo541.idearm.app.editor.RichTextFxEditorComponent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * ViewModel managing the collection of open documents and the active editor tab.
 */
public final class EditorAreaViewModel {

    private final ObservableList<EditorDocumentViewModel> documents = FXCollections.observableArrayList();
    private final ObjectProperty<EditorDocumentViewModel> activeDocument = new SimpleObjectProperty<>();
    private final Supplier<EditorComponent> editorFactory;
    private Consumer<EditorComponent> editorConfigurer;
    private Consumer<Path> onFileOpened = path -> {};

    public void setOnFileOpened(Consumer<Path> listener) { onFileOpened = Objects.requireNonNull(listener); }


    public EditorAreaViewModel() {
        this(RichTextFxEditorComponent::new);
    }

    public EditorAreaViewModel(Supplier<EditorComponent> editorFactory) {
        this.editorFactory = Objects.requireNonNull(editorFactory, "editorFactory cannot be null");
    }

    public void setEditorConfigurer(Consumer<EditorComponent> configurer) {
        this.editorConfigurer = configurer;
    }

    public ObservableList<EditorDocumentViewModel> getDocuments() {
        return documents;
    }

    public ObjectProperty<EditorDocumentViewModel> activeDocumentProperty() {
        return activeDocument;
    }

    public EditorDocumentViewModel getActiveDocument() {
        return activeDocument.get();
    }

    /**
     * Opens a file in the editor area. If already open, brings that tab to the front.
     *
     * @param file the path of the file to open
     * @return the opened or existing EditorDocumentViewModel
     */
    public EditorDocumentViewModel openFile(Path file) throws IOException {
        Path normalized = file.toAbsolutePath().normalize();

        for (EditorDocumentViewModel doc : documents) {
            if (doc.getFilePath().equals(normalized)) {
                activeDocument.set(doc);
                if (Files.isRegularFile(normalized)) onFileOpened.accept(normalized);
                return doc;
            }
        }

        EditorComponent component = editorFactory.get();
        if (editorConfigurer != null) {
            editorConfigurer.accept(component);
        }

        // Sources come from many editors: UTF-8, or Windows-1252 from "ANSI" editors, CRLF or LF. Reading them as
        // strict UTF-8 made a file with accents in its comments impossible to open.
        var format = io.github.dinamo541.idearm.domain.files.TextDecoding.TextFormat.DEFAULT;
        if (Files.exists(normalized)) {
            var decoded = io.github.dinamo541.idearm.domain.files.TextDecoding.decode(Files.readAllBytes(normalized));
            component.setText(decoded.text());
            format = decoded.format();
        } else {
            component.setText("");
        }

        EditorDocumentViewModel newDoc = new EditorDocumentViewModel(normalized, component, format);
        documents.add(newDoc);
        activeDocument.set(newDoc);
        if (Files.isRegularFile(normalized)) onFileOpened.accept(normalized);
        return newDoc;
    }

    /**
     * Closes an open document tab.
     */
    public void closeDocument(EditorDocumentViewModel doc) {
        int index = documents.indexOf(doc);
        if (index >= 0) {
            documents.remove(index);
            if (activeDocument.get() == doc) {
                if (!documents.isEmpty()) {
                    int nextIndex = Math.min(index, documents.size() - 1);
                    activeDocument.set(documents.get(nextIndex));
                } else {
                    activeDocument.set(null);
                }
            }
        }
    }

    /**
     * Keeps tabs attached to their files after a rename in the explorer, including files inside a renamed folder.
     */
    public void pathRenamed(Path from, Path to) {
        Path source = from.toAbsolutePath().normalize();
        Path target = to.toAbsolutePath().normalize();
        for (EditorDocumentViewModel doc : documents) {
            Path file = doc.getFilePath();
            if (file.equals(source)) {
                doc.filePathProperty().set(target);
            } else if (file.startsWith(source)) {
                doc.filePathProperty().set(target.resolve(source.relativize(file)));
            }
        }
    }

    /**
     * Closes the tabs of a deleted file or folder. A tab with unsaved changes stays open, as in VS Code: saving
     * it writes the file again, so nothing typed is lost.
     */
    public void pathDeleted(Path deleted) {
        Path removed = deleted.toAbsolutePath().normalize();
        for (EditorDocumentViewModel doc : List.copyOf(documents)) {
            if (doc.getFilePath().startsWith(removed) && !doc.isModified()) {
                closeDocument(doc);
            }
        }
    }

    /**
     * Saves the currently active document.
     */
    public void saveActive() throws IOException {
        EditorDocumentViewModel doc = activeDocument.get();
        if (doc != null) {
            doc.save();
        }
    }

    /**
     * Saves all open documents that have unsaved changes.
     */
    public void saveAll() throws IOException {
        for (EditorDocumentViewModel doc : documents) {
            if (doc.isModified()) {
                doc.save();
            }
        }
    }
}
