package io.github.dinamo541.idearm.app.ui;

import javafx.scene.Node;
import javafx.scene.layout.Pane;

/** Bundled Lucide PNG assets, displayed on the existing compact workbench canvas. */
public enum WorkbenchIcons {
    SEARCH("search"),
    HISTORY("rotate-ccw"),
    FOLDER("folder"),
    FILE("file-text"),
    ASSEMBLY("file-code-corner"),
    INCLUDE("brackets"),
    CONFIG("sliders-horizontal"),
    EXPLORER("folder-tree"),
    TERMINAL("square-terminal"),
    BOOK("book-open"),
    DATA_TRANSFER("arrow-right-left"),
    ARITHMETIC("calculator"),
    LOGIC("circuit-board"),
    CONTROL_FLOW("git-branch"),
    STRINGS("text-cursor-input"),
    FLAGS_CONTROL("flag"),
    STACK_PROCEDURES("layers"),
    SYSTEM_INTERRUPTS("zap"),
    IO_PORTS("cable"),
    BIT_MANIPULATION("binary"),
    FLOATING_POINT("variable"),
    REGISTER_GENERAL("database"),
    REGISTER_POINTER("corner-down-right"),
    REGISTER_SEGMENT("panels-top-left"),
    REGISTER_EXECUTION("timer"),
    REGISTER_VECTOR("grid-2x2"),
    REGISTER_SYSTEM("microchip"),
    LEFT("chevron-left"),
    RIGHT("chevron-right"),
    HOME("house"),
    COPY("copy"),
    NEW_FILE("file-plus-corner"),
    NEW_FOLDER("folder-plus"),
    FOLDER_OPEN("folder-open"),
    COLLAPSE("copy-minus"),
    SETTINGS("settings"),
    MINIMAP("panel-right"),
    OUTLINE("list-tree"),
    PROBLEMS("triangle-alert"),
    REFERENCES("code-xml"),
    CHIP("cpu"),
    RUN("play"),
    BUILD("hammer"),
    DEBUG("bug"),
    STOP("square"),
    THEME("contrast"),
    CLOSE("x"),
    MINIMIZE("minus"),
    MAXIMIZE("square"),
    RESTORE("copy"),
    FULL_SCREEN("maximize"),
    EXIT_FULL_SCREEN("minimize"),
    UP("chevron-up"),
    DOWN("chevron-down"),
    CLEAR("trash"),
    RESTART("rotate-ccw"),
    STEP_OVER("redo-2"),
    STEP_INTO("arrow-down-to-line"),
    STEP_OUT("arrow-up-from-line"),
    PAUSE("pause"),
    CHECK("circle-check"),
    PLUS("plus"),
    BREAKPOINT("circle-dot"),
    BREAKPOINT_DISABLED("circle");

    private final String asset;
    WorkbenchIcons(String asset) { this.asset = asset; }
    public Node create() {
        return create(16);
    }
    public Node create(double size) {
        var canvas = new Pane(new RasterIcon(asset, size));
        canvas.setMinSize(size, size); canvas.setPrefSize(size, size); canvas.setMaxSize(size, size);
        canvas.setMouseTransparent(true);
        return canvas;
    }
}
