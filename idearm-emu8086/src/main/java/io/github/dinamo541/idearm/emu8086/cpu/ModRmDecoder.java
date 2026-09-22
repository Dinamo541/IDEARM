package io.github.dinamo541.idearm.emu8086.cpu;

/**
 * Decodes 8086 ModR/M byte and calculates effective addresses for all memory and register addressing modes.
 */
public final class ModRmDecoder {

    public record DecodedModRm(
            int mod,
            int reg,
            int rm,
            boolean isRegister,
            int segment,
            int offset,
            int bytesConsumed) {
    }

    private ModRmDecoder() {}

    /**
     * Decodes the ModR/M byte at CS:IP, reading any displacement bytes from memory.
     *
     * @param modRmByte the raw ModR/M byte
     * @param regs CPU registers
     * @param memory Real mode memory
     * @param segmentOverride segment register override (-1 if none, 0=ES, 1=CS, 2=SS, 3=DS)
     * @return DecodedModRm description
     */
    public static DecodedModRm decode(int modRmByte, CpuRegisters regs, RealModeMemory memory, int segmentOverride) {
        int mod = (modRmByte >> 6) & 3;
        int reg = (modRmByte >> 3) & 7;
        int rm = modRmByte & 7;

        if (mod == 3) {
            // Register direct mode (no memory address, 0 displacement bytes)
            return new DecodedModRm(mod, reg, rm, true, 0, 0, 0);
        }

        int defaultSeg = regs.ds;
        int baseOffset = 0;
        int bytesConsumed = 0;

        if (mod == 0 && rm == 6) {
            // Direct 16-bit addressing [disp16]
            int disp16 = memory.read16(regs.cs, regs.ip);
            bytesConsumed = 2;
            baseOffset = disp16;
            defaultSeg = regs.ds;
        } else {
            // Calculate base from rm
            switch (rm) {
                case 0 -> { baseOffset = (regs.bx + regs.si) & 0xFFFF; defaultSeg = regs.ds; }
                case 1 -> { baseOffset = (regs.bx + regs.di) & 0xFFFF; defaultSeg = regs.ds; }
                case 2 -> { baseOffset = (regs.bp + regs.si) & 0xFFFF; defaultSeg = regs.ss; }
                case 3 -> { baseOffset = (regs.bp + regs.di) & 0xFFFF; defaultSeg = regs.ss; }
                case 4 -> { baseOffset = regs.si & 0xFFFF; defaultSeg = regs.ds; }
                case 5 -> { baseOffset = regs.di & 0xFFFF; defaultSeg = regs.ds; }
                case 6 -> { baseOffset = regs.bp & 0xFFFF; defaultSeg = regs.ss; }
                case 7 -> { baseOffset = regs.bx & 0xFFFF; defaultSeg = regs.ds; }
            }

            if (mod == 1) {
                // 8-bit signed displacement
                byte disp8 = (byte) memory.read8(regs.cs, regs.ip);
                bytesConsumed = 1;
                baseOffset = (baseOffset + disp8) & 0xFFFF;
            } else if (mod == 2) {
                // 16-bit displacement
                int disp16 = memory.read16(regs.cs, regs.ip);
                bytesConsumed = 2;
                baseOffset = (baseOffset + disp16) & 0xFFFF;
            }
        }

        int effectiveSeg = segmentOverride >= 0 ? regs.getSeg(segmentOverride) : defaultSeg;
        return new DecodedModRm(mod, reg, rm, false, effectiveSeg & 0xFFFF, baseOffset & 0xFFFF, bytesConsumed);
    }

    public static int readRm8(CpuRegisters regs, RealModeMemory mem, DecodedModRm ea) {
        if (ea.isRegister()) {
            return regs.getReg8(ea.rm());
        }
        return mem.read8(ea.segment(), ea.offset());
    }

    public static int readRm16(CpuRegisters regs, RealModeMemory mem, DecodedModRm ea) {
        if (ea.isRegister()) {
            return regs.getReg16(ea.rm());
        }
        return mem.read16(ea.segment(), ea.offset());
    }

    public static void writeRm8(CpuRegisters regs, RealModeMemory mem, DecodedModRm ea, int value) {
        if (ea.isRegister()) {
            regs.setReg8(ea.rm(), value);
        } else {
            mem.write8(ea.segment(), ea.offset(), value);
        }
    }

    public static void writeRm16(CpuRegisters regs, RealModeMemory mem, DecodedModRm ea, int value) {
        if (ea.isRegister()) {
            regs.setReg16(ea.rm(), value);
        } else {
            mem.write16(ea.segment(), ea.offset(), value);
        }
    }
}
