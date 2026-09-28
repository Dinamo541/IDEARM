package io.github.dinamo541.idearm.emu8086;

import io.github.dinamo541.idearm.emu8086.cpu.Disassembler8086;
import io.github.dinamo541.idearm.emu8086.cpu.RealModeMemory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class Disassembler8086Test {

    private static byte[] code(int... bytes) {
        byte[] block = new byte[bytes.length];
        for (int index = 0; index < bytes.length; index++) {
            block[index] = (byte) bytes[index];
        }
        return block;
    }

    private static String textOf(int... bytes) {
        return Disassembler8086.decode(code(bytes), 0).text();
    }

    private static int lengthOf(int... bytes) {
        return Disassembler8086.decode(code(bytes), 0).length();
    }

    @Test
    void readsTheInstructionsAStudentWritesFirst() {
        assertEquals("MOV AX, 4C00h", textOf(0xB8, 0x00, 0x4C));
        assertEquals("MOV DS, AX", textOf(0x8E, 0xD8));
        assertEquals("MOV AH, 09h", textOf(0xB4, 0x09));
        assertEquals("INT 21h", textOf(0xCD, 0x21));
        assertEquals("RET", textOf(0xC3));
        assertEquals("NOP", textOf(0x90));
        assertEquals("HLT", textOf(0xF4));
    }

    /** Each prefix byte recursed once, so memory full of them overflowed the stack of the refresh thread. */
    @Test
    void memoryFullOfPrefixesDoesNotOverflowTheStack() {
        var memory = new RealModeMemory();
        for (int offset = 0; offset < 0x10000; offset++) {
            memory.write8(0x1000, offset, 0x2E); // CS:
        }
        var instruction = Disassembler8086.decode(memory, 0x1000, 0);
        assertTrue(instruction.text().startsWith("DB 2Eh"), instruction.text());
        assertTrue(instruction.length() <= 15, "length " + instruction.length());
        // Prefixes in front of a real instruction still read normally.
        assertEquals("REP MOVSB", textOf(0xF3, 0xA4));
    }

    @Test
    void readsMemoryOperandsWithTheirAddressingMode() {
        // MOV AX, [BX+SI]
        assertEquals("MOV AX, [BX+SI]", textOf(0x8B, 0x00));
        // MOV AX, [BP+4] — BP addresses the stack, which the operand text shows as written
        assertEquals("MOV AX, [BP+04h]", textOf(0x8B, 0x46, 0x04));
        // MOV AX, [BP-2]
        assertEquals("MOV AX, [BP-02h]", textOf(0x8B, 0x46, 0xFE));
        // MOV AX, [1234h] — direct addressing
        assertEquals("MOV AX, [1234h]", textOf(0x8B, 0x06, 0x34, 0x12));
        // A byte written to memory has to say how wide it is, or the line is ambiguous.
        assertEquals("MOV BYTE PTR [BX], 05h", textOf(0xC6, 0x07, 0x05));
        assertEquals("MOV WORD PTR [BX], 0005h", textOf(0xC7, 0x07, 0x05, 0x00));
        // A segment override belongs to the operand it addresses.
        assertEquals("MOV AX, [ES:BX]", textOf(0x26, 0x8B, 0x07));
    }

    @Test
    void readsJumpsAsTheOffsetTheyLandOn() {
        // A short jump counts from the end of the instruction: EB FE lands on itself, the endless loop.
        assertEquals("JMP 0000h", textOf(0xEB, 0xFE));
        // The same jump one byte further on, after a NOP, goes back to that NOP.
        assertEquals("JMP 0000h", Disassembler8086.decode(code(0x90, 0xEB, 0xFD), 1).text());
        // 74 05: JZ five bytes past the end of the instruction.
        assertEquals("JZ 0007h", textOf(0x74, 0x05));
        assertEquals("CALL 0003h", textOf(0xE8, 0x00, 0x00));
        // A far jump carries its own segment, so it reads as segment:offset.
        assertEquals("JMP 1000:0003", textOf(0xEA, 0x03, 0x00, 0x00, 0x10));
    }

    @Test
    void readsTheGroupsThatShareAnOpcode() {
        assertEquals("CMP AX, 0005h", textOf(0x3D, 0x05, 0x00));
        assertEquals("ADD BX, 0010h", textOf(0x81, 0xC3, 0x10, 0x00));
        assertEquals("SUB AL, 01h", textOf(0x80, 0xE8, 0x01));
        assertEquals("SHL AX, 1", textOf(0xD1, 0xE0));
        assertEquals("SAR AX, CL", textOf(0xD3, 0xF8));
        assertEquals("MUL BL", textOf(0xF6, 0xE3));
        assertEquals("DIV CX", textOf(0xF7, 0xF1));
        assertEquals("NEG AX", textOf(0xF7, 0xD8));
        assertEquals("INC WORD PTR [BX]", textOf(0xFF, 0x07));
        assertEquals("PUSH WORD PTR [BX]", textOf(0xFF, 0x37));
        assertEquals("REP MOVSB", textOf(0xF3, 0xA4));
    }

    @Test
    void anUnknownOpcodeIsOneByteOfDataSoTheRestStaysAligned() {
        // 0F is POP CS on an 8086 and the two-byte prefix on later CPUs; 64h is not an 8086 opcode at all.
        assertEquals("DB 64h", textOf(0x64));
        assertEquals(1, lengthOf(0x64));
    }

    @Test
    void readsFromRealModeMemoryAtASegmentAndOffset() {
        var memory = new RealModeMemory();
        memory.write8(0x1000, 0x0100, 0xB8);
        memory.write8(0x1000, 0x0101, 0x34);
        memory.write8(0x1000, 0x0102, 0x12);

        var instruction = Disassembler8086.decode(memory, 0x1000, 0x0100);

        assertEquals("MOV AX, 1234h", instruction.text());
        assertEquals(3, instruction.length());
    }

    /**
     * The real oracle: TASM's own listing says what each instruction is and how many bytes it took. Disassembling
     * those bytes must give the same mnemonic and the same length, or the panel would show a program that is not
     * the one being debugged.
     */
    @Test
    @EnabledIf("helloListingExists")
    void agreesWithARealTasmListing() throws IOException {
        Path listing = Path.of("..", "fixtures", "listings", "tasm-4.1", "HELLO.LST");
        var checked = new ArrayList<String>();

        for (var line : codeLines(listing)) {
            var instruction = Disassembler8086.decode(line.bytes, 0);
            assertEquals(line.mnemonic, firstWord(instruction.text()),
                    "Listing line \"" + line.source + "\" (" + hex(line.bytes) + ")");
            assertEquals(line.bytes.length, instruction.length(),
                    "Length of \"" + line.source + "\" (" + hex(line.bytes) + ")");
            checked.add(line.mnemonic);
        }

        // HELLO.ASM is five MOVs and two INTs; if the listing stopped parsing, this catches it.
        assertEquals(7, checked.size(), "Checked instructions: " + checked);
        assertTrue(checked.contains("INT"));
        assertTrue(checked.contains("MOV"));
    }

    static boolean helloListingExists() {
        return Files.exists(Path.of("..", "fixtures", "listings", "tasm-4.1", "HELLO.LST"));
    }

    /** One instruction of a listing: its machine code and the mnemonic the source wrote. */
    private record ListingLine(byte[] bytes, String mnemonic, String source) {}

    /**
     * Reads the instruction lines of a TASM listing: line number, offset, machine code, then the source. Data
     * definitions, directives and the symbol table are left out; a relocation marker ({@code s} or {@code r} after
     * a value) is dropped, since it only says the linker will patch those bytes.
     */
    private static List<ListingLine> codeLines(Path listing) throws IOException {
        var pattern = Pattern.compile("^\\s*\\d+\\s+([0-9A-F]{4})\\s\\s((?:[0-9A-F]{2,4}[sr]?\\s?)+)\\t+\\s*(\\S.*)$");
        var lines = new ArrayList<ListingLine>();
        for (String raw : Files.readAllLines(listing, StandardCharsets.ISO_8859_1)) {
            var match = pattern.matcher(raw);
            if (!match.matches()) {
                continue;
            }
            String source = match.group(3).trim();
            String mnemonic = firstWord(source);
            // Only instructions: a directive starts with a dot, and a data definition names its label first.
            if (!INSTRUCTIONS.contains(mnemonic)) {
                continue;
            }
            byte[] bytes = parseBytes(match.group(2));
            if (bytes.length > 0) {
                lines.add(new ListingLine(bytes, mnemonic, source));
            }
        }
        return lines;
    }

    /** The mnemonics HELLO.ASM uses; anything else on a listing line is a directive or data. */
    private static final List<String> INSTRUCTIONS = List.of("MOV", "INT", "RET", "PUSH", "POP", "ADD", "SUB",
            "CMP", "JMP", "CALL", "INC", "DEC", "SHL", "SHR", "XOR", "AND", "OR", "LEA", "NOP", "HLT");

    private static byte[] parseBytes(String column) {
        var bytes = new ArrayList<Byte>();
        for (String group : column.trim().split("\\s+")) {
            // A relocation marker follows the value it applies to and is not machine code.
            String digits = group.replaceAll("[sr]$", "");
            for (int index = 0; index + 1 < digits.length(); index += 2) {
                bytes.add((byte) Integer.parseInt(digits.substring(index, index + 2), 16));
            }
        }
        byte[] block = new byte[bytes.size()];
        for (int index = 0; index < block.length; index++) {
            block[index] = bytes.get(index);
        }
        return block;
    }

    private static String firstWord(String text) {
        int space = text.indexOf(' ');
        int tab = text.indexOf('\t');
        int end = (space < 0) ? tab : (tab < 0 ? space : Math.min(space, tab));
        return (end < 0 ? text : text.substring(0, end)).toUpperCase(java.util.Locale.ROOT);
    }

    private static String hex(byte[] bytes) {
        var text = new StringBuilder();
        for (byte value : bytes) {
            text.append("%02X ".formatted(value));
        }
        return text.toString().trim();
    }
}
