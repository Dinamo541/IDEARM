package io.github.dinamo541.idearm.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * Where a MASM or TASM source says that a program starts in it.
 *
 * <p>A program has exactly one start. The module that holds it ends with {@code END main}; every other module of
 * the same program ends with a bare {@code END}. {@code .STARTUP} (MASM) and {@code STARTUPCODE} (TASM) declare the
 * start as well. A second file that declares its own start is therefore not a module but a program of its own, and
 * the linker does not say so: TLINK 3.01 links two of them without a word, starts in the first object and adds up
 * both stacks (verified: two {@code .STACK 100h} programs gave one entry point and a 200h stack in the map).
 *
 * <p>Only the start of each line is read, without comments. Nothing after the first {@code END} is assembled, so the
 * scan stops there.
 *
 * @param line        1-based line of the declaration
 * @param column      1-based column where it begins
 * @param declaration the declaration as written, such as {@code END MAIN} or {@code .STARTUP}
 */
record ProgramStart(int line, int column, String declaration) {

    /** The start the file declares; nothing when it declares none or cannot be read. */
    static Optional<ProgramStart> in(Path file) {
        try {
            // DOS sources are usually CP437; this encoding never rejects a byte, and only directives matter.
            return in(Files.readString(file, StandardCharsets.ISO_8859_1));
        } catch (IOException | RuntimeException unreadable) {
            // The workspace check and the assembler report a source that cannot be read.
            return Optional.empty();
        }
    }

    static Optional<ProgramStart> in(String source) {
        String[] lines = source.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String code = withoutComment(lines[i]);
            String[] words = code.strip().split("\\s+");
            String first = words[0].toUpperCase(Locale.ROOT);
            int column = code.indexOf(words[0]) + 1;
            if (first.equals("END")) {
                return words.length > 1
                        ? Optional.of(new ProgramStart(i + 1, column, words[0] + " " + words[1]))
                        : Optional.empty();
            }
            if (first.equals(".STARTUP") || first.equals("STARTUPCODE")) {
                return Optional.of(new ProgramStart(i + 1, column, words[0]));
            }
        }
        return Optional.empty();
    }

    private static String withoutComment(String line) {
        int semicolon = line.indexOf(';');
        return semicolon < 0 ? line : line.substring(0, semicolon);
    }
}
