package io.github.dinamo541.idearm.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProgramStartTest {

    @Test
    void findsTheLabelAfterEnd() {
        ProgramStart start = ProgramStart.in(".CODE\nmain PROC\n    ret\nmain ENDP\n\n  end   main ; start here\n")
                .orElseThrow();

        assertEquals(6, start.line());
        assertEquals(3, start.column());
        assertEquals("end main", start.declaration());
    }

    @Test
    void aBareEndIsAModuleOfAnotherProgram() {
        assertTrue(ProgramStart.in(".CODE\nPUBLIC Beep\nBeep PROC\nBeep ENDP\nEND ; no start here\n").isEmpty());
    }

    @Test
    void startupDirectivesDeclareTheStartToo() {
        assertEquals(".STARTUP", ProgramStart.in(".MODEL SMALL\n.CODE\n.STARTUP\n.EXIT\nEND\n").orElseThrow()
                .declaration());
        assertEquals("STARTUPCODE", ProgramStart.in("MODEL SMALL\nCODESEG\nSTARTUPCODE\nEXITCODE\nEND\n")
                .orElseThrow().declaration());
    }

    @Test
    void commentsAndTextAfterTheFirstEndAreNotRead() {
        assertTrue(ProgramStart.in("; END main\n.CODE\nEND\nEND main\n").isEmpty());
    }

    @Test
    void endingDirectivesOfProceduresAndSegmentsAreNotTheEndOfTheFile() {
        assertTrue(ProgramStart.in("CODE SEGMENT\nmain PROC\nmain ENDP\nCODE ENDS\nENDS\n").isEmpty());
    }

    @Test
    void anUnreadableFileDeclaresNothing(@TempDir Path folder) {
        assertTrue(ProgramStart.in(folder.resolve("missing.asm")).isEmpty());
    }
}
