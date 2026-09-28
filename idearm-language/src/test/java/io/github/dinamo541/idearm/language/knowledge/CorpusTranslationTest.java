package io.github.dinamo541.idearm.language.knowledge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The corpus fields that hold a single text (a register's use, a flag's meaning, a service's inputs, an operand's
 * note, a pitfall) must reach the UI in the UI's language. Each such text needs an entry in
 * {@code text/translations.json}; without one, the English UI showed the Spanish original.
 */
@DisplayName("Single-language corpus texts are translated")
class CorpusTranslationTest {

    private static final Set<String> SINGLE_LANGUAGE_FIELDS = Set.of(
            "conventionalUse", "writeSemantics", "meaning", "setCondition", "clearCondition", "role", "note",
            "condition", "pitfalls", "counterExamples", "flagsAndRegisters", "caveat", "reason", "value");

    /** Prose, as opposed to a code such as {@code 0Ah} or a register name. */
    private static final Pattern PROSE = Pattern.compile("[A-Za-zÁÉÍÓÚáéíóúÑñ]{3,}\\s+\\S");

    @Test
    void everySingleLanguageProseTextHasBothLanguages() throws Exception {
        Corpus corpus = Corpus.get();
        List<String> missing = new ArrayList<>();
        for (String text : proseTexts()) {
            if (!corpus.hasTranslation(text)) {
                missing.add(text);
                continue;
            }
            String english = corpus.localize(text, Locale.ENGLISH);
            String spanish = corpus.localize(text, Locale.forLanguageTag("es"));
            if (english.isBlank() || spanish.isBlank()) {
                missing.add(text);
            }
        }
        assertTrue(missing.isEmpty(), "Add these texts to text/translations.json: " + missing);
    }

    @Test
    void localizePicksTheLanguageAndKeepsUnknownTexts() {
        Corpus corpus = Corpus.get();
        assertEquals("Writing EBX zeroes the upper 32 bits of RBX.",
                corpus.localize("Escribir en EBX pone a cero los 32 bits superiores de RBX.", Locale.ENGLISH));
        assertEquals("Escribir en EBX pone a cero los 32 bits superiores de RBX.",
                corpus.localize("Escribir en EBX pone a cero los 32 bits superiores de RBX.", Locale.forLanguageTag("es")));
        assertEquals("Selector de la función Establecer modo de vídeo",
                corpus.localize("Set Video Mode function selector", Locale.forLanguageTag("es")));
        assertEquals("a text nobody translated", corpus.localize("a text nobody translated", Locale.ENGLISH));
    }

    private static List<String> proseTexts() throws Exception {
        URL base = CorpusTranslationTest.class.getResource("/io/github/dinamo541/idearm/language/knowledge/semantic");
        List<String> texts = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of(base.toURI()))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                try (InputStream in = Files.newInputStream(file)) {
                    collect(Json.parse(in), "", texts);
                }
            }
        }
        return texts;
    }

    private static void collect(Object node, String key, List<String> texts) {
        if (node instanceof Map<?, ?> map) {
            map.forEach((k, v) -> collect(v, String.valueOf(k), texts));
        } else if (node instanceof List<?> list) {
            list.forEach(v -> collect(v, key, texts));
        } else if (node instanceof String s && SINGLE_LANGUAGE_FIELDS.contains(key) && PROSE.matcher(s).find()) {
            texts.add(s);
        }
    }
}
