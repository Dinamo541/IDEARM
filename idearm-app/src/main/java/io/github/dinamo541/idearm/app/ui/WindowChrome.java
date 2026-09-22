package io.github.dinamo541.idearm.app.ui;

import io.github.dinamo541.idearm.app.i18n.Localization;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.HBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

/** Client-rendered title controls, title dragging and eight-way edge resizing. */
public final class WindowChrome {
    private WindowChrome() {}

    public static HBox controls(Stage stage, Localization text) {
        var minimize = button(WorkbenchIcons.MINIMIZE, "window.minimize", text, () -> stage.setIconified(true));
        minimize.setId("window-minimize");
        var maximize = button(WorkbenchIcons.MAXIMIZE, "window.maximize", text, () -> stage.setMaximized(!stage.isMaximized()));
        maximize.setId("window-maximize");
        stage.maximizedProperty().addListener((o, before, after) -> {
            maximize.setGraphic((after ? WorkbenchIcons.RESTORE : WorkbenchIcons.MAXIMIZE).create());
            HoverHelp.install(maximize, text, after ? "window.restore" : "window.maximize", "");
        });
        var close = button(WorkbenchIcons.CLOSE, "window.close", text, () -> requestClose(stage));
        close.setId("window-close"); close.getStyleClass().add("window-close");
        var controls = new HBox(minimize, maximize, close); controls.getStyleClass().add("window-controls");
        return controls;
    }

    private static Button button(WorkbenchIcons icon, String key, Localization text, Runnable action) {
        var button = new Button(null, icon.create()); button.getStyleClass().add("window-button");
        HoverHelp.install(button, text, key, ""); button.setOnAction(e -> action.run()); return button;
    }

    /** All exit paths must honor unsaved-buffer cancellation. Stage.close() alone skips that event. */
    public static void requestClose(Stage stage) {
        // JavaFX's default close-request handler hides the stage only if our handler did not consume it.
        stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
    }

    public static void install(Stage stage, HBox header, Localization text) {
        Scene scene = stage.getScene();
        double[] drag = new double[3];
        boolean[] gesture = new boolean[3]; // [0] armed on empty header space, [1] moved since press, [2] at a screen's top edge.
        // Toolbar skins consume bubbling mouse events. Capture the gesture before it reaches the skin.
        header.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            gesture[0] = false;
            gesture[1] = false;
            if (e.getButton() != MouseButton.PRIMARY || interactive(e.getTarget()) || stage.isFullScreen()) return;
            gesture[0] = true;
            drag[0] = e.getScreenX() - stage.getX(); drag[1] = e.getScreenY() - stage.getY();
            drag[2] = drag[0] / stage.getWidth();
            e.consume();
        });
        header.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (!gesture[0] || !e.isPrimaryButtonDown() || stage.isFullScreen()) return;
            gesture[1] = true;
            if (stage.isMaximized()) {
                stage.setMaximized(false); drag[0] = stage.getWidth() * drag[2]; drag[1] = 18;
            }
            stage.setX(e.getScreenX() - drag[0]); stage.setY(e.getScreenY() - drag[1]);
            gesture[2] = atTopEdge(e.getScreenX(), e.getScreenY());
            e.consume();
        });
        header.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            // Dragging the title to the top edge of a screen maximizes the window, as native windows do.
            if (gesture[0] && gesture[1] && gesture[2] && !stage.isFullScreen()) {
                stage.setMaximized(true);
            }
            gesture[0] = false; gesture[2] = false;
        });
        header.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
            if (!gesture[1] && e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && !interactive(e.getTarget())) {
                stage.setMaximized(!stage.isMaximized());
                e.consume();
            }
        });
        var systemMenu = new ContextMenu();
        for (String key : new String[]{"window.restore", "window.minimize", "window.maximize", "window.close"}) {
            var item = new MenuItem(); item.textProperty().bind(text.text(key));
            item.setOnAction(e -> {
                switch (key) {
                    case "window.restore" -> stage.setMaximized(false);
                    case "window.minimize" -> stage.setIconified(true);
                    case "window.maximize" -> stage.setMaximized(true);
                    default -> requestClose(stage);
                }
            });
            systemMenu.getItems().add(item);
        }
        header.setOnContextMenuRequested(e -> {
            if (!interactive(e.getTarget())) systemMenu.show(header, e.getScreenX(), e.getScreenY());
        });
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.isAltDown() && e.getCode() == KeyCode.F4) { requestClose(stage); e.consume(); }
            else if (e.isAltDown() && e.getCode() == KeyCode.SPACE) {
                systemMenu.show(header, stage.getX() + 8, stage.getY() + 36); e.consume();
            }
        });
        installResize(stage);
    }

    /** True when the pointer sits at the top edge of whichever screen it is over — the cue to maximize. */
    private static boolean atTopEdge(double screenX, double screenY) {
        for (Screen screen : Screen.getScreens()) {
            var bounds = screen.getVisualBounds();
            if (screenX >= bounds.getMinX() && screenX <= bounds.getMaxX() && screenY <= bounds.getMinY() + 4) {
                return true;
            }
        }
        return false;
    }

    private static boolean interactive(Object target) {
        if (!(target instanceof Node node)) return true;
        for (Node current = node; current != null; current = current.getParent()) {
            if (current instanceof ButtonBase || current instanceof MenuBar || current instanceof TextInputControl
                    || current.getStyleClass().contains("overflow-button")) return true;
        }
        return false;
    }

    private static void installResize(Stage stage) {
        Scene scene = stage.getScene();
        int[] edges = {0}; double[] start = new double[6];
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, e -> {
            int edge = edge(stage, e.getSceneX(), e.getSceneY());
            scene.setCursor(switch (edge) {
                case 1, 2 -> Cursor.H_RESIZE; case 4, 8 -> Cursor.V_RESIZE;
                case 5, 10 -> Cursor.NW_RESIZE; case 6, 9 -> Cursor.NE_RESIZE;
                default -> Cursor.DEFAULT;
            });
        });
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            edges[0] = edge(stage, e.getSceneX(), e.getSceneY());
            if (edges[0] == 0) return;
            start[0] = e.getScreenX(); start[1] = e.getScreenY(); start[2] = stage.getX(); start[3] = stage.getY();
            start[4] = stage.getWidth(); start[5] = stage.getHeight(); e.consume();
        });
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (edges[0] == 0) return;
            double dx = e.getScreenX() - start[0], dy = e.getScreenY() - start[1];
            if ((edges[0] & 3) != 0) {
                boolean west = (edges[0] & 1) != 0;
                double width = Math.max(stage.getMinWidth(), start[4] + (west ? -dx : dx));
                if (west) stage.setX(start[2] + start[4] - width); stage.setWidth(width);
            }
            if ((edges[0] & 12) != 0) {
                boolean north = (edges[0] & 4) != 0;
                double height = Math.max(stage.getMinHeight(), start[5] + (north ? -dy : dy));
                if (north) stage.setY(start[3] + start[5] - height); stage.setHeight(height);
            }
            e.consume();
        });
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (edges[0] != 0) { edges[0] = 0; scene.setCursor(Cursor.DEFAULT); e.consume(); }
        });
    }
    private static int edge(Stage stage, double x, double y) {
        if (stage.isMaximized() || stage.isFullScreen() || !stage.isResizable()) return 0;
        return (x < 5 ? 1 : x > stage.getWidth() - 5 ? 2 : 0)
                | (y < 5 ? 4 : y > stage.getHeight() - 5 ? 8 : 0);
    }
}
