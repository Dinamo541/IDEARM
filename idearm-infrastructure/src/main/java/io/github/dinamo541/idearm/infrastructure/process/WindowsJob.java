package io.github.dinamo541.idearm.infrastructure.process;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/** A Windows x64 Job Object whose descendants terminate when its last handle closes. */
final class WindowsJob implements AutoCloseable {
    private static final int EXTENDED_LIMIT_INFORMATION = 9;
    private static final int KILL_ON_JOB_CLOSE = 0x2000;
    private static final int PROCESS_TERMINATE = 0x0001;
    private static final int PROCESS_SET_QUOTA = 0x0100;
    private static final long LIMIT_FLAGS_OFFSET = 16;
    private static final long EXTENDED_LIMIT_INFO_SIZE = 144;
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup KERNEL32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
    private static final MethodHandle CREATE = function("CreateJobObjectW", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle SET = function("SetInformationJobObject", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT));
    private static final MethodHandle OPEN = function("OpenProcess", FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT));
    private static final MethodHandle ASSIGN = function("AssignProcessToJobObject", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    private static final MethodHandle CLOSE = function("CloseHandle", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle ERROR = function("GetLastError", FunctionDescriptor.of(JAVA_INT));
    private MemorySegment handle;

    private WindowsJob(MemorySegment handle) { this.handle = handle; }

    static WindowsJob create() throws IOException {
        if (ADDRESS.byteSize() != 8) throw new IOException("process.job.unsupportedArchitecture");
        WindowsJob result = null;
        try {
            MemorySegment handle = (MemorySegment) CREATE.invokeExact(MemorySegment.NULL, MemorySegment.NULL);
            if (handle.equals(MemorySegment.NULL)) throw failure("CreateJobObjectW");
            result = new WindowsJob(handle);
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment info = arena.allocate(EXTENDED_LIMIT_INFO_SIZE, 8);
                info.set(JAVA_INT, LIMIT_FLAGS_OFFSET, KILL_ON_JOB_CLOSE);
                int success = (int) SET.invokeExact(handle, EXTENDED_LIMIT_INFORMATION, info, (int) EXTENDED_LIMIT_INFO_SIZE);
                if (success == 0) throw failure("SetInformationJobObject");
            }
            return result;
        } catch (Throwable failure) {
            if (result != null) result.close();
            throw asIoException(failure);
        }
    }

    void assign(Process process) throws IOException {
        try {
            MemorySegment processHandle = (MemorySegment) OPEN.invokeExact(PROCESS_TERMINATE | PROCESS_SET_QUOTA, 0, (int) process.pid());
            // Very short-lived commands can finish before OpenProcess obtains a handle.
            if (processHandle.equals(MemorySegment.NULL)) {
                if (!process.isAlive()) return;
                throw failure("OpenProcess");
            }
            try {
                int success = (int) ASSIGN.invokeExact(handle, processHandle);
                if (success == 0 && process.isAlive()) throw failure("AssignProcessToJobObject");
            } finally {
                int ignored = (int) CLOSE.invokeExact(processHandle);
            }
        } catch (Throwable failure) { throw asIoException(failure); }
    }

    @Override public synchronized void close() {
        if (handle.equals(MemorySegment.NULL)) return;
        try { int ignored = (int) CLOSE.invokeExact(handle); }
        catch (Throwable failure) { throw new IllegalStateException("process.job.close", failure); }
        finally { handle = MemorySegment.NULL; }
    }

    private static IOException failure(String operation) throws Throwable {
        return new IOException("process.job.failed: " + operation + " (Windows error " + (int) ERROR.invokeExact() + ")");
    }
    private static IOException asIoException(Throwable failure) {
        if (failure instanceof IOException io) return io;
        return new IOException("process.job.failed", failure);
    }
    private static MethodHandle function(String name, FunctionDescriptor signature) {
        return LINKER.downcallHandle(KERNEL32.find(name).orElseThrow(), signature);
    }
}
