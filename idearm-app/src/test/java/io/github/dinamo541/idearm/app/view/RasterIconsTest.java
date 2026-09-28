package io.github.dinamo541.idearm.app.view;

import io.github.dinamo541.idearm.app.ui.BrandLogo;
import io.github.dinamo541.idearm.app.ui.WorkbenchIcons;
import io.github.dinamo541.idearm.app.ui.WorkbenchTheme;
import javafx.css.PseudoClass;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Asset completeness and state changes matter even when no SVG geometry is left in Java. */
class RasterIconsTest {
    @BeforeAll static void startJavaFx() throws InterruptedException { FxDialogs.startJavaFx(); }

    @Test void everyIconLoadsATransparentHighResolutionBitmapInsideItsCompactCanvas() throws Exception {
        FxDialogs.onFxThread(() -> {
            for (var icon : WorkbenchIcons.values()) {
                var canvas = (Pane) icon.create();
                var view = (ImageView) canvas.getChildren().getFirst();
                var image = view.getImage();
                assertFalse(image.isError(), icon.name());
                assertEquals(96, image.getWidth(), icon.name());
                assertEquals(96, image.getHeight(), icon.name());
                assertEquals(16, view.getFitWidth());
                assertEquals(16, canvas.prefWidth(-1));
                assertEquals(0, image.getPixelReader().getArgb(0, 0) >>> 24, icon.name());
                assertTrue(ink(image).getOpacity() > 0.9, "Empty or faint asset: " + icon);
            }
            return null;
        });
    }

    @Test void theSameBitmapFollowsThemeAndSelectionColorsWithoutLosingTransparency() throws Exception {
        FxDialogs.onFxThread(() -> {
            var canvas = (Pane) WorkbenchIcons.RUN.create();
            var button = new Button(null, canvas);
            button.getStyleClass().add("run-action");
            var root = new VBox(button);
            WorkbenchTheme.apply(root, null);
            new Scene(root);
            root.applyCss();
            var image = (ImageView) canvas.getChildren().getFirst();
            assertColor("#89d185", ink(image.getImage()));
            root.getStyleClass().add("light");
            root.applyCss();
            assertColor("#287d35", ink(image.getImage()));
            button.getStyleClass().add("accent");
            root.applyCss();
            assertColor("#ffffff", ink(image.getImage()));
            assertEquals(0, image.getImage().getPixelReader().getArgb(0, 0) >>> 24);
            button.getStyleClass().removeAll("accent", "run-action");
            button.getStyleClass().add("activity-button");
            button.pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"), true);
            root.applyCss();
            assertColor("#3b3b3b", ink(image.getImage()));
            return null;
        });
    }

    @Test void brandUsesTheExistingPngAtHeaderDialogAndWelcomeSizes() throws Exception {
        FxDialogs.onFxThread(() -> {
            for (int size : new int[] {25, 48, 56}) {
                var canvas = (Pane) BrandLogo.create(size);
                var image = (ImageView) canvas.getChildren().getFirst();
                assertTrue(image.getImage().getUrl().endsWith("/branding/idearm.png"));
                assertEquals(size, image.getFitWidth());
                assertEquals(size, image.getFitHeight());
                assertFalse(image.getImage().isError());
                assertTrue(image.getImage().getWidth() >= 256);
            }
            return null;
        });
    }

    private static Color ink(Image image) {
        Color darkest = Color.TRANSPARENT;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color pixel = image.getPixelReader().getColor(x, y);
                if (pixel.getOpacity() > darkest.getOpacity()) darkest = pixel;
            }
        }
        return darkest;
    }

    private static void assertColor(String expected, Color actual) {
        Color color = Color.web(expected);
        assertEquals(color.getRed(), actual.getRed(), 1.0 / 255);
        assertEquals(color.getGreen(), actual.getGreen(), 1.0 / 255);
        assertEquals(color.getBlue(), actual.getBlue(), 1.0 / 255);
    }
}
