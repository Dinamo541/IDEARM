package io.github.dinamo541.idearm.domain.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.model.RunConfiguration;
import io.github.dinamo541.idearm.domain.model.TargetProfileCatalog;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/** StagingPaths, DosBoxDialects and RunSettings: the rules every DOSBox session is generated from. */
class DosBoxRulesTest {

    @Test
    void onlyAsciiFoldersWithoutSpacesCanBeMounted() {
        assertTrue(StagingPaths.isMountable("C:\\Users\\domin\\AppData\\Local\\IDEARM\\staging"));
        assertTrue(StagingPaths.isMountable("/home/ana/.cache/idearm/staging"));
        assertFalse(StagingPaths.isMountable("C:\\Users\\Juan Perez\\AppData\\Local\\IDEARM\\staging"));
        assertFalse(StagingPaths.isMountable("C:\\Users\\Jos\u00e9\\AppData\\Local\\IDEARM\\staging"));
        assertFalse(StagingPaths.isMountable("C:\\a&b"));
        assertFalse(StagingPaths.isMountable(""));
        assertFalse(StagingPaths.isMountable(null));
    }

    @Test
    void theClassicDosBoxIsTriedFirst() {
        assertEquals(List.of("dosbox-0.74", "dosbox-x", "dosbox-staging"), DosBoxDialects.candidates("dosbox"));
    }

    @Test
    void aProjectChoiceComesFirstAndTheOthersRemainAsFallback() {
        assertEquals(List.of("dosbox-x", "dosbox-0.74", "dosbox-staging"), DosBoxDialects.candidates("DOSBox-X"));
        assertEquals(List.of("dosbox-staging", "dosbox-0.74", "dosbox-x"), DosBoxDialects.candidates("dosbox-staging"));
    }

    @Test
    void anUnknownChoiceIsIgnored() {
        assertEquals(DosBoxDialects.PREFERENCE, DosBoxDialects.candidates("nasm"));
        assertEquals(DosBoxDialects.PREFERENCE, DosBoxDialects.candidates(null));
        assertTrue(DosBoxDialects.isDosBox("dosbox-0.74"));
        assertFalse(DosBoxDialects.isDosBox("host"));
    }

    @Test
    void theDefaultRunSettingsAreValid() {
        assertTrue(RunSettings.problems(RunConfiguration.defaults(), TargetProfileCatalog.DOS_EXE_16).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"auto", "max", "MAX 80%", "fixed 3000", "3000"})
    void acceptedCycles(String cycles) {
        assertTrue(RunSettings.problems(run(cycles, 16, List.of()), TargetProfileCatalog.DOS_EXE_16).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"auto\r\n[autoexec]", "max\nmount d c:\\", "fast", ""})
    void cyclesThatCouldInjectLinesAreRejected(String cycles) {
        List<Diagnostic> problems = RunSettings.problems(run(cycles, 16, List.of()), TargetProfileCatalog.DOS_EXE_16);

        assertEquals("run.cycles.invalid", problems.getFirst().code());
        assertFalse(problems.getFirst().message().contains("\n"));
    }

    @Test
    void memoryOutsideWhatEveryDialectAcceptsIsRejected() {
        assertEquals("run.memsize.invalid",
                RunSettings.problems(run("auto", 64, List.of()), TargetProfileCatalog.DOS_EXE_16).getFirst().code());
        assertEquals("run.memsize.invalid",
                RunSettings.problems(run("auto", 0, List.of()), TargetProfileCatalog.DOS_EXE_16).getFirst().code());
    }

    @Test
    void argumentsThatWouldAddBatchCommandsAreRejected() {
        var problems = RunSettings.problems(run("auto", 16, List.of("ok", "x\r\nmount d c:\\")),
                TargetProfileCatalog.DOS_EXE_16);

        assertEquals(1, problems.size());
        assertEquals("run.argument.unsafe", problems.getFirst().code());
        assertEquals(List.of("x\\r\\nmount d c:\\"), problems.getFirst().arguments());
    }

    @Test
    void argumentsLongerThanTheDosCommandTailAreRejected() {
        var problems = RunSettings.problems(run("auto", 16, List.of("a".repeat(127))), TargetProfileCatalog.DOS_EXE_16);

        assertEquals("run.arguments.tooLong", problems.getFirst().code());
    }

    @Test
    void emulatorSettingsDoNotApplyToNativeTargets() {
        assertTrue(RunSettings.problems(run("whatever\n", 0, List.of("a b")), TargetProfileCatalog.WIN_PE64_CONSOLE).isEmpty());
    }

    @Test
    void aNativeArgumentWithALineBreakIsRejected() {
        var problems = RunSettings.problems(run("auto", 16, List.of("x\n-gdb-exit")), TargetProfileCatalog.LINUX_ELF64);

        assertEquals("run.argument.unsafe", problems.getFirst().code());
    }

    private static RunConfiguration run(String cycles, int memsize, List<String> args) {
        return new RunConfiguration("dosbox", "required", true, cycles, memsize, args);
    }
}
