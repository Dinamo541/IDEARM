package io.github.dinamo541.idearm.app.ui;

import javafx.animation.PauseTransition;
import javafx.beans.InvalidationListener;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Remembers normal bounds separately from maximized/full-screen/minimized geometry. */
public final class WindowSession {
    private WindowSession() {}

    public static void install(Stage stage, Path file) {
        var placement = read(file).fit(Screen.getScreens().stream().map(Screen::getVisualBounds).toList());
        var screen = Screen.getScreensForRectangle(placement.x(), placement.y(), placement.width(), placement.height())
                .stream().findFirst().orElse(Screen.getPrimary()).getVisualBounds();
        stage.setMinWidth(Math.min(800, screen.getWidth()));
        stage.setMinHeight(Math.min(500, screen.getHeight()));
        stage.setX(placement.x()); stage.setY(placement.y());
        stage.setWidth(placement.width()); stage.setHeight(placement.height());
        WindowPlacement[] normal = { placement };
        var settled = new PauseTransition(Duration.millis(200));
        Runnable capture = () -> {
            if (stage.isShowing() && !stage.isIconified() && !stage.isMaximized() && !stage.isFullScreen()) {
                normal[0] = new WindowPlacement(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight(), false);
            }
        };
        settled.setOnFinished(e -> capture.run());
        InvalidationListener changed = ignored -> settled.playFromStart();
        stage.xProperty().addListener(changed); stage.yProperty().addListener(changed);
        stage.widthProperty().addListener(changed); stage.heightProperty().addListener(changed);
        stage.maximizedProperty().addListener(changed); stage.fullScreenProperty().addListener(changed);
        stage.iconifiedProperty().addListener(changed);
        stage.addEventHandler(WindowEvent.WINDOW_SHOWN, e -> stage.setMaximized(placement.maximized()));
        // Hiding occurs only after close cancellation has been resolved, including Alt+F4 and File > Exit.
        stage.addEventHandler(WindowEvent.WINDOW_HIDING, e -> {
            settled.stop(); capture.run();
            var bounds = normal[0];
            write(file, new WindowPlacement(bounds.x(), bounds.y(), bounds.width(), bounds.height(), stage.isMaximized()));
        });
    }

    static WindowPlacement read(Path file) {
        try (var input = Files.newInputStream(file)) {
            var properties = new Properties(); properties.load(input);
            return new WindowPlacement(Double.parseDouble(properties.getProperty("x")),
                    Double.parseDouble(properties.getProperty("y")), Double.parseDouble(properties.getProperty("width")),
                    Double.parseDouble(properties.getProperty("height")), Boolean.parseBoolean(properties.getProperty("maximized")));
        } catch (IOException | IllegalArgumentException | NullPointerException ignored) {
            return new WindowPlacement(Double.NaN, Double.NaN, 1280, 800, false);
        }
    }

    static void write(Path file, WindowPlacement placement) {
        Path temporary = null;
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            temporary = Files.createTempFile(file.toAbsolutePath().getParent(), "window-", ".tmp");
            var properties = new Properties();
            properties.setProperty("x", Double.toString(placement.x())); properties.setProperty("y", Double.toString(placement.y()));
            properties.setProperty("width", Double.toString(placement.width()));
            properties.setProperty("height", Double.toString(placement.height()));
            properties.setProperty("maximized", Boolean.toString(placement.maximized()));
            try (var output = Files.newOutputStream(temporary)) { properties.store(output, "IDEARM window"); }
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) {
            System.err.println("Could not save window placement: " + failure.getMessage());
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }
}
