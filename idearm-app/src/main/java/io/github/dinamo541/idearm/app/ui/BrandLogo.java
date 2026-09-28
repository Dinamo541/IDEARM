package io.github.dinamo541.idearm.app.ui;

import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.collections.ListChangeListener;
import java.util.List;
import java.util.Objects;
import javafx.scene.paint.Color;
import javafx.scene.image.ImageView;

/** IDEARM's original brand, loaded from its bundled transparent PNG at every UI location. */
public final class BrandLogo {
    private BrandLogo() {}
    private static final String RESOURCE = Objects.requireNonNull(BrandLogo.class.getResource(
            "/io/github/dinamo541/idearm/app/branding/idearm.png")).toExternalForm();
    private static final Image LOGO = new Image(RESOURCE);
    private static List<Image> icons;
    private static boolean installed;

    /** Load real image resources, without relying on a layout/snapshot pulse during startup. */
    public static void apply(Stage stage) {
        if (icons == null) {
            icons = java.util.stream.IntStream.of(16, 24, 32, 48, 64, 128, 256)
                    .mapToObj(size -> new Image(RESOURCE, size, size, true, true)).toList();
        }
        stage.getIcons().setAll(icons);
    }

    /** Includes JavaFX Alert/TextInputDialog windows and future secondary stages. Popups have no OS icon. */
    public static void install() {
        if (installed) return;
        installed = true;
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) for (Window window : change.getAddedSubList()) {
                if (window instanceof Stage stage) apply(stage);
            }
        });
        for (Window window : Window.getWindows()) if (window instanceof Stage stage) apply(stage);
    }
    public static Node create(double size) {
        var image = new ImageView(LOGO);
        image.setFitWidth(size);
        image.setFitHeight(size);
        image.setPreserveRatio(true);
        image.setSmooth(true);
        image.setMouseTransparent(true);
        var wrapper = new javafx.scene.layout.Pane(image);
        wrapper.setMinSize(size, size); wrapper.setPrefSize(size, size); wrapper.setMaxSize(size, size);
        wrapper.setAccessibleText("IDEARM");
        return wrapper;
    }
    public static WritableImage image(int size) {
        var options = new SnapshotParameters(); options.setFill(Color.TRANSPARENT);
        return create(size).snapshot(options, new WritableImage(size, size));
    }
}
