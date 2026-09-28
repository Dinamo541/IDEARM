package io.github.dinamo541.idearm.app.view;

import io.github.dinamo541.idearm.app.i18n.Localization;
import io.github.dinamo541.idearm.app.ui.DialogWindow;
import io.github.dinamo541.idearm.app.viewmodel.WorkbenchViewModel;
import io.github.dinamo541.idearm.application.EntrySelection;
import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.build.FileNameRules;
import io.github.dinamo541.idearm.domain.model.DebugConfiguration;
import io.github.dinamo541.idearm.domain.model.Project;
import io.github.dinamo541.idearm.domain.model.TargetProfile;
import io.github.dinamo541.idearm.domain.model.TargetProfileCatalog;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;

/**
 * Dialog displaying read-only and configurable properties of the currently opened project.
 */
public final class ProjectPropertiesDialog extends Stage {

    private final WorkbenchViewModel viewModel;
    private final Localization localization;

    public ProjectPropertiesDialog(Window owner, WorkbenchViewModel viewModel, Localization localization) {
        this.viewModel = viewModel;
        this.localization = localization;

        initOwner(owner);
        io.github.dinamo541.idearm.app.ui.BrandLogo.apply(this);
        initModality(Modality.APPLICATION_MODAL);
        titleProperty().bind(localization.text("dialog.properties.title"));

        buildUi();
    }

    private void buildUi() {
        Project project = viewModel.getCurrentProject();

        VBox content;
        if (project == null) {
            var msg = new Label();
            msg.textProperty().bind(localization.text("dialog.properties.noProject"));
            content = new VBox(msg);
            content.setPadding(new Insets(24));
        } else {
            var grid = new GridPane();
            grid.getStyleClass().add("form-grid");
            var labelColumn = new javafx.scene.layout.ColumnConstraints();
            labelColumn.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
            var valueColumn = new javafx.scene.layout.ColumnConstraints();
            valueColumn.setHgrow(javafx.scene.layout.Priority.ALWAYS);
            grid.getColumnConstraints().addAll(labelColumn, valueColumn);
            grid.setHgap(12);
            grid.setVgap(12);


            // Name
            var nameLbl = new Label();
            nameLbl.textProperty().bind(localization.text("dialog.properties.name"));
            nameLbl.getStyleClass().add("form-label");
            var nameVal = new Label(project.info().name());
            grid.add(nameLbl, 0, 0);
            grid.add(nameVal, 1, 0);

            // Version
            var verLbl = new Label();
            verLbl.textProperty().bind(localization.text("dialog.properties.version"));
            verLbl.getStyleClass().add("form-label");
            var verVal = new Label(project.info().version());
            grid.add(verLbl, 0, 1);
            grid.add(verVal, 1, 1);

            // Target Profile
            var targetLbl = new Label();
            targetLbl.textProperty().bind(localization.text("dialog.properties.target"));
            targetLbl.getStyleClass().add("form-label");
            var targetVal = new Label(project.target().profile() + " (CPU: " + project.target().cpu() + ")");
            grid.add(targetLbl, 0, 2);
            grid.add(targetVal, 1, 2);

            // Toolchain
            var toolchainLbl = new Label();
            toolchainLbl.textProperty().bind(localization.text("dialog.properties.toolchain"));
            toolchainLbl.getStyleClass().add("form-label");
            var toolchainVal = new Label(project.toolchain().id() + " " + project.toolchain().version());
            grid.add(toolchainLbl, 0, 3);
            grid.add(toolchainVal, 1, 3);

            // Run Environment: DOS targets can pick which DOSBox dialect to use; native targets just show "host".
            var runLbl = new Label();
            runLbl.textProperty().bind(localization.text("dialog.properties.runEnv"));
            runLbl.getStyleClass().add("form-label");
            grid.add(runLbl, 0, 4);
            TargetProfile targetProfile = TargetProfileCatalog.require(project.target().profile());
            if (targetProfile.isDos()) {
                grid.add(buildRunEnvironmentCombo(project), 1, 4);
            } else {
                var runVal = new Label(project.run().environment() + " (cycles: " + project.run().cycles()
                        + ", memsize: " + project.run().memsize() + " MB)");
                grid.add(runVal, 1, 4);
            }

            // Debugger: the choice that decides whether the IDE can step and show registers at all.
            var debuggerLbl = new Label();
            debuggerLbl.textProperty().bind(localization.text("dialog.properties.debugger"));
            debuggerLbl.getStyleClass().add("form-label");
            grid.add(debuggerLbl, 0, 5);
            grid.add(buildDebuggerCombo(project, targetProfile), 1, 5);

            // Main file: which source Run executes. It also names the executable and is assembled first.
            var entryLbl = new Label();
            entryLbl.textProperty().bind(localization.text("dialog.properties.entry"));
            entryLbl.getStyleClass().add("form-label");
            grid.add(entryLbl, 0, 6);
            grid.add(buildEntryChooser(project, targetProfile), 1, 6);

            // Include folders: only needed when an included file sits in another folder of the project, because
            // the folder of the source that includes it is always searched.
            var includeLbl = new Label();
            includeLbl.textProperty().bind(localization.text("dialog.properties.includeDirs"));
            includeLbl.getStyleClass().add("form-label");
            grid.add(includeLbl, 0, 7);
            grid.add(buildIncludeFolders(project, targetProfile), 1, 7);

            content = new VBox(grid);
        }

        var closeButton = new Button();
        closeButton.setId("closeDialog");
        closeButton.textProperty().bind(localization.text("dialog.properties.close"));
        closeButton.setDefaultButton(true);
        closeButton.setCancelButton(true);
        closeButton.setOnAction(e -> close());

        var buttonBar = new HBox(closeButton);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.getStyleClass().add("dialog-footer");

        var root = new VBox(DialogWindow.heading(localization, "dialog.properties.title",
                "dialog.properties.description", io.github.dinamo541.idearm.app.ui.WorkbenchIcons.SETTINGS), content, buttonBar);
        DialogWindow.theme(root, getOwner());
        DialogWindow.fitToContent(this, root, 640);
    }

    /**
     * Which source Run executes, bound to {@code [sources] entry}, with the switch that says whether it is the
     * whole program or one module among several.
     *
     * <p>A folder of one-file exercises needs "build only this file", because linking them together puts every
     * exercise inside every executable and fails as soon as two of them export the same name; a program split
     * across modules needs the opposite. The choice is saved as soon as it is made, and saving the project file
     * is what makes the next Run rebuild.
     */
    private VBox buildEntryChooser(Project project, TargetProfile targetProfile) {
        List<String> sources = new ArrayList<>(viewModel.listAssemblySources());
        String entry = project.sources().entry();
        if (!sources.contains(entry)) {
            // A deleted or hand-edited entry still has to show, or the dialog would look like it lost it.
            sources.addFirst(entry);
        }
        var chooser = new ComboBox<>(FXCollections.observableArrayList(sources));
        chooser.setId("mainFileChooser");
        chooser.setValue(entry);
        chooser.setMaxWidth(Double.MAX_VALUE);

        var onlyThisFile = new CheckBox();
        onlyThisFile.setId("buildOnlyMainFile");
        onlyThisFile.textProperty().bind(localization.text("dialog.properties.entry.only"));
        onlyThisFile.setSelected(EntrySelection.buildsOnlyTheEntry(project.sources()));
        onlyThisFile.setTooltip(new Tooltip(localization.get("dialog.properties.entry.only.hint")));

        chooser.valueProperty().addListener((observable, previous, chosen) -> {
            if (chosen == null || chosen.equals(previous)) {
                return;
            }
            if (!accepts(chosen, targetProfile)) {
                chooser.setValue(previous);
                return;
            }
            viewModel.updateEntry(chosen, onlyThisFile.isSelected());
        });
        onlyThisFile.selectedProperty().addListener((observable, was, only) -> {
            if (only && !confirmsDropping(project.sources().modules())) {
                onlyThisFile.setSelected(false);
                return;
            }
            viewModel.updateEntry(chooser.getValue(), only);
        });

        return new VBox(6, chooser, onlyThisFile);
    }

    /** DOS tools only open 8.3 names, and staging would refuse the file; the dialog says so before saving. */
    private boolean accepts(String entry, TargetProfile targetProfile) {
        try {
            FileNameRules.validateRelativePath(entry, targetProfile.isDos());
            return true;
        } catch (DomainException invalid) {
            warn(localization.get("diagnostic." + invalid.code(), entry));
            return false;
        }
    }

    /** Building only the main file stops building the other modules, so the ones that go are named first. */
    private boolean confirmsDropping(List<String> modules) {
        if (modules.isEmpty()) {
            return true;
        }
        var alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(this);
        alert.setHeaderText(localization.get("dialog.properties.entry.confirm", String.join(", ", modules)));
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    /**
     * The folders an INCLUDE may name, bound to {@code [sources] include} and saved as soon as the list changes.
     *
     * <p>The chooser stays inside the project because the project file only carries relative paths, and a DOS
     * target needs every segment to be an 8.3 name as well, which is checked before the folder is accepted rather
     * than when the build fails to stage it.
     */
    private VBox buildIncludeFolders(Project project, TargetProfile targetProfile) {
        var folders = FXCollections.observableArrayList(project.sources().include());
        var list = new ListView<>(folders);
        list.setId("includeFolders");
        list.setPrefSize(320, 88);

        var hint = new Label();
        hint.textProperty().bind(localization.text("dialog.properties.includeDirs.hint"));
        hint.setWrapText(true);
        list.setPlaceholder(hint);

        var add = new Button();
        add.textProperty().bind(localization.text("dialog.properties.includeDirs.add"));
        add.setOnAction(e -> chooseIncludeFolder(targetProfile).ifPresent(folder -> {
            if (!folders.contains(folder)) {
                folders.add(folder);
                viewModel.updateIncludeDirs(List.copyOf(folders));
            }
        }));

        var remove = new Button();
        remove.textProperty().bind(localization.text("dialog.properties.includeDirs.remove"));
        remove.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        remove.setOnAction(e -> {
            folders.remove(list.getSelectionModel().getSelectedItem());
            viewModel.updateIncludeDirs(List.copyOf(folders));
        });

        var buttons = new HBox(8, add, remove);
        return new VBox(6, list, buttons);
    }

    /** A folder of this project, as the relative path {@code idearm.toml} stores, or nothing if it cannot be one. */
    private Optional<String> chooseIncludeFolder(TargetProfile targetProfile) {
        Path projectRoot = viewModel.getCurrentProjectPath();
        if (projectRoot == null) {
            return Optional.empty();
        }
        Path root = projectRoot.toAbsolutePath().normalize();
        var chooser = new DirectoryChooser();
        chooser.setTitle(localization.get("dialog.properties.includeDirs.chooser"));
        chooser.setInitialDirectory(root.toFile());
        File chosen = chooser.showDialog(this);
        if (chosen == null) {
            return Optional.empty();
        }
        Path folder = chosen.toPath().toAbsolutePath().normalize();
        if (!folder.startsWith(root) || folder.equals(root)) {
            // A folder outside the project has no relative path, and the project root is not a name DOS accepts.
            return warn(localization.get("dialog.properties.includeDirs.outside"));
        }
        String relative = root.relativize(folder).toString().replace('\\', '/');
        try {
            FileNameRules.validateRelativePath(relative, targetProfile.isDos());
        } catch (DomainException invalid) {
            return warn(localization.get("diagnostic." + invalid.code(), relative));
        }
        return Optional.of(relative);
    }

    private Optional<String> warn(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(this);
        alert.setHeaderText(message);
        alert.showAndWait();
        return Optional.empty();
    }

    /**
     * A dropdown of the DOSBox dialects installed on this machine, plus "Auto", bound to the project's
     * {@code [run] environment}. Picking one saves it to {@code idearm.toml} immediately.
     */
    private ComboBox<String> buildRunEnvironmentCombo(Project project) {
        var installed = viewModel.getToolRegistry().all().keySet();
        var items = FXCollections.<String>observableArrayList("dosbox");
        for (String dialect : io.github.dinamo541.idearm.domain.execution.DosBoxDialects.PREFERENCE) {
            if (installed.contains(dialect)) {
                items.add(dialect);
            }
        }
        // The saved choice is always offered, even if this machine cannot honour it right now: Run, Build and
        // Debug fall back to Auto gracefully, and dropping it here would look like the setting was lost.
        String current = project.run().environment();
        if (!items.contains(current)) {
            items.add(current);
        }
        var combo = new ComboBox<>(items);
        combo.setConverter(new StringConverter<>() {
            @Override public String toString(String id) {
                if (id == null) return "";
                return switch (id) {
                    case "dosbox" -> localization.get("dialog.properties.runEnv.auto");
                    case "dosbox-x" -> localization.get("dialog.properties.runEnv.dosboxX");
                    case "dosbox-staging" -> localization.get("dialog.properties.runEnv.dosboxStaging");
                    case "dosbox-0.74" -> localization.get("dialog.properties.runEnv.dosbox074");
                    default -> id;
                };
            }
            @Override public String fromString(String label) { return label; }
        });
        combo.setValue(current);
        combo.setMaxWidth(Double.MAX_VALUE);
        // updateRunEnvironment ignores a no-op change, so no stale-value guard is needed here.
        combo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                viewModel.updateRunEnvironment(newVal);
            }
        });
        return combo;
    }

    /**
     * Which debugger the project uses, bound to {@code [debug] backend} and saved as soon as it is picked.
     *
     * <p>A DOS project chooses between the built-in 8086 emulator, where the IDE steps line by line and shows
     * registers, and Turbo Debugger or CodeView in a DOSBox window, where the debugging happens in that window.
     * A native target is debugged by GDB and has nothing to choose.
     */
    private ComboBox<String> buildDebuggerCombo(Project project, TargetProfile targetProfile) {
        var items = targetProfile.isDos()
                ? FXCollections.observableArrayList(DebugConfiguration.EMULATOR, DebugConfiguration.EXTERNAL)
                : FXCollections.observableArrayList(DebugConfiguration.GDB);
        String current = project.debug().backend();
        // The emulator's older name still reads as the emulator rather than as an unknown value.
        String selected = project.debug().usesEmulator() && targetProfile.isDos()
                ? DebugConfiguration.EMULATOR : current;
        if (!items.contains(selected)) {
            items.add(selected);
        }
        var combo = new ComboBox<>(items);
        combo.setConverter(new StringConverter<>() {
            @Override public String toString(String id) {
                if (id == null) return "";
                return switch (id) {
                    case DebugConfiguration.EMULATOR -> localization.get("dialog.properties.debugger.emulator");
                    case DebugConfiguration.EXTERNAL -> localization.get("dialog.properties.debugger.external");
                    case DebugConfiguration.GDB -> localization.get("dialog.properties.debugger.gdb");
                    default -> id;
                };
            }
            @Override public String fromString(String label) { return label; }
        });
        combo.setValue(selected);
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.setDisable(!targetProfile.isDos());
        // updateDebugBackend ignores a no-op change, so no stale-value guard is needed here.
        combo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                viewModel.updateDebugBackend(newVal);
            }
        });
        return combo;
    }
}
