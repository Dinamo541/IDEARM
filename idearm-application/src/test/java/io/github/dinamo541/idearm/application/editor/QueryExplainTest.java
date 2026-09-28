package io.github.dinamo541.idearm.application.editor;

import io.github.dinamo541.idearm.language.knowledge.CompatibilityContext;
import io.github.dinamo541.idearm.language.index.ProjectSymbolIndex;
import io.github.dinamo541.idearm.language.index.SymbolDefinition;
import io.github.dinamo541.idearm.language.index.SymbolKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AA-P4-02: QueryExplain, Selection Help, and Acceptance Cases 1 & 2")
class QueryExplainTest {

    private final QueryExplain queryExplain = new QueryExplain();

    @Test
    @DisplayName("Acceptance Case 1: Complete decomposition of 'mov ax, [bx+si+4]' in 16-bit DOS")
    void testAcceptanceCase1Decomposition() {
        String selection = "mov ax, [bx+si+4]";
        CompatibilityContext ctx = null;

        Optional<ExplanationNode> resultOpt = queryExplain.explainSelection(selection, "es", ctx);
        assertTrue(resultOpt.isPresent(), "Explanation must be present for valid instruction statement");

        ExplanationNode root = resultOpt.get();
        assertEquals("STATEMENT", root.role());

        List<ExplanationNode> children = root.children();
        assertFalse(children.isEmpty());

        // 1. Check mnemonic node (mov)
        ExplanationNode mnem = children.stream().filter(c -> c.role().equals("INSTRUCTION")).findFirst().orElseThrow();
        assertEquals("mov", mnem.title());
        assertTrue(mnem.details().toLowerCase().contains("no modifica banderas"));

        // 2. Check destination register (ax)
        ExplanationNode ax = children.stream().filter(c -> c.role().equals("REGISTER_OPERAND")).findFirst().orElseThrow();
        assertEquals("ax", ax.title());
        assertTrue(ax.summary().toLowerCase().contains("destino"));
        assertTrue(ax.summary().toLowerCase().contains("se escribe completo"));

        // 3. Check separator (,)
        ExplanationNode comma = children.stream().filter(c -> c.role().equals("SEPARATOR")).findFirst().orElseThrow();
        assertEquals(",", comma.title());
        assertTrue(comma.details().toLowerCase().contains("destino va primero"));

        // 4. Check memory operand [bx+si+4]
        ExplanationNode mem = children.stream().filter(c -> c.role().equals("MEMORY_OPERAND")).findFirst().orElseThrow();
        assertEquals("[bx+si+4]", mem.title());
        assertTrue(mem.summary().toLowerCase().contains("se lee"));

        // Check sub-parts of memory operand
        List<ExplanationNode> memParts = mem.children();
        assertTrue(memParts.stream().anyMatch(c -> c.role().equals("BRACKETS") && c.summary().contains("contenido de la memoria")));
        assertTrue(memParts.stream().anyMatch(c -> c.role().equals("BASE_REGISTER") && c.summary().contains("base")));
        assertTrue(memParts.stream().anyMatch(c -> c.role().equals("INDEX_REGISTER") && c.summary().contains("índice")));
        assertTrue(memParts.stream().anyMatch(c -> c.role().equals("DISPLACEMENT") && c.summary().contains("desplazamiento")));
        assertTrue(memParts.stream().anyMatch(c -> c.role().equals("SEGMENT") && c.summary().contains("DS por omisión")));
        assertTrue(memParts.stream().anyMatch(c -> c.role().equals("ADDRESS_CALCULATION") && c.summary().contains("efectiva") && c.summary().contains("física")));

        // 5. Check addressing rules pedagogical note (two bases, AX base)
        ExplanationNode rules = children.stream().filter(c -> c.role().equals("ADDRESSING_RULES")).findFirst().orElseThrow();
        assertTrue(rules.details().contains("[bx+bp]"), "Must explain why [bx+bp] is invalid");
        assertTrue(rules.details().contains("dos bases"), "Must mention two bases");
        assertTrue(rules.details().contains("[ax]"), "Must explain why [ax] is invalid");
        assertTrue(rules.details().contains("32 bits"), "Must contrast with 32-bit addressing");
    }

    @Test
    @DisplayName("Acceptance Case 2: Differentiates DS:DX, DX:AX, etiqueta:, and ES:[DI]")
    void testAcceptanceCase2ColonDistinctions() {
        // 1. DS:DX -> logical address notation, NOT code, no concatenation
        Optional<ExplanationNode> dsDx = queryExplain.explainSelection("DS:DX", "es", null);
        assertTrue(dsDx.isPresent());
        assertTrue(dsDx.get().notationNotCode());
        assertFalse(dsDx.get().assemblableSyntax());
        assertTrue(dsDx.get().summary().contains("Notación"));
        assertTrue(dsDx.get().details().toLowerCase().contains("no concatenación"));
        assertTrue(dsDx.get().details().toLowerCase().contains("no es código"));

        // 2. DX:AX -> register pair notation, NOT code, no concatenation
        Optional<ExplanationNode> dxAx = queryExplain.explainSelection("DX:AX", "es", null);
        assertTrue(dxAx.isPresent());
        assertTrue(dxAx.get().notationNotCode());
        assertFalse(dxAx.get().assemblableSyntax());
        assertTrue(dxAx.get().summary().contains("Notación"));
        assertTrue(dxAx.get().details().toLowerCase().contains("no representan concatenación"));

        // 3. etiqueta: -> label definition, IS assemblable syntax
        Optional<ExplanationNode> label = queryExplain.explainSelection("bucle:", "es", null);
        assertTrue(label.isPresent());
        assertFalse(label.get().notationNotCode());
        assertTrue(label.get().assemblableSyntax());
        assertEquals("LABEL_DEFINITION", label.get().role());

        // 4. ES:[DI] -> segment override, IS assemblable syntax
        Optional<ExplanationNode> segOverride = queryExplain.explainSelection("ES:[DI]", "es", null);
        assertTrue(segOverride.isPresent());
        assertFalse(segOverride.get().notationNotCode());
        assertTrue(segOverride.get().assemblableSyntax());
        assertEquals("SEGMENT_OVERRIDE_SYNTAX", segOverride.get().role());
    }

    @Test
    @DisplayName("Resolves 21h in context of 'INT 21h' as DOS service dispatcher, not ASCII character")
    void testInterruptContextResolution() {
        Optional<ExplanationNode> exp = queryExplain.explainSelection("int 21h", "es", null);
        assertTrue(exp.isPresent());
        assertEquals("INTERRUPT_SERVICE", exp.get().role());
        assertTrue(exp.get().details().contains("DOS"));
        assertFalse(exp.get().details().contains("ASCII"));
    }

    @Test
    @DisplayName("Acceptance Case 10: User project symbol wins and catalog entry becomes secondary note")
    void testUserSymbolPriorityOverHomonymInstruction() {
        ProjectSymbolIndex index = new ProjectSymbolIndex();
        index.updateFile("main.asm", "LOOP MACRO\nENDM\n");

        QueryExplain.Request req = new QueryExplain.Request("LOOP", 1, 1, "es", null, index);
        Optional<ExplanationNode> result = queryExplain.execute(req);

        assertTrue(result.isPresent());
        ExplanationNode root = result.get();
        assertEquals("USER_SYMBOL", root.role());
        assertEquals("LOOP", root.title());

        assertFalse(root.children().isEmpty(), "Must contain homonym instruction note as child");
        ExplanationNode sec = root.children().getFirst();
        assertEquals("HOMONYM_INSTRUCTION", sec.role());
    }
}
