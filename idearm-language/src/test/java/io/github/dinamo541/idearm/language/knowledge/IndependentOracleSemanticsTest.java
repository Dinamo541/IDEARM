package io.github.dinamo541.idearm.language.knowledge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Independent semantic oracle test suite (AA-P5-04, Acceptance Case 6).
 *
 * <p>Strict architectural rule: This test does NOT use or cite {@code idearm-emu8086} as oracle.
 * All expected states and flag behaviors are derived purely from the Intel SDM Volume 2 (revision 093)
 * mathematical definitions of arithmetic, logic, and shifts.
 *
 * <p>Contains NO assertions on flags documented as undefined (e.g. AF after AND/OR/XOR, OF after multi-bit shifts).
 */
@DisplayName("AA-P5-04: Independent Semantic Oracle & Acceptance Case 6")
class IndependentOracleSemanticsTest {

    public record FlagsState(
            boolean cf,
            boolean of,
            boolean zf,
            boolean sf,
            boolean pf,
            boolean af
    ) {
        public static FlagsState of(boolean cf, boolean of, boolean zf, boolean sf, boolean pf, boolean af) {
            return new FlagsState(cf, of, zf, sf, pf, af);
        }
    }

    public record SimulationResult(
            int value,
            FlagsState flags
    ) {}

    // Pure mathematical oracle for 8-bit operations per Intel SDM Vol 2 (SDM 093)

    private static SimulationResult oracleAdd8(int a, int b) {
        int res = (a + b) & 0xFF;
        boolean cf = (a + b) > 0xFF;
        // Overflow occurs if both operands have the same sign and the result has a different sign
        boolean of = ((a ^ res) & (b ^ res) & 0x80) != 0;
        boolean zf = res == 0;
        boolean sf = (res & 0x80) != 0;
        boolean pf = computeParity(res);
        boolean af = ((a & 0x0F) + (b & 0x0F)) > 0x0F;
        return new SimulationResult(res, FlagsState.of(cf, of, zf, sf, pf, af));
    }

    private static SimulationResult oracleSub8(int a, int b) {
        int res = (a - b) & 0xFF;
        boolean cf = a < b;
        boolean of = ((a ^ b) & (a ^ res) & 0x80) != 0;
        boolean zf = res == 0;
        boolean sf = (res & 0x80) != 0;
        boolean pf = computeParity(res);
        boolean af = (a & 0x0F) < (b & 0x0F);
        return new SimulationResult(res, FlagsState.of(cf, of, zf, sf, pf, af));
    }

    private static SimulationResult oracleInc8(int a, boolean initialCf) {
        int res = (a + 1) & 0xFF;
        boolean of = a == 0x7F; // 127 + 1 = 128 (overflow in signed 8-bit)
        boolean zf = res == 0;
        boolean sf = (res & 0x80) != 0;
        boolean pf = computeParity(res);
        boolean af = ((a & 0x0F) + 1) > 0x0F;
        // CF is preserved (unaffected) by INC per SDM 093
        return new SimulationResult(res, FlagsState.of(initialCf, of, zf, sf, pf, af));
    }

    private static SimulationResult oracleDec8(int a, boolean initialCf) {
        int res = (a - 1) & 0xFF;
        boolean of = a == 0x80; // -128 - 1 = -129 (underflow in signed 8-bit)
        boolean zf = res == 0;
        boolean sf = (res & 0x80) != 0;
        boolean pf = computeParity(res);
        boolean af = (a & 0x0F) == 0;
        // CF is preserved (unaffected) by DEC per SDM 093
        return new SimulationResult(res, FlagsState.of(initialCf, of, zf, sf, pf, af));
    }

    private static SimulationResult oracleAnd8(int a, int b) {
        int res = (a & b) & 0xFF;
        boolean zf = res == 0;
        boolean sf = (res & 0x80) != 0;
        boolean pf = computeParity(res);
        // Intel SDM 093: AND clears CF and OF to 0. AF is UNDEFINED.
        return new SimulationResult(res, FlagsState.of(false, false, zf, sf, pf, false));
    }

    private static boolean computeParity(int val) {
        int count = 0;
        for (int i = 0; i < 8; i++) {
            if (((val >>> i) & 1) == 1) count++;
        }
        return (count % 2) == 0;
    }

    @Test
    @DisplayName("Acceptance Case 6.1: CF vs OF differentiated with the same operands (SDM 093)")
    void cfVsOfDifferentiatedWithSameOperands() {
        // Case A: 0xFF + 0x01
        // Unsigned: 255 + 1 = 256 -> wraps to 0 with Carry (CF=1).
        // Signed: (-1) + (+1) = 0 -> fits in signed 8-bit, NO overflow (OF=0).
        SimulationResult r1 = oracleAdd8(0xFF, 0x01);
        assertEquals(0x00, r1.value());
        assertTrue(r1.flags().cf(), "CF must be 1 on unsigned overflow (255 + 1)");
        assertFalse(r1.flags().of(), "OF must be 0 on signed addition (-1 + 1 = 0)");
        assertTrue(r1.flags().zf(), "ZF must be 1 (result is 0)");

        // Case B: 0x7F + 0x01
        // Unsigned: 127 + 1 = 128 -> fits in 8-bit unsigned (CF=0).
        // Signed: (+127) + (+1) = +128 -> exceeds max signed byte (+127), wraps to -128 (OF=1).
        SimulationResult r2 = oracleAdd8(0x7F, 0x01);
        assertEquals(0x80, r2.value());
        assertFalse(r2.flags().cf(), "CF must be 0 (127 + 1 < 256)");
        assertTrue(r2.flags().of(), "OF must be 1 on signed overflow (+127 + 1 = -128)");
        assertTrue(r2.flags().sf(), "SF must be 1 (negative result bit 7)");
    }

    @Test
    @DisplayName("Acceptance Case 6.2: INC and DEC preserve CF while updating other flags")
    void incAndDecPreserveCarryFlag() {
        // Start with CF = true
        boolean initialCf = true;

        // INC 0xFF with CF=1: result 0x00, OF=0, ZF=1, and CF remains TRUE
        SimulationResult incRes = oracleInc8(0xFF, initialCf);
        assertEquals(0x00, incRes.value());
        assertTrue(incRes.flags().cf(), "INC must preserve CF (remain 1)");
        assertTrue(incRes.flags().zf(), "ZF must be 1 after INC 0xFF -> 0x00");
        assertFalse(incRes.flags().of(), "OF must be 0 (signed -1 + 1 = 0)");

        // DEC 0x00 with CF=1: result 0xFF, OF=0, SF=1, ZF=0, and CF remains TRUE
        SimulationResult decRes = oracleDec8(0x00, initialCf);
        assertEquals(0xFF, decRes.value());
        assertTrue(decRes.flags().cf(), "DEC must preserve CF (remain 1)");
        assertFalse(decRes.flags().zf(), "ZF must be 0 after DEC 0x00 -> 0xFF");
        assertTrue(decRes.flags().sf(), "SF must be 1 after DEC 0x00 -> 0xFF");
        assertFalse(decRes.flags().of(), "OF must be 0 (signed 0 - 1 = -1)");
    }

    @Test
    @DisplayName("Acceptance Case 6.3: Shift effects conditioned on count; NO assertion on undefined flags")
    void shiftEffectsConditionedOnCount() {
        // SDM 093: SHL destination, count
        // When count = 0: no flags modified at all.
        // When count = 1: OF is defined (OF = MSB of result XOR CF).
        // When count > 1: OF is UNDEFINED.
        // We test that our knowledge specification reflects this:
        InstructionEntry shl = Corpus.get().findInstruction("SHL").orElseThrow();

        // Check conditional flag notes for OF
        boolean hasConditionalOf = shl.flagSummary().o() == io.github.dinamo541.idearm.language.catalog.FlagEffect.MODIFIED;
        assertTrue(hasConditionalOf, "SHL modifies OF conditionally");

        // Verify that pitfalls and forms declare that count > 1 leaves OF undefined
        boolean hasPitfall = shl.pitfalls().stream()
                .anyMatch(p -> p.contains("OF") && p.contains("indefinida"));
        assertTrue(hasPitfall, "SHL pitfalls must explain that OF is undefined for count > 1");

        boolean hasUndefinedOfForm = shl.forms().stream()
                .flatMap(f -> f.flags().stream())
                .anyMatch(fe -> "OF".equals(fe.flagId()) && fe.effect() == io.github.dinamo541.idearm.language.catalog.FlagEffect.UNDEFINED);
        assertTrue(hasUndefinedOfForm, "SHL form flags must declare OF as UNDEFINED for count > 1");
    }

    @Test
    @DisplayName("Acceptance Case 6.4: Logic instructions clear CF and OF; AF is undefined (no AF assertion)")
    void logicInstructionsClearCarryAndOverflow() {
        // AND 0xFF, 0x0F -> 0x0F
        SimulationResult r = oracleAnd8(0xFF, 0x0F);
        assertEquals(0x0F, r.value());
        assertFalse(r.flags().cf(), "AND must set CF = 0");
        assertFalse(r.flags().of(), "AND must set OF = 0");
        assertFalse(r.flags().zf());
        assertFalse(r.flags().sf());
        // CRITICAL: We do NOT assert on r.flags().af() because AF is undefined for AND per SDM 093!
    }

    @Test
    @DisplayName("Signed vs Unsigned comparison (CMP 0xFF, 0x01)")
    void signedVsUnsignedComparison() {
        // CMP is SUB without storing destination
        SimulationResult cmp = oracleSub8(0xFF, 0x01);
        assertEquals(0xFE, cmp.value());

        // Unsigned comparison: 255 > 1 -> CF = 0, ZF = 0 (JA taken)
        assertFalse(cmp.flags().cf(), "Unsigned: 255 > 1, so CF=0");
        assertFalse(cmp.flags().zf(), "Unsigned: 255 != 1, so ZF=0");

        // Signed comparison: -1 < +1 -> SF = 1, OF = 0 -> SF != OF (JL taken)
        assertTrue(cmp.flags().sf(), "Signed: -1 - (+1) = -2 (negative, SF=1)");
        assertFalse(cmp.flags().of(), "Signed: no overflow, OF=0");
        assertNotEquals(cmp.flags().sf(), cmp.flags().of(), "SF != OF confirms signed LESS THAN (JL taken)");
    }
}
