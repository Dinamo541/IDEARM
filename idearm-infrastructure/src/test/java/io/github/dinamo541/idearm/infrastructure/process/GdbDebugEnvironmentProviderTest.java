package io.github.dinamo541.idearm.infrastructure.process;

import io.github.dinamo541.idearm.domain.model.TargetProfileCatalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GdbDebugEnvironmentProviderTest {

    @Test
    void providerIdentificationAndSupport() {
        var provider = new GdbDebugEnvironmentProvider();
        assertEquals("gdb", provider.id());

        // GDB debugs what this machine runs: Windows programs on Windows, Linux programs elsewhere.
        boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("windows");
        assertEquals(windows, provider.supports(TargetProfileCatalog.WIN_PE64_CONSOLE));
        assertEquals(windows, provider.supports(TargetProfileCatalog.WIN_PE32_CONSOLE));
        assertEquals(!windows, provider.supports(TargetProfileCatalog.LINUX_ELF64));

        assertFalse(provider.supports(TargetProfileCatalog.DOS_EXE_16));
        assertFalse(provider.supports(null));
    }
}
