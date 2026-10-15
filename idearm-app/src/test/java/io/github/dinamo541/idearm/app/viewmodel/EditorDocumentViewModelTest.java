package io.github.dinamo541.idearm.app.viewmodel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.dinamo541.idearm.app.editor.FakeEditorComponent;
import io.github.dinamo541.idearm.domain.files.TextDecoding;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EditorDocumentViewModelTest {

    @TempDir
    Path tempDir;

    @Test
    void saveWithEncodingExplicitlyWritesTargetEncoding() throws IOException {
        Path file = tempDir.resolve("MAIN.ASM");
        Files.writeString(file, "msg DB 'Hola año$'\n", StandardCharsets.UTF_8);

        var editor = new FakeEditorComponent();
        editor.setText("msg DB 'Hola año$'\n");
        var doc = new EditorDocumentViewModel(file, editor);

        doc.saveWithEncoding(TextDecoding.CP437);

        assertEquals(TextDecoding.CP437, doc.getFormat().charset());
        assertFalse(doc.isModified());

        byte[] savedBytes = Files.readAllBytes(file);
        // In CP437, 'ñ' is 0xA4
        byte[] expected = "msg DB 'Hola a\u00F1o$'\n".getBytes(TextDecoding.CP437);
        assertArrayEquals(expected, savedBytes);
    }

    @Test
    void reopenWithEncodingDecodesWithSpecifiedCharset() throws IOException {
        Path file = tempDir.resolve("DOS.ASM");
        // Write raw CP437 bytes: ¿ (0xA8), a, ñ (0xA4), o, ?
        byte[] cp437Bytes = {(byte) 0xA8, 'a', (byte) 0xA4, 'o', '?'};
        Files.write(file, cp437Bytes);

        var editor = new FakeEditorComponent();
        var doc = new EditorDocumentViewModel(file, editor);

        doc.reopenWithEncoding(TextDecoding.CP437);

        assertEquals("¿año?", editor.getText());
        assertEquals(TextDecoding.CP437, doc.getFormat().charset());
        assertFalse(doc.isModified());

        // Reopening as Windows-1252 interprets 0xA8 as ¨ and 0xA4 as ¤
        doc.reopenWithEncoding(TextDecoding.WINDOWS_1252);
        assertEquals("¨a¤o?", editor.getText());
        assertEquals(TextDecoding.WINDOWS_1252, doc.getFormat().charset());
    }

    @Test
    void saveWithEncodingThrowsOnUnmappableCharacter() throws IOException {
        Path file = tempDir.resolve("TEST.ASM");
        Files.writeString(file, "test\n");

        var editor = new FakeEditorComponent();
        editor.setText("π = 3.14\n");
        var doc = new EditorDocumentViewModel(file, editor);

        assertThrows(CharacterCodingException.class, () -> doc.saveWithEncoding(TextDecoding.WINDOWS_1252));
    }
}
