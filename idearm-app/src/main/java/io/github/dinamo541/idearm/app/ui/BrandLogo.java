package io.github.dinamo541.idearm.app.ui;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.*;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Scale;

/** IDEARM's original assembly monogram. Keep geometry in sync with branding/idearm.svg. */
public final class BrandLogo {
    private BrandLogo() {}
    public static Node create(double size) {
        var edge = path("M32 2L58 17V47L32 62L6 47V17Z", new LinearGradient(0, 0, 1, 1, true,
                CycleMethod.NO_CYCLE, new Stop(0, Color.web("#52d6ef")), new Stop(1, Color.web("#3976f6"))));
        var core = path("M32 7L53 20V44L32 57L11 44V20Z", Color.web("#101b2d"));
        var monogram = path("M20 44L29 20H35L44 44H37L35 38H28L26 44Z M30 32H33L31.5 26Z", Color.web("#79efd2"));
        monogram.setFillRule(javafx.scene.shape.FillRule.EVEN_ODD);
        var code = path("M19 24L14 31L19 38 M45 24L50 31L45 38", Color.TRANSPARENT);
        code.setStroke(Color.web("#52c6ff")); code.setStrokeWidth(2);
        var group = new Group(edge, core, monogram, code);
        group.getTransforms().add(new Scale(size / 64, size / 64));
        var wrapper = new javafx.scene.layout.Pane(group);
        wrapper.setMinSize(size, size); wrapper.setPrefSize(size, size); wrapper.setMaxSize(size, size);
        wrapper.setAccessibleText("IDEARM");
        return wrapper;
    }
    private static SVGPath path(String content, Paint fill) {
        var path = new SVGPath(); path.setContent(content); path.setFill(fill); return path;
    }
    public static WritableImage image(int size) {
        var options = new SnapshotParameters(); options.setFill(Color.TRANSPARENT);
        return create(size).snapshot(options, new WritableImage(size, size));
    }
}
