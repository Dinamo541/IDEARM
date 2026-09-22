package io.github.dinamo541.idearm.domain.debug;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RegisterStateTest {

    @Test
    void masksValuesTo16Bits() {
        RegisterState state = new RegisterState(
                0x12345, 0xFFFF00, 0x10000, 0xABCD,
                0, 0, 0, 0, 0,
                0, 0, 0, 0, 0
        );
        assertEquals(0x2345, state.ax());
        assertEquals(0xFF00, state.bx());
        assertEquals(0x0000, state.cx());
        assertEquals(0xABCD, state.dx());
    }

    @Test
    void extractsHighAndLowBytes() {
        RegisterState state = new RegisterState(
                0x1234, 0x5678, 0x9ABC, 0xDEF0,
                0, 0, 0, 0, 0,
                0, 0, 0, 0, 0
        );
        assertEquals(0x12, state.ah());
        assertEquals(0x34, state.al());
        assertEquals(0x56, state.bh());
        assertEquals(0x78, state.bl());
        assertEquals(0x9A, state.ch());
        assertEquals(0xBC, state.cl());
        assertEquals(0xDE, state.dh());
        assertEquals(0xF0, state.dl());
    }

    @Test
    void extractsConditionCodeFlags() {
        // CF=1 (bit 0), PF=1 (bit 2), AF=1 (bit 4), ZF=1 (bit 6), SF=1 (bit 7),
        // TF=1 (bit 8), IF=1 (bit 9), DF=1 (bit 10), OF=1 (bit 11)
        int allFlags = 0x0001 | 0x0004 | 0x0010 | 0x0040 | 0x0080 | 0x0100 | 0x0200 | 0x0400 | 0x0800;
        RegisterState state = new RegisterState(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, allFlags);

        assertTrue(state.cf());
        assertTrue(state.pf());
        assertTrue(state.af());
        assertTrue(state.zf());
        assertTrue(state.sf());
        assertTrue(state.tf());
        assertTrue(state.ifFlag());
        assertTrue(state.df());
        assertTrue(state.of());

        RegisterState zeroFlags = new RegisterState(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        assertFalse(zeroFlags.cf());
        assertFalse(zeroFlags.pf());
        assertFalse(zeroFlags.af());
        assertFalse(zeroFlags.zf());
        assertFalse(zeroFlags.sf());
        assertFalse(zeroFlags.tf());
        assertFalse(zeroFlags.ifFlag());
        assertFalse(zeroFlags.df());
        assertFalse(zeroFlags.of());
    }

    @Test
    void initialDosStateProvidesStandardValues() {
        RegisterState initial = RegisterState.initialDosState();
        assertEquals(0x0100, initial.ip());
        assertEquals(0x0710, initial.cs());
        assertEquals(0xFFFE, initial.sp());
        assertTrue(initial.ifFlag());
    }

    @Test
    void detectsChangedRegistersAndFlags() {
        RegisterState s1 = new RegisterState(0x1000, 0x2000, 0, 0, 0, 0, 0, 0, 0x100, 0, 0, 0, 0, 0x0202);
        RegisterState s2 = new RegisterState(0x1001, 0x2000, 0, 0, 0, 0, 0, 0, 0x102, 0, 0, 0, 0, 0x0243); // AX, IP, CF, ZF changed

        assertTrue(s2.isChanged("AX", s1));
        assertFalse(s2.isChanged("BX", s1));
        assertTrue(s2.isChanged("IP", s1));
        assertTrue(s2.isChanged("CF", s1));
        assertTrue(s2.isChanged("ZF", s1));
        assertFalse(s2.isChanged("OF", s1));

        var diff = s2.changedRegisters(s1);
        assertTrue(diff.contains("AX"));
        assertTrue(diff.contains("IP"));
        assertTrue(diff.contains("CF"));
        assertTrue(diff.contains("ZF"));
        assertFalse(diff.contains("BX"));
    }

    @Test
    void supportsExtended64BitRegisters() {
        var s1 = new RegisterState(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x0202,
                java.util.Map.of("RAX", 0x1122334455667788L, "RBX", 0x100L));
        var s2 = new RegisterState(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x0202,
                java.util.Map.of("RAX", 0x99AABBCCDDEEFF00L, "RBX", 0x100L));

        assertEquals(0x1122334455667788L, s1.getExtended("RAX", 0));
        assertEquals(0x100L, s1.getExtended("RBX", 0));
        assertEquals(-1L, s1.getExtended("RCX", -1L));

        assertTrue(s2.isChanged("RAX", s1));
        assertFalse(s2.isChanged("RBX", s1));

        var diff = s2.changedRegisters(s1);
        assertTrue(diff.contains("RAX"));
        assertFalse(diff.contains("RBX"));
    }

    @Test
    void buildsFromExtendedMap() {
        var regs = java.util.Map.of(
                "RAX", 0x1122334455667788L,
                "RBX", 0x2233445566778899L,
                "RIP", 0x0000000000401050L,
                "RFLAGS", 0x0246L // ZF=1, PF=1
        );
        RegisterState state = RegisterState.fromExtended(regs);
        assertEquals(0x7788, state.ax());
        assertEquals(0x8899, state.bx());
        assertEquals(0x1050, state.ip());
        assertEquals(0x1122334455667788L, state.getExtended("RAX", 0));
        assertEquals(0x0000000000401050L, state.getExtended("RIP", 0));
        assertTrue(state.zf());
        assertTrue(state.pf());
        assertFalse(state.cf());
    }
}
