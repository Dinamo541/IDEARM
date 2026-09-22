package io.github.dinamo541.idearm.app.i18n;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class MessageBundlesTest {

    /** Keys as they are written in the sources: localization.text("x"), get("x"), Message.of("x", ...). */
    private static final Pattern USED_KEY = Pattern.compile(
            "(?:localization\\.(?:text|get)|Message\\.of)\\(\\s*\"([a-zA-Z0-9_.]+)\"");
    /** Any dotted literal in the sources, to spot a key that is chosen inside an expression. */
    private static final Pattern ANY_LITERAL = Pattern.compile("\"([a-z][a-zA-Z0-9]*(?:\\.[a-zA-Z0-9]*)+)\"");

    @Test
    void everyLanguageDefinesTheSameKeys() throws IOException {
        Set<String> english = keys("messages_en.properties");
        Set<String> spanish = keys("messages_es.properties");

        assertEquals(english, spanish, "The English and Spanish bundles must define the same keys");
    }

    @Test
    void everyKeyTheCodeAsksForExists() throws IOException {
        Set<String> defined = keys("messages_en.properties");
        var missing = new TreeSet<String>();

        for (Path source : sources()) {
            String text = Files.readString(source, StandardCharsets.UTF_8);
            Matcher matcher = USED_KEY.matcher(text);
            while (matcher.find()) {
                String key = matcher.group(1);
                if (key.endsWith(".")) {
                    continue; // A prefix completed at runtime, such as "severity." plus the enum name.
                }
                if (!defined.contains(key)) {
                    missing.add(key + " (" + source.getFileName() + ")");
                }
            }
        }

        // A missing key is a MissingResourceException the moment that screen opens.
        assertTrue(missing.isEmpty(), "Keys used in code but absent from the bundles: " + missing);
    }

    @Test
    void everyDefinedKeyIsUsed() throws IOException {
        Set<String> defined = new TreeSet<>(keys("messages_en.properties"));
        var mentioned = new TreeSet<String>();

        for (Path source : sourcesIncludingLowerLayers()) {
            Matcher matcher = ANY_LITERAL.matcher(Files.readString(source, StandardCharsets.UTF_8));
            while (matcher.find()) {
                mentioned.add(matcher.group(1));
            }
        }
        // A key may be written whole, or built from a prefix such as "severity." plus an enum name.
        defined.removeIf(key -> mentioned.contains(key)
                || mentioned.stream().anyMatch(prefix -> prefix.endsWith(".") && key.startsWith(prefix)));

        assertTrue(defined.isEmpty(), "Text nothing shows any more, to delete from both bundles: " + defined);
    }

    /**
     * Name checks come from the domain and application layers as codes the explorer translates
     * ({@code explorer.name.*}); every such code needs a text in both languages.
     */
    @Test
    void everyCodeTheExplorerShowsIsTranslated() throws IOException {
        Set<String> defined = keys("messages_en.properties");
        var missing = new TreeSet<String>();
        for (Path source : lowerLayerSources()) {
            Matcher matcher = ANY_LITERAL.matcher(Files.readString(source, StandardCharsets.UTF_8));
            while (matcher.find()) {
                String code = matcher.group(1);
                if (code.startsWith("explorer.name.") && !defined.contains(code)) {
                    missing.add(code + " (" + source.getFileName() + ")");
                }
            }
        }
        assertTrue(missing.isEmpty(), "Explorer codes without a translation: " + missing);
    }

    /** Codes of the problems the lower layers report; each needs a text in both languages. */
    private static final Pattern PROBLEM_CODE = Pattern.compile(
            "(?:new DomainException|\\bfail|\\berror|\\bfailure|\\bproblem)\\(\\s*\"([a-z][a-zA-Z0-9_.-]*)\""
                    + "|\"((?:lint|explorer)\\.[a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)*|build\\.failed|build\\.timedOut)\"");
    private static final List<String> PROBLEM_MODULES = List.of("idearm-domain", "idearm-application",
            "idearm-infrastructure", "idearm-toolchain-dos", "idearm-toolchain-nasm", "idearm-language", "idearm-emu8086");

    @Test
    void everyProblemCodeHasATranslationAndEveryTranslationAProblem() throws IOException {
        Set<String> defined = keys("messages_en.properties");
        var codes = new TreeSet<String>();
        for (String module : PROBLEM_MODULES) {
            try (var tree = Files.walk(Path.of("..", module, "src", "main", "java"))) {
                for (Path source : tree.filter(path -> path.toString().endsWith(".java")).toList()) {
                    Matcher matcher = PROBLEM_CODE.matcher(Files.readString(source, StandardCharsets.UTF_8));
                    while (matcher.find()) {
                        codes.add(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
                    }
                }
            }
        }
        // File-name checks already have their own explorer texts; Windows API names are not problem codes.
        codes.removeIf(code -> !code.contains(".") || code.startsWith("explorer.name.")
                || defined.contains(code) && code.startsWith("explorer."));

        var untranslated = new TreeSet<String>();
        for (String code : codes) {
            if (!defined.contains("diagnostic." + code)) {
                untranslated.add(code);
            }
        }
        assertTrue(untranslated.isEmpty(), "Problem codes without a diagnostic.<code> text: " + untranslated);

        var stale = new TreeSet<String>();
        for (String key : defined) {
            if (key.startsWith("diagnostic.") && !codes.contains(key.substring("diagnostic.".length()))) {
                stale.add(key);
            }
        }
        assertTrue(stale.isEmpty(), "Translations for problems nothing reports any more: " + stale);
    }

    /** These texts are always formatted, where a lone quote would silently disappear. */
    @Test
    void everyProblemTextIsAValidPattern() throws IOException {
        for (String bundle : List.of("messages_en.properties", "messages_es.properties")) {
            Properties properties = load(bundle);
            for (String key : properties.stringPropertyNames()) {
                if (!key.startsWith("diagnostic.")) {
                    continue;
                }
                String text = properties.getProperty(key);
                assertFalse(text.replace("''", "").contains("'"), bundle + " " + key + " has a lone quote");
                assertDoesNotThrow(() -> new java.text.MessageFormat(text), bundle + " " + key);
            }
        }
    }

    private static List<Path> lowerLayerSources() throws IOException {
        var result = new java.util.ArrayList<Path>();
        for (String module : List.of("idearm-domain", "idearm-application")) {
            try (var tree = Files.walk(Path.of("..", module, "src", "main", "java"))) {
                tree.filter(path -> path.toString().endsWith(".java")).forEach(result::add);
            }
        }
        return result;
    }

    private static List<Path> sourcesIncludingLowerLayers() throws IOException {
        var result = new java.util.ArrayList<>(sources());
        result.addAll(lowerLayerSources());
        return result;
    }

    private static List<Path> sources() throws IOException {
        try (var tree = Files.walk(Path.of("src", "main", "java"))) {
            return tree.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    private static Set<String> keys(String bundleFile) throws IOException {
        return load(bundleFile).stringPropertyNames();
    }

    private static Properties load(String bundleFile) throws IOException {
        var properties = new Properties();
        try (var in = MessageBundlesTest.class.getResourceAsStream(bundleFile)) {
            assertNotNull(in, "Missing bundle " + bundleFile);
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
