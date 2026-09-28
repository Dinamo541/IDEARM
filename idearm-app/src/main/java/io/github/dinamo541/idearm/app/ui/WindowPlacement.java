package io.github.dinamo541.idearm.app.ui;

import javafx.geometry.Rectangle2D;
import java.util.Comparator;
import java.util.List;

/** Screen-independent placement math, in JavaFX logical pixels (including negative monitor origins). */
record WindowPlacement(double x, double y, double width, double height, boolean maximized) {
    WindowPlacement fit(List<Rectangle2D> screens) {
        if (screens.isEmpty()) throw new IllegalArgumentException("At least one screen is required");
        boolean valid = Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(width)
                && Double.isFinite(height) && width > 0 && height > 0;
        Rectangle2D screen = valid ? screens.stream().max(Comparator.comparingDouble(this::overlap))
                .filter(bounds -> overlap(bounds) > 0).orElse(screens.getFirst()) : screens.getFirst();
        double w = Math.min(screen.getWidth(), Math.max(800, valid ? width : 1280));
        double h = Math.min(screen.getHeight(), Math.max(500, valid ? height : 800));
        boolean relocate = !valid || overlap(screen) == 0;
        double left = relocate ? screen.getMinX() + (screen.getWidth() - w) / 2 : x;
        double top = relocate ? screen.getMinY() + (screen.getHeight() - h) / 2 : y;
        return new WindowPlacement(Math.clamp(left, screen.getMinX(), screen.getMaxX() - w),
                Math.clamp(top, screen.getMinY(), screen.getMaxY() - h), w, h, maximized);
    }

    private double overlap(Rectangle2D screen) {
        return Math.max(0, Math.min(x + width, screen.getMaxX()) - Math.max(x, screen.getMinX()))
                * Math.max(0, Math.min(y + height, screen.getMaxY()) - Math.max(y, screen.getMinY()));
    }
}
