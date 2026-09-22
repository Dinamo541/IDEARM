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
    public WorkbenchToolBar(WorkbenchViewModel model, Localization text) {
        getStyleClass().add("workbench-toolbar");
        var command = new Button(null, WorkbenchIcons.SEARCH.create());
        command.textProperty().bind(text.text("workbench.commandCenter"));
        command.getStyleClass().add("command-center");
        HoverHelp.install(command, text, "help.commands", "Ctrl+Shift+P");
        command.setOnAction(e -> onCommandPaletteRequested.run());
        var spacer = new Region();
        spacer.setId("title-drag-area");
        spacer.setPickOnBounds(true);
        spacer.setMinHeight(28);
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var recent = action(WorkbenchIcons.HISTORY, "help.recent", text, () -> onRecentRequested.run());
        recent.setId("recent-action");
        var run = action(WorkbenchIcons.RUN, "tooltip.run", text, () -> model.run(false));
        run.setId("run-action");
        run.getStyleClass().add("run-action");
        run.disableProperty().bind(model.busyProperty().or(model.currentProjectProperty().isNull()));
        var build = action(WorkbenchIcons.BUILD, "tooltip.build", text, model::build);
        build.disableProperty().bind(run.disableProperty());
        var debug = action(WorkbenchIcons.DEBUG, "help.debug", text, () -> {
            if (model.getBottomPanel().getDebugViewModel().pausedProperty().get()) model.resumeDebug(); else model.debug();
        });
        debug.disableProperty().bind(model.currentProjectProperty().isNull().or(model.busyProperty()
                .and(model.getBottomPanel().getDebugViewModel().pausedProperty().not())));
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
        getItems().addAll(command, spacer, recent, new Separator(), run, debug, build, stop, new Separator(), theme, language);
    }
    private Button action(WorkbenchIcons icon, String key, Localization text, Runnable action) {
        var button = new Button(null, icon.create());
        button.getStyleClass().add("icon-button");
        HoverHelp.install(button, text, key, "");
        button.setOnAction(e -> action.run());
        return button;
    }
    public void setOnCommandPaletteRequested(Runnable action) { onCommandPaletteRequested = action; }
    public void setOnThemeRequested(Runnable action) { onThemeRequested = action; }
    public void setOnRecentRequested(Runnable action) { onRecentRequested = action; }
}
