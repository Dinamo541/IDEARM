package io.github.dinamo541.idearm.emu8086.debug;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bidirectional mapping between executable offsets and source code lines parsed from assembler listings.
 *
 * <p>Only instructions of the code segment are mapped: data and directives also carry offsets (the data segment
 * starts at 0000 too), and mapping them would move a breakpoint or the current-line marker onto a data line.
 */
public final class SourceMap {

    /** Nearest mapped instruction accepted when an address has no listing line of its own (macro expansions). */
    private static final int MAX_FLOOR_DISTANCE = 16;

    private final Map<SourceLocation, Integer> locationToOffset = new HashMap<>();
    private final NavigableMap<Integer, SourceLocation> offsetToLocation = new TreeMap<>();
    private final Map<SourceLocation, Integer> dataLocationToOffset = new HashMap<>();

    public SourceMap() {}

    public void addMapping(String file, int line, int offset) {
        SourceLocation loc = new SourceLocation(file, line);
        // The first instruction of a line is where a breakpoint on that line stops.
        locationToOffset.putIfAbsent(loc, offset);
        offsetToLocation.put(offset, loc);
    }

    public void addDataMapping(String file, int line, int offset) {
        SourceLocation loc = new SourceLocation(file, line);
        dataLocationToOffset.putIfAbsent(loc, offset);
    }

    public Optional<Integer> findOffset(String file, int line) {
        if (file == null) return Optional.empty();
        SourceLocation loc = new SourceLocation(file, line);
        Integer offset = locationToOffset.get(loc);
        if (offset != null) {
            return Optional.of(offset);
        }
        String normalized = file.replace('\\', '/');
        String simpleName = normalized.contains("/") ? normalized.substring(normalized.lastIndexOf('/') + 1) : normalized;
        // Case-insensitive file match
        for (var entry : locationToOffset.entrySet()) {
            if (entry.getKey().line() == line) {
                String entryFile = entry.getKey().file().replace('\\', '/');
                String entrySimple = entryFile.contains("/") ? entryFile.substring(entryFile.lastIndexOf('/') + 1) : entryFile;
                if (entryFile.equalsIgnoreCase(normalized) || entryFile.equalsIgnoreCase(simpleName)
                        || entrySimple.equalsIgnoreCase(simpleName)) {
                    return Optional.of(entry.getValue());
                }
            }
        }
        return Optional.empty();
    }

    public Optional<Integer> findDataOffset(String file, int line) {
        if (file == null) return Optional.empty();
        SourceLocation loc = new SourceLocation(file, line);
        Integer offset = dataLocationToOffset.get(loc);
        if (offset != null) {
            return Optional.of(offset);
        }
        String normalized = file.replace('\\', '/');
        String simpleName = normalized.contains("/") ? normalized.substring(normalized.lastIndexOf('/') + 1) : normalized;
        for (var entry : dataLocationToOffset.entrySet()) {
            if (entry.getKey().line() == line) {
                String entryFile = entry.getKey().file().replace('\\', '/');
                String entrySimple = entryFile.contains("/") ? entryFile.substring(entryFile.lastIndexOf('/') + 1) : entryFile;
                if (entryFile.equalsIgnoreCase(normalized) || entryFile.equalsIgnoreCase(simpleName)
                        || entrySimple.equalsIgnoreCase(simpleName)) {
                    return Optional.of(entry.getValue());
                }
            }
        }
        return Optional.empty();
    }

    /** The source line of an address; an address far from any mapped instruction has none. */
    public Optional<SourceLocation> findLocation(int offset) {
        var exact = offsetToLocation.get(offset);
        if (exact != null) {
            return Optional.of(exact);
        }
        var floorEntry = offsetToLocation.floorEntry(offset);
        if (floorEntry != null && offset - floorEntry.getKey() <= MAX_FLOOR_DISTANCE) {
            return Optional.of(floorEntry.getValue());
        }
        return Optional.empty();
    }

    /** Whether an address is the first byte of a mapped instruction. */
    public boolean isMappedInstruction(int offset) {
        return offsetToLocation.containsKey(offset);
    }

    public boolean isEmpty() {
        return locationToOffset.isEmpty();
    }

    public static SourceMap empty() {
        return new SourceMap();
    }

    // TASM: right-aligned line number, offset, bytes, source.  "     11\t0000  B8 0000s\t\t\t mov ax, @DATA"
    private static final Pattern TASM_LINE = Pattern.compile(
            "^\\s{2,}(\\d+)(?:[ \\t]+([0-9A-Fa-f]{4})(?=\\s|$))?(.*)$");
    // MASM 6.11: one space, offset, bytes, source; no line numbers.  " 0000  B8 4C00\t\t\t    mov ax, 4C00h"
    private static final Pattern MASM_LINE = Pattern.compile("^ ([0-9A-Fa-f]{4})(?=\\s|$)(.*)$");
    // MASM 6.11 source line without an offset.  "\t\t\t\t.MODEL small"
    private static final Pattern MASM_TEXT = Pattern.compile("^\\t+(.*)$");
    // Machine code starts one or two spaces after the offset; source text is further away.
    private static final Pattern BYTES = Pattern.compile("^ {1,2}[0-9A-Fa-f]{2}(?![0-9A-Za-z_])");
    private static final Pattern BYTES_AND_TEXT = Pattern.compile("\\t|\\s{3,}");
    private static final Pattern CODE_SEGMENT = Pattern.compile("(?i)^\\.code\\b.*");
    private static final Pattern OTHER_SEGMENT = Pattern.compile("(?i)^\\.(data\\??|stack|const|fardata\\??)\\b.*");
    private static final Pattern SEGMENT = Pattern.compile("(?i)^(\\S+)\\s+segment\\b(.*)$");
    private static final Pattern ENDS = Pattern.compile("(?i)^\\S+\\s+ends\\b.*");
    private static final int ALIGNMENT_WINDOW = 64;

    /**
     * Parses a TASM listing using the line numbers it prints. Those count listing lines, so they drift from the
     * source after a data definition that wraps; prefer the overload that receives the source.
     *
     * @param sourceFileName the project-relative source path the mappings are reported under
     */
    public static SourceMap parseListing(String sourceFileName, String lstContent) {
        return parseListing(sourceFileName, lstContent, List.of());
    }

    /**
     * Parses a TASM or MASM listing.
     *
     * @param sourceLines the source file; each listing line is matched to it in order, which numbers MASM
     *                    listings (they print no numbers) and corrects TASM's; without it only TASM's own
     *                    numbers are used
     */
    public static SourceMap parseListing(String sourceFileName, String lstContent, List<String> sourceLines) {
        SourceMap map = new SourceMap();
        if (lstContent == null || lstContent.isBlank()) {
            return map;
        }
        List<String> normalizedSource = sourceLines.stream().map(SourceMap::normalize).toList();
        boolean inCode = false;
        int cursor = 0;

        for (String rawLine : lstContent.split("\\R")) {
            Integer lineNo = null;
            String offsetHex = null;
            String rest;
            Matcher masm = MASM_LINE.matcher(rawLine);
            Matcher tasm = TASM_LINE.matcher(rawLine);
            Matcher masmText = MASM_TEXT.matcher(rawLine);
            if (masm.matches()) {
                offsetHex = masm.group(1);
                rest = masm.group(2);
            } else if (tasm.matches() && tasm.group(1).length() <= 6) {
                lineNo = Integer.parseInt(tasm.group(1));
                offsetHex = tasm.group(2);
                rest = tasm.group(3);
            } else if (masmText.matches()) {
                rest = masmText.group(1);
            } else {
                continue;
            }

            boolean hasBytes = offsetHex != null && BYTES.matcher(rest).lookingAt();
            String text = rest.strip();
            if (hasBytes) {
                String[] parts = BYTES_AND_TEXT.split(text, 2);
                text = parts.length > 1 ? parts[1].strip() : "";
            } else {
                inCode = segmentAfter(text, inCode);
            }

            // TASM numbers listing lines, not source lines: a data definition that wraps onto a second listing
            // line shifts every later number. The source text is the reliable key whenever the source is known.
            if (!normalizedSource.isEmpty()) {
                lineNo = null;
                if (!text.isEmpty()) {
                    int found = find(normalizedSource, normalize(text), cursor);
                    if (found >= 0) {
                        lineNo = found + 1;
                        cursor = found + 1;
                    }
                }
            }

            if (hasBytes && lineNo != null && lineNo > 0) {
                int offset = Integer.parseInt(offsetHex, 16);
                if (inCode) {
                    map.addMapping(sourceFileName, lineNo, offset);
                } else {
                    map.addDataMapping(sourceFileName, lineNo, offset);
                }
            }
        }
        return map;
    }

    private static boolean segmentAfter(String text, boolean inCode) {
        if (CODE_SEGMENT.matcher(text).matches()) {
            return true;
        }
        if (OTHER_SEGMENT.matcher(text).matches() || ENDS.matcher(text).matches()) {
            return false;
        }
        Matcher segment = SEGMENT.matcher(text);
        if (segment.matches()) {
            String declaration = (segment.group(1) + " " + segment.group(2)).toUpperCase(Locale.ROOT);
            return declaration.contains("CODE") || declaration.contains("_TEXT");
        }
        return inCode;
    }

    private static int find(List<String> source, String text, int from) {
        int end = Math.min(source.size(), from + ALIGNMENT_WINDOW);
        for (int i = from; i < end; i++) {
            if (source.get(i).equals(text)) {
                return i;
            }
        }
        return -1;
    }

    private static String normalize(String line) {
        return line.strip().replaceAll("\\s+", " ");
    }
}
