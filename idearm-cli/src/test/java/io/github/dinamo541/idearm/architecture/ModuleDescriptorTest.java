package io.github.dinamo541.idearm.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The desktop app runs on the module path, where {@code ServiceLoader.load(X.class)} fails unless the calling
 * module declares {@code uses X}. The CLI and the tests run on the class path and would never notice, so the
 * descriptors are checked here.
 */
class ModuleDescriptorTest {

    private static final Pattern LOADED = Pattern.compile("ServiceLoader\\.load\\(\\s*([A-Z][A-Za-z0-9]*)\\.class");

    @Test
    void everyModuleDeclaresTheServicesItLoads() throws IOException {
        Path repository = Path.of("").toAbsolutePath().getParent();
        var missing = new TreeSet<String>();
        try (var modules = Files.list(repository)) {
            for (Path module : modules.filter(path -> path.getFileName().toString().startsWith("idearm-")).toList()) {
                Path sources = module.resolve("src/main/java");
                Path descriptor = sources.resolve("module-info.java");
                if (!Files.isRegularFile(descriptor)) {
                    continue;
                }
                String declared = Files.readString(descriptor, StandardCharsets.UTF_8);
                try (var files = Files.walk(sources)) {
                    for (Path source : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                        Matcher loaded = LOADED.matcher(Files.readString(source, StandardCharsets.UTF_8));
                        while (loaded.find()) {
                            String service = loaded.group(1);
                            if (!Pattern.compile("uses\\s+[\\w.]*\\b" + service + "\\s*;").matcher(declared).find()) {
                                missing.add(module.getFileName() + " uses " + service
                                        + " (" + source.getFileName() + ")");
                            }
                        }
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "module-info.java is missing: " + missing);
    }
}
