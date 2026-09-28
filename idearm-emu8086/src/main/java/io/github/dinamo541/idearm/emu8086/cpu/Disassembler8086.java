package io.github.dinamo541.idearm.emu8086.cpu;

import java.util.Locale;

/**
 * Turns 8086 machine code back into readable instructions, for the debugger's disassembly panel.
 *
 * <p>It decodes text only and never touches the CPU: {@link ModRmDecoder} computes an effective address from live
 * registers, which is what execution needs and what disassembly must not depend on, so the addressing modes are
 * written out here instead.
 *
 * <p>The instruction set is the one {@link Cpu8086} executes. An opcode it does not know is reported as
 * {@code DB xx}, one byte long, so the panel keeps its place instead of losing the rest of the listing.
 */
public final class Disassembler8086 {

    /** One decoded instruction: how long it is, and how it reads. */
    public record Instruction(int length, String text) {}

    private static final String[] REG8 = {"AL", "CL", "DL", "BL", "AH", "CH", "DH", "BH"};
    private static final String[] REG16 = {"AX", "CX", "DX", "BX", "SP", "BP", "SI", "DI"};
    private static final String[] SREG = {"ES", "CS", "SS", "DS"};
    /** The base of a memory operand for mod 0, 1 and 2, in rm order. */
    private static final String[] RM_BASE = {"BX+SI", "BX+DI", "BP+SI", "BP+DI", "SI", "DI", "BP", "BX"};
    private static final String[] ALU = {"ADD", "OR", "ADC", "SBB", "AND", "SUB", "XOR", "CMP"};
    private static final String[] SHIFT = {"ROL", "ROR", "RCL", "RCR", "SHL", "SHR", "SHL", "SAR"};
    private static final String[] CONDITION = {"JO", "JNO", "JB", "JNB", "JZ", "JNZ", "JBE", "JA",
            "JS", "JNS", "JP", "JNP", "JL", "JGE", "JLE", "JG"};

    /** How the bytes of one instruction are read; a cursor keeps the length honest. */
    private interface Bytes {
        int at(int index);
    }

    private final Bytes source;
    private final int start;
    private int cursor;
    private String segmentPrefix = "";

    /** The most prefix bytes read in front of one instruction; x86 instructions are at most 15 bytes long. */
    private static final int MAX_PREFIX_BYTES = 14;

    private Disassembler8086(Bytes source, int start) {
        this.source = source;
        this.start = start;
        this.cursor = start;
    }

    /** Decodes the instruction that begins at {@code start} in a block of machine code. */
    public static Instruction decode(byte[] code, int start) {
        return new Disassembler8086(index -> index >= 0 && index < code.length ? code[index] & 0xFF : 0, start).run();
    }

    /** Decodes the instruction at {@code segment:offset} in real-mode memory. */
    public static Instruction decode(RealModeMemory memory, int segment, int offset) {
        return new Disassembler8086(index -> memory.read8(segment, index & 0xFFFF), offset & 0xFFFF).run();
    }

    private int next() {
        return source.at(cursor++);
    }

    private int nextWord() {
        int low = next();
        int high = next();
        return (high << 8) | low;
    }

    private Instruction run() {
        String text = instruction();
        return new Instruction(Math.max(1, cursor - start), text);
    }

    private String instruction() {
        if (cursor - start >= MAX_PREFIX_BYTES) {
            // Memory full of prefix bytes (uninitialised data, say) recursed once per byte until the stack
            // overflowed; the run of prefixes is shown as data instead.
            return "DB " + hex8(source.at(start)) + " ; prefixes only";
        }
        int opcode = next();

        // Prefixes. A segment override belongs to the memory operand that follows; the repeat and lock prefixes
        // read as a word of their own in front of the instruction.
        switch (opcode) {
            case 0x26 -> { segmentPrefix = "ES:"; return instruction(); }
            case 0x2E -> { segmentPrefix = "CS:"; return instruction(); }
            case 0x36 -> { segmentPrefix = "SS:"; return instruction(); }
            case 0x3E -> { segmentPrefix = "DS:"; return instruction(); }
            case 0xF0 -> { return "LOCK " + instruction(); }
            case 0xF2 -> { return "REPNE " + instruction(); }
            case 0xF3 -> { return "REP " + instruction(); }
            default -> { /* not a prefix */ }
        }

        // The eight arithmetic and logic operations share one block of opcodes, in the same six forms each.
        if (opcode < 0x40 && (opcode & 7) < 6) {
            String mnemonic = ALU[(opcode >> 3) & 7];
            return switch (opcode & 7) {
                case 0 -> modRm(mnemonic, false, true);
                case 1 -> modRm(mnemonic, true, true);
                case 2 -> modRm(mnemonic, false, false);
                case 3 -> modRm(mnemonic, true, false);
                case 4 -> mnemonic + " AL, " + hex8(next());
                default -> mnemonic + " AX, " + hex16(nextWord());
            };
        }

        if (opcode >= 0x40 && opcode <= 0x47) return "INC " + REG16[opcode - 0x40];
        if (opcode >= 0x48 && opcode <= 0x4F) return "DEC " + REG16[opcode - 0x48];
        if (opcode >= 0x50 && opcode <= 0x57) return "PUSH " + REG16[opcode - 0x50];
        if (opcode >= 0x58 && opcode <= 0x5F) return "POP " + REG16[opcode - 0x58];
        if (opcode >= 0x70 && opcode <= 0x7F) return CONDITION[opcode - 0x70] + " " + shortLabel(next());
        if (opcode >= 0x91 && opcode <= 0x97) return "XCHG AX, " + REG16[opcode - 0x90];
        if (opcode >= 0xB0 && opcode <= 0xB7) return "MOV " + REG8[opcode - 0xB0] + ", " + hex8(next());
        if (opcode >= 0xB8 && opcode <= 0xBF) return "MOV " + REG16[opcode - 0xB8] + ", " + hex16(nextWord());

        return switch (opcode) {
            case 0x06 -> "PUSH ES";
            case 0x07 -> "POP ES";
            case 0x0E -> "PUSH CS";
            case 0x0F -> "POP CS";
            case 0x16 -> "PUSH SS";
            case 0x17 -> "POP SS";
            case 0x1E -> "PUSH DS";
            case 0x1F -> "POP DS";
            case 0x27 -> "DAA";
            case 0x2F -> "DAS";
            case 0x37 -> "AAA";
            case 0x3F -> "AAS";

            // Group 1: an arithmetic or logic operation against an immediate value.
            case 0x80, 0x81, 0x82, 0x83 -> group1(opcode);

            case 0x84 -> modRm("TEST", false, true);
            case 0x85 -> modRm("TEST", true, true);
            case 0x86 -> modRm("XCHG", false, true);
            case 0x87 -> modRm("XCHG", true, true);
            case 0x88 -> modRm("MOV", false, true);
            case 0x89 -> modRm("MOV", true, true);
            case 0x8A -> modRm("MOV", false, false);
            case 0x8B -> modRm("MOV", true, false);
            case 0x8C -> segmentMove(false);
            case 0x8D -> modRm("LEA", true, false);
            case 0x8E -> segmentMove(true);
            case 0x8F -> unary("POP", true);

            case 0x90 -> "NOP";
            case 0x98 -> "CBW";
            case 0x99 -> "CWD";
            case 0x9A -> farTarget("CALL");
            case 0x9B -> "WAIT";
            case 0x9C -> "PUSHF";
            case 0x9D -> "POPF";
            case 0x9E -> "SAHF";
            case 0x9F -> "LAHF";

            case 0xA0 -> "MOV AL, " + memoryAt(hex16(nextWord()));
            case 0xA1 -> "MOV AX, " + memoryAt(hex16(nextWord()));
            case 0xA2 -> "MOV " + memoryAt(hex16(nextWord())) + ", AL";
            case 0xA3 -> "MOV " + memoryAt(hex16(nextWord())) + ", AX";
            case 0xA4 -> "MOVSB";
            case 0xA5 -> "MOVSW";
            case 0xA6 -> "CMPSB";
            case 0xA7 -> "CMPSW";
            case 0xA8 -> "TEST AL, " + hex8(next());
            case 0xA9 -> "TEST AX, " + hex16(nextWord());
            case 0xAA -> "STOSB";
            case 0xAB -> "STOSW";
            case 0xAC -> "LODSB";
            case 0xAD -> "LODSW";
            case 0xAE -> "SCASB";
            case 0xAF -> "SCASW";

            case 0xC2 -> "RET " + hex16(nextWord());
            case 0xC3 -> "RET";
            case 0xC4 -> modRm("LES", true, false);
            case 0xC5 -> modRm("LDS", true, false);
            case 0xC6 -> immediateToMemory(false);
            case 0xC7 -> immediateToMemory(true);
            case 0xCA -> "RETF " + hex16(nextWord());
            case 0xCB -> "RETF";
            case 0xCC -> "INT 3";
            case 0xCD -> "INT " + hex8(next());
            case 0xCE -> "INTO";
            case 0xCF -> "IRET";

            case 0xD0 -> shift(false, "1");
            case 0xD1 -> shift(true, "1");
            case 0xD2 -> shift(false, "CL");
            case 0xD3 -> shift(true, "CL");
            case 0xD4 -> "AAM";
            case 0xD5 -> "AAD";
            case 0xD7 -> "XLAT";

            case 0xE0 -> "LOOPNZ " + shortLabel(next());
            case 0xE1 -> "LOOPZ " + shortLabel(next());
            case 0xE2 -> "LOOP " + shortLabel(next());
            case 0xE3 -> "JCXZ " + shortLabel(next());
            case 0xE4 -> "IN AL, " + hex8(next());
            case 0xE5 -> "IN AX, " + hex8(next());
            case 0xE6 -> "OUT " + hex8(next()) + ", AL";
            case 0xE7 -> "OUT " + hex8(next()) + ", AX";
            case 0xE8 -> "CALL " + nearLabel(nextWord());
            case 0xE9 -> "JMP " + nearLabel(nextWord());
            case 0xEA -> farTarget("JMP");
            case 0xEB -> "JMP " + shortLabel(next());
            case 0xEC -> "IN AL, DX";
            case 0xED -> "IN AX, DX";
            case 0xEE -> "OUT DX, AL";
            case 0xEF -> "OUT DX, AX";

            case 0xF4 -> "HLT";
            case 0xF5 -> "CMC";
            case 0xF6 -> group3(false);
            case 0xF7 -> group3(true);
            case 0xF8 -> "CLC";
            case 0xF9 -> "STC";
            case 0xFA -> "CLI";
            case 0xFB -> "STI";
            case 0xFC -> "CLD";
            case 0xFD -> "STD";
            case 0xFE -> incDec(false);
            case 0xFF -> group5();

            // Not an instruction this CPU knows: one byte of data, so the next line still lines up.
            default -> "DB " + hex8(opcode);
        };
    }

    /** An instruction with a ModR/M byte: {@code wide} picks 16-bit operands, {@code toRm} the direction. */
    private String modRm(String mnemonic, boolean wide, boolean toRm) {
        int modRm = next();
        String register = register((modRm >> 3) & 7, wide);
        String rm = rmOperand(modRm, wide);
        return toRm ? mnemonic + " " + rm + ", " + register
                    : mnemonic + " " + register + ", " + rm;
    }

    private String segmentMove(boolean toSegment) {
        int modRm = next();
        String segment = SREG[(modRm >> 3) & 3];
        String rm = rmOperand(modRm, true);
        return toSegment ? "MOV " + segment + ", " + rm : "MOV " + rm + ", " + segment;
    }

    private String group1(int opcode) {
        boolean wide = (opcode & 1) == 1;
        int modRm = next();
        String mnemonic = ALU[(modRm >> 3) & 7];
        String rm = rmOperand(modRm, wide);
        // 83h is the compact form: one signed byte widened to a word.
        String immediate = (opcode == 0x81) ? hex16(nextWord()) : hex8(next());
        return mnemonic + " " + sized(rm, wide) + ", " + immediate;
    }

    private String immediateToMemory(boolean wide) {
        int modRm = next();
        String rm = rmOperand(modRm, wide);
        String immediate = wide ? hex16(nextWord()) : hex8(next());
        return "MOV " + sized(rm, wide) + ", " + immediate;
    }

    private String shift(boolean wide, String count) {
        int modRm = next();
        String mnemonic = SHIFT[(modRm >> 3) & 7];
        return mnemonic + " " + sized(rmOperand(modRm, wide), wide) + ", " + count;
    }

    /** F6h and F7h: TEST, NOT, NEG and the multiply and divide instructions. */
    private String group3(boolean wide) {
        int modRm = next();
        int operation = (modRm >> 3) & 7;
        String rm = rmOperand(modRm, wide);
        if (operation == 0 || operation == 1) {
            String immediate = wide ? hex16(nextWord()) : hex8(next());
            return "TEST " + sized(rm, wide) + ", " + immediate;
        }
        String mnemonic = switch (operation) {
            case 2 -> "NOT";
            case 3 -> "NEG";
            case 4 -> "MUL";
            case 5 -> "IMUL";
            case 6 -> "DIV";
            default -> "IDIV";
        };
        return mnemonic + " " + sized(rm, wide);
    }

    private String incDec(boolean wide) {
        int modRm = next();
        String mnemonic = ((modRm >> 3) & 7) == 0 ? "INC" : "DEC";
        return mnemonic + " " + sized(rmOperand(modRm, wide), wide);
    }

    /** FFh: increment, decrement, the indirect calls and jumps, and push. */
    private String group5() {
        int modRm = next();
        int operation = (modRm >> 3) & 7;
        String rm = rmOperand(modRm, true);
        return switch (operation) {
            case 0 -> "INC " + sized(rm, true);
            case 1 -> "DEC " + sized(rm, true);
            case 2 -> "CALL " + rm;
            case 3 -> "CALL FAR " + rm;
            case 4 -> "JMP " + rm;
            case 5 -> "JMP FAR " + rm;
            default -> "PUSH " + sized(rm, true);
        };
    }

    private String unary(String mnemonic, boolean wide) {
        int modRm = next();
        return mnemonic + " " + sized(rmOperand(modRm, wide), wide);
    }

    private String farTarget(String mnemonic) {
        int offset = nextWord();
        int segment = nextWord();
        return mnemonic + " " + plainHex(segment, 4) + ":" + plainHex(offset, 4);
    }

    /** A register or memory operand, as the ModR/M byte describes it. */
    private String rmOperand(int modRm, boolean wide) {
        int mod = (modRm >> 6) & 3;
        int rm = modRm & 7;
        if (mod == 3) {
            return register(rm, wide);
        }
        if (mod == 0 && rm == 6) {
            return memoryAt(hex16(nextWord()));
        }
        StringBuilder address = new StringBuilder(RM_BASE[rm]);
        if (mod == 1) {
            int displacement = (byte) next();
            address.append(displacement < 0 ? "-" : "+").append(hexLiteral(Math.abs(displacement), 2));
        } else if (mod == 2) {
            address.append('+').append(hex16(nextWord()));
        }
        return memoryAt(address.toString());
    }

    /** Wraps a memory operand in brackets, carrying any segment override the prefix asked for. */
    private String memoryAt(String address) {
        return "[" + segmentPrefix + address + "]";
    }

    /**
     * Says how wide a memory operand is. A register already says it, and an immediate on its own does not: without
     * this, {@code MOV [BX], 1} would not say whether one byte or two are written.
     */
    private static String sized(String operand, boolean wide) {
        return operand.startsWith("[") ? (wide ? "WORD PTR " : "BYTE PTR ") + operand : operand;
    }

    private static String register(int index, boolean wide) {
        return wide ? REG16[index] : REG8[index];
    }

    /** The target of a jump that counts from the end of the instruction, written as an absolute offset. */
    private String shortLabel(int displacement) {
        return hexLiteral((cursor + (byte) displacement) & 0xFFFF, 4);
    }

    private String nearLabel(int displacement) {
        return hexLiteral((cursor + (short) displacement) & 0xFFFF, 4);
    }

    private static String hex8(int value) {
        return hexLiteral(value & 0xFF, 2);
    }

    private static String hex16(int value) {
        return hexLiteral(value & 0xFFFF, 4);
    }

    /**
     * A hexadecimal literal as an assembler accepts it: an {@code h} suffix, and a leading zero when the value
     * would otherwise begin with a letter and read as a name.
     */
    private static String hexLiteral(int value, int digits) {
        String text = plainHex(value, digits);
        return (Character.isDigit(text.charAt(0)) ? text : "0" + text) + "h";
    }

    private static String plainHex(int value, int digits) {
        return ("%0" + digits + "X").formatted(value).toUpperCase(Locale.ROOT);
    }
}
