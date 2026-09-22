package io.github.dinamo541.idearm.infrastructure.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.dinamo541.idearm.infrastructure.workspace.StagingLocation.Host;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class StagingLocationTest {

    private static final Function<Path, Optional<String>> NO_SHORT_NAMES = path -> Optional.empty();

    @Test
    void anAsciiUserFolderIsUsedAsIs() {
        var host = windows(Map.of("LOCALAPPDATA", "C:\\Users\\domin\\AppData\\Local", "ProgramData", "C:\\ProgramData"),
                NO_SHORT_NAMES);

        assertEquals(Path.of("C:\\Users\\domin\\AppData\\Local", "IDEARM", "staging"), StagingLocation.resolve(host));
    }

    @Test
    void aUserFolderWithASpaceFallsBackToItsShortName() {
        var host = windows(Map.of("LOCALAPPDATA", "C:\\Users\\Juan Perez\\AppData\\Local", "ProgramData", "C:\\ProgramData"),
                path -> Optional.of("C:\\Users\\JUANPE~1\\AppData\\Local\\IDEARM\\staging"));

        assertEquals(Path.of("C:\\Users\\JUANPE~1\\AppData\\Local\\IDEARM\\staging"), StagingLocation.resolve(host));
    }

    @Test
    void anAccentedUserFolderWithoutShortNamesUsesProgramData() {
        var host = windows(Map.of("LOCALAPPDATA", "C:\\Users\\Jos\u00e9\\AppData\\Local", "ProgramData", "C:\\ProgramData",
                "SystemDrive", "C:"), NO_SHORT_NAMES);

        assertEquals(Path.of("C:\\ProgramData", "IDEARM", "staging"), StagingLocation.resolve(host));
    }

    @Test
    void theSystemDriveIsTheLastWindowsChoice() {
        var host = windows(Map.of("LOCALAPPDATA", "C:\\Users\\Jos\u00e9\\AppData\\Local", "ProgramData", "C:\\Datos del programa",
                "SystemDrive", "C:"), NO_SHORT_NAMES);

        assertEquals(Path.of("C:\\", "IDEARM", "staging"), StagingLocation.resolve(host));
    }

    @Test
    void theUserCanChooseTheFolder() {
        var host = windows(Map.of("IDEARM_STAGING_DIR", "D:\\stage", "LOCALAPPDATA", "C:\\Users\\domin\\AppData\\Local"),
                NO_SHORT_NAMES);

        assertEquals(Path.of("D:\\stage"), StagingLocation.resolve(host));
    }

    @Test
    void linuxUsesTheCacheFolder() {
        var host = new Host(Map.of(), false, Path.of("/home/ana"), Path.of("/tmp"), "ana", NO_SHORT_NAMES, path -> true);

        assertEquals(Path.of("/home/ana/.cache/idearm/staging"), StagingLocation.resolve(host));
    }

    @Test
    void anAccentedLinuxHomeFallsBackToAPrivateTemporaryFolder() {
        var host = new Host(Map.of(), false, Path.of("/home/jos\u00e9"), Path.of("/tmp"), "jos\u00e9", NO_SHORT_NAMES,
                path -> true);

        assertEquals(Path.of("/tmp/idearm-jos_/staging"), StagingLocation.resolve(host));
    }

    private static Host windows(Map<String, String> environment, Function<Path, Optional<String>> shortNames) {
        return new Host(environment, true, Path.of("C:\\Users\\x"), Path.of("C:\\Temp"), "x", shortNames, path -> true);
    }
}
