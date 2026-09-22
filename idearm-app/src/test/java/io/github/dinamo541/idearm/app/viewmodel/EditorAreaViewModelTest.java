package io.github.dinamo541.idearm.app.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dinamo541.idearm.app.editor.FakeEditorComponent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EditorAreaViewModelTest {

    @TempDir
    Path tempDir;

    @Test
    void openFileAddsDocumentAndSetsActive() throws IOException {
        Path file1 = tempDir.resolve("MAIN.ASM");
        Files.writeString(file1, "MOV AX, 4C00h\nINT 21h");

        var viewModel = new EditorAreaViewModel(FakeEditorComponent::new);
        EditorDocumentViewModel doc1 = viewModel.openFile(file1);

        assertNotNull(doc1);
        assertEquals(1, viewModel.getDocuments().size());
        assertEquals(doc1, viewModel.getActiveDocument());
        assertEquals("MOV AX, 4C00h\nINT 21h", doc1.getEditor().getText());
    }

    @Test
    void openingSameFileTwiceDoesNotDuplicateTab() throws IOException {
        Path file1 = tempDir.resolve("MAIN.ASM");
        Files.writeString(file1, "MOV AX, 0");

        var viewModel = new EditorAreaViewModel(FakeEditorComponent::new);
        EditorDocumentViewModel docA = viewModel.openFile(file1);
        EditorDocumentViewModel docB = viewModel.openFile(file1);

        assertEquals(docA, docB);
        assertEquals(1, viewModel.getDocuments().size());
    }

    @Test
    void closingDocumentUpdatesActiveTab() throws IOException {
        Path file1 = tempDir.resolve("MAIN.ASM");
        Path file2 = tempDir.resolve("UTILS.ASM");
        Files.writeString(file1, "code 1");
        Files.writeString(file2, "code 2");

        var viewModel = new EditorAreaViewModel(FakeEditorComponent::new);
        EditorDocumentViewModel doc1 = viewModel.openFile(file1);
        EditorDocumentViewModel doc2 = viewModel.openFile(file2);

        assertEquals(2, viewModel.getDocuments().size());
        assertEquals(doc2, viewModel.getActiveDocument());

        viewModel.closeDocument(doc2);
        assertEquals(1, viewModel.getDocuments().size());
        assertEquals(doc1, viewModel.getActiveDocument());
    }

    @Test
    void savingDocumentWritesToDiskAndClearsDirty() throws IOException {
        Path file1 = tempDir.resolve("MAIN.ASM");
        Files.writeString(file1, "original");

        var viewModel = new EditorAreaViewModel(FakeEditorComponent::new);
        EditorDocumentViewModel doc = viewModel.openFile(file1);

        doc.getEditor().setText("modified content");
        doc.getEditor().modifiedProperty().set(true);
        assertTrue(doc.isModified());

        viewModel.saveActive();
        assertFalse(doc.isModified());
        assertEquals("modified content", Files.readString(file1));
    }

    /** An "ANSI" file with accents in its comments could not be opened at all when every file was read as UTF-8. */
    @Test
    void anAnsiFileOpensAndIsSavedBackInItsOwnFormat() throws IOException {
        var ansi = java.nio.charset.Charset.forName("windows-1252");
        Path file = tempDir.resolve("main.asm");
        byte[] original = "; Cálculo del año\r\nmov ax, 1\r\n".getBytes(ansi);
        Files.write(file, original);

        var viewModel = new EditorAreaViewModel(FakeEditorComponent::new);
        EditorDocumentViewModel doc = viewModel.openFile(file);

        assertEquals("; Cálculo del año\nmov ax, 1\n", doc.getEditor().getText());
        assertEquals("windows-1252 · CRLF", WorkbenchViewModel.describe(doc.getFormat()));

        doc.getEditor().modifiedProperty().set(true);
        viewModel.saveActive();
        org.junit.jupiter.api.Assertions.assertArrayEquals(original, Files.readAllBytes(file),
                "An unchanged file keeps its bytes, line endings included");

        doc.getEditor().setText("; Cálculo del año\nmov ax, 2\n");
        doc.getEditor().modifiedProperty().set(true);
        viewModel.saveActive();
        assertEquals("; Cálculo del año\r\nmov ax, 2\r\n", new String(Files.readAllBytes(file), ansi));
    }

    @Test
    void aCharacterTheFileEncodingLacksSwitchesItToUtf8InsteadOfLosingIt() throws IOException {
        Path file = tempDir.resolve("main.asm");
        Files.write(file, "; año\r\n".getBytes(java.nio.charset.Charset.forName("windows-1252")));
        var viewModel = new EditorAreaViewModel(FakeEditorComponent::new);
        EditorDocumentViewModel doc = viewModel.openFile(file);

        doc.getEditor().setText("; año π\n");
        doc.getEditor().modifiedProperty().set(true);
        viewModel.saveActive();

        assertEquals("; año π\r\n", Files.readString(file, java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(java.nio.charset.StandardCharsets.UTF_8, doc.getFormat().charset());
        try (var leftovers = Files.list(tempDir)) {
            assertEquals(1, leftovers.count(), "The temporary file of the atomic save is gone");
        }
    }
}
