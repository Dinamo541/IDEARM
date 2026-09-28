package io.github.dinamo541.idearm.language.knowledge;

import io.github.dinamo541.idearm.language.catalog.FlagEffect;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InstructionFormSemanticsTest {

    @Test
    void imulThirteenFormsThreeGroupsWithAuthoritativeRequirements() {
        Corpus corpus = Corpus.get();
        Optional<InstructionEntry> imulOpt = corpus.findInstruction("IMUL");
        assertTrue(imulOpt.isPresent(), "IMUL must be present");

        InstructionEntry imul = imulOpt.get();
        List<InstructionForm> forms = imul.forms();
        assertEquals(13, forms.size(), "IMUL must declare exactly 13 forms according to Intel SDM");

        // Group 1: 1-operand (8086: reg/mem8, reg/mem16; 386: reg/mem32; 64-bit: reg/mem64)
        InstructionForm form1Op8 = forms.get(0);
        assertEquals(1, form1Op8.operands().size());
        assertEquals(CpuGeneration.I8086, form1Op8.requirement().minGeneration());
        assertFalse(form1Op8.implicitOperands().isEmpty(), "1-op IMUL must have implicit operands (AL -> AX)");

        InstructionForm form1Op32 = forms.get(2);
        assertEquals(CpuGeneration.I80386, form1Op32.requirement().minGeneration());

        InstructionForm form1Op64 = forms.get(3);
        assertEquals(CpuGeneration.X86_64, form1Op64.requirement().minGeneration());

        // Group 2: 2-operand (386: r16, r/m16; r32, r/m32; 64-bit: r64, r/m64)
        InstructionForm form2Op16 = forms.get(4);
        assertEquals(2, form2Op16.operands().size());
        assertEquals(CpuGeneration.I80386, form2Op16.requirement().minGeneration());

        // Group 3: 3-operand with immediate (80186+: r16, r/m16, imm8/imm16)
        InstructionForm form3Op16 = forms.get(7);
        assertEquals(3, form3Op16.operands().size());
        assertEquals(CpuGeneration.I80186, form3Op16.requirement().minGeneration());
    }

    @Test
    void implicitOperandsAreExplicitlyDeclared() {
        Corpus corpus = Corpus.get();

        // MUL (1-operand, implicit accumulator and double-width result)
        InstructionEntry mul = corpus.findInstruction("MUL").orElseThrow();
        InstructionForm mulByte = mul.forms().get(0);
        assertTrue(mulByte.implicitOperands().stream().anyMatch(op -> "x86.reg.al".equals(op.register())));
        assertTrue(mulByte.implicitOperands().stream().anyMatch(op -> "x86.reg.ax".equals(op.register())));

        // DIV (1-operand, implicit dividend and quotient/remainder)
        InstructionEntry div = corpus.findInstruction("DIV").orElseThrow();
        InstructionForm divWord = div.forms().get(1);
        assertTrue(divWord.implicitOperands().stream().anyMatch(op -> "x86.reg.dx".equals(op.register())));
        assertTrue(divWord.implicitOperands().stream().anyMatch(op -> "x86.reg.ax".equals(op.register())));

        // XLAT (implicit AL, BX in DS, AL translated byte)
        InstructionEntry xlat = corpus.findInstruction("XLAT").orElseThrow();
        InstructionForm xlatForm = xlat.forms().getFirst();
        assertEquals(3, xlatForm.implicitOperands().size());
        assertTrue(xlatForm.implicitOperands().stream().anyMatch(op -> "x86.reg.bx".equals(op.register())));

        // MOVSB (implicit DS:SI and ES:DI)
        InstructionEntry movsb = corpus.findInstruction("MOVSB").orElseThrow();
        InstructionForm movsbForm = movsb.forms().getFirst();
        assertEquals(2, movsbForm.implicitOperands().size());
        assertTrue(movsbForm.implicitOperands().stream().anyMatch(op -> "x86.reg.si".equals(op.register())));
        assertTrue(movsbForm.implicitOperands().stream().anyMatch(op -> "x86.reg.di".equals(op.register())));
    }

    @Test
    void homonymsDisambiguationDeclared() {
        Corpus corpus = Corpus.get();
        InstructionEntry movsd = corpus.findInstruction("MOVSD").orElseThrow();
        assertFalse(movsd.homonyms().isEmpty(), "MOVSD must declare homonyms");
        assertTrue(movsd.homonyms().contains("x86.instr.movsd.sse2"));

        InstructionEntry cmpsd = corpus.findInstruction("CMPSD").orElseThrow();
        assertFalse(cmpsd.homonyms().isEmpty(), "CMPSD must declare homonyms");
        assertTrue(cmpsd.homonyms().contains("x86.instr.cmpsd.sse2"));
    }

    @Test
    void conditionalFlagsSpecificationsParsed() {
        Corpus corpus = Corpus.get();
        InstructionEntry shl = corpus.findInstruction("SHL").orElseThrow();

        // Form 1: SHL reg, 1 -> OF is modified
        InstructionForm form1 = shl.forms().get(0);
        FlagEffectSpec ofSpec1 = form1.flags().stream()
                .filter(f -> "OF".equals(f.flagId()))
                .findFirst().orElseThrow();
        assertEquals(FlagEffect.MODIFIED, ofSpec1.effect());
        assertEquals("count == 1", ofSpec1.condition());

        // Form 2: SHL reg, CL -> OF is undefined when count > 1
        InstructionForm form2 = shl.forms().get(1);
        FlagEffectSpec ofSpec2 = form2.flags().stream()
                .filter(f -> "OF".equals(f.flagId()))
                .findFirst().orElseThrow();
        assertEquals(FlagEffect.UNDEFINED, ofSpec2.effect());
        assertEquals("count > 1", ofSpec2.condition());
    }

    @Test
    void x87InstructionsDeclareOtherState() {
        Corpus corpus = Corpus.get();

        InstructionEntry fld = corpus.findInstruction("FLD").orElseThrow();
        assertFalse(fld.forms().getFirst().otherState().isEmpty());
        assertTrue(fld.forms().getFirst().otherState().contains("x87.status.TOP"));

        InstructionEntry fsqrt = corpus.findInstruction("FSQRT").orElseThrow();
        assertFalse(fsqrt.forms().getFirst().otherState().isEmpty());
        assertTrue(fsqrt.forms().getFirst().otherState().contains("x87.status.C1"));

        InstructionEntry fcom = corpus.findInstruction("FCOM").orElseThrow();
        assertFalse(fcom.forms().getFirst().otherState().isEmpty());
        assertTrue(fcom.forms().getFirst().otherState().contains("x87.status.C0"));

        InstructionEntry fstsw = corpus.findInstruction("FSTSW").orElseThrow();
        assertFalse(fstsw.forms().getFirst().otherState().isEmpty());
        assertTrue(fstsw.forms().getFirst().otherState().contains("x87.status"));
    }
}
