package io.github.dinamo541.idearm.language.knowledge;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AA-P3-03: Interrupt Services, Vector Index, and Acceptance Case 4")
class InterruptServicesTest {

    private static Corpus corpus;

    @BeforeAll
    static void setUp() {
        corpus = Corpus.get();
        assertNotNull(corpus, "Corpus must be loaded");
    }

    @Test
    @DisplayName("Acceptance Case 4: INT 21h with AH=09h vs AH=0Ah resolve to distinct services with accurate contracts")
    void testAcceptanceCase4_Int21hServices() {
        Optional<ServiceEntry> service09 = corpus.findService("21h", "09h");
        Optional<ServiceEntry> service0A = corpus.findService("21h", "0Ah");

        assertTrue(service09.isPresent(), "INT 21h AH=09h service must be present");
        assertTrue(service0A.isPresent(), "INT 21h AH=0Ah service must be present");
        assertNotEquals(service09.get().id(), service0A.get().id(), "AH=09h and AH=0Ah must resolve to distinct services");

        ServiceEntry s09 = service09.get();
        assertEquals("dos.int21.09h", s09.id());
        assertEquals("21h", s09.vector());
        assertEquals("09h", s09.selector().value());
        assertTrue(s09.summaryEs().contains("$") || s09.descriptionEs().contains("$"),
                "09h contract must explain '$' terminator");
        assertTrue(s09.inputs().stream().anyMatch(i -> i.register().equalsIgnoreCase("DS:DX")),
                "09h inputs must document DS:DX pointer");

        ServiceEntry s0A = service0A.get();
        assertEquals("dos.int21.0ah", s0A.id());
        assertEquals("21h", s0A.vector());
        assertEquals("0Ah", s0A.selector().value());
        assertFalse(s0A.bufferFormat().isEmpty(), "0Ah must declare buffered structure layout");
        assertTrue(s0A.bufferFormat().size() >= 3, "0Ah buffer layout must define offsets 0, 1, and 2");
        assertEquals(0, s0A.bufferFormat().get(0).offset());
        assertEquals(1, s0A.bufferFormat().get(1).offset());
        assertEquals(2, s0A.bufferFormat().get(2).offset());

        // Check emu8086 availability caveat for 0Ah divergence
        BackendAvailability emuAvail0A = s0A.availability().stream()
                .filter(a -> "emu8086".equalsIgnoreCase(a.backend()))
                .findFirst()
                .orElse(null);
        assertNotNull(emuAvail0A, "0Ah must document emu8086 availability");
        assertNotNull(emuAvail0A.caveat(), "0Ah emu8086 availability must declare caveat regarding buffer length handling");
    }

    @Test
    @DisplayName("BIOS Video Services: INT 10h functions exist and AH=13h reports emulator caveat")
    void testBiosInt10Services() {
        Optional<ServiceEntry> s00 = corpus.findService("10h", "00h");
        Optional<ServiceEntry> s02 = corpus.findService("10h", "02h");
        Optional<ServiceEntry> s0E = corpus.findService("10h", "0Eh");
        Optional<ServiceEntry> s13 = corpus.findService("10h", "13h");

        assertTrue(s00.isPresent(), "INT 10h AH=00h (Set Video Mode) must exist");
        assertTrue(s02.isPresent(), "INT 10h AH=02h (Set Cursor Position) must exist");
        assertTrue(s0E.isPresent(), "INT 10h AH=0Eh (Teletype Output) must exist");
        assertTrue(s13.isPresent(), "INT 10h AH=13h (Write String) must exist");

        // AH=13h is unsupported in emu8086
        BackendAvailability emuAvail13 = s13.get().availability().stream()
                .filter(a -> "emu8086".equalsIgnoreCase(a.backend()))
                .findFirst()
                .orElse(null);
        assertNotNull(emuAvail13);
        assertEquals(BackendAvailability.AvailabilityStatus.UNAVAILABLE, emuAvail13.status());
        assertEquals("diagnostic.emu.bios.unsupported", emuAvail13.caveat());
    }

    @Test
    @DisplayName("BIOS Keyboard Services: INT 16h functions exist")
    void testBiosInt16Services() {
        Optional<ServiceEntry> s00 = corpus.findService("16h", "00h");
        Optional<ServiceEntry> s01 = corpus.findService("16h", "01h");
        Optional<ServiceEntry> s02 = corpus.findService("16h", "02h");

        assertTrue(s00.isPresent(), "INT 16h AH=00h (Read Keystroke) must exist");
        assertTrue(s01.isPresent(), "INT 16h AH=01h (Check Keystroke Buffer) must exist");
        assertTrue(s02.isPresent(), "INT 16h AH=02h (Get Shift Flags) must exist");
    }

    @Test
    @DisplayName("CPU Architectural Exceptions: vectors 00h-08h, 0Dh, 0Eh exist")
    void testCpuExceptions() {
        Optional<ServiceEntry> exc00 = corpus.findService("cpu.exc.00h");
        Optional<ServiceEntry> exc03 = corpus.findService("cpu.exc.03h");
        Optional<ServiceEntry> exc0D = corpus.findService("cpu.exc.0dh");
        Optional<ServiceEntry> exc0E = corpus.findService("cpu.exc.0eh");

        assertTrue(exc00.isPresent(), "Divide Error #DE must exist");
        assertEquals(VectorStatus.ARCHITECTURAL_CPU, exc00.get().vectorStatus());

        assertTrue(exc03.isPresent(), "Breakpoint #BP must exist");
        assertEquals(VectorStatus.ARCHITECTURAL_CPU, exc03.get().vectorStatus());

        assertTrue(exc0D.isPresent(), "General Protection #GP must exist");
        assertEquals(VectorStatus.ARCHITECTURAL_CPU, exc0D.get().vectorStatus());

        assertTrue(exc0E.isPresent(), "Page Fault #PF must exist");
        assertEquals(VectorStatus.ARCHITECTURAL_CPU, exc0E.get().vectorStatus());
    }

    @Test
    @DisplayName("Vector Index: Exactly 256 vector slots with correct status classifications")
    void testVectorIndexIntegrity() {
        List<VectorIndexEntry> index = corpus.getVectorIndex();
        assertEquals(256, index.size(), "Vector index must contain all 256 vector slots (00h-FFh)");

        for (int i = 0; i < 256; i++) {
            Optional<VectorIndexEntry> entryOpt = corpus.findVector(i);
            assertTrue(entryOpt.isPresent(), "Vector slot " + i + " must be present in index");
            VectorIndexEntry entry = entryOpt.get();
            assertEquals(i, entry.vectorNumber());
            assertNotNull(entry.status(), "Status must not be null for vector " + i);
            assertFalse(entry.titleEn().isBlank(), "Title EN must not be blank for vector " + i);
            assertFalse(entry.titleEs().isBlank(), "Title ES must not be blank for vector " + i);

            // Architectural CPU range check
            if (i <= 0x04) {
                assertEquals(VectorStatus.ARCHITECTURAL_CPU, entry.status(), "Vector " + i + " must be ARCHITECTURAL_CPU");
            }
            // Standard BIOS range check
            if (i >= 0x08 && i <= 0x1F) {
                assertEquals(VectorStatus.BIOS_STANDARD, entry.status(), "Vector " + i + " must be BIOS_STANDARD");
            }
            // MS-DOS kernel range check
            if (i >= 0x20 && i <= 0x28) {
                assertEquals(VectorStatus.DOS_KERNEL, entry.status(), "Vector " + i + " must be DOS_KERNEL");
            }
        }
    }
}
