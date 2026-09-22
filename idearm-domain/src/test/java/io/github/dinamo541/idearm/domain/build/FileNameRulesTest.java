package io.github.dinamo541.idearm.domain.build;

import io.github.dinamo541.idearm.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileNameRulesTest {

    @Test
    void acceptsStandardDosPaths() {
        assertDoesNotThrow(() -> FileNameRules.validateRelativePath("src/main.asm"));
        assertDoesNotThrow(() -> FileNameRules.validateRelativePath("SRC/MAIN.ASM"));
        assertDoesNotThrow(() -> FileNameRules.validateRelativePath("a/b/c/prog_1.asm"));
        assertDoesNotThrow(() -> FileNameRules.validateRelativePath("utils.inc"));
    }

    @Test
    void rejectsInvalidPathFormats() {
        assertThrows(DomainException.class, () -> FileNameRules.validateRelativePath("/src/main.asm"));
        assertThrows(DomainException.class, () -> FileNameRules.validateRelativePath("src\\main.asm"));
        assertThrows(DomainException.class, () -> FileNameRules.validateRelativePath("C:src/main.asm"));
        assertThrows(DomainException.class, () -> FileNameRules.validateRelativePath(""));
        assertThrows(DomainException.class, () -> FileNameRules.validateRelativePath(null));
    }

    @Test
    void rejectsNon83Names() {
        assertThrows(DomainException.class, () -> FileNameRules.validateRelativePath("verylongname.asm"));
        assertThrows(DomainException.class, () -> FileNameRules.validateRelativePath("src/longextension.asmm"));
        assertThrows(DomainException.class, () -> FileNameRules.validateRelativePath("src/space name.asm"));
        assertThrows(DomainException.class, () -> FileNameRules.validateRelativePath("src/café.asm"));
    }

    @Test
    void rejectsDosDeviceNames() {
        assertThrows(DomainException.class, () -> FileNameRules.validateRelativePath("con.asm"));
        assertThrows(DomainException.class, () -> FileNameRules.validateRelativePath("src/nul.txt"));
        assertThrows(DomainException.class, () -> FileNameRules.validateRelativePath("prn"));
        assertThrows(DomainException.class, () -> FileNameRules.validateRelativePath("com1.asm"));
    }

    @Test
    void detectsCaseAndPathCollisions() {
        assertThrows(DomainException.class, () ->
                FileNameRules.validateDistinctPaths(List.of("src/main.asm", "SRC/MAIN.ASM")));
        assertThrows(DomainException.class, () ->
                FileNameRules.validateDistinctPaths(List.of("src", "src/main.asm")));
        assertDoesNotThrow(() ->
                FileNameRules.validateDistinctPaths(List.of("src/main.asm", "src/helper.asm", "include/mac.inc")));
    }
}
