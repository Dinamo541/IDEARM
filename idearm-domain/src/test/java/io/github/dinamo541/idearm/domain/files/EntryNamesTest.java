package io.github.dinamo541.idearm.domain.files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dinamo541.idearm.domain.files.EntryNames.Kind;
import io.github.dinamo541.idearm.domain.files.EntryNames.Severity;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class EntryNamesTest {

    @Test
    void extensionsAreAlwaysStoredInLowerCase() {
        assertEquals("Main.asm", EntryNames.normalize("Main.ASM", Kind.FILE));
        assertEquals("src/Video.inc", EntryNames.normalize("src/Video.INC", Kind.FILE));
        assertEquals("notes", EntryNames.normalize("notes", Kind.FILE));
        assertEquals(".gitignore", EntryNames.normalize(".gitignore", Kind.FILE));
    }

    @Test
    void namesKeepTheSpellingTheUserTyped() {
        assertEquals("GameLoop.asm", EntryNames.normalize("GameLoop.asm", Kind.FILE));
        assertEquals("Sprites", EntryNames.normalize("Sprites", Kind.FOLDER));
        assertEquals("Assets.V2", EntryNames.normalize("Assets.V2", Kind.FOLDER), "Folders have no extension");
    }

    @Test
    void theUserIsToldWhenTheExtensionWasLowered() {
        EntryNames.Check check = EntryNames.check("MAIN.ASM", Kind.FILE, true);

        assertEquals(Severity.INFO, check.severity());
        assertEquals("explorer.name.lowerCaseExtension", check.code());
        assertEquals(List.of("MAIN.asm"), check.arguments());
        assertFalse(check.blocking());
    }

    @Test
    void slashesCreateNestedFoldersAndATrailingSlashAsksForAFolder() {
        assertEquals("lib/io/disk.asm", EntryNames.normalize("lib\\io/disk.asm", Kind.FILE));
        assertEquals(Kind.FOLDER, EntryNames.requestedKind("assets/", Kind.FILE));
        assertEquals("assets", EntryNames.normalize("assets/", Kind.FOLDER));
        assertEquals(Kind.FILE, EntryNames.requestedKind("main.asm", Kind.FILE));
    }

    @ParameterizedTest
    @CsvSource({
            "'', explorer.name.empty",
            "'   ', explorer.name.empty",
            "/abs.asm, explorer.name.absolute",
            "C:/abs.asm, explorer.name.absolute",
            "' lead.asm', explorer.name.whitespace",
            "'trail.asm ', explorer.name.whitespace",
            "../up.asm, explorer.name.relative",
            "a//b.asm, explorer.name.emptySegment",
            "what?.asm, explorer.name.invalidCharacter",
            "pipe|.asm, explorer.name.invalidCharacter",
            "dot., explorer.name.trailingDot",
            "CON, explorer.name.reserved",
            "nul.asm, explorer.name.reserved",
            "src/lpt1.inc, explorer.name.reserved"
    })
    void rejectsNamesWindowsCannotStoreOrThatLeaveTheFolder(String typed, String code) {
        EntryNames.Check check = EntryNames.check(typed, Kind.FILE, false);

        assertTrue(check.blocking(), typed);
        assertEquals(code, check.code());
    }

    @ParameterizedTest
    @ValueSource(strings = {"functions.asm", "main.asm1", "my file.asm", "señal.asm", "long-folder-name/a.asm"})
    void warnsWhenDosToolsCannotSeeTheName(String typed) {
        EntryNames.Check check = EntryNames.check(typed, Kind.FILE, true);

        assertEquals(Severity.WARNING, check.severity(), typed);
        assertEquals("explorer.name.notDos", check.code());
    }

    @Test
    void longNamesAreFineOutsideDosProjects() {
        assertEquals(EntryNames.Check.OK, EntryNames.check("functions.asm", Kind.FILE, false));
    }

    @Test
    void aRenameChangesOneNameOnly() {
        assertEquals("explorer.name.separator", EntryNames.checkRename("lib/io.asm", Kind.FILE, true).code());
        assertEquals(EntryNames.Check.OK, EntryNames.checkRename("io.asm", Kind.FILE, true));
    }
}
