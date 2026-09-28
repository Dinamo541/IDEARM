package io.github.dinamo541.idearm.app.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javafx.css.CssMetaData;
import javafx.css.StyleConverter;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

/** A bundled PNG silhouette whose color follows CSS, including live theme and control state changes. */
final class RasterIcon extends ImageView {
    private static final String ROOT = "/io/github/dinamo541/idearm/app/icons/lucide/";
    private static final Map<String, Image> SOURCES = new HashMap<>();
    private static final Map<Tint, Image> TINTS = new HashMap<>();
    private record Tint(String asset, int argb) { }

    private static final CssMetaData<RasterIcon, Color> COLOR = new CssMetaData<>(
            "-wb-icon-color", StyleConverter.getColorConverter(), Color.GRAY) {
        @Override public boolean isSettable(RasterIcon icon) { return !icon.color.isBound(); }
        @Override public StyleableProperty<Color> getStyleableProperty(RasterIcon icon) { return icon.color; }
    };
    private static final List<CssMetaData<? extends Styleable, ?>> CSS;
    static {
        var metadata = new ArrayList<CssMetaData<? extends Styleable, ?>>(ImageView.getClassCssMetaData());
        metadata.add(COLOR);
        CSS = List.copyOf(metadata);
    }

    private final String asset;
    private final StyleableObjectProperty<Color> color = new StyleableObjectProperty<>(Color.GRAY) {
        @Override protected void invalidated() { updateImage(); }
        @Override public Object getBean() { return RasterIcon.this; }
        @Override public String getName() { return "iconColor"; }
        @Override public CssMetaData<? extends Styleable, Color> getCssMetaData() { return COLOR; }
    };

    RasterIcon(String asset, double size) {
        this.asset = asset;
        setFitWidth(size);
        setFitHeight(size);
        setPreserveRatio(true);
        setSmooth(true);
        setMouseTransparent(true);
        getStyleClass().add("workbench-icon");
        updateImage();
    }

    @Override public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() { return CSS; }

    private void updateImage() {
        var paint = Objects.requireNonNullElse(color.get(), Color.GRAY);
        int argb = ((int) Math.round(paint.getOpacity() * 255) << 24)
                | ((int) Math.round(paint.getRed() * 255) << 16)
                | ((int) Math.round(paint.getGreen() * 255) << 8)
                | (int) Math.round(paint.getBlue() * 255);
        setImage(TINTS.computeIfAbsent(new Tint(asset, argb), RasterIcon::tint));
    }

    /** Only recolors the downloaded bitmap's alpha mask; no icon geometry is generated at runtime. */
    private static Image tint(Tint key) {
        var source = SOURCES.computeIfAbsent(key.asset(), name -> {
            var url = Objects.requireNonNull(RasterIcon.class.getResource(ROOT + name + ".png"),
                    "Missing bundled icon: " + name);
            var image = new Image(url.toExternalForm());
            if (image.isError()) throw new IllegalStateException("Unreadable icon: " + name, image.getException());
            return image;
        });
        int width = (int) source.getWidth();
        int height = (int) source.getHeight();
        var result = new WritableImage(width, height);
        var reader = source.getPixelReader();
        var writer = result.getPixelWriter();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int alpha = ((reader.getArgb(x, y) >>> 24) * (key.argb() >>> 24) + 127) / 255;
                writer.setArgb(x, y, (alpha << 24) | (key.argb() & 0x00ffffff));
            }
        }
        return result;
    }
}
