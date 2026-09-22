package io.github.dinamo541.idearm.app.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocalizationTest {

    private final Localization localization = new Localization();

    @Test
    void aDomainFailureIsShownInTheSelectedLanguage() {
        var failure = new DomainException("run.executable.missing", "Executable not found: C:/p/main.exe",
                "C:/p/main.exe");

        assertEquals("Program not found: C:/p/main.exe", localization.describe(Problem.of(failure)));
        localization.localeProperty().set(Localization.SPANISH);
        assertEquals("No se encontró el programa: C:/p/main.exe", localization.describe(Problem.of(failure)));
    }

    @Test
    void theEnglishTextIsKeptWhenTheTranslationNeedsValuesTheProblemLacks() {
        localization.localeProperty().set(Localization.SPANISH);
        var failure = new DomainException("build.unsafe-path", "DOS output escaped staging.");

        assertEquals("DOS output escaped staging.", localization.describe(Problem.of(failure)));
    }

    @Test
    void toolOutputWithoutACodeKeepsTheToolsWords() {
        localization.localeProperty().set(Localization.SPANISH);
        var diagnostic = new Diagnostic(Severity.ERROR, "", "Undefined symbol: PRINTNUM", null, "tasm", "raw");

        assertEquals("Undefined symbol: PRINTNUM", localization.describe(Problem.of(diagnostic)));
    }

    @Test
    void aFileNameCheckUsesTheExplorerText() {
        var failure = new DomainException("explorer.name.exists", "Already exists: C:/p/src/main.asm", "main.asm");

        assertEquals("A file or folder main.asm already exists at this location. Please choose a different name.",
                localization.describe(Problem.of(failure)));
    }

    @Test
    void anUnexpectedFailureShowsItsOwnMessage() {
        assertEquals("disk on fire", localization.describe(Problem.of(new IllegalStateException("disk on fire"))));
        assertEquals("IllegalStateException", localization.describe(Problem.of(new IllegalStateException())));
    }

    @Test
    void aProblemInsideAStatusMessageIsTranslatedWhenShown() {
        var message = Message.of("status.task.failed",
                new Problem("project.busy", List.of(), "Another task is active for this project."));

        localization.localeProperty().set(Localization.SPANISH);
        assertEquals(localization.get("status.task.failed",
                        "Otra tarea sigue en curso en este proyecto; espere a que termine o pulse Detener."),
                localization.get(message));
    }

    @Test
    void lintWarningsAreTranslatedWithTheirValues() {
        localization.localeProperty().set(Localization.SPANISH);
        var warning = new Diagnostic(Severity.WARNING, "lint.cpu-baseline-register32", "Register 'EAX' ...", null,
                "idearm-linter", "MOV EAX, 1", List.of("EAX", "8086"));

        assertEquals("EAX es un registro de 32 bits (80386 o posterior), pero el proyecto usa la 8086.",
                localization.describe(Problem.of(warning)));
    }
}
