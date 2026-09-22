package io.github.dinamo541.idearm.app.view;

import io.github.dinamo541.idearm.app.i18n.Localization;
import io.github.dinamo541.idearm.app.i18n.Problem;
import io.github.dinamo541.idearm.domain.model.ToolInstallation;
import io.github.dinamo541.idearm.domain.port.ToolRegistry;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * The Tool Doctor: every tool the IDE will use (DOSBox, TASM, TLINK, MASM, LINK, NASM, ld, GDB), with its version,
 * path and whether its files are still there, plus a way to register a folder of the user's own tools.
 */
public final class DoctorDialog extends Stage {

    private final Localization localization;
    private final ToolRegistry toolRegistry;

    public record ToolRow(
            String id,
            String version,
            String hostKind,
            String path,
            String companions,
            boolean usable
    ) {}

    public DoctorDialog(Window owner, Localization localization, ToolRegistry toolRegistry) {
        this.localization = localization;
        this.toolRegistry = toolRegistry;

        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        titleProperty().bind(localization.text("dialog.doctor.title"));

        buildUi();
    }

    private void buildUi() {
        var header = new Label();
        header.textProperty().bind(localization.text("dialog.doctor.detected"));
        header.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        var table = new TableView<ToolRow>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        var idCol = new TableColumn<ToolRow, String>();
        idCol.textProperty().bind(localization.text("dialog.doctor.tool"));
        idCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().id()));
        idCol.setMaxWidth(140);

        var verCol = new TableColumn<ToolRow, String>();
        verCol.textProperty().bind(localization.text("dialog.doctor.version"));
        verCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().version()));
        verCol.setMaxWidth(100);

        var kindCol = new TableColumn<ToolRow, String>();
        kindCol.textProperty().bind(localization.text("dialog.doctor.hostKind"));
        kindCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().hostKind()));
        kindCol.setMinWidth(110);
        kindCol.setMaxWidth(130);

        var pathCol = new TableColumn<ToolRow, String>();
        pathCol.textProperty().bind(localization.text("dialog.doctor.path"));
        pathCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().path()));

        var statusCol = new TableColumn<ToolRow, Boolean>();
        statusCol.textProperty().bind(localization.text("dialog.doctor.status"));
        statusCol.setCellValueFactory(c -> new SimpleBooleanProperty(c.getValue().usable()));
        statusCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Boolean usable, boolean empty) {
                super.updateItem(usable, empty);
                if (empty || usable == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(localization.get(usable ? "dialog.doctor.ready" : "dialog.doctor.unusable"));
                setStyle(usable ? "-fx-text-fill: -color-success-fg;"
                        : "-fx-text-fill: -color-danger-fg; -fx-font-weight: bold;");
            }
        });
        statusCol.setMaxWidth(120);

        table.getColumns().add(idCol);
        table.getColumns().add(verCol);
        table.getColumns().add(kindCol);
        table.getColumns().add(statusCol);
        table.getColumns().add(pathCol);

        table.setItems(rows());
        var emptyLabel = new Label();
        emptyLabel.textProperty().bind(localization.text("dialog.doctor.empty"));
        emptyLabel.setWrapText(true);
        table.setPlaceholder(emptyLabel);

        var hint = new Label();
        hint.textProperty().bind(localization.text("dialog.doctor.hint"));
        hint.setWrapText(true);

        var feedback = new Label();
        feedback.setWrapText(true);
        HBox.setHgrow(feedback, Priority.ALWAYS);
        feedback.setMaxWidth(Double.MAX_VALUE);

        var addButton = new Button();
        addButton.textProperty().bind(localization.text("dialog.doctor.addFolder"));
        addButton.setOnAction(e -> addFolder(table, addButton, feedback));

        var closeButton = new Button();
        closeButton.textProperty().bind(localization.text("dialog.doctor.close"));
        closeButton.setDefaultButton(true);
        closeButton.setOnAction(e -> close());

        var buttonBar = new HBox(10, addButton, feedback, closeButton);
        buttonBar.setAlignment(Pos.CENTER_LEFT);
        buttonBar.setPadding(new Insets(10, 0, 0, 0));

        var root = new VBox(12, header, hint, table, buttonBar);
        VBox.setVgrow(table, Priority.ALWAYS);
        root.setPadding(new Insets(16));

        setScene(new Scene(root, 980, 460));
    }

    /** The same registry the build uses: detecting again here could report tools the build never picks. */
    private javafx.collections.ObservableList<ToolRow> rows() {
        Map<String, ToolInstallation> tools = new TreeMap<>(toolRegistry.all());
        var rows = FXCollections.<ToolRow>observableArrayList();
        for (var entry : tools.entrySet()) {
            ToolInstallation tool = entry.getValue();
            String companions = tool.companions().isEmpty() ? "" : tool.companions().keySet().toString();
            rows.add(new ToolRow(
                    entry.getKey(),
                    tool.version(),
                    tool.hostKind().name(),
                    tool.executable().toString(),
                    companions,
                    usable(tool)
            ));
        }
        return rows;
    }

    /**
     * Registers the tools in a folder the user picks, such as their own copy of TASM. Detection asks native tools
     * for their version, so it runs away from the UI thread.
     */
    private void addFolder(TableView<ToolRow> table, Button addButton, Label feedback) {
        var chooser = new DirectoryChooser();
        chooser.setTitle(localization.get("dialog.doctor.chooseFolder"));
        File chosen = chooser.showDialog(this);
        if (chosen == null) {
            return;
        }
        Path folder = chosen.toPath();
        addButton.setDisable(true);
        CompletableFuture.supplyAsync(() -> toolRegistry.registerFolder(folder))
                .whenComplete((added, failure) -> Platform.runLater(() -> {
                    addButton.setDisable(false);
                    if (failure != null) {
                        Throwable cause = failure instanceof CompletionException ? failure.getCause() : failure;
                        feedback.setText(localization.describe(Problem.of(cause)));
                    } else if (added.isEmpty()) {
                        feedback.setText(localization.get("dialog.doctor.nothingFound", folder.toString()));
                    } else {
                        feedback.setText(localization.get("dialog.doctor.added", added.size(), folder.toString()));
                        table.setItems(rows());
                    }
                }));
    }

    /**
     * A registered tool is only useful when its files are still there: a registry entry can outlive the
     * installation, and a companion such as RTM.EXE or DPMI16BI.OVL is what TLINK 7 needs to start at all.
     */
    private static boolean usable(ToolInstallation tool) {
        if (!Files.isRegularFile(tool.executable(), LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        return tool.companions().values().stream()
                .allMatch(companion -> Files.exists(companion, LinkOption.NOFOLLOW_LINKS));
    }
}
