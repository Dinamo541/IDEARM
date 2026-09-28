package io.github.dinamo541.idearm.application.editor;

import io.github.dinamo541.idearm.language.index.ProjectSymbolIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Comprehensive Educational Hover Tests (Directives, Registers, Operators, Symbols, Interrupts)")
class QueryHoverExtendedTest {

    private QueryHover hover;
    private ProjectSymbolIndex index;

    @BeforeEach
    void setUp() {
        hover = new QueryHover();
        index = new ProjectSymbolIndex();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "PROC", "ENDP", "OFFSET", "@DATA", ".MODEL", "MODEL",
            "SEGMENT", "ENDS", "ASSUME", "EQU", "ORG", "DB", "DW",
            "MACRO", "ENDM", "PUBLIC", "EXTRN", "PTR", "PAGE", "TITLE",
            "INCLUDE", "%INCLUDE", "TIMES", "ALIGN", "EVEN", "GLOBAL"
    })
    @DisplayName("Syntax items and directives are resolved in Spanish and English")
    void reservedWordsAndDirectivesHaveEducationalHover(String token) {
        // Spanish
        Optional<HoverInfo> esOpt = hover.execute(token, "es", index);
        assertTrue(esOpt.isPresent(), "Expected hover in Spanish for: " + token);
        HoverInfo es = esOpt.get();
        assertNotNull(es.title());
        assertNotNull(es.description());
        assertFalse(es.description().isBlank(), "Description should not be empty for: " + token);
        assertTrue(
                es.kind() == HoverKind.DIRECTIVE ||
                es.kind() == HoverKind.OPERATOR ||
                es.kind() == HoverKind.PREDEFINED_SYMBOL ||
                es.kind() == HoverKind.KEYWORD,
                "Expected syntax kind for " + token + " but was " + es.kind()
        );

        // English
        Optional<HoverInfo> enOpt = hover.execute(token, "en", index);
        assertTrue(enOpt.isPresent(), "Expected hover in English for: " + token);
        HoverInfo en = enOpt.get();
        assertNotNull(en.title());
        assertNotNull(en.description());
        assertFalse(en.description().isBlank(), "Description should not be empty for: " + token);
    }

    @Test
    @DisplayName("Registers: AX, AH, AL, DS, FLAGS have rich educational details")
    void registersEducationalHover() {
        // AX
        HoverInfo ax = hover.execute("AX", "es", index).orElseThrow();
        assertEquals(HoverKind.REGISTER, ax.kind());
        assertTrue(ax.title().contains("AX"));
        assertTrue(ax.syntax().contains("RAX [64] > EAX [32] > AX [16] > AH [15..8] : AL [7..0]"));
        assertNotNull(ax.example());

        // AH (subregister view)
        HoverInfo ah = hover.execute("AH", "es", index).orElseThrow();
        assertEquals(HoverKind.REGISTER, ah.kind());
        assertTrue(ah.title().contains("AH"));
        assertTrue(ah.description().contains("Subregistro de 8 bits"));

        // DS (segment register)
        HoverInfo ds = hover.execute("DS", "es", index).orElseThrow();
        assertEquals(HoverKind.REGISTER, ds.kind());
        assertTrue(ds.syntax().contains("Dirección física = Segmento * 16 + Desplazamiento"));

        // FLAGS (includes bit breakdown table)
        HoverInfo flags = hover.execute("FLAGS", "es", index).orElseThrow();
        assertEquals(HoverKind.REGISTER, flags.kind());
        assertNotNull(flags.flagsTable());
        assertTrue(flags.flagsTable().contains("CF"));
        assertTrue(flags.flagsTable().contains("ZF"));
        assertTrue(flags.flagsTable().contains("SF"));
        assertTrue(flags.flagsTable().contains("OF"));

        // 64-bit RAX in English
        HoverInfo raxEn = hover.execute("RAX", "en", index).orElseThrow();
        assertEquals(HoverKind.REGISTER, raxEn.kind());
        assertTrue(raxEn.title().contains("64-bit"));
    }

    @Test
    @DisplayName("Special punctuation: colon (:), brackets ([ ]), and dollar ($)")
    void specialPunctuationEducationalHover() {
        // Colon (:)
        HoverInfo colonEs = hover.execute(":", "es", index).orElseThrow();
        assertTrue(colonEs.title().contains(":"));
        assertTrue(colonEs.description().contains("Terminador de etiqueta"));
        assertTrue(colonEs.description().contains("Prefijo de segmento"));
        assertTrue(colonEs.description().contains("DX:AX"));

        HoverInfo colonEn = hover.execute(":", "en", index).orElseThrow();
        assertTrue(colonEn.description().contains("Segment override"));

        // Brackets ([ ])
        HoverInfo bracket = hover.execute("[", "es", index).orElseThrow();
        assertEquals(HoverKind.OPERATOR, bracket.kind());
        assertTrue(bracket.description().contains("corchetes"));
        assertTrue(bracket.description().contains("BX, BP, SI y DI"));

        HoverInfo bracketClose = hover.execute("]", "en", index).orElseThrow();
        assertEquals(HoverKind.OPERATOR, bracketClose.kind());
        assertTrue(bracketClose.description().contains("Square brackets"));

        // Dollar ($)
        HoverInfo dollar = hover.execute("$", "es", index).orElseThrow();
        assertEquals(HoverKind.PREDEFINED_SYMBOL, dollar.kind());
        assertTrue(dollar.description().contains("desplazamiento actual"));
        assertTrue(dollar.description().contains("INT 21h, AH=09h"));
    }

    @Test
    @DisplayName("Interrupt numbers: 21h, 10h, 16h include primary base conversion and secondary service card")
    void interruptServicesAttachedToNumbers() {
        // 21h MS-DOS
        HoverInfo int21 = hover.execute("21h", "es", index).orElseThrow();
        assertEquals(HoverKind.NUMBER_CONVERSION, int21.kind());
        assertEquals(33L, int21.number());
        assertNotNull(int21.secondary(), "Secondary card should be attached for 21h");
        assertEquals(HoverKind.INTERRUPT_SERVICE, int21.secondary().kind());
        assertTrue(int21.secondary().title().contains("INT 21h"));
        assertTrue(int21.secondary().description().contains("AH=09h"));
        assertTrue(int21.secondary().description().contains("AH=4Ch"));

        // 10h BIOS Video
        HoverInfo int10 = hover.execute("10h", "en", index).orElseThrow();
        assertEquals(HoverKind.NUMBER_CONVERSION, int10.kind());
        assertNotNull(int10.secondary());
        assertEquals(HoverKind.INTERRUPT_SERVICE, int10.secondary().kind());
        assertTrue(int10.secondary().title().contains("INT 10h"));
        assertTrue(int10.secondary().description().contains("AH=0Eh"));

        // 16h BIOS Keyboard
        HoverInfo int16 = hover.execute("16h", "es", index).orElseThrow();
        assertEquals(HoverKind.NUMBER_CONVERSION, int16.kind());
        assertNotNull(int16.secondary());
        assertEquals(HoverKind.INTERRUPT_SERVICE, int16.secondary().kind());
        assertTrue(int16.secondary().title().contains("INT 16h"));
        assertTrue(int16.secondary().description().contains("AH=00h"));
    }
}
