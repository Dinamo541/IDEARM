package io.github.dinamo541.idearm.emu8086.cpu;

import io.github.dinamo541.idearm.domain.debug.RegisterState;

/**
 * Mutable representation of 8086 CPU registers and FLAGS.
 */
public final class CpuRegisters {

    // General purpose 16-bit registers
    public int ax;
    public int bx;
    public int cx;
    public int dx;

    // Pointer and index registers
    public int sp = 0xFFFE;
    public int bp;
    public int si;
    public int di;

    // Segment registers
    public int cs;
    public int ds;
    public int ss;
    public int es;

    // Instruction pointer
    public int ip;

    // FLAGS register (default IF=1, reserved bit 1=1 -> 0x0202)
    public int flags = 0x0202;

    public static final int FLAG_CF = 0x0001; // Carry
    public static final int FLAG_PF = 0x0004; // Parity
    public static final int FLAG_AF = 0x0010; // Auxiliary carry
    public static final int FLAG_ZF = 0x0040; // Zero
    public static final int FLAG_SF = 0x0080; // Sign
    public static final int FLAG_TF = 0x0100; // Trap
    public static final int FLAG_IF = 0x0200; // Interrupt enable
    public static final int FLAG_DF = 0x0400; // Direction
    public static final int FLAG_OF = 0x0800; // Overflow

    public CpuRegisters() {}

    // 8-bit register accessors
    public int getAl() { return ax & 0xFF; }
    public void setAl(int val) { ax = (ax & 0xFF00) | (val & 0xFF); }

    public int getAh() { return (ax >> 8) & 0xFF; }
    public void setAh(int val) { ax = (ax & 0x00FF) | ((val & 0xFF) << 8); }

    public int getBl() { return bx & 0xFF; }
    public void setBl(int val) { bx = (bx & 0xFF00) | (val & 0xFF); }

    public int getBh() { return (bx >> 8) & 0xFF; }
    public void setBh(int val) { bx = (bx & 0x00FF) | ((val & 0xFF) << 8); }

    public int getCl() { return cx & 0xFF; }
    public void setCl(int val) { cx = (cx & 0xFF00) | (val & 0xFF); }

    public int getCh() { return (cx >> 8) & 0xFF; }
    public void setCh(int val) { cx = (cx & 0x00FF) | ((val & 0xFF) << 8); }

    public int getDl() { return dx & 0xFF; }
    public void setDl(int val) { dx = (dx & 0xFF00) | (val & 0xFF); }

    public int getDh() { return (dx >> 8) & 0xFF; }
    public void setDh(int val) { dx = (dx & 0x00FF) | ((val & 0xFF) << 8); }

    // 16-bit register getters/setters by standard index (0=AX, 1=CX, 2=DX, 3=BX, 4=SP, 5=BP, 6=SI, 7=DI)
    public int getReg16(int index) {
        return switch (index & 7) {
            case 0 -> ax & 0xFFFF;
            case 1 -> cx & 0xFFFF;
            case 2 -> dx & 0xFFFF;
            case 3 -> bx & 0xFFFF;
            case 4 -> sp & 0xFFFF;
            case 5 -> bp & 0xFFFF;
            case 6 -> si & 0xFFFF;
            case 7 -> di & 0xFFFF;
            default -> 0;
        };
    }

    public void setReg16(int index, int val) {
        int masked = val & 0xFFFF;
        switch (index & 7) {
            case 0 -> ax = masked;
            case 1 -> cx = masked;
            case 2 -> dx = masked;
            case 3 -> bx = masked;
            case 4 -> sp = masked;
            case 5 -> bp = masked;
            case 6 -> si = masked;
            case 7 -> di = masked;
        }
    }

    // 8-bit register getters/setters by standard index (0=AL, 1=CL, 2=DL, 3=BL, 4=AH, 5=CH, 6=DH, 7=BH)
    public int getReg8(int index) {
        return switch (index & 7) {
            case 0 -> getAl();
            case 1 -> getCl();
            case 2 -> getDl();
            case 3 -> getBl();
            case 4 -> getAh();
            case 5 -> getCh();
            case 6 -> getDh();
            case 7 -> getBh();
            default -> 0;
        };
    }

    public void setReg8(int index, int val) {
        switch (index & 7) {
            case 0 -> setAl(val);
            case 1 -> setCl(val);
            case 2 -> setDl(val);
            case 3 -> setBl(val);
            case 4 -> setAh(val);
            case 5 -> setCh(val);
            case 6 -> setDh(val);
            case 7 -> setBh(val);
        }
    }

    // Segment register by index (0=ES, 1=CS, 2=SS, 3=DS)
    public int getSeg(int index) {
        return switch (index & 3) {
            case 0 -> es & 0xFFFF;
            case 1 -> cs & 0xFFFF;
            case 2 -> ss & 0xFFFF;
            case 3 -> ds & 0xFFFF;
            default -> 0;
        };
    }

    public void setSeg(int index, int val) {
        int masked = val & 0xFFFF;
        switch (index & 3) {
            case 0 -> es = masked;
            case 1 -> cs = masked;
            case 2 -> ss = masked;
            case 3 -> ds = masked;
        }
    }

    // Flag helpers
    public boolean isCf() { return (flags & FLAG_CF) != 0; }
    public void setCf(boolean set) { setFlagBit(FLAG_CF, set); }

    public boolean isPf() { return (flags & FLAG_PF) != 0; }
    public void setPf(boolean set) { setFlagBit(FLAG_PF, set); }

    public boolean isAf() { return (flags & FLAG_AF) != 0; }
    public void setAf(boolean set) { setFlagBit(FLAG_AF, set); }

    public boolean isZf() { return (flags & FLAG_ZF) != 0; }
    public void setZf(boolean set) { setFlagBit(FLAG_ZF, set); }

    public boolean isSf() { return (flags & FLAG_SF) != 0; }
    public void setSf(boolean set) { setFlagBit(FLAG_SF, set); }

    public boolean isTf() { return (flags & FLAG_TF) != 0; }
    public void setTf(boolean set) { setFlagBit(FLAG_TF, set); }

    public boolean isIf() { return (flags & FLAG_IF) != 0; }
    public void setIf(boolean set) { setFlagBit(FLAG_IF, set); }

    public boolean isDf() { return (flags & FLAG_DF) != 0; }
    public void setDf(boolean set) { setFlagBit(FLAG_DF, set); }

    public boolean isOf() { return (flags & FLAG_OF) != 0; }
    public void setOf(boolean set) { setFlagBit(FLAG_OF, set); }

    private void setFlagBit(int mask, boolean set) {
        if (set) {
            flags |= mask;
        } else {
            flags &= ~mask;
        }
        // Keep bit 1 always 1
        flags |= 0x0002;
    }

    public void setFlags(int newFlags) {
        this.flags = (newFlags & 0xFFFF) | 0x0002;
    }

    public RegisterState toDomainRegisterState() {
        return new RegisterState(
                ax, bx, cx, dx,
                si, di, bp, sp, ip,
                cs, ds, es, ss,
                flags
        );
    }
}
