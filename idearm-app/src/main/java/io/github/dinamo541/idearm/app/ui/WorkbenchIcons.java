package io.github.dinamo541.idearm.app.ui;

import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.shape.SVGPath;

/** Original line icons on a shared 16 px grid, independent of installed symbol fonts. */
public enum WorkbenchIcons {
    SEARCH("M7 2a5 5 0 1 0 0 10a5 5 0 1 0 0-10 M11 11l4 4"),
    HISTORY("M2 6a6 6 0 1 1 0 5 M2 2v4h4 M8 4v4l3 2"),
    FOLDER("M1 4h5l2 2h7v8H1Z M1 4V2h5l2 2h6v2"),
    FILE("M3 1h6l4 4v10H3Z M9 1v4h4 M5 8h6 M5 11h4"),
    RUN("M4 2l9 6-9 6Z"),
    BUILD("M2 2h6l3 3-2 2-2-2H5L3 7 1 5Z M8 7l6 6-2 2-6-6"),
    DEBUG("M5 5h6v6a3 3 0 0 1-6 0Z M6 5V3h4v2 M2 6l3 2 M11 8l3-2 M1 10h4 M11 10h4 M2 14l3-2 M11 12l3 2"),
    STOP("M3 3h10v10H3Z"),
    THEME("M8 1a7 7 0 1 0 0 14a7 7 0 1 0 0-14 M8 1v14 M8 4h4 M8 7h6 M8 10h5 M8 13h3"),
    CLOSE("M4 4l8 8 M12 4l-8 8"),
    MINIMIZE("M3 8h10"),
    MAXIMIZE("M3 3h10v10H3Z"),
    RESTORE("M5 5V2h9v9h-3 M2 5h9v9H2Z"),
    UP("M3 10l5-5 5 5"),
    DOWN("M3 6l5 5 5-5"),
    CLEAR("M3 4h10 M6 4V2h4v2 M4 4l1 10h6l1-10 M7 6v6 M9 6v6"),
    RESTART("M2 6a6 6 0 1 1 0 5 M2 2v4h4"),
    STEP_OVER("M2 5q6-6 12 0 M11 2l3 3-3 2 M8 9v5 M5 11l3 3 3-3"),
    STEP_INTO("M8 1v9 M4 6l4 4 4-4 M4 14h8"),
    STEP_OUT("M8 11V2 M4 6l4-4 4 4 M4 14h8"),
    PLUS("M8 2v12 M2 8h12");

    private final String path;
    WorkbenchIcons(String path) { this.path = path; }
    public Node create() {
        var shape = new SVGPath(); shape.setContent(path);
        shape.getStyleClass().add("workbench-icon"); shape.setMouseTransparent(true);
        var canvas = new Pane(shape);
        canvas.setMinSize(16, 16); canvas.setPrefSize(16, 16); canvas.setMaxSize(16, 16);
        canvas.setMouseTransparent(true);
        return canvas;
    }
}
