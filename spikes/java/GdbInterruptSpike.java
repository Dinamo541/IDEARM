import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Spike S9 — can a freely running program be interrupted under GDB/MI, so the IDE can offer Pause?
 *
 * <p>IDEARM starts the program with {@code -gdb-set new-console on} on Windows, which gives it its own console.
 * The question is whether {@code -exec-interrupt} still reaches it, how long it takes, and where the program is
 * reported to be afterwards.
 *
 * <p>Usage:
 * <pre>
 * gcc -g -O0 -o loop.exe loop.c        // a program with an endless loop
 * java spikes/java/GdbInterruptSpike.java &lt;gdb.exe&gt; loop.exe [async] [console]
 * </pre>
 * where {@code async} and {@code console} are {@code true} or {@code false} (both default to {@code true}).
 */
public final class GdbInterruptSpike {

    private static final long INTERRUPT_TIMEOUT_NANOS = 5_000_000_000L;

    private final BufferedWriter writer;
    private final LinkedBlockingQueue<String> lines = new LinkedBlockingQueue<>();

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: GdbInterruptSpike <gdb> <program> [async] [console]");
            System.exit(2);
        }
        String gdb = args[0];
        String program = Paths.get(args[1]).toAbsolutePath().normalize().toString().replace(File.separatorChar, '/');
        boolean async = args.length < 3 || Boolean.parseBoolean(args[2]);
        boolean newConsole = args.length < 4 || Boolean.parseBoolean(args[3]);

        var builder = new ProcessBuilder(gdb, "--interpreter=mi3", "--nx", "--quiet");
        builder.redirectErrorStream(true);
        Process process = builder.start();
        var spike = new GdbInterruptSpike(process);

        spike.send("-gdb-set pagination off");
        if (async) {
            // Without this GDB blocks while the program runs and does not even read the interrupt command.
            spike.send("-gdb-set mi-async on");
        }
        if (newConsole) {
            spike.send("-gdb-set new-console on");
        }
        spike.send("-file-exec-and-symbols \"" + program + "\"");
        Thread.sleep(500);
        spike.send("-exec-run");
        Thread.sleep(2000);

        spike.lines.clear();
        long start = System.nanoTime();
        spike.send("-exec-interrupt");
        String stopped = spike.awaitStopped();
        long millis = (System.nanoTime() - start) / 1_000_000;

        System.out.println();
        System.out.println("mi-async=" + async + " new-console=" + newConsole);
        if (stopped == null) {
            System.out.println("RESULT: no *stopped within 5 s; the interrupt did not reach the program.");
        } else {
            System.out.println("RESULT: stopped after " + millis + " ms");
            System.out.println("        " + stopped);
            // On Windows the interrupt arrives on a thread GDB injects, which stands in ntdll with no source
            // line. The program's own thread has to be selected before its position can be reported.
            spike.send("-thread-info");
            Thread.sleep(700);
            spike.send("-thread-select 1");
            Thread.sleep(700);
            spike.send("-stack-list-frames 0 2");
            Thread.sleep(700);
            spike.send("-exec-continue");
            Thread.sleep(500);
        }
        spike.send("-gdb-exit");
        process.waitFor(3, TimeUnit.SECONDS);
        process.destroyForcibly();
    }

    private GdbInterruptSpike(Process process) {
        this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        Thread readerThread = new Thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                    System.out.println("  < " + line);
                }
            } catch (IOException closed) {
                // The session ended; nothing left to read.
            }
        }, "spike-gdb-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private String awaitStopped() throws InterruptedException {
        long deadline = System.nanoTime() + INTERRUPT_TIMEOUT_NANOS;
        while (System.nanoTime() < deadline) {
            String line = lines.poll(200, TimeUnit.MILLISECONDS);
            if (line != null && line.startsWith("*stopped")) {
                return line;
            }
        }
        return null;
    }

    private void send(String command) throws IOException {
        System.out.println("  > " + command);
        writer.write(command);
        writer.write("\n");
        writer.flush();
    }
}
