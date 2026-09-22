package io.github.dinamo541.idearm.toolchain.dos;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PathMapperTest {

    @Test
    void mapsDosSourcePathToHostPath() {
        Path root = Path.of("C:\\MyProject");
        var mapper = new PathMapper(root, List.of("src/main.asm", "utils.inc"));

        String mapped = mapper.apply("S:\\src\\main.asm");
        assertEquals(root.resolve("src/main.asm").normalize().toString(), mapped);

        String mappedUpper = mapper.apply("S:\\SRC\\MAIN.ASM");
        assertEquals(root.resolve("src/main.asm").normalize().toString(), mappedUpper);
    }

    @Test
    void preservesUnknownPath() {
        Path root = Path.of("C:\\MyProject");
        var mapper = new PathMapper(root, List.of("src/main.asm"));

        String unmapped = mapper.apply("C:\\UNKNOWN.ASM");
        assertEquals("C:\\UNKNOWN.ASM", unmapped);
    }
}
