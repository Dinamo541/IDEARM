package io.github.dinamo541.idearm.app.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dinamo541.idearm.app.i18n.Localization;
import io.github.dinamo541.idearm.domain.model.HostKind;
import io.github.dinamo541.idearm.domain.model.ToolInstallation;
import io.github.dinamo541.idearm.domain.port.ToolRegistry;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DoctorDialogTest {

    @TempDir
    Path tempDir;

    @BeforeAll
    static void initJavaFx() throws InterruptedException {
        FxDialogs.startJavaFx();
    }

    @Test
    void opensInstantlyAndContainsRescanButton() throws Exception {
        var refreshed = new AtomicBoolean(false);
        ToolRegistry registry = new ToolRegistry() {
            @Override public Optional<ToolInstallation> find(String toolId) { return Optional.empty(); }
            @Override public Map<String, ToolInstallation> all() { return Map.of(); }
            @Override public void refresh() { refreshed.set(true); }
        };

        Boolean ok = FxDialogs.onFxThread(() -> {
            var dialog = new DoctorDialog(new Stage(), new Localization(), registry);
            dialog.show();
            try {
                Button rescan = (Button) dialog.getScene().lookup("#rescanTools");
                Button addFolder = (Button) dialog.getScene().lookup("#addToolsFolder");
                assertNotNull(rescan, "Rescan button must be present");
                assertNotNull(addFolder, "Add Tools Folder button must be present");

                // Trigger rescan
                rescan.fire();
                return true;
            } finally {
                dialog.close();
            }
        });

        assertTrue(ok);
        // Rescan runs asynchronously, wait a moment for completion
        Thread.sleep(150);
        assertTrue(refreshed.get(), "refresh() must have been called on ToolRegistry");
    }

    @Test
    void populatesTableRowsAsynchronously() throws Exception {
        var tool = new ToolInstallation("tasm", "4.1", tempDir.resolve("tasm.exe"), HostKind.DOS_REAL, Map.of());
        ToolRegistry registry = new ToolRegistry() {
            @Override public Optional<ToolInstallation> find(String toolId) { return Optional.of(tool); }
            @Override public Map<String, ToolInstallation> all() { return Map.of("tasm", tool); }
        };

        var latch = new CountDownLatch(1);
        FxDialogs.onFxThread(() -> {
            var dialog = new DoctorDialog(new Stage(), new Localization(), registry);
            dialog.show();
            try {
                @SuppressWarnings("unchecked")
                TableView<DoctorDialog.ToolRow> table = (TableView<DoctorDialog.ToolRow>) dialog.getScene().lookup(".table-view");
                assertNotNull(table);

                // Wait for async row loading
                table.itemsProperty().addListener((obs, oldVal, newVal) -> {
                    if (newVal != null && !newVal.isEmpty()) {
                        latch.countDown();
                    }
                });
                if (table.getItems() != null && !table.getItems().isEmpty()) {
                    latch.countDown();
                }
                return true;
            } finally {
                Platform.runLater(dialog::close);
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Table rows should be populated asynchronously");
    }
}
