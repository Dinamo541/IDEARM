package io.github.dinamo541.idearm.language.linter;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.language.knowledge.AddressingMode;
import io.github.dinamo541.idearm.language.model.SourceFileNode;
import io.github.dinamo541.idearm.language.parser.AssemblyParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AA-P4-03: AddressingMode Catalog and Linter Validation")
class AddressingModeRuleTest {

    private final AddressingModeRule rule = new AddressingModeRule();
    private final AssemblyParser parser = new AssemblyParser();

    @Test
    @DisplayName("Acceptance Criteria 1: [bx+bp] produces diagnostic with reason 'dos registros base'")
    void rejectTwoBasesWithExplicitReason() {
        String code = """
                .code
                mov ax, [bx+bp]
                """;
        SourceFileNode ast = parser.parse(code);
        List<Diagnostic> diags = rule.check(ast, "test.asm", "8086", null);

        assertFalse(diags.isEmpty(), "Should produce diagnostic for [bx+bp]");
        Diagnostic d = diags.getFirst();
        assertEquals("lint.addressing.invalid", d.code());
        assertTrue(d.arguments().get(1).toLowerCase().contains("dos registros base"),
                "Spanish reason must explain 'dos registros base', was: " + d.arguments());
        assertTrue(d.message().contains("Two base registers"), "The message is English, was: " + d.message());
    }

    @Test
    @DisplayName("Acceptance Criteria 2: Catalog lists valid 16-bit modes and 32/64-bit modes")
    void verifyCatalogCoverage() {
        List<AddressingMode> modes16 = AddressingMode.get16BitValidModes();
        assertFalse(modes16.isEmpty());
        // Verify the 4 primary based-indexed combinations exist
        assertTrue(modes16.stream().anyMatch(m -> m.pattern().contains("BX + SI")));
        assertTrue(modes16.stream().anyMatch(m -> m.pattern().contains("BX + DI")));
        assertTrue(modes16.stream().anyMatch(m -> m.pattern().contains("BP + SI")));
        assertTrue(modes16.stream().anyMatch(m -> m.pattern().contains("BP + DI")));

        List<AddressingMode> modes32 = AddressingMode.get32And64BitValidModes();
        assertFalse(modes32.isEmpty());
        assertTrue(modes32.stream().anyMatch(m -> m.addressSize() == 32));
        assertTrue(modes32.stream().anyMatch(m -> m.addressSize() == 64));
    }

    @Test
    @DisplayName("Acceptance Criteria 3: Zero false positives for [eax*4+d] in 32-bit addressing")
    void acceptScaledIndexIn32BitWithoutFalsePositives() {
        String code = """
                .code
                mov edx, [eax*4+d]
                """;
        SourceFileNode ast = parser.parse(code);
        List<Diagnostic> diags = rule.check(ast, "test.asm", "80386", null);

        assertTrue(diags.isEmpty(), "32-bit [eax*4+d] must not produce any diagnostic in 80386+, got: " + diags);
    }

    @Test
    @DisplayName("Rejects [ax] as base/index in 16-bit addressing")
    void rejectAxAsBaseIn16Bit() {
        String code = """
                .code
                mov bx, [ax]
                """;
        SourceFileNode ast = parser.parse(code);
        List<Diagnostic> diags = rule.check(ast, "test.asm", "8086", null);

        assertFalse(diags.isEmpty());
        assertTrue(diags.getFirst().arguments().get(1).contains("AX no puede ser"));
        assertTrue(diags.getFirst().message().contains("AX cannot be used"));
    }

    @Test
    @DisplayName("Rejects ESP as index register in 32-bit SIB addressing")
    void rejectEspAsIndexInSib() {
        String code = """
                .code
                mov eax, [ebx+esp*4]
                """;
        SourceFileNode ast = parser.parse(code);
        List<Diagnostic> diags = rule.check(ast, "test.asm", "80386", null);

        assertFalse(diags.isEmpty());
        assertTrue(diags.getFirst().message().toLowerCase().contains("esp"));
    }

    @Test
    @DisplayName("ESP is the base in [esp+4] and [eax+esp], so neither is an error")
    void acceptStackPointerAsBase() {
        String code = """
                .code
                mov eax, [esp+4]
                mov eax, [eax+esp]
                mov eax, [esp+eax*2+8]
                """;
        List<Diagnostic> diags = rule.check(parser.parse(code), "test.asm", "80386", null);

        assertTrue(diags.isEmpty(), "ESP may always be the base register, got: " + diags);
    }

    @Test
    @DisplayName("16-bit addresses follow the 8086 rules on an 80386 too")
    void reject16BitMistakesOn386() {
        String code = """
                .code
                mov bx, [ax]
                mov ax, [bx+bp]
                mov ax, [bx+si+4]
                """;
        List<Diagnostic> diags = rule.check(parser.parse(code), "test.asm", "80386", null);

        assertEquals(2, diags.size(), "[ax] and [bx+bp] are invalid, [bx+si+4] is fine: " + diags);
    }
}
