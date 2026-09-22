package io.github.dinamo541.idearm.infrastructure.process;

import io.github.dinamo541.idearm.domain.build.ProcessRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The environment a child process starts with.
 *
 * <p>Without inheritance only the variables a process needs to start are kept, so tool-specific ones such as
 * INCLUDE, LIB, TASM or MASM cannot change how a build behaves. On Linux a program with a window (DOSBox) also needs
 * the display variables of X11 or Wayland.
 */
final class ProcessEnvironment {

    static final List<String> ESSENTIAL = List.of(
            "SystemRoot", "windir", "SystemDrive", "ProgramData", "PATH", "PATHEXT", "TEMP", "TMP",
            "USERPROFILE", "APPDATA", "LOCALAPPDATA", "NUMBER_OF_PROCESSORS", "PROCESSOR_ARCHITECTURE",
            "HOME", "LANG", "LC_ALL", "DISPLAY", "WAYLAND_DISPLAY", "XDG_RUNTIME_DIR", "XAUTHORITY");

    private ProcessEnvironment() {
    }

    static void apply(ProcessBuilder builder, ProcessRequest request) {
        Map<String, String> environment = builder.environment();
        if (!request.inheritEnvironment()) {
            Map<String, String> inherited = new HashMap<>(environment);
            environment.clear();
            for (String name : ESSENTIAL) {
                String value = inherited.get(name);
                if (value != null) {
                    environment.put(name, value);
                }
            }
        }
        environment.putAll(request.environment());
    }
}
