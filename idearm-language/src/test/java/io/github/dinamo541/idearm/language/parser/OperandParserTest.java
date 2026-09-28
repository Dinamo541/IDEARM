package io.github.dinamo541.idearm.language.parser;

import io.github.dinamo541.idearm.language.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AA-P4-01: OperandParser and Structured Memory Expressions")
class OperandParserTest {

    @Test
    @DisplayName("Parses [bx+si+4] as 16-bit memory expression with base, index, disp, DS segment")
    void parseBaseIndexDisplacement() {
        ParsedOperand op = OperandParser.parse("[bx+si+4]", 1, 10);
        assertInstanceOf(MemoryOperand.class, op);
        MemoryOperand mem = (MemoryOperand) op;

        assertNull(mem.segmentOverride());
        assertNull(mem.sizePrefix());
        assertNotNull(mem.expression());

        MemoryExpression expr = mem.expression();
        assertNotNull(expr.base());
        assertEquals("BX", expr.base().text().toUpperCase());
        assertNotNull(expr.index());
        assertEquals("SI", expr.index().text().toUpperCase());
        assertEquals(1, expr.scale());
        assertNotNull(expr.displacement());
        assertEquals(4L, expr.displacementValue());

        assertEquals(16, expr.addressSize());
        assertEquals("DS", expr.defaultSegment());
        assertEquals("DS", mem.effectiveSegment());
        assertEquals("BX + SI + 4", expr.effectiveAddressFormula());
        assertEquals("DS * 16 + (BX + SI + 4)", expr.physicalAddressFormula(null));
        assertTrue(expr.isValid16Bit());
        assertNull(expr.getInvalid16BitReason("es"));
    }

    @Test
    @DisplayName("Parses ES:[di] with segment override and index register")
    void parseSegmentOverrideAndIndex() {
        ParsedOperand op = OperandParser.parse("ES:[di]", 2, 5);
        assertInstanceOf(MemoryOperand.class, op);
        MemoryOperand mem = (MemoryOperand) op;

        assertEquals("ES", mem.segmentOverride());
        assertNotNull(mem.segmentComponent());
        assertEquals("ES", mem.effectiveSegment());

        MemoryExpression expr = mem.expression();
        assertNotNull(expr);
        assertNull(expr.base());
        assertNotNull(expr.index());
        assertEquals("DI", expr.index().text().toUpperCase());
        assertEquals("DI", expr.effectiveAddressFormula());
        assertEquals("ES * 16 + (DI)", expr.physicalAddressFormula(mem.segmentOverride()));
        assertTrue(expr.isValid16Bit());
    }

    @Test
    @DisplayName("Parses word ptr [bp-2] with size prefix, BP base, negative disp, SS default segment")
    void parseSizePrefixAndBpBase() {
        ParsedOperand op = OperandParser.parse("word ptr [bp-2]", 5, 1);
        assertInstanceOf(MemoryOperand.class, op);
        MemoryOperand mem = (MemoryOperand) op;

        assertEquals("WORD PTR", mem.sizePrefix());
        assertNotNull(mem.sizeComponent());
        assertNull(mem.segmentOverride());

        MemoryExpression expr = mem.expression();
        assertNotNull(expr);
        assertNotNull(expr.base());
        assertEquals("BP", expr.base().text().toUpperCase());
        assertNull(expr.index());
        assertEquals(-2L, expr.displacementValue());

        assertEquals("SS", expr.defaultSegment());
        assertEquals("SS", mem.effectiveSegment());
        assertEquals("BP - 2", expr.effectiveAddressFormula());
        assertEquals("SS * 16 + (BP - 2)", expr.physicalAddressFormula(null));
        assertTrue(expr.isValid16Bit());
    }

    @Test
    @DisplayName("Parses [eax*4+tabla] with scale, 32-bit register and symbol displacement")
    void parseScaleAndSymbolDisplacement() {
        ParsedOperand op = OperandParser.parse("[eax*4+tabla]", 10, 8);
        assertInstanceOf(MemoryOperand.class, op);
        MemoryOperand mem = (MemoryOperand) op;

        MemoryExpression expr = mem.expression();
        assertNotNull(expr);
        assertNotNull(expr.index());
        assertEquals("EAX", expr.index().text().toUpperCase());
        assertEquals(4, expr.scale());
        assertEquals("tabla", expr.displacementSymbol());
        assertEquals(32, expr.addressSize());

        // In 16-bit real mode, scale and 32-bit registers are invalid
        assertFalse(expr.isValid16Bit());
        String reasonEs = expr.getInvalid16BitReason("es");
        assertNotNull(reasonEs);
        assertTrue(reasonEs.contains("80386+"));
    }

    @Test
    @DisplayName("Rejects [bx+bp] in 16-bit addressing with 'Dos registros base' rationale")
    void rejectTwoBasesIn16Bit() {
        ParsedOperand op = OperandParser.parse("[bx+bp]", 1, 1);
        assertInstanceOf(MemoryOperand.class, op);
        MemoryOperand mem = (MemoryOperand) op;

        MemoryExpression expr = mem.expression();
        assertNotNull(expr);
        assertFalse(expr.isValid16Bit());
        String reasonEs = expr.getInvalid16BitReason("es");
        assertNotNull(reasonEs);
        assertTrue(reasonEs.toLowerCase().contains("dos registros base"));

        String reasonEn = expr.getInvalid16BitReason("en");
        assertNotNull(reasonEn);
        assertTrue(reasonEn.toLowerCase().contains("two base registers"));
    }

    @Test
    @DisplayName("Rejects [ax] in 16-bit addressing because AX cannot be base or index")
    void rejectAxIn16Bit() {
        ParsedOperand op = OperandParser.parse("[ax]", 1, 1);
        assertInstanceOf(MemoryOperand.class, op);
        MemoryOperand mem = (MemoryOperand) op;

        MemoryExpression expr = mem.expression();
        assertNotNull(expr);
        assertFalse(expr.isValid16Bit());
        String reasonEs = expr.getInvalid16BitReason("es");
        assertNotNull(reasonEs);
        assertTrue(reasonEs.contains("AX no puede ser"));
    }

    @Test
    @DisplayName("Parses register, immediate and symbol operands with exact properties")
    void parseBasicOperands() {
        ParsedOperand reg = OperandParser.parse("ax", 1, 1);
        assertInstanceOf(RegisterOperand.class, reg);
        RegisterOperand r = (RegisterOperand) reg;
        assertEquals("AX", r.normalizedRegister());
        assertEquals(16, r.bitSize());

        ParsedOperand immHex = OperandParser.parse("10h", 1, 1);
        assertInstanceOf(ImmediateOperand.class, immHex);
        ImmediateOperand imm = (ImmediateOperand) immHex;
        assertEquals(16L, imm.numericValue());

        ParsedOperand sym = OperandParser.parse("@data", 1, 1);
        assertInstanceOf(SymbolOperand.class, sym);
        SymbolOperand s = (SymbolOperand) sym;
        assertEquals("@data", s.symbolName());
    }

    @Test
    @DisplayName("Fallback: malformed or empty operand produces UnknownOperand without throwing")
    void fallbackToUnknownWithoutCrashing() {
        ParsedOperand empty = OperandParser.parse("", 1, 1);
        assertInstanceOf(UnknownOperand.class, empty);

        ParsedOperand malformed = OperandParser.parse("???###+++", 1, 1);
        assertInstanceOf(UnknownOperand.class, malformed);
    }

    @Test
    @DisplayName("AssemblyParser integration: attaches parsedOperands to InstructionNode")
    void assemblyParserIntegration() {
        String code = "mov ax, [bx+si+4]\n";
        SourceFileNode ast = new AssemblyParser().parse(code);
        assertEquals(1, ast.instructions().size());

        InstructionNode inst = ast.instructions().getFirst();
        assertEquals("MOV", inst.mnemonic());
        assertEquals(2, inst.operands().size());
        assertEquals("ax", inst.operands().get(0));
        assertEquals("[bx+si+4]", inst.operands().get(1));

        assertEquals(2, inst.parsedOperands().size());
        assertInstanceOf(RegisterOperand.class, inst.parsedOperands().get(0));
        assertInstanceOf(MemoryOperand.class, inst.parsedOperands().get(1));

        MemoryOperand mem = (MemoryOperand) inst.parsedOperands().get(1);
        assertEquals("BX", mem.expression().base().text().toUpperCase());
        assertEquals("SI", mem.expression().index().text().toUpperCase());
        assertEquals(4L, mem.expression().displacementValue());
    }

    @Test
    @DisplayName("A label spelled with hex letters and h (beach, fah) is a symbol, not a number")
    void hexLookingLabelIsASymbol() {
        for (String label : List.of("beach", "fah", "each")) {
            MemoryOperand mem = (MemoryOperand) OperandParser.parse("[bx+" + label + "]", 1, 10);
            assertEquals(label, mem.expression().displacementSymbol(), label);
            assertNull(mem.expression().displacementValue(), label);
        }
    }

    @Test
    @DisplayName("ESP is the base register of [esp+4] and of [eax+esp]")
    void stackPointerIsTheBase() {
        MemoryExpression first = ((MemoryOperand) OperandParser.parse("[esp+4]", 1, 10)).expression();
        assertEquals("ESP", first.base().text().toUpperCase());
        assertNull(first.index());

        MemoryExpression second = ((MemoryOperand) OperandParser.parse("[eax+esp]", 1, 10)).expression();
        assertEquals("ESP", second.base().text().toUpperCase());
        assertEquals("EAX", second.index().text().toUpperCase());
    }

    @Test
    @DisplayName("Decimal and octal suffixes and NASM prefixes give an immediate value")
    void numericLiteralForms() {
        assertEquals(10L, OperandParser.parseNumericLiteral("10d"));
        assertEquals(15L, OperandParser.parseNumericLiteral("17q"));
        assertEquals(5L, OperandParser.parseNumericLiteral("0b101"));
        assertEquals(-16L, OperandParser.parseNumericLiteral("-10h"));
        assertEquals(65L, OperandParser.parseNumericLiteral("'A'"));
        assertNull(OperandParser.parseNumericLiteral("beach"));
    }
}
