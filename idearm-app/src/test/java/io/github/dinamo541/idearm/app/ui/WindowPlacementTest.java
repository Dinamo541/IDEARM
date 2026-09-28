package io.github.dinamo541.idearm.app.ui;

import javafx.geometry.Rectangle2D;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WindowPlacementTest {
    private static final Rectangle2D PRIMARY = new Rectangle2D(0, 0, 1920, 1040);
    @TempDir Path directory;

    @Test void preservesNormalBoundsOnNegativeOriginMonitor() {
        var saved = new WindowPlacement(-1500, 80, 1000, 720, true);
        assertEquals(saved, saved.fit(List.of(PRIMARY, new Rectangle2D(-1920, 0, 1920, 1080))));
    }

    @Test void recoversWindowWhenMonitorIsDisconnected() {
        var fitted = new WindowPlacement(-1800, -1000, 1200, 800, true).fit(List.of(PRIMARY));
        assertEquals(new WindowPlacement(360, 120, 1200, 800, true), fitted);
    }

    @Test void respectsTaskbarAndSmallHighDpiWorkAreas() {
        var screen = new Rectangle2D(30, 40, 720, 440);
        var fitted = new WindowPlacement(500, 500, 1280, 800, false).fit(List.of(screen));
        assertEquals(new WindowPlacement(30, 40, 720, 440, false), fitted);
    }

    @Test void clampsPartlyVisibleWindowToBestMatchingMonitor() {
        var fitted = new WindowPlacement(1800, -50, 1000, 600, false)
                .fit(List.of(PRIMARY, new Rectangle2D(1920, 0, 1920, 1040)));
        assertEquals(1920, fitted.x()); assertEquals(0, fitted.y());
    }

    @Test void rejectsNonFiniteOrInvalidStoredGeometry() {
        for (double value : new double[] { Double.NaN, Double.POSITIVE_INFINITY, -1, 0 }) {
            var fitted = new WindowPlacement(0, 0, value, 700, false).fit(List.of(PRIMARY));
            assertEquals(1280, fitted.width()); assertEquals(800, fitted.height());
            assertEquals(320, fitted.x()); assertEquals(120, fitted.y());
        }
    }

    @Test void stateRoundTripsAndCorruptionFallsBackSafely() throws Exception {
        var file = directory.resolve("settings/window.properties");
        var saved = new WindowPlacement(60, 80, 900, 640, true);
        WindowSession.write(file, saved);
        assertEquals(saved, WindowSession.read(file));
        Files.writeString(file, "width=garbage\nx=NaN\n");
        assertEquals(new WindowPlacement(320, 120, 1280, 800, false), WindowSession.read(file).fit(List.of(PRIMARY)));
        assertDoesNotThrow(() -> WindowSession.read(directory.resolve("missing")));
    }
}
