package io.github.dinamo541.idearm.app.view;

import io.github.dinamo541.idearm.app.i18n.Localization;
import io.github.dinamo541.idearm.app.viewmodel.WorkbenchViewModel;
import io.github.dinamo541.idearm.app.ui.WorkbenchIcons;
import io.github.dinamo541.idearm.app.ui.HoverHelp;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Compact workbench actions; less frequent project commands remain in the menus and palette. */
public final class WorkbenchToolBar extends ToolBar {
    private Runnable onCommandPaletteRequested = () -> {};
    private Runnable onThemeRequested = () -> {};
    private Runnable onRecentRequested = () -> {};
    /** Width of the action buttons, separators and spacing beside the search box. */
    private static final double RESERVED_WIDTH = 320;
    /** Width of one stepping button, shown only while a debug session is paused. */
    private static final double STEP_WIDTH = 32;
    public WorkbenchToolBar(WorkbenchViewModel model, Localization text) {
        getStyleClass().add("workbench-toolbar");
        var command = new Button(null, WorkbenchIcons.SEARCH.create(14));
        var compact = widthProperty().lessThan(450);
        command.textProperty().bind(javafx.beans.binding.Bindings.when(compact).then("")
                .otherwise(text.text("workbench.commandCenter")));
        command.minWidthProperty().bind(javafx.beans.binding.Bindings.when(compact).then(32).otherwise(140));
        command.getStyleClass().add("command-center");
        HoverHelp.install(command, text, "help.commands", "Ctrl+Shift+P");
        command.setOnAction(e -> onCommandPaletteRequested.run());

        var leftSpacer = new Region();
        leftSpacer.setId("title-drag-area-left");
        leftSpacer.setPickOnBounds(true);
        leftSpacer.setMinHeight(28);
        io.github.dinamo541.idearm.app.ui.WindowChrome.draggable(leftSpacer);
        HBox.setHgrow(leftSpacer, Priority.ALWAYS);

        var rightSpacer = new Region();
        rightSpacer.setId("title-drag-area");
        rightSpacer.setPickOnBounds(true);
        rightSpacer.setMinHeight(28);
        io.github.dinamo541.idearm.app.ui.WindowChrome.draggable(rightSpacer);
        HBox.setHgrow(rightSpacer, Priority.ALWAYS);

        var recent = action(WorkbenchIcons.HISTORY, "help.recent", text, () -> onRecentRequested.run());
        recent.setId("recent-action");
        var run = action(WorkbenchIcons.RUN, "tooltip.run", text, model::run);
        run.setId("run-action");
        run.getStyleClass().add("run-action");
        run.disableProperty().bind(model.busyProperty().or(model.currentProjectProperty().isNull()));
        var build = action(WorkbenchIcons.BUILD, "tooltip.build", text, model::build);
        build.disableProperty().bind(run.disableProperty());
        var debug = action(WorkbenchIcons.DEBUG, "help.debug", text, () -> {
            if (model.getBottomPanel().getDebugViewModel().pausedProperty().get()) model.resumeDebug(); else model.debug();
        });
        debug.getStyleClass().add("debug-action");
        debug.disableProperty().bind(model.currentProjectProperty().isNull().or(model.busyProperty()
                .and(model.getBottomPanel().getDebugViewModel().pausedProperty().not())));
        // The stepping controls appear only while a session that can step is running, so the toolbar stays compact
        // for everything else. A launch-only debugger never shows them.
        var debugModel = model.getBottomPanel().getDebugViewModel();
        var steppable = debugModel.pausedProperty().and(debugModel.canStepProperty());
        var pause = stepAction(WorkbenchIcons.PAUSE, "tooltip.pause", text, model::pauseDebug,
                debugModel.activeProperty().and(debugModel.pausedProperty().not())
                        .and(debugModel.canPauseProperty()));
        var stepOver = stepAction(WorkbenchIcons.STEP_OVER, "tooltip.stepOver", text, model::stepOver, steppable);
        var stepInto = stepAction(WorkbenchIcons.STEP_INTO, "tooltip.stepInto", text, model::stepInto, steppable);
        var stepOut = stepAction(WorkbenchIcons.STEP_OUT, "tooltip.stepOut", text, model::stepOut, steppable);
        // The search box takes what the actions leave, so the theme and language buttons never fall into the
        // overflow menu: a ToolBar overflows by preferred widths, and Spanish menu names are longer.
        var steps = java.util.List.of(pause, stepOver, stepInto, stepOut);
        var reserved = javafx.beans.binding.Bindings.createDoubleBinding(
                () -> RESERVED_WIDTH + STEP_WIDTH * steps.stream().filter(javafx.scene.Node::isManaged).count(),
                pause.managedProperty(), stepOver.managedProperty(), stepInto.managedProperty(), stepOut.managedProperty());
        command.prefWidthProperty().bind(javafx.beans.binding.Bindings.when(compact).then(32.0).otherwise(
                javafx.beans.binding.Bindings.createDoubleBinding(
                        () -> Math.max(160, Math.min(320, getWidth() - reserved.get())), widthProperty(), reserved)));
        var stop = action(WorkbenchIcons.STOP, "tooltip.stop", text, model::stop);
        stop.getStyleClass().add("stop-action");
        stop.disableProperty().bind(model.busyProperty().not());
        var theme = action(WorkbenchIcons.THEME, "help.theme", text, () -> onThemeRequested.run());
        theme.setId("theme-action");
        var language = new Button();
        language.getStyleClass().add("language-button");
        language.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(
                () -> text.localeProperty().get().getLanguage().toUpperCase(java.util.Locale.ROOT), text.localeProperty()));
        language.setOnAction(e -> text.localeProperty().set(text.localeProperty().get().equals(Localization.ENGLISH)
                ? Localization.SPANISH : Localization.ENGLISH));
        HoverHelp.install(language, text, "action.switchLanguage", "");
        getItems().addAll(leftSpacer, command, rightSpacer, recent, new Separator(), run, debug, pause, stepOver, stepInto, stepOut,
                build, stop, new Separator(), theme, language);
    }
    private Button action(WorkbenchIcons icon, String key, Localization text, Runnable action) {
        var button = new Button(null, icon.create());
        button.getStyleClass().add("icon-button");
        HoverHelp.install(button, text, key, "");
        button.setOnAction(e -> action.run());
        return button;
    }
    /** A debug control that takes no room while the session cannot use it. */
    private Button stepAction(WorkbenchIcons icon, String key, Localization text, Runnable action,
                              javafx.beans.binding.BooleanExpression enabled) {
        var button = action(icon, key, text, action);
        button.visibleProperty().bind(enabled);
        button.managedProperty().bind(button.visibleProperty());
        return button;
    }
    public void setOnCommandPaletteRequested(Runnable action) { onCommandPaletteRequested = action; }
    public void setOnThemeRequested(Runnable action) { onThemeRequested = action; }
    public void setOnRecentRequested(Runnable action) { onRecentRequested = action; }
}
