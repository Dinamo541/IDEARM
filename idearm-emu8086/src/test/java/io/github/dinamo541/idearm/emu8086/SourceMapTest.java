package io.github.dinamo541.idearm.emu8086;

import io.github.dinamo541.idearm.emu8086.debug.SourceLocation;
import io.github.dinamo541.idearm.emu8086.debug.SourceMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SourceMapTest {

    @Test
    void testParseTasmListing() {
        String lst = """
               1                                  .model small
               2                                  .stack 100h
               3                                  .data
               4 0000  48 65 6C 6C 6F 24         msg db 'Hello$'
               5                                  .code
               6 0000                             main proc
               7 0000  B8 ---- R                 mov ax, @data
               8 0003  8E D8                      mov ds, ax
               9 0005  BA 0000 R                 mov dx, offset msg
              10 0008  B4 09                      mov ah, 09h
              11 000A  CD 21                      int 21h
              12 000C  B4 4C                      mov ah, 4Ch
              13 000E  CD 21                      int 21h
              14 0010                             main endp
              15                                  end main
            """;

        SourceMap map = SourceMap.parseListing("MAIN.ASM", lst);

        // Line 7 is at offset 0000
        assertEquals(0x0000, map.findOffset("MAIN.ASM", 7).orElse(-1));
        // Line 8 is at offset 0003
        assertEquals(0x0003, map.findOffset("MAIN.ASM", 8).orElse(-1));
        // Relative path "src/MAIN.ASM" matches
        assertEquals(0x0003, map.findOffset("src/MAIN.ASM", 8).orElse(-1));
        // Line 11 is at offset 000A
        assertEquals(0x000A, map.findOffset("MAIN.ASM", 11).orElse(-1));

        // Offset to location lookup
        assertEquals(new SourceLocation("MAIN.ASM", 7), map.findLocation(0x0000).orElse(null));
        assertEquals(new SourceLocation("MAIN.ASM", 8), map.findLocation(0x0003).orElse(null));
        // Offset 0x0004 falls back to line 8 (0x0003)
        assertEquals(new SourceLocation("MAIN.ASM", 8), map.findLocation(0x0004).orElse(null));
    }
}
