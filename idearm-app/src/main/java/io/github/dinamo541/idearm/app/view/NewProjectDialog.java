package io.github.dinamo541.idearm.app.view;

import io.github.dinamo541.idearm.app.i18n.Localization;
import io.github.dinamo541.idearm.app.ui.DialogWindow;
import io.github.dinamo541.idearm.app.viewmodel.WorkbenchViewModel;
import io.github.dinamo541.idearm.domain.model.DebugConfiguration;
import io.github.dinamo541.idearm.domain.model.Project;
import java.io.File;
import java.nio.file.Path;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Dialog wizard for creating a new x86 Assembly project with target profile
 * and toolchain selection.
 */
public final class NewProjectDialog extends Stage {

    private final WorkbenchViewModel viewModel;
    private final Localization localization;

    private final TextField nameField = new TextField("MyAsmProject");
    private final TextField locationField = new TextField();
    private final ComboBox<String> profileBox = new ComboBox<>();
    private final ComboBox<String> cpuBox = new ComboBox<>();
    private final ComboBox<String> toolchainBox = new ComboBox<>();
    private final ComboBox<String> debuggerBox = new ComboBox<>();

    public NewProjectDialog(Window owner, WorkbenchViewModel viewModel, Localization localization) {
        this.viewModel = viewModel;
        this.localization = localization;

        initOwner(owner);
        io.github.dinamo541.idearm.app.ui.BrandLogo.apply(this);
        initModality(Modality.APPLICATION_MODAL);
        titleProperty().bind(localization.text("dialog.newProject.title"));

        Path defaultParent = viewModel.getCurrentProjectPath() != null
                ? viewModel.getCurrentProjectPath().getParent()
                : Path.of(System.getProperty("user.home"), "idearm-projects");
        if (defaultParent != null) {
            locationField.setText(defaultParent.toAbsolutePath().normalize().toString());
        }

        // Only targets this machine can build: MSYS2's ld writes PE files, a Linux linker writes ELF files.
        boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("windows");
        if (windows) {
            profileBox.getItems().addAll("dos-exe-16", "win-pe64-console", "win-pe32-console");
        } else {
            profileBox.getItems().addAll("dos-exe-16", "linux-elf64");
        }

        cpuBox.getItems().addAll("8086", "80186", "80286", "80386", "x86-64");

        // Each target lists only the toolchains that can build it, so an impossible pairing cannot be chosen.
        profileBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if ("dos-exe-16".equals(newVal)) {
                toolchainBox.getItems().setAll("borland-tasm", "microsoft-masm");
                toolchainBox.setValue("borland-tasm");
                cpuBox.setValue("8086");
                // The built-in emulator first: it needs no proprietary debugger and drives every IDE panel.
                debuggerBox.getItems().setAll(DebugConfiguration.EMULATOR, DebugConfiguration.EXTERNAL);
                debuggerBox.setValue(DebugConfiguration.EMULATOR);
                debuggerBox.setDisable(false);
            } else {
                toolchainBox.getItems().setAll("nasm");
                toolchainBox.setValue("nasm");
                cpuBox.setValue("win-pe32-console".equals(newVal) ? "80386" : "x86-64");
                // A native program is debugged by GDB; there is nothing to choose.
                debuggerBox.getItems().setAll(DebugConfiguration.GDB);
                debuggerBox.setValue(DebugConfiguration.GDB);
                debuggerBox.setDisable(true);
            }
        });
        debuggerBox.setConverter(new javafx.util.StringConverter<>() {
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
        profileBox.setValue("dos-exe-16");

        buildUi();
    }

    private void buildUi() {
        var grid = new GridPane();
        grid.getStyleClass().add("form-grid");
        var labelColumn = new javafx.scene.layout.ColumnConstraints();
        labelColumn.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        var valueColumn = new javafx.scene.layout.ColumnConstraints();
        valueColumn.setHgrow(javafx.scene.layout.Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelColumn, valueColumn);
        grid.setHgap(12);
        grid.setVgap(14);


        // Row 0: Name
        var nameLabel = new Label();
        nameLabel.textProperty().bind(localization.text("dialog.newProject.name"));
        nameLabel.getStyleClass().add("form-label");
        nameLabel.setLabelFor(nameField);
        GridPane.setHgrow(nameField, Priority.ALWAYS);
        grid.add(nameLabel, 0, 0);
        grid.add(nameField, 1, 0);

        // Row 1: Location + Browse
        var locationLabel = new Label();
        locationLabel.textProperty().bind(localization.text("dialog.newProject.location"));
        locationLabel.getStyleClass().add("form-label");
        locationLabel.setLabelFor(locationField);

        var browseButton = new Button();
        browseButton.textProperty().bind(localization.text("dialog.newProject.browse"));
        browseButton.setOnAction(e -> {
            var chooser = new DirectoryChooser();
            chooser.setTitle(localization.get("dialog.newProject.chooseLocation"));
            File current = new File(locationField.getText());
            if (current.exists() && current.isDirectory()) {
                chooser.setInitialDirectory(current);
            }
            File chosen = chooser.showDialog(this);
            if (chosen != null) {
                locationField.setText(chosen.getAbsolutePath());
            }
        });

        var locBox = new HBox(8, locationField, browseButton);
        HBox.setHgrow(locationField, Priority.ALWAYS);
        grid.add(locationLabel, 0, 1);
        grid.add(locBox, 1, 1);

        // Row 2: Target Profile
        var profileLabel = new Label();
        profileLabel.textProperty().bind(localization.text("dialog.newProject.profile"));
        profileLabel.getStyleClass().add("form-label");
        profileLabel.setLabelFor(profileBox);
        profileBox.setMaxWidth(Double.MAX_VALUE);
        grid.add(profileLabel, 0, 2);
        grid.add(profileBox, 1, 2);

        // Row 3: CPU
        var cpuLabel = new Label();
        cpuLabel.textProperty().bind(localization.text("dialog.newProject.cpu"));
        cpuLabel.getStyleClass().add("form-label");
        cpuLabel.setLabelFor(cpuBox);
        cpuBox.setMaxWidth(Double.MAX_VALUE);
        grid.add(cpuLabel, 0, 3);
        grid.add(cpuBox, 1, 3);

        // Row 4: Toolchain
        var toolchainLabel = new Label();
        toolchainLabel.textProperty().bind(localization.text("dialog.newProject.toolchain"));
        toolchainLabel.getStyleClass().add("form-label");
        toolchainLabel.setLabelFor(toolchainBox);
        toolchainBox.setMaxWidth(Double.MAX_VALUE);
        grid.add(toolchainLabel, 0, 4);
        grid.add(toolchainBox, 1, 4);

        // Row 5: Debugger
        var debuggerLabel = new Label();
        debuggerLabel.textProperty().bind(localization.text("dialog.newProject.debugger"));
        debuggerLabel.getStyleClass().add("form-label");
        debuggerLabel.setLabelFor(debuggerBox);
        debuggerBox.setMaxWidth(Double.MAX_VALUE);
        grid.add(debuggerLabel, 0, 5);
        grid.add(debuggerBox, 1, 5);

        // Buttons
        var createButton = new Button();
        createButton.setId("createProject");
        createButton.textProperty().bind(localization.text("dialog.newProject.create"));
        createButton.setDefaultButton(true);
        createButton.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                () -> nameField.getText().isBlank() || locationField.getText().isBlank(),
                nameField.textProperty(), locationField.textProperty()));
        createButton.getStyleClass().addAll("accent");
        createButton.setOnAction(e -> handleCreate());

        var cancelButton = new Button();
        cancelButton.textProperty().bind(localization.text("dialog.newProject.cancel"));
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(e -> close());

        var buttonBar = new HBox(10, cancelButton, createButton);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.getStyleClass().add("dialog-footer");

        var root = new VBox(DialogWindow.heading(localization, "dialog.newProject.title",
                "dialog.newProject.description", io.github.dinamo541.idearm.app.ui.WorkbenchIcons.PLUS), grid, buttonBar);
        DialogWindow.theme(root, getOwner());
        DialogWindow.fitToContent(this, root, 600);
    }

    private void handleCreate() {
        String name = nameField.getText().trim();
        String loc = locationField.getText().trim();

        if (name.isEmpty() || loc.isEmpty()) {
            return;
        }

        try {
            Path parentDir = Path.of(loc);
            if (!java.nio.file.Files.exists(parentDir)) {
                java.nio.file.Files.createDirectories(parentDir);
            }

            String selectedTc = toolchainBox.getValue();
            String versionConstraint;
            if ("microsoft-masm".equals(selectedTc)) {
                versionConstraint = ">=6.11";
            } else if ("nasm".equals(selectedTc)) {
                versionConstraint = ">=2.14";
            } else {
                versionConstraint = ">=3.2";
            }
            Project project = viewModel.createProject(
                    parentDir,
                    name,
                    profileBox.getValue(),
                    cpuBox.getValue(),
                    selectedTc,
                    versionConstraint,
                    debuggerBox.getValue()
            );

            if (project != null) {
                close();
            }
        } catch (Exception ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.initOwner(this);
            alert.setHeaderText(localization.get("dialog.newProject.error",
                    ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
            alert.showAndWait();
        }
    }
}
