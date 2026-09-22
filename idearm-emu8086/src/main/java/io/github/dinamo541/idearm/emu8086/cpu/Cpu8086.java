package io.github.dinamo541.idearm.emu8086.cpu;

/**
 * Cycle-accurate 8086 Real Mode CPU emulator.
 * Implements fetch-decode-execute loop, flags calculation, segment overrides, and repeat prefixes.
 */
public final class Cpu8086 {

    public enum State {
        RUNNING,
        HALTED,
        TERMINATED,
        /** Stopped on an instruction the program cannot continue from; {@link #fault()} says why. */
        FAULTED
    }

    /**
     * Why the CPU stopped on an instruction: a stable code (translated by the user interface as
     * {@code diagnostic.<code>}), an English explanation, the values it mentions, and where that instruction starts.
     */
    public record Fault(String code, String message, java.util.List<String> arguments, int cs, int ip) {
        public Fault {
            arguments = java.util.List.copyOf(arguments);
        }
    }

    /** {@code INT 3} with no handler of the program's own: the debugger pauses there, like on a breakpoint. */
    public static final String BREAKPOINT = "emu.breakpoint";

    @FunctionalInterface
    public interface InterruptHandler {
        /**
         * Invoked when an INT instruction is executed.
         *
         * @param intNumber interrupt vector number (0x00 - 0xFF)
         * @param cpu reference to CPU
         * @return true if the interrupt was handled by the host, false to dispatch to real-mode IDT
         */
        boolean handleInterrupt(int intNumber, Cpu8086 cpu);
    }

    private final CpuRegisters registers;
    private final RealModeMemory memory;
    private InterruptHandler interruptHandler;
    private State state = State.RUNNING;
    private int exitCode = 0;
    private long instructionsExecuted = 0;
    private int callDepth = 0;
    private Fault fault;
    /** Where the instruction being executed starts, prefixes included. */
    private int instructionCs;
    private int instructionIp;

    // Precomputed parity table for fast PF lookup
    private static final boolean[] PARITY = new boolean[256];
    static {
        for (int i = 0; i < 256; i++) {
            int bits = 0;
            for (int b = 0; b < 8; b++) {
                if ((i & (1 << b)) != 0) bits++;
            }
            PARITY[i] = (bits % 2) == 0;
        }
    }

    public Cpu8086(CpuRegisters registers, RealModeMemory memory) {
        this.registers = registers;
        this.memory = memory;
    }

    public CpuRegisters registers() {
        return registers;
    }

    public RealModeMemory memory() {
        return memory;
    }

    public State state() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public int exitCode() {
        return exitCode;
    }

    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }

    public void setInterruptHandler(InterruptHandler handler) {
        this.interruptHandler = handler;
    }

    public long getInstructionsExecuted() {
        return instructionsExecuted;
    }

    /** Why the CPU is {@link State#FAULTED}, or {@code null}. */
    public Fault fault() {
        return fault;
    }

    /**
     * Stops on the current instruction. {@code CS:IP} go back to its first byte, so the registers, the editor and the
     * explanation all point at the instruction that failed.
     */
    public void fail(String code, String message, Object... arguments) {
        var values = new java.util.ArrayList<String>();
        for (Object argument : arguments) {
            values.add(String.valueOf(argument));
        }
        registers.cs = instructionCs;
        registers.ip = instructionIp;
        fault = new Fault(code, message, values, instructionCs, instructionIp);
        state = State.FAULTED;
    }

    /** Where the instruction being executed (or the last one executed) starts. */
    public int instructionIp() {
        return instructionIp;
    }

    /** Continues after an {@code INT 3} pause: the instruction after it runs next. */
    public void continueAfterBreakpoint() {
        if (state == State.FAULTED && fault != null && BREAKPOINT.equals(fault.code())) {
            registers.ip = (fault.ip() + 1) & 0xFFFF;
            fault = null;
            state = State.RUNNING;
        }
    }

    public int getCallDepth() {
        return callDepth;
    }

    private int fetch8() {
        int val = memory.read8(registers.cs, registers.ip);
        registers.ip = (registers.ip + 1) & 0xFFFF;
        return val;
    }

    private int fetch16() {
        int val = memory.read16(registers.cs, registers.ip);
        registers.ip = (registers.ip + 2) & 0xFFFF;
        return val;
    }

    /**
     * Executes one instruction at CS:IP.
     *
     * @return true if an instruction was executed, false if CPU is halted or terminated
     */
    public boolean step() {
        if (state != State.RUNNING) {
            return false;
        }

        instructionCs = registers.cs;
        instructionIp = registers.ip;
        int segmentOverride = -1; // -1: none, 0=ES, 1=CS, 2=SS, 3=DS
        int repPrefix = 0;        // 0: none, 0xF2: REPNE/REPNZ, 0xF3: REP/REPE/REPZ

        int opcode;
        while (true) {
            opcode = fetch8();
            if (opcode == 0x26) {
                segmentOverride = 0; // ES:
            } else if (opcode == 0x2E) {
                segmentOverride = 1; // CS:
            } else if (opcode == 0x36) {
                segmentOverride = 2; // SS:
            } else if (opcode == 0x3E) {
                segmentOverride = 3; // DS:
            } else if (opcode == 0xF2 || opcode == 0xF3) {
                repPrefix = opcode;
            } else if (opcode == 0xF0) {
                // LOCK prefix ignored
            } else {
                break;
            }
        }

        executeOpcode(opcode, segmentOverride, repPrefix);
        instructionsExecuted++;
        return true;
    }

    private void executeOpcode(int op, int segOverride, int rep) {
        switch (op) {
            // ADD
            case 0x00 -> opAddRm8R8(segOverride);
            case 0x01 -> opAddRm16R16(segOverride);
            case 0x02 -> opAddR8Rm8(segOverride);
            case 0x03 -> opAddR16Rm16(segOverride);
            case 0x04 -> registers.setAl(add8(registers.getAl(), fetch8()));
            case 0x05 -> registers.ax = add16(registers.ax, fetch16());

            // PUSH/POP ES
            case 0x06 -> memory.push(registers, registers.es);
            case 0x07 -> registers.es = memory.pop(registers);

            // OR
            case 0x08 -> opOrRm8R8(segOverride);
            case 0x09 -> opOrRm16R16(segOverride);
            case 0x0A -> opOrR8Rm8(segOverride);
            case 0x0B -> opOrR16Rm16(segOverride);
            case 0x0C -> registers.setAl(or8(registers.getAl(), fetch8()));
            case 0x0D -> registers.ax = or16(registers.ax, fetch16());

            // PUSH CS
            case 0x0E -> memory.push(registers, registers.cs);

            // ADC
            case 0x10 -> opAdcRm8R8(segOverride);
            case 0x11 -> opAdcRm16R16(segOverride);
            case 0x12 -> opAdcR8Rm8(segOverride);
            case 0x13 -> opAdcR16Rm16(segOverride);
            case 0x14 -> registers.setAl(adc8(registers.getAl(), fetch8()));
            case 0x15 -> registers.ax = adc16(registers.ax, fetch16());

            // PUSH/POP SS
            case 0x16 -> memory.push(registers, registers.ss);
            case 0x17 -> registers.ss = memory.pop(registers);

            // SBB
            case 0x18 -> opSbbRm8R8(segOverride);
            case 0x19 -> opSbbRm16R16(segOverride);
            case 0x1A -> opSbbR8Rm8(segOverride);
            case 0x1B -> opSbbR16Rm16(segOverride);
            case 0x1C -> registers.setAl(sbb8(registers.getAl(), fetch8()));
            case 0x1D -> registers.ax = sbb16(registers.ax, fetch16());

            // PUSH/POP DS
            case 0x1E -> memory.push(registers, registers.ds);
            case 0x1F -> registers.ds = memory.pop(registers);

            // AND
            case 0x20 -> opAndRm8R8(segOverride);
            case 0x21 -> opAndRm16R16(segOverride);
            case 0x22 -> opAndR8Rm8(segOverride);
            case 0x23 -> opAndR16Rm16(segOverride);
            case 0x24 -> registers.setAl(and8(registers.getAl(), fetch8()));
            case 0x25 -> registers.ax = and16(registers.ax, fetch16());

            // DAA
            case 0x27 -> opDaa();

            // SUB
            case 0x28 -> opSubRm8R8(segOverride);
            case 0x29 -> opSubRm16R16(segOverride);
            case 0x2A -> opSubR8Rm8(segOverride);
            case 0x2B -> opSubR16Rm16(segOverride);
            case 0x2C -> registers.setAl(sub8(registers.getAl(), fetch8()));
            case 0x2D -> registers.ax = sub16(registers.ax, fetch16());

            // DAS
            case 0x2F -> opDas();

            // XOR
            case 0x30 -> opXorRm8R8(segOverride);
            case 0x31 -> opXorRm16R16(segOverride);
            case 0x32 -> opXorR8Rm8(segOverride);
            case 0x33 -> opXorR16Rm16(segOverride);
            case 0x34 -> registers.setAl(xor8(registers.getAl(), fetch8()));
            case 0x35 -> registers.ax = xor16(registers.ax, fetch16());

            // AAA
            case 0x37 -> opAaa();

            // CMP
            case 0x38 -> opCmpRm8R8(segOverride);
            case 0x39 -> opCmpRm16R16(segOverride);
            case 0x3A -> opCmpR8Rm8(segOverride);
            case 0x3B -> opCmpR16Rm16(segOverride);
            case 0x3C -> sub8(registers.getAl(), fetch8());
            case 0x3D -> sub16(registers.ax, fetch16());

            // AAS
            case 0x3F -> opAas();

            // INC r16 (0x40 - 0x47)
            case 0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47 -> {
                int r = op & 7;
                registers.setReg16(r, inc16(registers.getReg16(r)));
            }

            // DEC r16 (0x48 - 0x4F)
            case 0x48, 0x49, 0x4A, 0x4B, 0x4C, 0x4D, 0x4E, 0x4F -> {
                int r = op & 7;
                registers.setReg16(r, dec16(registers.getReg16(r)));
            }

            // PUSH r16 (0x50 - 0x57)
            case 0x50, 0x51, 0x52, 0x53, 0x54, 0x55, 0x56, 0x57 ->
                memory.push(registers, registers.getReg16(op & 7));

            // POP r16 (0x58 - 0x5F)
            case 0x58, 0x59, 0x5A, 0x5B, 0x5C, 0x5D, 0x5E, 0x5F ->
                registers.setReg16(op & 7, memory.pop(registers));

            // PUSH imm16 / imm8 (80186+)
            case 0x68 -> memory.push(registers, fetch16());
            case 0x6A -> memory.push(registers, (byte) fetch8());

            // Short conditional jumps (0x70 - 0x7F)
            case 0x70 -> jumpCond(registers.isOf());                     // JO
            case 0x71 -> jumpCond(!registers.isOf());                    // JNO
            case 0x72 -> jumpCond(registers.isCf());                     // JB / JC / JNAE
            case 0x73 -> jumpCond(!registers.isCf());                    // JNB / JNC / JAE
            case 0x74 -> jumpCond(registers.isZf());                     // JZ / JE
            case 0x75 -> jumpCond(!registers.isZf());                    // JNZ / JNE
            case 0x76 -> jumpCond(registers.isCf() || registers.isZf()); // JBE / JNA
            case 0x77 -> jumpCond(!registers.isCf() && !registers.isZf());// JA / JNBE
            case 0x78 -> jumpCond(registers.isSf());                     // JS
            case 0x79 -> jumpCond(!registers.isSf());                    // JNS
            case 0x7A -> jumpCond(registers.isPf());                     // JP / JPE
            case 0x7B -> jumpCond(!registers.isPf());                    // JNP / JPO
            case 0x7C -> jumpCond(registers.isSf() != registers.isOf()); // JL / JNGE
            case 0x7D -> jumpCond(registers.isSf() == registers.isOf()); // JGE / JNL
            case 0x7E -> jumpCond(registers.isZf() || (registers.isSf() != registers.isOf())); // JLE / JNG
            case 0x7F -> jumpCond(!registers.isZf() && (registers.isSf() == registers.isOf())); // JG / JNLE

            // Group 1 immediate operations (0x80 - 0x83)
            case 0x80, 0x82 -> opGroup1(false, false, segOverride);
            case 0x81 -> opGroup1(true, false, segOverride);
            case 0x83 -> opGroup1(true, true, segOverride);

            // TEST
            case 0x84 -> opTestRm8R8(segOverride);
            case 0x85 -> opTestRm16R16(segOverride);

            // XCHG
            case 0x86 -> opXchgRm8R8(segOverride);
            case 0x87 -> opXchgRm16R16(segOverride);

            // MOV
            case 0x88 -> opMovRm8R8(segOverride);
            case 0x89 -> opMovRm16R16(segOverride);
            case 0x8A -> opMovR8Rm8(segOverride);
            case 0x8B -> opMovR16Rm16(segOverride);
            case 0x8C -> opMovRm16Seg(segOverride);
            case 0x8D -> opLea(segOverride);
            case 0x8E -> opMovSegRm16(segOverride);
            case 0x8F -> opPopRm16(segOverride);

            // NOP / XCHG AX, r16 (0x90 - 0x97)
            case 0x90 -> {} // NOP
            case 0x91, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97 -> {
                int r = op & 7;
                int tmp = registers.ax;
                registers.ax = registers.getReg16(r);
                registers.setReg16(r, tmp);
            }

            // CBW, CWD
            // The sign-extended byte must stay a 16-bit value: -1 is 0xFFFF, not an int -1 that breaks carries.
            case 0x98 -> registers.ax = ((byte) registers.getAl()) & 0xFFFF;
            case 0x99 -> registers.dx = (registers.ax & 0x8000) != 0 ? 0xFFFF : 0x0000;

            // CALL far (0x9A)
            case 0x9A -> {
                int targetIp = fetch16();
                int targetCs = fetch16();
                memory.push(registers, registers.cs);
                memory.push(registers, registers.ip);
                registers.cs = targetCs;
                registers.ip = targetIp;
                callDepth++;
            }

            // WAIT (0x9B)
            case 0x9B -> {}

            // PUSHF, POPF
            case 0x9C -> memory.push(registers, registers.flags);
            case 0x9D -> registers.setFlags(memory.pop(registers));

            // SAHF, LAHF
            case 0x9E -> {
                int ah = registers.getAh();
                registers.flags = (registers.flags & 0xFF00) | (ah & 0xD5) | 0x02;
            }
            case 0x9F -> registers.setAh(registers.flags & 0xFF);

            // MOV accum, [disp16] / MOV [disp16], accum
            case 0xA0 -> {
                int off = fetch16();
                int seg = segOverride >= 0 ? registers.getSeg(segOverride) : registers.ds;
                registers.setAl(memory.read8(seg, off));
            }
            case 0xA1 -> {
                int off = fetch16();
                int seg = segOverride >= 0 ? registers.getSeg(segOverride) : registers.ds;
                registers.ax = memory.read16(seg, off);
            }
            case 0xA2 -> {
                int off = fetch16();
                int seg = segOverride >= 0 ? registers.getSeg(segOverride) : registers.ds;
                memory.write8(seg, off, registers.getAl());
            }
            case 0xA3 -> {
                int off = fetch16();
                int seg = segOverride >= 0 ? registers.getSeg(segOverride) : registers.ds;
                memory.write16(seg, off, registers.ax);
            }

            // String operations (MOVSB, MOVSW, CMPSB, CMPSW, STOSB, STOSW, LODSB, LODSW, SCASB, SCASW)
            case 0xA4, 0xA5, 0xA6, 0xA7, 0xAA, 0xAB, 0xAC, 0xAD, 0xAE, 0xAF ->
                executeStringOp(op, segOverride, rep);

            // TEST AL/AX, imm
            case 0xA8 -> and8(registers.getAl(), fetch8());
            case 0xA9 -> and16(registers.ax, fetch16());

            // MOV r8, imm8 (0xB0 - 0xB7)
            case 0xB0, 0xB1, 0xB2, 0xB3, 0xB4, 0xB5, 0xB6, 0xB7 ->
                registers.setReg8(op & 7, fetch8());

            // MOV r16, imm16 (0xB8 - 0xBF)
            case 0xB8, 0xB9, 0xBA, 0xBB, 0xBC, 0xBD, 0xBE, 0xBF ->
                registers.setReg16(op & 7, fetch16());

            // Shift/rotate with imm8 (80186+)
            case 0xC0 -> opGroup2(false, fetch8(), segOverride);
            case 0xC1 -> opGroup2(true, fetch8(), segOverride);

            // RET imm16 / RET
            case 0xC2 -> {
                int popBytes = fetch16();
                registers.ip = memory.pop(registers);
                registers.sp = (registers.sp + popBytes) & 0xFFFF;
                callDepth--;
            }
            case 0xC3 -> {
                registers.ip = memory.pop(registers);
                callDepth--;
            }

            // LES, LDS
            case 0xC4 -> opLes(segOverride);
            case 0xC5 -> opLds(segOverride);

            // MOV rm, imm
            case 0xC6 -> {
                var ea = decodeModRm(segOverride);
                int val = fetch8();
                ModRmDecoder.writeRm8(registers, memory, ea, val);
            }
            case 0xC7 -> {
                var ea = decodeModRm(segOverride);
                int val = fetch16();
                ModRmDecoder.writeRm16(registers, memory, ea, val);
            }

            // RETF imm16 / RETF
            case 0xCA -> {
                int popBytes = fetch16();
                registers.ip = memory.pop(registers);
                registers.cs = memory.pop(registers);
                registers.sp = (registers.sp + popBytes) & 0xFFFF;
                callDepth--;
            }
            case 0xCB -> {
                registers.ip = memory.pop(registers);
                registers.cs = memory.pop(registers);
                callDepth--;
            }

            // INT 3, INT imm8, INTO, IRET
            case 0xCC -> triggerInterrupt(3);
            case 0xCD -> triggerInterrupt(fetch8());
            case 0xCE -> {
                if (registers.isOf()) triggerInterrupt(4);
            }
            case 0xCF -> {
                registers.ip = memory.pop(registers);
                registers.cs = memory.pop(registers);
                registers.setFlags(memory.pop(registers));
                callDepth--;
            }

            // Group 2 shifts/rotates (D0 - D3)
            case 0xD0 -> opGroup2(false, 1, segOverride);
            case 0xD1 -> opGroup2(true, 1, segOverride);
            case 0xD2 -> opGroup2(false, registers.getCl(), segOverride);
            case 0xD3 -> opGroup2(true, registers.getCl(), segOverride);

            // AAM, AAD
            // AAM and AAD set SF, ZF and PF from AL; AAM with a zero base is a division by zero.
            case 0xD4 -> {
                int base = fetch8();
                if (base == 0) {
                    divideError();
                    return;
                }
                int al = registers.getAl();
                registers.setAh(al / base);
                registers.setAl(al % base);
                setZfSfPf8(registers.getAl());
            }
            case 0xD5 -> {
                int base = fetch8();
                registers.setAl((registers.getAl() + registers.getAh() * base) & 0xFF);
                registers.setAh(0);
                setZfSfPf8(registers.getAl());
            }

            // XLATB (0xD7)
            case 0xD7 -> {
                int seg = segOverride >= 0 ? registers.getSeg(segOverride) : registers.ds;
                int addr = (registers.bx + registers.getAl()) & 0xFFFF;
                registers.setAl(memory.read8(seg, addr));
            }

            // LOOPNZ / LOOPNE
            case 0xE0 -> {
                byte disp = (byte) fetch8();
                registers.cx = (registers.cx - 1) & 0xFFFF;
                if (registers.cx != 0 && !registers.isZf()) {
                    registers.ip = (registers.ip + disp) & 0xFFFF;
                }
            }
            // LOOPZ / LOOPE
            case 0xE1 -> {
                byte disp = (byte) fetch8();
                registers.cx = (registers.cx - 1) & 0xFFFF;
                if (registers.cx != 0 && registers.isZf()) {
                    registers.ip = (registers.ip + disp) & 0xFFFF;
                }
            }
            // LOOP
            case 0xE2 -> {
                byte disp = (byte) fetch8();
                registers.cx = (registers.cx - 1) & 0xFFFF;
                if (registers.cx != 0) {
                    registers.ip = (registers.ip + disp) & 0xFFFF;
                }
            }
            // JCXZ
            case 0xE3 -> {
                byte disp = (byte) fetch8();
                if (registers.cx == 0) {
                    registers.ip = (registers.ip + disp) & 0xFFFF;
                }
            }

            // IN / OUT
            case 0xE4 -> { fetch8(); registers.setAl(0xFF); }
            case 0xE5 -> { fetch8(); registers.ax = 0xFFFF; }
            case 0xE6 -> fetch8();
            case 0xE7 -> fetch8();
            case 0xEC -> registers.setAl(0xFF);
            case 0xED -> registers.ax = 0xFFFF;
            case 0xEE -> {}
            case 0xEF -> {}

            // CALL near (0xE8)
            case 0xE8 -> {
                short disp = (short) fetch16();
                memory.push(registers, registers.ip);
                registers.ip = (registers.ip + disp) & 0xFFFF;
                callDepth++;
            }

            // JMP near (0xE9)
            case 0xE9 -> {
                short disp = (short) fetch16();
                registers.ip = (registers.ip + disp) & 0xFFFF;
            }

            // JMP far (0xEA)
            case 0xEA -> {
                int targetIp = fetch16();
                int targetCs = fetch16();
                registers.cs = targetCs;
                registers.ip = targetIp;
            }

            // JMP short (0xEB)
            case 0xEB -> {
                byte disp = (byte) fetch8();
                registers.ip = (registers.ip + disp) & 0xFFFF;
            }

            // HLT
            case 0xF4 -> state = State.HALTED;

            // CMC
            case 0xF5 -> registers.setCf(!registers.isCf());

            // Group 3 (0xF6, 0xF7)
            case 0xF6 -> opGroup3(false, segOverride);
            case 0xF7 -> opGroup3(true, segOverride);

            // Flag instructions
            case 0xF8 -> registers.setCf(false); // CLC
            case 0xF9 -> registers.setCf(true);  // STC
            case 0xFA -> registers.setIf(false); // CLI
            case 0xFB -> registers.setIf(true);  // STI
            case 0xFC -> registers.setDf(false); // CLD
            case 0xFD -> registers.setDf(true);  // STD

            // Group 4 (0xFE)
            case 0xFE -> opGroup4(segOverride);

            // Group 5 (0xFF)
            case 0xFF -> opGroup5(segOverride);

            default -> fail("emu.invalid-opcode",
                    String.format("The built-in emulator does not implement opcode %02Xh.", op),
                    String.format("%02X", op));
        }
    }

    /**
     * A division by zero, or a quotient too large for its register. A program that installed its own INT 0 handler
     * gets it called, as on a real 8086; otherwise the debugger stops on the instruction and explains it.
     */
    private void divideError() {
        if (vectorInstalled(0)) {
            triggerInterrupt(0);
        } else {
            fail("emu.divide-error", "Division by zero, or a quotient too large for its register.");
        }
    }

    private boolean vectorInstalled(int number) {
        return memory.readPhysical16(number * 4) != 0 || memory.readPhysical16(number * 4 + 2) != 0;
    }

    private void jumpCond(boolean condition) {
        byte disp = (byte) fetch8();
        if (condition) {
            registers.ip = (registers.ip + disp) & 0xFFFF;
        }
    }

    public void triggerInterrupt(int intNum) {
        if (interruptHandler != null && interruptHandler.handleInterrupt(intNum, this)) {
            return;
        }
        // The interrupt table starts empty: jumping through an unset vector would run whatever is at 0000:0000.
        if (!vectorInstalled(intNum)) {
            if (intNum == 3) {
                fail(BREAKPOINT, "INT 3 breakpoint.");
            } else {
                fail("emu.interrupt.unsupported", String.format(
                        "The built-in emulator does not provide INT %02Xh, and the program installed no handler for it.",
                        intNum), String.format("%02X", intNum));
            }
            return;
        }
        // Real-mode IDT dispatch
        memory.push(registers, registers.flags);
        memory.push(registers, registers.cs);
        memory.push(registers, registers.ip);
        registers.setIf(false);
        registers.setTf(false);
        registers.ip = memory.readPhysical16(intNum * 4);
        registers.cs = memory.readPhysical16(intNum * 4 + 2);
        callDepth++;
    }

    private ModRmDecoder.DecodedModRm decodeModRm(int segOverride) {
        int modrm = fetch8();
        var decoded = ModRmDecoder.decode(modrm, registers, memory, segOverride);
        registers.ip = (registers.ip + decoded.bytesConsumed()) & 0xFFFF;
        return decoded;
    }

    // --- Arithmetic & Logic Helpers ---

    private int add8(int a, int b) {
        int res = a + b;
        setFlagsAdd8(a, b, res);
        return res & 0xFF;
    }

    private int add16(int a, int b) {
        int res = a + b;
        setFlagsAdd16(a, b, res);
        return res & 0xFFFF;
    }

    private int adc8(int a, int b) {
        int c = registers.isCf() ? 1 : 0;
        int res = a + b + c;
        setFlagsAdd8(a, b + c, res);
        return res & 0xFF;
    }

    private int adc16(int a, int b) {
        int c = registers.isCf() ? 1 : 0;
        int res = a + b + c;
        setFlagsAdd16(a, b + c, res);
        return res & 0xFFFF;
    }

    private int sub8(int a, int b) {
        int res = a - b;
        setFlagsSub8(a, b, res);
        return res & 0xFF;
    }

    private int sub16(int a, int b) {
        int res = a - b;
        setFlagsSub16(a, b, res);
        return res & 0xFFFF;
    }

    private int sbb8(int a, int b) {
        int c = registers.isCf() ? 1 : 0;
        int res = a - (b + c);
        setFlagsSub8(a, b + c, res);
        return res & 0xFF;
    }

    private int sbb16(int a, int b) {
        int c = registers.isCf() ? 1 : 0;
        int res = a - (b + c);
        setFlagsSub16(a, b + c, res);
        return res & 0xFFFF;
    }

    private int inc8(int val) {
        boolean cf = registers.isCf();
        int res = add8(val, 1);
        registers.setCf(cf); // INC preserves CF
        return res;
    }

    private int inc16(int val) {
        boolean cf = registers.isCf();
        int res = add16(val, 1);
        registers.setCf(cf); // INC preserves CF
        return res;
    }

    private int dec8(int val) {
        boolean cf = registers.isCf();
        int res = sub8(val, 1);
        registers.setCf(cf); // DEC preserves CF
        return res;
    }

    private int dec16(int val) {
        boolean cf = registers.isCf();
        int res = sub16(val, 1);
        registers.setCf(cf); // DEC preserves CF
        return res;
    }

    private int and8(int a, int b) {
        int res = (a & b) & 0xFF;
        registers.setCf(false);
        registers.setOf(false);
        setZfSfPf8(res);
        return res;
    }

    private int and16(int a, int b) {
        int res = (a & b) & 0xFFFF;
        registers.setCf(false);
        registers.setOf(false);
        setZfSfPf16(res);
        return res;
    }

    private int or8(int a, int b) {
        int res = (a | b) & 0xFF;
        registers.setCf(false);
        registers.setOf(false);
        setZfSfPf8(res);
        return res;
    }

    private int or16(int a, int b) {
        int res = (a | b) & 0xFFFF;
        registers.setCf(false);
        registers.setOf(false);
        setZfSfPf16(res);
        return res;
    }

    private int xor8(int a, int b) {
        int res = (a ^ b) & 0xFF;
        registers.setCf(false);
        registers.setOf(false);
        setZfSfPf8(res);
        return res;
    }

    private int xor16(int a, int b) {
        int res = (a ^ b) & 0xFFFF;
        registers.setCf(false);
        registers.setOf(false);
        setZfSfPf16(res);
        return res;
    }

    private void setZfSfPf8(int res) {
        registers.setZf((res & 0xFF) == 0);
        registers.setSf((res & 0x80) != 0);
        registers.setPf(PARITY[res & 0xFF]);
    }

    private void setZfSfPf16(int res) {
        registers.setZf((res & 0xFFFF) == 0);
        registers.setSf((res & 0x8000) != 0);
        registers.setPf(PARITY[res & 0xFF]);
    }

    private void setFlagsAdd8(int a, int b, int res) {
        setZfSfPf8(res);
        registers.setCf((res & 0x100) != 0);
        registers.setAf(((a & 0x0F) + (b & 0x0F)) > 0x0F);
        registers.setOf(((a ^ res) & (b ^ res) & 0x80) != 0);
    }

    private void setFlagsAdd16(int a, int b, int res) {
        setZfSfPf16(res);
        registers.setCf((res & 0x10000) != 0);
        registers.setAf(((a & 0x0F) + (b & 0x0F)) > 0x0F);
        registers.setOf(((a ^ res) & (b ^ res) & 0x8000) != 0);
    }

    private void setFlagsSub8(int a, int b, int res) {
        setZfSfPf8(res);
        registers.setCf(a < b);
        registers.setAf((a & 0x0F) < (b & 0x0F));
        registers.setOf(((a ^ b) & (a ^ res) & 0x80) != 0);
    }

    private void setFlagsSub16(int a, int b, int res) {
        setZfSfPf16(res);
        registers.setCf(a < b);
        registers.setAf((a & 0x0F) < (b & 0x0F));
        registers.setOf(((a ^ b) & (a ^ res) & 0x8000) != 0);
    }

    // BCD adjustments
    private void opDaa() {
        int al = registers.getAl();
        boolean oldCf = registers.isCf();
        boolean oldAf = registers.isAf();
        if ((al & 0x0F) > 9 || oldAf) {
            al += 6;
            registers.setAf(true);
        }
        if (al > 0x9F || oldCf) {
            al += 0x60;
            registers.setCf(true);
        }
        registers.setAl(al);
        setZfSfPf8(registers.getAl());
    }

    private void opDas() {
        int al = registers.getAl();
        boolean oldCf = registers.isCf();
        boolean oldAf = registers.isAf();
        if ((al & 0x0F) > 9 || oldAf) {
            al -= 6;
            registers.setAf(true);
        }
        if (al > 0x9F || oldCf) {
            al -= 0x60;
            registers.setCf(true);
        }
        registers.setAl(al);
        setZfSfPf8(registers.getAl());
    }

    private void opAaa() {
        if ((registers.getAl() & 0x0F) > 9 || registers.isAf()) {
            registers.setAl(registers.getAl() + 6);
            registers.setAh(registers.getAh() + 1);
            registers.setAf(true);
            registers.setCf(true);
        } else {
            registers.setAf(false);
            registers.setCf(false);
        }
        registers.setAl(registers.getAl() & 0x0F);
    }

    private void opAas() {
        if ((registers.getAl() & 0x0F) > 9 || registers.isAf()) {
            registers.setAl(registers.getAl() - 6);
            registers.setAh(registers.getAh() - 1);
            registers.setAf(true);
            registers.setCf(true);
        } else {
            registers.setAf(false);
            registers.setCf(false);
        }
        registers.setAl(registers.getAl() & 0x0F);
    }

    // --- ModR/M Handlers ---

    private void opAddRm8R8(int seg) {
        var ea = decodeModRm(seg);
        int val = add8(ModRmDecoder.readRm8(registers, memory, ea), registers.getReg8(ea.reg()));
        ModRmDecoder.writeRm8(registers, memory, ea, val);
    }

    private void opAddRm16R16(int seg) {
        var ea = decodeModRm(seg);
        int val = add16(ModRmDecoder.readRm16(registers, memory, ea), registers.getReg16(ea.reg()));
        ModRmDecoder.writeRm16(registers, memory, ea, val);
    }

    private void opAddR8Rm8(int seg) {
        var ea = decodeModRm(seg);
        int val = add8(registers.getReg8(ea.reg()), ModRmDecoder.readRm8(registers, memory, ea));
        registers.setReg8(ea.reg(), val);
    }

    private void opAddR16Rm16(int seg) {
        var ea = decodeModRm(seg);
        int val = add16(registers.getReg16(ea.reg()), ModRmDecoder.readRm16(registers, memory, ea));
        registers.setReg16(ea.reg(), val);
    }

    private void opAdcRm8R8(int seg) {
        var ea = decodeModRm(seg);
        int val = adc8(ModRmDecoder.readRm8(registers, memory, ea), registers.getReg8(ea.reg()));
        ModRmDecoder.writeRm8(registers, memory, ea, val);
    }

    private void opAdcRm16R16(int seg) {
        var ea = decodeModRm(seg);
        int val = adc16(ModRmDecoder.readRm16(registers, memory, ea), registers.getReg16(ea.reg()));
        ModRmDecoder.writeRm16(registers, memory, ea, val);
    }

    private void opAdcR8Rm8(int seg) {
        var ea = decodeModRm(seg);
        int val = adc8(registers.getReg8(ea.reg()), ModRmDecoder.readRm8(registers, memory, ea));
        registers.setReg8(ea.reg(), val);
    }

    private void opAdcR16Rm16(int seg) {
        var ea = decodeModRm(seg);
        int val = adc16(registers.getReg16(ea.reg()), ModRmDecoder.readRm16(registers, memory, ea));
        registers.setReg16(ea.reg(), val);
    }

    private void opSubRm8R8(int seg) {
        var ea = decodeModRm(seg);
        int val = sub8(ModRmDecoder.readRm8(registers, memory, ea), registers.getReg8(ea.reg()));
        ModRmDecoder.writeRm8(registers, memory, ea, val);
    }

    private void opSubRm16R16(int seg) {
        var ea = decodeModRm(seg);
        int val = sub16(ModRmDecoder.readRm16(registers, memory, ea), registers.getReg16(ea.reg()));
        ModRmDecoder.writeRm16(registers, memory, ea, val);
    }

    private void opSubR8Rm8(int seg) {
        var ea = decodeModRm(seg);
        int val = sub8(registers.getReg8(ea.reg()), ModRmDecoder.readRm8(registers, memory, ea));
        registers.setReg8(ea.reg(), val);
    }

    private void opSubR16Rm16(int seg) {
        var ea = decodeModRm(seg);
        int val = sub16(registers.getReg16(ea.reg()), ModRmDecoder.readRm16(registers, memory, ea));
        registers.setReg16(ea.reg(), val);
    }

    private void opSbbRm8R8(int seg) {
        var ea = decodeModRm(seg);
        int val = sbb8(ModRmDecoder.readRm8(registers, memory, ea), registers.getReg8(ea.reg()));
        ModRmDecoder.writeRm8(registers, memory, ea, val);
    }

    private void opSbbRm16R16(int seg) {
        var ea = decodeModRm(seg);
        int val = sbb16(ModRmDecoder.readRm16(registers, memory, ea), registers.getReg16(ea.reg()));
        ModRmDecoder.writeRm16(registers, memory, ea, val);
    }

    private void opSbbR8Rm8(int seg) {
        var ea = decodeModRm(seg);
        int val = sbb8(registers.getReg8(ea.reg()), ModRmDecoder.readRm8(registers, memory, ea));
        registers.setReg8(ea.reg(), val);
    }

    private void opSbbR16Rm16(int seg) {
        var ea = decodeModRm(seg);
        int val = sbb16(registers.getReg16(ea.reg()), ModRmDecoder.readRm16(registers, memory, ea));
        registers.setReg16(ea.reg(), val);
    }

    private void opAndRm8R8(int seg) {
        var ea = decodeModRm(seg);
        int val = and8(ModRmDecoder.readRm8(registers, memory, ea), registers.getReg8(ea.reg()));
        ModRmDecoder.writeRm8(registers, memory, ea, val);
    }

    private void opAndRm16R16(int seg) {
        var ea = decodeModRm(seg);
        int val = and16(ModRmDecoder.readRm16(registers, memory, ea), registers.getReg16(ea.reg()));
        ModRmDecoder.writeRm16(registers, memory, ea, val);
    }

    private void opAndR8Rm8(int seg) {
        var ea = decodeModRm(seg);
        int val = and8(registers.getReg8(ea.reg()), ModRmDecoder.readRm8(registers, memory, ea));
        registers.setReg8(ea.reg(), val);
    }

    private void opAndR16Rm16(int seg) {
        var ea = decodeModRm(seg);
        int val = and16(registers.getReg16(ea.reg()), ModRmDecoder.readRm16(registers, memory, ea));
        registers.setReg16(ea.reg(), val);
    }

    private void opOrRm8R8(int seg) {
        var ea = decodeModRm(seg);
        int val = or8(ModRmDecoder.readRm8(registers, memory, ea), registers.getReg8(ea.reg()));
        ModRmDecoder.writeRm8(registers, memory, ea, val);
    }

    private void opOrRm16R16(int seg) {
        var ea = decodeModRm(seg);
        int val = or16(ModRmDecoder.readRm16(registers, memory, ea), registers.getReg16(ea.reg()));
        ModRmDecoder.writeRm16(registers, memory, ea, val);
    }

    private void opOrR8Rm8(int seg) {
        var ea = decodeModRm(seg);
        int val = or8(registers.getReg8(ea.reg()), ModRmDecoder.readRm8(registers, memory, ea));
        registers.setReg8(ea.reg(), val);
    }

    private void opOrR16Rm16(int seg) {
        var ea = decodeModRm(seg);
        int val = or16(registers.getReg16(ea.reg()), ModRmDecoder.readRm16(registers, memory, ea));
        registers.setReg16(ea.reg(), val);
    }

    private void opXorRm8R8(int seg) {
        var ea = decodeModRm(seg);
        int val = xor8(ModRmDecoder.readRm8(registers, memory, ea), registers.getReg8(ea.reg()));
        ModRmDecoder.writeRm8(registers, memory, ea, val);
    }

    private void opXorRm16R16(int seg) {
        var ea = decodeModRm(seg);
        int val = xor16(ModRmDecoder.readRm16(registers, memory, ea), registers.getReg16(ea.reg()));
        ModRmDecoder.writeRm16(registers, memory, ea, val);
    }

    private void opXorR8Rm8(int seg) {
        var ea = decodeModRm(seg);
        int val = xor8(registers.getReg8(ea.reg()), ModRmDecoder.readRm8(registers, memory, ea));
        registers.setReg8(ea.reg(), val);
    }

    private void opXorR16Rm16(int seg) {
        var ea = decodeModRm(seg);
        int val = xor16(registers.getReg16(ea.reg()), ModRmDecoder.readRm16(registers, memory, ea));
        registers.setReg16(ea.reg(), val);
    }

    private void opCmpRm8R8(int seg) {
        var ea = decodeModRm(seg);
        sub8(ModRmDecoder.readRm8(registers, memory, ea), registers.getReg8(ea.reg()));
    }

    private void opCmpRm16R16(int seg) {
        var ea = decodeModRm(seg);
        sub16(ModRmDecoder.readRm16(registers, memory, ea), registers.getReg16(ea.reg()));
    }

    private void opCmpR8Rm8(int seg) {
        var ea = decodeModRm(seg);
        sub8(registers.getReg8(ea.reg()), ModRmDecoder.readRm8(registers, memory, ea));
    }

    private void opCmpR16Rm16(int seg) {
        var ea = decodeModRm(seg);
        sub16(registers.getReg16(ea.reg()), ModRmDecoder.readRm16(registers, memory, ea));
    }

    private void opTestRm8R8(int seg) {
        var ea = decodeModRm(seg);
        and8(ModRmDecoder.readRm8(registers, memory, ea), registers.getReg8(ea.reg()));
    }

    private void opTestRm16R16(int seg) {
        var ea = decodeModRm(seg);
        and16(ModRmDecoder.readRm16(registers, memory, ea), registers.getReg16(ea.reg()));
    }

    private void opXchgRm8R8(int seg) {
        var ea = decodeModRm(seg);
        int rVal = registers.getReg8(ea.reg());
        int rmVal = ModRmDecoder.readRm8(registers, memory, ea);
        registers.setReg8(ea.reg(), rmVal);
        ModRmDecoder.writeRm8(registers, memory, ea, rVal);
    }

    private void opXchgRm16R16(int seg) {
        var ea = decodeModRm(seg);
        int rVal = registers.getReg16(ea.reg());
        int rmVal = ModRmDecoder.readRm16(registers, memory, ea);
        registers.setReg16(ea.reg(), rmVal);
        ModRmDecoder.writeRm16(registers, memory, ea, rVal);
    }

    private void opMovRm8R8(int seg) {
        var ea = decodeModRm(seg);
        ModRmDecoder.writeRm8(registers, memory, ea, registers.getReg8(ea.reg()));
    }

    private void opMovRm16R16(int seg) {
        var ea = decodeModRm(seg);
        ModRmDecoder.writeRm16(registers, memory, ea, registers.getReg16(ea.reg()));
    }

    private void opMovR8Rm8(int seg) {
        var ea = decodeModRm(seg);
        registers.setReg8(ea.reg(), ModRmDecoder.readRm8(registers, memory, ea));
    }

    private void opMovR16Rm16(int seg) {
        var ea = decodeModRm(seg);
        registers.setReg16(ea.reg(), ModRmDecoder.readRm16(registers, memory, ea));
    }

    private void opMovRm16Seg(int seg) {
        var ea = decodeModRm(seg);
        ModRmDecoder.writeRm16(registers, memory, ea, registers.getSeg(ea.reg()));
    }

    private void opMovSegRm16(int seg) {
        var ea = decodeModRm(seg);
        registers.setSeg(ea.reg(), ModRmDecoder.readRm16(registers, memory, ea));
    }

    private void opLea(int seg) {
        var ea = decodeModRm(seg);
        registers.setReg16(ea.reg(), ea.offset());
    }

    private void opPopRm16(int seg) {
        var ea = decodeModRm(seg);
        int val = memory.pop(registers);
        ModRmDecoder.writeRm16(registers, memory, ea, val);
    }

    private void opLes(int seg) {
        var ea = decodeModRm(seg);
        int off = memory.read16(ea.segment(), ea.offset());
        int segVal = memory.read16(ea.segment(), (ea.offset() + 2) & 0xFFFF);
        registers.setReg16(ea.reg(), off);
        registers.es = segVal;
    }

    private void opLds(int seg) {
        var ea = decodeModRm(seg);
        int off = memory.read16(ea.segment(), ea.offset());
        int segVal = memory.read16(ea.segment(), (ea.offset() + 2) & 0xFFFF);
        registers.setReg16(ea.reg(), off);
        registers.ds = segVal;
    }

    // --- Opcode Groups ---

    private void opGroup1(boolean isWord, boolean signExtend, int seg) {
        var ea = decodeModRm(seg);
        int imm;
        if (!isWord) {
            imm = fetch8();
        } else if (signExtend) {
            imm = (short) (byte) fetch8();
        } else {
            imm = fetch16();
        }

        int opType = ea.reg();
        if (!isWord) {
            int src = ModRmDecoder.readRm8(registers, memory, ea);
            int res = switch (opType) {
                case 0 -> add8(src, imm);
                case 1 -> or8(src, imm);
                case 2 -> adc8(src, imm);
                case 3 -> sbb8(src, imm);
                case 4 -> and8(src, imm);
                case 5 -> sub8(src, imm);
                case 6 -> xor8(src, imm);
                case 7 -> { sub8(src, imm); yield src; } // CMP
                default -> src;
            };
            if (opType != 7) {
                ModRmDecoder.writeRm8(registers, memory, ea, res);
            }
        } else {
            int src = ModRmDecoder.readRm16(registers, memory, ea);
            int res = switch (opType) {
                case 0 -> add16(src, imm);
                case 1 -> or16(src, imm);
                case 2 -> adc16(src, imm);
                case 3 -> sbb16(src, imm);
                case 4 -> and16(src, imm);
                case 5 -> sub16(src, imm);
                case 6 -> xor16(src, imm);
                case 7 -> { sub16(src, imm); yield src; } // CMP
                default -> src;
            };
            if (opType != 7) {
                ModRmDecoder.writeRm16(registers, memory, ea, res);
            }
        }
    }

    private void opGroup2(boolean isWord, int count, int seg) {
        var ea = decodeModRm(seg);
        int opType = ea.reg();
        count &= 0x1F;
        if (count == 0) return;

        if (!isWord) {
            int val = ModRmDecoder.readRm8(registers, memory, ea);
            for (int i = 0; i < count; i++) {
                switch (opType) {
                    case 0 -> { // ROL
                        int msb = (val >> 7) & 1;
                        val = ((val << 1) | msb) & 0xFF;
                        registers.setCf(msb != 0);
                        registers.setOf(((val >> 7) & 1) != msb);
                    }
                    case 1 -> { // ROR
                        int lsb = val & 1;
                        val = ((val >> 1) | (lsb << 7)) & 0xFF;
                        registers.setCf(lsb != 0);
                        registers.setOf(((val ^ (val << 1)) & 0x80) != 0);
                    }
                    case 2 -> { // RCL
                        int oldCf = registers.isCf() ? 1 : 0;
                        int msb = (val >> 7) & 1;
                        val = ((val << 1) | oldCf) & 0xFF;
                        registers.setCf(msb != 0);
                        registers.setOf(((val >> 7) & 1) != msb);
                    }
                    case 3 -> { // RCR
                        int oldCf = registers.isCf() ? 1 : 0;
                        int lsb = val & 1;
                        val = ((val >> 1) | (oldCf << 7)) & 0xFF;
                        registers.setCf(lsb != 0);
                        registers.setOf(((val ^ (val << 1)) & 0x80) != 0);
                    }
                    case 4, 6 -> { // SHL / SAL
                        int msb = (val >> 7) & 1;
                        val = (val << 1) & 0xFF;
                        registers.setCf(msb != 0);
                        registers.setOf(((val >> 7) & 1) != msb);
                    }
                    case 5 -> { // SHR
                        int lsb = val & 1;
                        int msb = (val >> 7) & 1;
                        val = (val >> 1) & 0xFF;
                        registers.setCf(lsb != 0);
                        registers.setOf(msb != 0);
                    }
                    case 7 -> { // SAR
                        int lsb = val & 1;
                        val = ((byte) val) >> 1;
                        val &= 0xFF;
                        registers.setCf(lsb != 0);
                        registers.setOf(false);
                    }
                }
            }
            if (opType >= 4) {
                setZfSfPf8(val);
            }
            ModRmDecoder.writeRm8(registers, memory, ea, val);
        } else {
            int val = ModRmDecoder.readRm16(registers, memory, ea);
            for (int i = 0; i < count; i++) {
                switch (opType) {
                    case 0 -> { // ROL
                        int msb = (val >> 15) & 1;
                        val = ((val << 1) | msb) & 0xFFFF;
                        registers.setCf(msb != 0);
                        registers.setOf(((val >> 15) & 1) != msb);
                    }
                    case 1 -> { // ROR
                        int lsb = val & 1;
                        val = ((val >> 1) | (lsb << 15)) & 0xFFFF;
                        registers.setCf(lsb != 0);
                        registers.setOf(((val ^ (val << 1)) & 0x8000) != 0);
                    }
                    case 2 -> { // RCL
                        int oldCf = registers.isCf() ? 1 : 0;
                        int msb = (val >> 15) & 1;
                        val = ((val << 1) | oldCf) & 0xFFFF;
                        registers.setCf(msb != 0);
                        registers.setOf(((val >> 15) & 1) != msb);
                    }
                    case 3 -> { // RCR
                        int oldCf = registers.isCf() ? 1 : 0;
                        int lsb = val & 1;
                        val = ((val >> 1) | (oldCf << 15)) & 0xFFFF;
                        registers.setCf(lsb != 0);
                        registers.setOf(((val ^ (val << 1)) & 0x8000) != 0);
                    }
                    case 4, 6 -> { // SHL / SAL
                        int msb = (val >> 15) & 1;
                        val = (val << 1) & 0xFFFF;
                        registers.setCf(msb != 0);
                        registers.setOf(((val >> 15) & 1) != msb);
                    }
                    case 5 -> { // SHR
                        int lsb = val & 1;
                        int msb = (val >> 15) & 1;
                        val = (val >> 1) & 0xFFFF;
                        registers.setCf(lsb != 0);
                        registers.setOf(msb != 0);
                    }
                    case 7 -> { // SAR
                        int lsb = val & 1;
                        val = ((short) val) >> 1;
                        val &= 0xFFFF;
                        registers.setCf(lsb != 0);
                        registers.setOf(false);
                    }
                }
            }
            if (opType >= 4) {
                setZfSfPf16(val);
            }
            ModRmDecoder.writeRm16(registers, memory, ea, val);
        }
    }

    private void opGroup3(boolean isWord, int seg) {
        var ea = decodeModRm(seg);
        int opType = ea.reg();

        if (!isWord) {
            int src = ModRmDecoder.readRm8(registers, memory, ea);
            switch (opType) {
                case 0, 1 -> { // TEST imm8
                    int imm = fetch8();
                    and8(src, imm);
                }
                case 2 -> ModRmDecoder.writeRm8(registers, memory, ea, ~src & 0xFF); // NOT
                case 3 -> { // NEG
                    int res = sub8(0, src);
                    ModRmDecoder.writeRm8(registers, memory, ea, res);
                    registers.setCf(src != 0);
                }
                case 4 -> { // MUL AL
                    int prod = registers.getAl() * src;
                    registers.ax = prod & 0xFFFF;
                    boolean carry = registers.getAh() != 0;
                    registers.setCf(carry);
                    registers.setOf(carry);
                }
                case 5 -> { // IMUL AL
                    int prod = ((byte) registers.getAl()) * ((byte) src);
                    registers.ax = prod & 0xFFFF;
                    boolean carry = (prod < -128 || prod > 127);
                    registers.setCf(carry);
                    registers.setOf(carry);
                }
                case 6 -> { // DIV AL
                    if (src == 0) { divideError(); return; }
                    int quot = (registers.ax & 0xFFFF) / src;
                    int rem = (registers.ax & 0xFFFF) % src;
                    if (quot > 0xFF) { divideError(); return; }
                    registers.setAl(quot);
                    registers.setAh(rem);
                }
                case 7 -> { // IDIV AL
                    if (src == 0) { divideError(); return; }
                    int num = (short) registers.ax;
                    int den = (byte) src;
                    int quot = num / den;
                    int rem = num % den;
                    if (quot < -128 || quot > 127) { divideError(); return; }
                    registers.setAl(quot);
                    registers.setAh(rem);
                }
            }
        } else {
            int src = ModRmDecoder.readRm16(registers, memory, ea);
            switch (opType) {
                case 0, 1 -> { // TEST imm16
                    int imm = fetch16();
                    and16(src, imm);
                }
                case 2 -> ModRmDecoder.writeRm16(registers, memory, ea, ~src & 0xFFFF); // NOT
                case 3 -> { // NEG
                    int res = sub16(0, src);
                    ModRmDecoder.writeRm16(registers, memory, ea, res);
                    registers.setCf(src != 0);
                }
                case 4 -> { // MUL AX
                    long prod = (long) (registers.ax & 0xFFFF) * (src & 0xFFFF);
                    registers.ax = (int) (prod & 0xFFFF);
                    registers.dx = (int) ((prod >> 16) & 0xFFFF);
                    boolean carry = registers.dx != 0;
                    registers.setCf(carry);
                    registers.setOf(carry);
                }
                case 5 -> { // IMUL AX
                    long prod = (long) ((short) registers.ax) * ((short) src);
                    registers.ax = (int) (prod & 0xFFFF);
                    registers.dx = (int) ((prod >> 16) & 0xFFFF);
                    boolean carry = (prod < -32768 || prod > 32767);
                    registers.setCf(carry);
                    registers.setOf(carry);
                }
                case 6 -> { // DIV AX
                    if (src == 0) { divideError(); return; }
                    long num = (((long) (registers.dx & 0xFFFF)) << 16) | (registers.ax & 0xFFFF);
                    long quot = num / (src & 0xFFFF);
                    long rem = num % (src & 0xFFFF);
                    if (quot > 0xFFFF) { divideError(); return; }
                    registers.ax = (int) (quot & 0xFFFF);
                    registers.dx = (int) (rem & 0xFFFF);
                }
                case 7 -> { // IDIV AX
                    if (src == 0) { divideError(); return; }
                    long num = (((long) (registers.dx & 0xFFFF)) << 16) | (registers.ax & 0xFFFF);
                    int den = (short) src;
                    long quot = num / den;
                    long rem = num % den;
                    if (quot < -32768 || quot > 32767) { divideError(); return; }
                    registers.ax = (int) (quot & 0xFFFF);
                    registers.dx = (int) (rem & 0xFFFF);
                }
            }
        }
    }

    private void opGroup4(int seg) {
        var ea = decodeModRm(seg);
        int opType = ea.reg();
        if (opType == 0) { // INC rm8
            ModRmDecoder.writeRm8(registers, memory, ea, inc8(ModRmDecoder.readRm8(registers, memory, ea)));
        } else if (opType == 1) { // DEC rm8
            ModRmDecoder.writeRm8(registers, memory, ea, dec8(ModRmDecoder.readRm8(registers, memory, ea)));
        }
    }

    private void opGroup5(int seg) {
        var ea = decodeModRm(seg);
        int opType = ea.reg();
        switch (opType) {
            case 0 -> ModRmDecoder.writeRm16(registers, memory, ea, inc16(ModRmDecoder.readRm16(registers, memory, ea))); // INC
            case 1 -> ModRmDecoder.writeRm16(registers, memory, ea, dec16(ModRmDecoder.readRm16(registers, memory, ea))); // DEC
            case 2 -> { // CALL near rm16
                int target = ModRmDecoder.readRm16(registers, memory, ea);
                memory.push(registers, registers.ip);
                registers.ip = target;
                callDepth++;
            }
            case 3 -> { // CALL far m16:16
                int targetIp = memory.read16(ea.segment(), ea.offset());
                int targetCs = memory.read16(ea.segment(), (ea.offset() + 2) & 0xFFFF);
                memory.push(registers, registers.cs);
                memory.push(registers, registers.ip);
                registers.cs = targetCs;
                registers.ip = targetIp;
                callDepth++;
            }
            case 4 -> registers.ip = ModRmDecoder.readRm16(registers, memory, ea); // JMP near rm16
            case 5 -> { // JMP far m16:16
                registers.ip = memory.read16(ea.segment(), ea.offset());
                registers.cs = memory.read16(ea.segment(), (ea.offset() + 2) & 0xFFFF);
            }
            case 6 -> memory.push(registers, ModRmDecoder.readRm16(registers, memory, ea)); // PUSH rm16
        }
    }

    // --- String Operations ---

    private void executeStringOp(int op, int segOverride, int rep) {
        int count = (rep == 0) ? 1 : registers.cx;
        int step = registers.isDf() ? -1 : 1;
        int seg = segOverride >= 0 ? registers.getSeg(segOverride) : registers.ds;

        while (count > 0) {
            switch (op) {
                case 0xA4 -> { // MOVSB
                    int b = memory.read8(seg, registers.si);
                    memory.write8(registers.es, registers.di, b);
                    registers.si = (registers.si + step) & 0xFFFF;
                    registers.di = (registers.di + step) & 0xFFFF;
                }
                case 0xA5 -> { // MOVSW
                    int w = memory.read16(seg, registers.si);
                    memory.write16(registers.es, registers.di, w);
                    registers.si = (registers.si + step * 2) & 0xFFFF;
                    registers.di = (registers.di + step * 2) & 0xFFFF;
                }
                case 0xA6 -> { // CMPSB
                    int b1 = memory.read8(seg, registers.si);
                    int b2 = memory.read8(registers.es, registers.di);
                    sub8(b1, b2);
                    registers.si = (registers.si + step) & 0xFFFF;
                    registers.di = (registers.di + step) & 0xFFFF;
                }
                case 0xA7 -> { // CMPSW
                    int w1 = memory.read16(seg, registers.si);
                    int w2 = memory.read16(registers.es, registers.di);
                    sub16(w1, w2);
                    registers.si = (registers.si + step * 2) & 0xFFFF;
                    registers.di = (registers.di + step * 2) & 0xFFFF;
                }
                case 0xAA -> { // STOSB
                    memory.write8(registers.es, registers.di, registers.getAl());
                    registers.di = (registers.di + step) & 0xFFFF;
                }
                case 0xAB -> { // STOSW
                    memory.write16(registers.es, registers.di, registers.ax);
                    registers.di = (registers.di + step * 2) & 0xFFFF;
                }
                case 0xAC -> { // LODSB
                    registers.setAl(memory.read8(seg, registers.si));
                    registers.si = (registers.si + step) & 0xFFFF;
                }
                case 0xAD -> { // LODSW
                    registers.ax = memory.read16(seg, registers.si);
                    registers.si = (registers.si + step * 2) & 0xFFFF;
                }
                case 0xAE -> { // SCASB
                    int b = memory.read8(registers.es, registers.di);
                    sub8(registers.getAl(), b);
                    registers.di = (registers.di + step) & 0xFFFF;
                }
                case 0xAF -> { // SCASW
                    int w = memory.read16(registers.es, registers.di);
                    sub16(registers.ax, w);
                    registers.di = (registers.di + step * 2) & 0xFFFF;
                }
            }

            if (rep != 0) {
                registers.cx = (registers.cx - 1) & 0xFFFF;
                count = registers.cx;

                // REPE/REPZ terminates if ZF == 0
                if (rep == 0xF3 && (op == 0xA6 || op == 0xA7 || op == 0xAE || op == 0xAF)) {
                    if (!registers.isZf()) break;
                }
                // REPNE/REPNZ terminates if ZF == 1
                if (rep == 0xF2 && (op == 0xA6 || op == 0xA7 || op == 0xAE || op == 0xAF)) {
                    if (registers.isZf()) break;
                }
            } else {
                break;
            }
        }
    }
}
