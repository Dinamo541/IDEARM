package io.github.dinamo541.idearm.emu8086;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dinamo541.idearm.emu8086.debug.SourceLocation;
import io.github.dinamo541.idearm.emu8086.debug.SourceMap;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Source maps built from the listings real assemblers produced in Phase 0 ({@code fixtures/listings}).
 *
 * <p>TASM prints listing line numbers, which drift from the source once the string in {@code .DATA} wraps onto a
 * second listing line; MASM prints none. Both must still point every instruction at its source line.
 */
class RealListingSourceMapTest {

    /** The source these listings were assembled from, as MASM echoes it. */
    private static final List<String> HELLO = List.of(
            "; HELLO.ASM - valid reference program",
            ".MODEL small",
            ".STACK 100h",
            "",
            ".DATA",
            "message DB 'Hello World', 13, 10, '$'",
            "",
            ".CODE",
            "main PROC",
            "    mov ax, @DATA",
            "    mov ds, ax",
            "    mov dx, OFFSET message",
            "    mov ah, 09h",
            "    int 21h",
            "    mov ax, 4C00h",
            "    int 21h",
            "main ENDP",
            "END main");

    /** Source line to code offset, identical for every assembler. */
    private static final Map<Integer, Integer> EXPECTED = Map.of(
            10, 0x0000, 11, 0x0003, 12, 0x0005, 13, 0x0008, 14, 0x000A, 15, 0x000C, 16, 0x000F);

    @ParameterizedTest
    @ValueSource(strings = {"tasm-3.2", "tasm-4.1", "ml-6.11"})
    void mapsEveryInstructionToItsSourceLine(String assembler) throws IOException {
        SourceMap map = SourceMap.parseListing("src/hello.asm", listing(assembler), HELLO);

        EXPECTED.forEach((line, offset) -> {
            assertEquals(offset, map.findOffset("src/hello.asm", line).orElse(-1), assembler + " line " + line);
            assertEquals(new SourceLocation("src/hello.asm", line), map.findLocation(offset).orElseThrow(),
                    assembler + " offset " + offset);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"tasm-3.2", "tasm-4.1", "ml-6.11"})
    void neverMapsDataOrDirectives(String assembler) throws IOException {
        SourceMap map = SourceMap.parseListing("src/hello.asm", listing(assembler), HELLO);

        // The string sits at offset 0000 of the data segment, like the first instruction of the code segment.
        assertTrue(map.findOffset("src/hello.asm", 6).isEmpty(), "data line");
        assertTrue(map.findOffset("src/hello.asm", 9).isEmpty(), "main PROC generates no code");
        assertTrue(map.findOffset("src/hello.asm", 8).isEmpty(), ".CODE generates no code");
    }

    @ParameterizedTest
    @ValueSource(strings = {"tasm-3.2", "tasm-4.1"})
    void tasmLineNumbersAloneAreOffByTheWrappedDataLine(String assembler) throws IOException {
        SourceMap map = SourceMap.parseListing("src/hello.asm", listing(assembler));

        // Without the source, "mov ax, @DATA" is reported on listing line 11 although it is source line 10.
        assertEquals(0x0000, map.findOffset("src/hello.asm", 11).orElse(-1));
        assertFalse(map.isEmpty());
    }

    private static String listing(String assembler) throws IOException {
        Path file = Path.of("..", "fixtures", "listings", assembler, "HELLO.LST");
        return Files.readString(file, StandardCharsets.ISO_8859_1);
    }
}
