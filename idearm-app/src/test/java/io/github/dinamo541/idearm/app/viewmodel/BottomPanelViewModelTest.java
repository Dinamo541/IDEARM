package io.github.dinamo541.idearm.app.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Location;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import java.util.List;
import org.junit.jupiter.api.Test;

class BottomPanelViewModelTest {

    @Test
    void appendsAndClearsBuildText() {
        var vm = new BottomPanelViewModel();
        assertTrue(vm.getBuildText().isEmpty());

        vm.appendBuildLine("Step 1");
        vm.appendBuildLine("Step 2");
        assertEquals("Step 1\nStep 2", vm.getBuildText());

        vm.clearBuild();
        assertTrue(vm.getBuildText().isEmpty());
    }

    /** INT 21h/02h prints one character per call; each used to land on its own line. */
    @Test
    void programOutputIsShownExactlyAsWritten() {
        var vm = new BottomPanelViewModel();

        vm.appendOutputLine("[DEBUG] emu8086");
        for (char c : "Hola\r\n".toCharArray()) {
            vm.appendOutputText(String.valueOf(c));
        }
        vm.appendOutputText("Ana");
        vm.appendOutputText("\b");
        vm.appendOutputLine("[DEBUG] exit code 0");

        assertEquals("[DEBUG] emu8086\nHola\nAn\n[DEBUG] exit code 0\n", vm.getOutputText());
    }

    @Test
    void typedKeysReachTheProgramOnlyWhileItCanReadThem() {
        var vm = new BottomPanelViewModel();
        var received = new StringBuilder();

        vm.sendProgramInput("x");
        vm.setProgramInput(BottomPanelViewModel.ProgramInput.KEYS, received::append);
        vm.sendProgramInput("a");
        vm.sendProgramInput("\r");
        vm.setProgramInput(BottomPanelViewModel.ProgramInput.NONE, null);
        vm.sendProgramInput("z");

        assertEquals("a\r", received.toString());
    }

    @Test
    void appendsAndClearsOutputText() {
        var vm = new BottomPanelViewModel();
        assertTrue(vm.getOutputText().isEmpty());

        vm.appendOutputLine("Output line A");
        vm.appendOutputLine("Output line B");
        assertEquals("Output line A\nOutput line B\n", vm.getOutputText());

        vm.clearOutput();
        assertTrue(vm.getOutputText().isEmpty());
    }

    @Test
    void setsAndClearsDiagnostics() {
        var vm = new BottomPanelViewModel();
        assertTrue(vm.getProblems().isEmpty());

        Diagnostic d1 = new Diagnostic(Severity.ERROR, "E101", "Undefined symbol",
                new Location("MAIN.ASM", 12, 5), "TASM", "Error line");
        Diagnostic d2 = new Diagnostic(Severity.WARNING, "W202", "No stack",
                new Location("MAIN.ASM", 1, null), "TLINK", "Warning line");

        vm.setDiagnostics(List.of(d1, d2));
        assertEquals(2, vm.getProblems().size());
        assertEquals("Undefined symbol", vm.getProblems().getFirst().getMessage());
        assertEquals(12, vm.getProblems().getFirst().getLine());

        vm.clearDiagnostics();
        assertTrue(vm.getProblems().isEmpty());
    }
}
