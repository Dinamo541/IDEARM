package io.github.dinamo541.idearm.app.view;

import io.github.dinamo541.idearm.app.i18n.Localization;
import io.github.dinamo541.idearm.app.viewmodel.WorkbenchViewModel;
import io.github.dinamo541.idearm.domain.model.Project;
import io.github.dinamo541.idearm.domain.model.TargetProfile;
import io.github.dinamo541.idearm.domain.model.TargetProfileCatalog;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
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
            grid.setHgap(12);
            grid.setVgap(12);
            grid.setPadding(new Insets(16));

            // Name
            var nameLbl = new Label();
            nameLbl.textProperty().bind(localization.text("dialog.properties.name"));
            nameLbl.setStyle("-fx-font-weight: bold;");
            var nameVal = new Label(project.info().name());
            grid.add(nameLbl, 0, 0);
            grid.add(nameVal, 1, 0);

            // Version
            var verLbl = new Label();
            verLbl.textProperty().bind(localization.text("dialog.properties.version"));
            verLbl.setStyle("-fx-font-weight: bold;");
            var verVal = new Label(project.info().version());
            grid.add(verLbl, 0, 1);
            grid.add(verVal, 1, 1);

            // Target Profile
            var targetLbl = new Label();
            targetLbl.textProperty().bind(localization.text("dialog.properties.target"));
            targetLbl.setStyle("-fx-font-weight: bold;");
            var targetVal = new Label(project.target().profile() + " (CPU: " + project.target().cpu() + ")");
            grid.add(targetLbl, 0, 2);
            grid.add(targetVal, 1, 2);

            // Toolchain
            var toolchainLbl = new Label();
            toolchainLbl.textProperty().bind(localization.text("dialog.properties.toolchain"));
            toolchainLbl.setStyle("-fx-font-weight: bold;");
            var toolchainVal = new Label(project.toolchain().id() + " " + project.toolchain().version());
            grid.add(toolchainLbl, 0, 3);
            grid.add(toolchainVal, 1, 3);

            // Run Environment: DOS targets can pick which DOSBox dialect to use; native targets just show "host".
            var runLbl = new Label();
            runLbl.textProperty().bind(localization.text("dialog.properties.runEnv"));
            runLbl.setStyle("-fx-font-weight: bold;");
            grid.add(runLbl, 0, 4);
            TargetProfile targetProfile = TargetProfileCatalog.require(project.target().profile());
            if (targetProfile.isDos()) {
                grid.add(buildRunEnvironmentCombo(project), 1, 4);
            } else {
                var runVal = new Label(project.run().environment() + " (cycles: " + project.run().cycles()
                        + ", memsize: " + project.run().memsize() + " MB)");
                grid.add(runVal, 1, 4);
            }

            content = new VBox(grid);
        }

        var closeButton = new Button();
        closeButton.textProperty().bind(localization.text("dialog.properties.close"));
        closeButton.setDefaultButton(true);
        closeButton.setOnAction(e -> close());

        var buttonBar = new HBox(closeButton);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.setPadding(new Insets(10, 16, 16, 16));

        var root = new VBox(content, buttonBar);
        setScene(new Scene(root, 480, 280));
        setResizable(false);
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
}
