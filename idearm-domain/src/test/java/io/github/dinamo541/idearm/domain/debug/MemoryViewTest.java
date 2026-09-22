package io.github.dinamo541.idearm.domain.debug;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MemoryViewTest {

    @Test
    void calculatesLinear20BitAddress() {
        // Linear address: segment * 16 + offset
        // 0x0710:0x0020 -> 0x07100 + 0x0020 = 0x07120
        MemoryView view = new MemoryView(0x0710, 0x0020, new byte[16]);
        assertEquals(0x07120, view.linearAddress());

        // F000:FFF0 -> F0000 + FFF0 = FFFF0
        MemoryView bios = new MemoryView(0xF000, 0xFFF0, new byte[16]);
        assertEquals(0xFFFF0, bios.linearAddress());
    }

    @Test
    void formatsHexAndAsciiRows() {
        byte[] bytes = new byte[] {
                'H', 'e', 'l', 'l', 'o', ',', ' ', 'W',
                'o', 'r', 'l', 'd', '!', 0x0D, 0x0A, '$'
        };
        MemoryView view = new MemoryView(0x1000, 0x0000, bytes);
        assertEquals(16, view.length());

        String hex = view.formatHexRow(0);
        assertNotNull(hex);
        // 'H' = 0x48, 'e' = 0x65, 'l' = 0x6C
        assertTrue(hex.startsWith("48 65 6C"));

        String ascii = view.formatAsciiRow(0);
        // Printable ascii keeps characters, 0x0D and 0x0A become '.'
        assertEquals("Hello, World!..$", ascii);
    }

    @Test
    void aFlatViewShowsTheProgramsOwnAddresses() {
        byte[] bytes = new byte[20];
        String dump32 = MemoryView.flat(0x00403000L, bytes).toHexDump();
        assertTrue(dump32.startsWith("00403000  "), dump32);
        assertTrue(dump32.contains("\n00403010  "), dump32);

        String dump64 = MemoryView.flat(0x7FF6_1234_5000L, bytes).toHexDump();
        assertTrue(dump64.startsWith("00007FF612345000  "), dump64);
        assertTrue(MemoryView.flat(0, bytes).isFlat());
        assertFalse(new MemoryView(0x0710, 0, bytes).isFlat());
    }

    @Test
    void extendedRegistersKeepTheDebuggersOrder() {
        var registers = new java.util.LinkedHashMap<String, Long>();
        for (String name : java.util.List.of("RAX", "RBX", "RCX", "R8", "RIP", "EFLAGS")) {
            registers.put(name, 1L);
        }
        assertEquals(java.util.List.copyOf(registers.keySet()),
                java.util.List.copyOf(RegisterState.fromExtended(registers).extended().keySet()));
    }
}
