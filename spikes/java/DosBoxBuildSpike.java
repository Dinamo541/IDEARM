import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.stream.Collectors.joining;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Spike F0-S2 in Java: TASM + TLINK build in a single headless DOSBox-X session launched from Java.
 *
 * <p>Throwaway prototype of what F1 turns into {@code DosBoxToolRunner}, {@code PathMapper} and the Borland
 * diagnostic parsers (Infrastructure layer). It runs without compiling, through the source-file launcher:
 *
 * <pre>java spikes/java/DosBoxBuildSpike.java &lt;dosbox-x.exe&gt; &lt;tasmDir&gt; &lt;sourcesDir&gt; &lt;workDir&gt; MODULE...</pre>
 */
public class DosBoxBuildSpike {

    enum Severity { WARNING, ERROR, FATAL }

    record Diagnostic(Severity severity, String tool, Path file, Integer line, String message) {}

    private static final Charset CP437 = Charset.forName("IBM437");

    /** Limit of a DOS program's command tail; anything longer needs a response file. */
    private static final int DOS_MAX_COMMAND = 126;

    /** TASM with a line: {@code **Error** S:\ERRSYM.ASM(12) Undefined symbol: PRINTNUMBER}. */
    private static final Pattern TASM_WITH_LINE =
            Pattern.compile("^\\*+(Error|Fatal|Warning)\\*+\\s+(\\S+)\\((\\d+)\\)\\s+(.*)$");

    /** TASM without a line: {@code **Fatal** Command line: Can't locate file: S:\NOFILE.ASM}. */
    private static final Pattern TASM_WITHOUT_LINE = Pattern.compile("^\\*+(Error|Fatal|Warning)\\*+\\s+(.*)$");

    /** TLINK: {@code Error: Undefined symbol PRINTNUMBER in module S:\EXTERN.ASM}. */
    private static final Pattern TLINK = Pattern.compile("^(Error|Fatal|Warning):\\s+(.*?)(?:\\s+in module\\s+(\\S+))?$");

    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.err.println("Usage: java DosBoxBuildSpike.java <dosbox-x.exe> <tasmDir> <sourcesDir> <workDir> MODULE...");
            System.exit(2);
        }
        Path dosbox = Path.of(args[0]);
        Path tools = Path.of(args[1]).toAbsolutePath();
        Path sources = Path.of(args[2]).toAbsolutePath();
        Path work = Path.of(args[3]).toAbsolutePath();
        List<String> modules = Stream.of(args).skip(4).map(module -> module.toUpperCase(Locale.ROOT)).toList();

        prepareWorkDirectory(work);
        Path driveC = work.resolve("C");
        for (String dir : List.of("OBJ", "OUT", "LOG")) {
            Files.createDirectories(driveC.resolve(dir));
        }

        // A single session: assemble every module and link only if none of them failed.
        // The failure flag is an environment variable, not a file: DOSBox's shell opens redirections before it
        // evaluates IF, so "if errorlevel 1 echo x>FLAG" creates the flag file even when the condition is false.
        var batch = new ArrayList<>(List.of("@echo off", "set PATH=T:\\", "set FAILED=", "C:", "cd \\"));
        var steps = new ArrayList<String>();
        for (String module : modules) {
            String step = "S%02d".formatted(steps.size() + 1);
            addStep(batch, step, "T:\\TASM.EXE /zi /l /w2 S:\\%1$s.ASM,C:\\OBJ\\%1$s.OBJ,C:\\OUT\\%1$s.LST".formatted(module));
            batch.add("if not \"%RC%\"==\"0\" set FAILED=1");
            steps.add(step);
        }
        String objects = modules.stream().map(module -> "C:\\OBJ\\" + module + ".OBJ").collect(joining(" "));
        String exe = modules.getFirst();
        batch.add("if \"%FAILED%\"==\"1\" goto end");
        addStep(batch, "LINK", "T:\\TLINK.EXE /v /m %s,C:\\OUT\\%s.EXE,C:\\OUT\\%s.MAP".formatted(objects, exe, exe));
        steps.add("LINK");
        batch.addAll(List.of(":end", "echo DONE>C:\\LOG\\DONE.TXT", "exit"));
        Files.writeString(driveC.resolve("BUILD.BAT"), String.join("\r\n", batch) + "\r\n", US_ASCII);

        Path conf = work.resolve("build.conf");
        Files.writeString(conf, String.join("\r\n",
                "[sdl]", "fullscreen=false",
                "[dosbox]", "memsize=16",
                "[cpu]", "core=auto", "cycles=max",
                "[serial]", "serial1=disabled", "serial2=disabled",
                "[autoexec]",
                "mount T \"%s\" -ro".formatted(tools),
                "mount S \"%s\" -ro".formatted(sources),
                "mount C \"%s\"".formatted(driveC),
                "C:",
                "BUILD.BAT") + "\r\n", US_ASCII);

        long started = System.nanoTime();
        Process process = new ProcessBuilder(dosbox.toString(), "-silent", "-exit", "-fastlaunch", "-conf", conf.toString())
                .directory(work.toFile())
                .redirectErrorStream(true)
                .redirectOutput(work.resolve("dosbox-console.txt").toFile())
                .start();
        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
        System.out.printf("DOSBox-X: %d ms | finished=%s | exit=%s | DONE=%s%n",
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started), finished,
                finished ? process.exitValue() : "timeout", Files.exists(driveC.resolve("LOG").resolve("DONE.TXT")));

        Map<Character, Path> drives = Map.of('T', tools, 'S', sources, 'C', driveC);
        var diagnostics = new ArrayList<Diagnostic>();
        for (String step : steps) {
            Path log = driveC.resolve("LOG").resolve(step + ".LOG");
            if (!Files.exists(log)) {
                System.out.printf("  %-4s not executed%n", step);
                continue;
            }
            String rc = Files.readString(driveC.resolve("LOG").resolve(step + ".RC"), US_ASCII).trim();
            System.out.printf("  %-4s %s%n", step, rc);
            String tool = step.equals("LINK") ? "tlink" : "tasm";
            for (String line : Files.readAllLines(log, CP437)) {
                parse(tool, line, drives).ifPresent(diagnostics::add);
            }
        }

        System.out.println("Diagnostics:");
        for (Diagnostic diagnostic : diagnostics) {
            String location = diagnostic.file() == null ? "(no file)"
                    : diagnostic.file() + (diagnostic.line() == null ? "" : ":" + diagnostic.line())
                            + (Files.exists(diagnostic.file()) ? "" : " [missing on the host]");
            System.out.printf("  %-7s %-5s %s  %s%n", diagnostic.severity(), diagnostic.tool(), location, diagnostic.message());
        }
        Path exePath = driveC.resolve("OUT").resolve(exe + ".EXE");
        System.out.println("Executable: " + (Files.exists(exePath) ? exePath + " (" + Files.size(exePath) + " B)" : "not generated"));
    }

    static void addStep(List<String> batch, String name, String command) {
        if (command.length() > DOS_MAX_COMMAND) {
            throw new IllegalArgumentException("DOS command of %d characters (max %d): a response file is needed"
                    .formatted(command.length(), DOS_MAX_COMMAND));
        }
        batch.add(command + " > C:\\LOG\\" + name + ".LOG");
        batch.add("set RC=0");
        for (int level : new int[] {1, 2, 3, 4, 255}) {
            batch.add("if errorlevel %d set RC=%d".formatted(level, level));
        }
        batch.add("echo RC=%RC%>C:\\LOG\\" + name + ".RC");
    }

    static Optional<Diagnostic> parse(String tool, String line, Map<Character, Path> drives) {
        if (tool.equals("tasm")) {
            Matcher withLine = TASM_WITH_LINE.matcher(line);
            if (withLine.matches()) {
                return Optional.of(new Diagnostic(severity(withLine.group(1)), tool, toHost(withLine.group(2), drives),
                        Integer.valueOf(withLine.group(3)), withLine.group(4)));
            }
            Matcher withoutLine = TASM_WITHOUT_LINE.matcher(line);
            return withoutLine.matches()
                    ? Optional.of(new Diagnostic(severity(withoutLine.group(1)), tool, null, null, withoutLine.group(2)))
                    : Optional.empty();
        }
        Matcher linker = TLINK.matcher(line);
        if (!linker.matches()) {
            return Optional.empty();
        }
        Path module = linker.group(3) == null ? null : toHost(linker.group(3), drives);
        return Optional.of(new Diagnostic(severity(linker.group(1)), tool, module, null, linker.group(2)));
    }

    static Severity severity(String word) {
        return switch (word) {
            case "Warning" -> Severity.WARNING;
            case "Fatal" -> Severity.FATAL;
            default -> Severity.ERROR;
        };
    }

    /** Translates a DOS path (e.g. {@code S:\ERRSYM.ASM}) into a host path using the mounts. */
    static Path toHost(String dosPath, Map<Character, Path> drives) {
        if (dosPath.length() < 3 || dosPath.charAt(1) != ':') {
            return Path.of(dosPath);
        }
        Path root = drives.get(Character.toUpperCase(dosPath.charAt(0)));
        if (root == null) {
            return Path.of(dosPath);
        }
        Path candidate = root;
        for (String part : dosPath.substring(3).split("\\\\")) {
            candidate = resolveIgnoringCase(candidate, part);
        }
        return candidate;
    }

    static Path resolveIgnoringCase(Path dir, String name) {
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.filter(entry -> entry.getFileName().toString().equalsIgnoreCase(name))
                    .findFirst()
                    .orElse(dir.resolve(name));
        } catch (IOException e) {
            return dir.resolve(name);
        }
    }

    /** Deletes the working folder only when it is empty or was created by this spike (it contains build.conf). */
    static void prepareWorkDirectory(Path work) throws IOException {
        if (Files.exists(work)) {
            boolean empty;
            try (Stream<Path> entries = Files.list(work)) {
                empty = entries.findAny().isEmpty();
            }
            if (!empty && !Files.exists(work.resolve("build.conf"))) {
                throw new IOException("The folder exists and does not look like this spike's output: " + work);
            }
            try (Stream<Path> tree = Files.walk(work)) {
                for (Path path : tree.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
        Files.createDirectories(work);
    }
}
