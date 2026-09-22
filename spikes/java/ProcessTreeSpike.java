import static java.lang.ProcessBuilder.Redirect.DISCARD;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Spike F0-S6: stop DOSBox from Java and avoid orphan processes when the IDE dies abruptly.
 *
 * <pre>
 * java --enable-native-access=ALL-UNNAMED spikes/java/ProcessTreeSpike.java stop  &lt;dosbox-x.exe&gt;
 * java --enable-native-access=ALL-UNNAMED spikes/java/ProcessTreeSpike.java crash &lt;dosbox-x.exe&gt; spikes/java/ProcessTreeSpike.java
 * </pre>
 */
public class ProcessTreeSpike {

    public static void main(String[] args) throws Throwable {
        switch (args[0]) {
            case "stop" -> stopScenario(Path.of(args[1]));
            case "crash" -> crashScenario(Path.of(args[1]), Path.of(args[2]));
            case "crash-child" -> crashChild(Path.of(args[1]), Boolean.parseBoolean(args[2]));
            default -> throw new IllegalArgumentException("Unknown mode: " + args[0]);
        }
    }

    /** Normal Stop from the IDE: end the session and confirm nothing is left running. */
    static void stopScenario(Path dosbox) throws Exception {
        Process process = startDosBox(dosbox);
        Thread.sleep(2000);
        long descendants = process.descendants().count();
        long started = System.nanoTime();
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy(); // on Windows this is TerminateProcess: there is no graceful close
        boolean ended = process.waitFor(5, TimeUnit.SECONDS);
        System.out.printf("Stop: descendants=%d | ended=%s in %d ms | exit=%s%n", descendants, ended,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started), ended ? process.exitValue() : "-");
    }

    /** Abrupt IDE exit, first without and then with a Job Object. */
    static void crashScenario(Path dosbox, Path self) throws Exception {
        String java = ProcessHandle.current().info().command().orElse("java");
        for (boolean withJob : new boolean[] {false, true}) {
            Process ide = new ProcessBuilder(java, "--enable-native-access=ALL-UNNAMED", self.toString(),
                    "crash-child", dosbox.toString(), Boolean.toString(withJob))
                    .redirectErrorStream(true)
                    .start();
            long dosboxPid = -1;
            try (var reader = ide.inputReader()) {
                for (String line; (line = reader.readLine()) != null; ) {
                    if (line.startsWith("PID=")) {
                        dosboxPid = Long.parseLong(line.substring(4));
                    } else {
                        System.out.println("  [simulated IDE] " + line);
                    }
                }
            }
            ide.waitFor();
            Thread.sleep(1500);
            long pid = dosboxPid;
            boolean alive = ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
            System.out.printf("IDE dies %s a Job Object -> DOSBox-X (pid %d) %s%n",
                    withJob ? "WITH" : "WITHOUT", pid, alive ? "IS STILL RUNNING (orphan)" : "was terminated");
            if (alive) {
                ProcessHandle.of(pid).ifPresent(ProcessHandle::destroyForcibly);
            }
        }
    }

    /** An "IDE" that launches DOSBox-X and dies abruptly, without running any cleanup. */
    static void crashChild(Path dosbox, boolean withJob) throws Throwable {
        Process process = startDosBox(dosbox);
        if (withJob) {
            WindowsJob.killOnClose().assign(process.pid());
        }
        System.out.println("PID=" + process.pid());
        System.out.flush();
        Thread.sleep(1500);
        Runtime.getRuntime().halt(0);
    }

    /** Output is discarded: if DOSBox inherited it, it would keep the parent's pipe open. */
    static Process startDosBox(Path dosbox) throws Exception {
        return new ProcessBuilder(dosbox.toString(), "-fastlaunch")
                .redirectOutput(DISCARD)
                .redirectError(DISCARD)
                .start();
    }

    /** Windows Job Object with KILL_ON_JOB_CLOSE, through Java's FFM API (no JNA). */
    static final class WindowsJob {
        private static final int JOB_OBJECT_EXTENDED_LIMIT_INFORMATION = 9;
        private static final int JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE = 0x2000;
        private static final long LIMIT_FLAGS_OFFSET = 16;         // BasicLimitInformation.LimitFlags on x64
        private static final long EXTENDED_LIMIT_INFO_SIZE = 144;  // sizeof(JOBOBJECT_EXTENDED_LIMIT_INFORMATION) on x64
        private static final int PROCESS_TERMINATE = 0x0001;
        private static final int PROCESS_SET_QUOTA = 0x0100;

        private static final Linker LINKER = Linker.nativeLinker();
        private static final SymbolLookup KERNEL32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
        private static final MethodHandle CREATE_JOB_OBJECT =
                function("CreateJobObjectW", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
        private static final MethodHandle SET_INFORMATION_JOB_OBJECT =
                function("SetInformationJobObject", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT));
        private static final MethodHandle OPEN_PROCESS =
                function("OpenProcess", FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT));
        private static final MethodHandle ASSIGN_PROCESS_TO_JOB_OBJECT =
                function("AssignProcessToJobObject", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
        private static final MethodHandle CLOSE_HANDLE =
                function("CloseHandle", FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment handle;

        private WindowsJob(MemorySegment handle) {
            this.handle = handle;
        }

        static WindowsJob killOnClose() throws Throwable {
            var job = (MemorySegment) CREATE_JOB_OBJECT.invokeExact(MemorySegment.NULL, MemorySegment.NULL);
            if (job.equals(MemorySegment.NULL)) {
                throw new IllegalStateException("CreateJobObjectW failed");
            }
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment info = arena.allocate(EXTENDED_LIMIT_INFO_SIZE, 8); // zero-initialized
                info.set(JAVA_INT, LIMIT_FLAGS_OFFSET, JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE);
                int ok = (int) SET_INFORMATION_JOB_OBJECT.invokeExact(job, JOB_OBJECT_EXTENDED_LIMIT_INFORMATION, info,
                        (int) EXTENDED_LIMIT_INFO_SIZE);
                if (ok == 0) {
                    throw new IllegalStateException("SetInformationJobObject failed");
                }
            }
            return new WindowsJob(job);
        }

        void assign(long pid) throws Throwable {
            var process = (MemorySegment) OPEN_PROCESS.invokeExact(PROCESS_SET_QUOTA | PROCESS_TERMINATE, 0, (int) pid);
            if (process.equals(MemorySegment.NULL)) {
                throw new IllegalStateException("OpenProcess failed for pid " + pid);
            }
            try {
                int ok = (int) ASSIGN_PROCESS_TO_JOB_OBJECT.invokeExact(handle, process);
                if (ok == 0) {
                    throw new IllegalStateException("AssignProcessToJobObject failed");
                }
            } finally {
                int ignored = (int) CLOSE_HANDLE.invokeExact(process);
            }
        }

        private static MethodHandle function(String name, FunctionDescriptor descriptor) {
            return LINKER.downcallHandle(KERNEL32.find(name).orElseThrow(), descriptor);
        }
    }
}
