package io.github.dinamo541.idearm.infrastructure.tools;

import io.github.dinamo541.idearm.domain.model.HostKind;
import io.github.dinamo541.idearm.domain.model.ToolInstallation;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Finds tool installations on this machine: well-known install folders, the IDE's own tools folder
 * ({@code %LOCALAPPDATA%\IDEARM\tools}), the folders listed in {@code IDEARM_TOOL_PATH}, and the system PATH.
 *
 * <p>DOS tools are identified by reading their binaries, because a 16-bit program cannot run on 64-bit Windows.
 * Native tools (NASM, GNU ld, gcc, GDB) are asked for their version, so the Doctor reports what is really
 * installed; a tool that does not answer is reported as {@value #UNKNOWN_VERSION}.
 */
public final class ToolDetector {

    static final String UNKNOWN_VERSION = "unknown";
    /** Folders under a tools root are searched this deep: {@code dosbox-x-<version>\bin\x64\Release}. */
    static final int TOOLS_ROOT_DEPTH = 4;
    /** A folder the user picks may be the installation root, with the programs one or two levels down. */
    static final int PICKED_FOLDER_DEPTH = 3;
    private static final int MAX_FOLDERS_PER_ROOT = 400;
    private static final Pattern VERSION_AFTER_WORD = Pattern.compile("(?i)\\bversion\\s+(\\d+(?:\\.\\d+)+)");
    private static final Pattern VERSION_NUMBER = Pattern.compile("\\d+(?:\\.\\d+)+");

    private ToolDetector() {}

    /** Every tool found on this machine, keyed by the role it can fill; the preferred installation comes first. */
    public static Map<String, List<ToolInstallation>> detectAll() {
        var results = new LinkedHashMap<String, List<ToolInstallation>>();
        for (Path folder : candidateFolders()) {
            detectInDirectory(folder, results);
        }
        return results;
    }

    /** The tools in one folder, without looking into its subfolders. */
    public static Map<String, List<ToolInstallation>> detectInDirectory(Path dir) {
        var results = new LinkedHashMap<String, List<ToolInstallation>>();
        detectInDirectory(dir, results);
        return results;
    }

    /** The tools in a folder and its subfolders, for a folder the user picked as "my tools are here". */
    public static Map<String, List<ToolInstallation>> detectUnder(Path root) {
        var results = new LinkedHashMap<String, List<ToolInstallation>>();
        for (Path folder : foldersUnder(root, PICKED_FOLDER_DEPTH)) {
            detectInDirectory(folder, results);
        }
        return results;
    }

    private static void detectInDirectory(Path dir, Map<String, List<ToolInstallation>> results) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        detectTasm(dir).ifPresent(tool -> addTool(results, "tasm", tool));
        detectTlink(dir).ifPresent(tool -> addTool(results, "tlink", tool));
        detectTurboDebugger(dir).ifPresent(tool -> {
            addTool(results, "td", tool);
            addTool(results, "turbo-debugger", tool);
        });
        detectMasm(dir).ifPresent(tool -> addTool(results, "ml", tool));
        detectLink(dir).ifPresent(tool -> addTool(results, "link", tool));
        detectCodeView(dir).ifPresent(tool -> {
            addTool(results, "cv", tool);
            addTool(results, "codeview", tool);
        });
        detectDosBox(dir).ifPresent(tool -> {
            addTool(results, tool.toolId(), tool);
            if (!tool.toolId().equals("dosbox")) {
                addTool(results, "dosbox", tool);
            }
        });
        detectHostTool(dir, "nasm", "-v").ifPresent(tool -> addTool(results, "nasm", tool));
        detectHostTool(dir, "gcc", "--version").ifPresent(tool -> addTool(results, "gcc", tool));
        detectHostTool(dir, "ld", "--version").ifPresent(tool -> addTool(results, "ld", tool));
        detectHostTool(dir, "gdb", "--version").ifPresent(tool -> addTool(results, "gdb", tool));
    }

    private static Optional<ToolInstallation> detectTurboDebugger(Path dir) {
        Path file = findExecutable(dir, "TD.EXE");
        if (file == null) return Optional.empty();
        Map<String, Path> companions = new LinkedHashMap<>();
        Path help = findExecutable(dir, "TDHELP.TDH");
        if (help != null) companions.put("TDHELP.TDH", help);
        Path mem = findExecutable(dir, "TDMEM.EXE");
        if (mem != null) companions.put("TDMEM.EXE", mem);
        return dosTool("td", file, "3.1", companions);
    }

    private static Optional<ToolInstallation> detectCodeView(Path dir) {
        Path file = findExecutable(dir, "CV.EXE");
        if (file == null) return Optional.empty();
        Map<String, Path> companions = new LinkedHashMap<>();
        Path cvpack = findExecutable(dir, "CVPACK.EXE");
        if (cvpack != null) companions.put("CVPACK.EXE", cvpack);
        return dosTool("cv", file, "4.10", companions);
    }

    private static Optional<ToolInstallation> detectTasm(Path dir) {
        Path file = findExecutable(dir, "TASM.EXE");
        return file == null ? Optional.empty() : dosTool("tasm", file, "4.1", Map.of());
    }

    private static Optional<ToolInstallation> detectTlink(Path dir) {
        Path file = findExecutable(dir, "TLINK.EXE");
        if (file == null) return Optional.empty();
        Map<String, Path> companions = new LinkedHashMap<>();
        Path rtm = findExecutable(dir, "RTM.EXE");
        if (rtm != null) companions.put("RTM.EXE", rtm);
        Path dpmi = findExecutable(dir, "DPMI16BI.OVL");
        if (dpmi != null) companions.put("DPMI16BI.OVL", dpmi);
        return dosTool("tlink", file, "7.1", companions);
    }

    private static Optional<ToolInstallation> detectMasm(Path dir) {
        Path file = findExecutable(dir, "ML.EXE");
        return file == null ? Optional.empty() : dosTool("ml", file, "6.11", Map.of());
    }

    private static Optional<ToolInstallation> detectLink(Path dir) {
        Path file = findExecutable(dir, "LINK.EXE");
        return file == null ? Optional.empty() : dosTool("link", file, "5.31", Map.of());
    }

    private static Optional<ToolInstallation> detectDosBox(Path dir) {
        // DOSBox-X ships as dosbox-x.exe. DOSBox Staging and the classic 0.74 both ship as dosbox.exe, so a bare
        // "dosbox" binary is told apart by the name it carries inside ("DOSBox 0.74-3", "dosbox-staging 0.82.2",
        // S7); a binary without either falls back to its install folder ("staging" in the name means Staging).
        // Each dialect keeps its own registry id (never the generic "dosbox"), so the user can select a specific
        // one; detectInDirectory still adds the "dosbox" auto alias.
        Path dosboxX = findDosBox(dir, "dosbox-x");
        if (dosboxX != null) return dosTool("dosbox-x", dosboxX, UNKNOWN_VERSION, Map.of());
        Path staging = findDosBox(dir, "dosbox-staging");
        if (staging != null) return dosTool("dosbox-staging", staging, UNKNOWN_VERSION, Map.of());
        Path bare = findDosBox(dir, "dosbox");
        if (bare != null) {
            Optional<BinaryScanner.DosBoxIdentity> identity;
            try {
                identity = BinaryScanner.identifyDosBox(bare);
            } catch (IOException unreadable) {
                identity = Optional.empty();
            }
            String id = identity.map(BinaryScanner.DosBoxIdentity::dialect).orElseGet(() ->
                    dir.toString().toLowerCase(Locale.ROOT).contains("staging") ? "dosbox-staging" : "dosbox-0.74");
            return dosTool(id, bare, identity.map(BinaryScanner.DosBoxIdentity::version).orElse(UNKNOWN_VERSION),
                    Map.of());
        }
        return Optional.empty();
    }

    /** A DOSBox executable named {@code <stem>.exe} (or bare {@code <stem>} on non-Windows), matched case-insensitively. */
    private static Path findDosBox(Path dir, String stem) {
        Path file = findExecutable(dir, stem + ".exe");
        return file != null ? file : findExecutable(dir, stem);
    }

    /**
     * A tool read from its binary: its version text, or for a Windows program its version resource. The fallback
     * is the version the product line is known by, for binaries whose text is compressed; DOSBox gets none, since
     * nothing depends on its version.
     */
    private static Optional<ToolInstallation> dosTool(String id, Path file, String fallbackVersion,
                                                      Map<String, Path> companions) {
        try {
            HostKind host = BinaryScanner.detectHostKind(file);
            Optional<String> version = BinaryScanner.scanVersion(file, id);
            if (version.isEmpty() && (host == HostKind.WIN64 || host == HostKind.WIN32_CONSOLE)) {
                version = BinaryScanner.productVersion(file);
            }
            String sha256 = BinaryScanner.computeSha256(file);
            return Optional.of(new ToolInstallation(id, version.orElse(fallbackVersion), file, host, companions,
                    sha256, "detected"));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static Optional<ToolInstallation> detectHostTool(Path dir, String id, String versionFlag) {
        Path file = findExecutable(dir, id + ".exe");
        if (file == null) file = findExecutable(dir, id);
        if (file == null) return Optional.empty();
        try {
            HostKind host = BinaryScanner.detectHostKind(file);
            if (host != HostKind.WIN64 && host != HostKind.WIN32_CONSOLE && host != HostKind.LINUX_ELF) {
                return Optional.empty();
            }
            String version = askVersion(file, versionFlag).orElse(UNKNOWN_VERSION);
            String sha256 = BinaryScanner.computeSha256(file);
            return Optional.of(new ToolInstallation(id, version, file, host, Map.of(), sha256, "detected"));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** Runs {@code tool --version} (NASM: {@code -v}) and reads the version from its first line. */
    static Optional<String> askVersion(Path executable, String versionFlag) {
        Process process = null;
        try {
            process = new ProcessBuilder(executable.toString(), versionFlag)
                    .directory(executable.getParent().toFile())
                    .redirectErrorStream(true)
                    .redirectInput(ProcessBuilder.Redirect.from(new File(isWindows() ? "NUL" : "/dev/null")))
                    .start();
            process.getOutputStream().close();
            byte[] head;
            try (InputStream output = process.getInputStream()) {
                head = output.readNBytes(4096);
            }
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                return Optional.empty();
            }
            String text = new String(head, StandardCharsets.UTF_8);
            return parseVersion(text.lines().findFirst().orElse(""));
        } catch (IOException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    /**
     * "NASM version 3.01 compiled on ..." gives 3.01; "GNU ld (GNU Binutils) 2.46" and
     * "gcc.exe (Rev5, Built by MSYS2 project) 16.1.0" give their last number.
     */
    static Optional<String> parseVersion(String firstLine) {
        Matcher afterWord = VERSION_AFTER_WORD.matcher(firstLine);
        if (afterWord.find()) {
            return Optional.of(afterWord.group(1));
        }
        String last = null;
        Matcher number = VERSION_NUMBER.matcher(firstLine);
        while (number.find()) {
            last = number.group();
        }
        return Optional.ofNullable(last);
    }

    private static Path findExecutable(Path dir, String filename) {
        Path direct = dir.resolve(filename);
        if (Files.isRegularFile(direct)) return direct;

        // DOS tool names are matched without regard to case.
        try (var stream = Files.list(dir)) {
            return stream.filter(p -> p.getFileName().toString().equalsIgnoreCase(filename) && Files.isRegularFile(p))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private static void addTool(Map<String, List<ToolInstallation>> map, String key, ToolInstallation tool) {
        List<ToolInstallation> found = map.computeIfAbsent(key, k -> new ArrayList<>());
        if (found.stream().noneMatch(existing -> existing.executable().equals(tool.executable()))) {
            found.add(tool);
        }
    }

    private static List<Path> candidateFolders() {
        var folders = new LinkedHashSet<Path>();

        // Folders the user or the IDE set aside for tools come first.
        String toolPath = System.getenv("IDEARM_TOOL_PATH");
        if (toolPath != null) {
            for (String part : toolPath.split(Pattern.quote(File.pathSeparator))) {
                if (!part.isBlank()) {
                    folders.addAll(foldersUnder(Path.of(part.strip()), PICKED_FOLDER_DEPTH));
                }
            }
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            folders.addAll(foldersUnder(Path.of(localAppData, "IDEARM", "tools"), TOOLS_ROOT_DEPTH));
        }

        if (isWindows()) {
            folders.add(Path.of("C:\\Program Files\\DOSBox-X"));
            folders.add(Path.of("C:\\DOSBox-X"));
            folders.add(Path.of("C:\\Program Files\\DOSBox Staging"));
            folders.add(Path.of("C:\\Program Files (x86)\\DOSBox-0.74-3"));
            folders.add(Path.of("C:\\masm\\MASM611\\BIN"));
            folders.add(Path.of("C:\\MASM611\\BIN"));
            folders.add(Path.of("C:\\TASM\\BIN"));
            folders.add(Path.of("C:\\TASM"));
            folders.add(Path.of("C:\\msys64\\ucrt64\\bin"));
            folders.add(Path.of("C:\\msys64\\mingw64\\bin"));
            folders.add(Path.of("C:\\msys64\\mingw32\\bin"));
            folders.add(Path.of("C:\\Program Files\\NASM"));
        } else {
            // The counterpart of %LOCALAPPDATA%\IDEARM\tools, then where Linux packages and macOS bundles put DOSBox.
            String dataHome = System.getenv("XDG_DATA_HOME");
            Path data = dataHome != null && !dataHome.isBlank()
                    ? Path.of(dataHome.strip())
                    : Path.of(System.getProperty("user.home", "."), ".local", "share");
            folders.addAll(foldersUnder(data.resolve("idearm").resolve("tools"), TOOLS_ROOT_DEPTH));
            folders.add(Path.of("/usr/games"));
            folders.add(Path.of("/Applications/DOSBox.app/Contents/MacOS"));
            folders.add(Path.of("/Applications/dosbox-x.app/Contents/MacOS"));
            folders.add(Path.of("/Applications/DOSBox Staging.app/Contents/MacOS"));
        }

        String envPath = System.getenv("PATH");
        if (envPath != null) {
            for (String part : envPath.split(Pattern.quote(File.pathSeparator))) {
                if (!part.isBlank()) {
                    try {
                        folders.add(Path.of(part.strip()));
                    } catch (RuntimeException invalid) {
                        // A malformed PATH entry is skipped, as the shell would.
                    }
                }
            }
        }
        return new ArrayList<>(folders);
    }

    /** A folder and its subfolders, shallow first, capped so a mistaken pick such as C:\ stays quick. */
    static List<Path> foldersUnder(Path root, int depth) {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        try (Stream<Path> tree = Files.walk(root, depth)) {
            return tree.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .limit(MAX_FOLDERS_PER_ROOT)
                    .sorted(Comparator.comparingInt(Path::getNameCount))
                    .toList();
        } catch (IOException | RuntimeException unreadable) {
            return List.of(root);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
    }
}
