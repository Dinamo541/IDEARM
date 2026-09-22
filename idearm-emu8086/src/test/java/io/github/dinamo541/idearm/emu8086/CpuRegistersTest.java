package io.github.dinamo541.idearm.emu8086;

import io.github.dinamo541.idearm.domain.debug.RegisterState;
import io.github.dinamo541.idearm.emu8086.cpu.CpuRegisters;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CpuRegistersTest {

    @Test
    void test16BitAnd8BitGeneralPurposeRegisters() {
        CpuRegisters regs = new CpuRegisters();
        regs.ax = 0x1234;
        assertEquals(0x12, regs.getAh());
        assertEquals(0x34, regs.getAl());

        regs.setAh(0xAB);
        assertEquals(0xAB34, regs.ax);
        regs.setAl(0xCD);
        assertEquals(0xABCD, regs.ax);

        regs.bx = 0x5678;
        assertEquals(0x56, regs.getBh());
        assertEquals(0x78, regs.getBl());

        regs.cx = 0x9ABC;
        assertEquals(0x9A, regs.getCh());
        assertEquals(0xBC, regs.getCl());

        regs.dx = 0xDEF0;
        assertEquals(0xDE, regs.getDh());
        assertEquals(0xF0, regs.getDl());
    }

    @Test
    void testRegisterIndices() {
        CpuRegisters regs = new CpuRegisters();
        for (int i = 0; i < 8; i++) {
            regs.setReg16(i, 0x1000 + i);
            assertEquals(0x1000 + i, regs.getReg16(i));
        }
        assertEquals(0x1000, regs.ax);
        assertEquals(0x1001, regs.cx);
        assertEquals(0x1002, regs.dx);
        assertEquals(0x1003, regs.bx);
        assertEquals(0x1004, regs.sp);
        assertEquals(0x1005, regs.bp);
        assertEquals(0x1006, regs.si);
        assertEquals(0x1007, regs.di);
    }

    @Test
    void testFlags() {
        CpuRegisters regs = new CpuRegisters();
        assertFalse(regs.isCf());
        assertFalse(regs.isZf());
        assertTrue(regs.isIf()); // default IF=1

        regs.setCf(true);
        regs.setZf(true);
        regs.setSf(true);
        regs.setOf(true);
        regs.setAf(true);
        regs.setPf(true);
        regs.setDf(true);

        assertTrue(regs.isCf());
        assertTrue(regs.isZf());
        assertTrue(regs.isSf());
        assertTrue(regs.isOf());
        assertTrue(regs.isAf());
        assertTrue(regs.isPf());
        assertTrue(regs.isDf());

        RegisterState domainState = regs.toDomainRegisterState();
        assertTrue(domainState.cf());
        assertTrue(domainState.zf());
        assertTrue(domainState.sf());
        assertTrue(domainState.of());
    }
}
