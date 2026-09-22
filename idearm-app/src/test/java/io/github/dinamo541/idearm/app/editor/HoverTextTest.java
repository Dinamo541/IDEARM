package io.github.dinamo541.idearm.app.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dinamo541.idearm.app.i18n.Localization;
import io.github.dinamo541.idearm.application.editor.HoverInfo;
import io.github.dinamo541.idearm.application.editor.QueryHover;
import io.github.dinamo541.idearm.language.index.ProjectSymbolIndex;
import org.junit.jupiter.api.Test;

/** Number and symbol hovers follow the UI language; they were hard-coded, and half Spanish only in part. */
class HoverTextTest {

    private final Localization localization = new Localization();

    @Test
    void aNumberIsDescribedInSpanishOneFactPerLine() {
        localization.localeProperty().set(Localization.SPANISH);
        HoverInfo info = HoverText.localize(new QueryHover().execute("4Ch", "es", null).orElseThrow(), localization);

        assertEquals("Número 4Ch", info.title());
        assertEquals("Decimal: 76\nHexadecimal: 0x4C\nBinario: 100 1100b\nASCII: 'L'", info.description());
    }

    @Test
    void aNumberIsDescribedInEnglish() {
        HoverInfo info = HoverText.localize(new QueryHover().execute("21h", "en", null).orElseThrow(), localization);

        assertEquals("Number 21h", info.title());
        assertTrue(info.description().startsWith("Decimal: 33\n"), info.description());
        assertTrue(info.description().contains("Binary: 10 0001b"), info.description());
    }

    @Test
    void aSymbolSaysWhereItIsDefinedInTheUserLanguage() {
        var index = new ProjectSymbolIndex();
        index.updateFile("src/main.asm", ".code\nmain proc\nfin:\n    ret\nmain endp\n");
        localization.localeProperty().set(Localization.SPANISH);

        HoverInfo info = HoverText.localize(new QueryHover().execute("fin", "es", index).orElseThrow(), localization);

        assertEquals("Etiqueta fin", info.title());
        assertEquals("Definido en src/main.asm:3", info.description());
    }
}
