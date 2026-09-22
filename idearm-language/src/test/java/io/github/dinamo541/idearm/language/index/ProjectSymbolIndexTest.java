package io.github.dinamo541.idearm.language.index;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ProjectSymbolIndexTest {

    private ProjectSymbolIndex index;

    @BeforeEach
    void setUp() {
        index = new ProjectSymbolIndex();
    }

    @Test
    void indexesSingleFileSymbols() {
        String code = """
                .code
                main proc
                saludo:
                    mov ax, 1
                    ret
                main endp
                """;

        index.updateFile("src/main.asm", code);

        assertEquals(1, index.getFileCount());

        Optional<SymbolDefinition> mainProc = index.findDefinition("main");
        assertTrue(mainProc.isPresent());
        assertEquals(SymbolKind.PROCEDURE, mainProc.get().kind());
        assertEquals("src/main.asm", mainProc.get().filePath());
        assertEquals(2, mainProc.get().line());

        Optional<SymbolDefinition> labelSaludo = index.findDefinition("saludo");
        assertTrue(labelSaludo.isPresent());
        assertEquals(SymbolKind.LABEL, labelSaludo.get().kind());
        assertEquals(3, labelSaludo.get().line());
    }

    @Test
    void resolvesCrossFileSymbols() {
        String mainCode = """
                .code
                main proc
                    call calculate
                    ret
                main endp
                """;

        String extrasCode = """
                .code
                calculate proc
                    add ax, 5
                    ret
                calculate endp
                """;

        index.updateFile("src/main.asm", mainCode);
        index.updateFile("src/extras.asm", extrasCode);

        assertEquals(2, index.getFileCount());

        Optional<SymbolDefinition> calcDef = index.findDefinition("calculate");
        assertTrue(calcDef.isPresent());
        assertEquals("src/extras.asm", calcDef.get().filePath());
        assertEquals(2, calcDef.get().line());

        List<SymbolReference> refs = index.findReferences("calculate");
        assertFalse(refs.isEmpty());
        assertTrue(refs.stream().anyMatch(r -> r.filePath().equals("src/main.asm")));
    }

    @Test
    void searchesPrefixForCompletion() {
        String code = """
                .data
                counter_total dw 0
                counter_curr  dw 0
                calc_val      dw 10
                """;

        index.updateFile("src/vars.asm", code);

        List<SymbolDefinition> matches = index.findDefinitionsStartingWith("count");
        assertEquals(2, matches.size());
        assertTrue(matches.stream().allMatch(m -> m.name().startsWith("counter_")));
    }

    @Test
    void removesFileFromIndex() {
        index.updateFile("temp.asm", "temp_lbl: nop");
        assertTrue(index.findDefinition("temp_lbl").isPresent());

        index.removeFile("temp.asm");
        assertFalse(index.findDefinition("temp_lbl").isPresent());
    }
}
