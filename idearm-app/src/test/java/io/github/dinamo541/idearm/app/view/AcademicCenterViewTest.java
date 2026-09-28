package io.github.dinamo541.idearm.app.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dinamo541.idearm.app.i18n.Localization;
import io.github.dinamo541.idearm.language.knowledge.RegisterEntry;
import io.github.dinamo541.idearm.language.knowledge.SyntaxItem;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The Academic Center offers the corpus as three browsable layers, and a register card shows every width of the
 * same storage: a student who reads AX has to be able to reach EAX and RAX without knowing they exist.
 */
class AcademicCenterViewTest {

    @BeforeAll
    static void initJavaFx() throws InterruptedException {
        FxDialogs.startJavaFx();
    }

    private <T> T onCenter(Function<AcademicCenterView, T> action) throws InterruptedException {
        return FxDialogs.onFxThread(() -> {
            var localization = new Localization();
            localization.localeProperty().set(Locale.forLanguageTag("es"));
            var view = new AcademicCenterView(null, localization);
            try {
                view.show();
                settle(view);
                return action.apply(view);
            } finally {
                view.hide();
            }
        });
    }

    /** A TabPane only attaches the content of the tab in front, and a lookup only finds what has been laid out. */
    private static void settle(AcademicCenterView view) {
        view.getScene().getRoot().applyCss();
        view.getScene().getRoot().layout();
    }

    /** Brings one layer to the front and lets it lay out, so its list is reachable by id. */
    private static void openLayer(AcademicCenterView view, int index) {
        TabPane tabs = (TabPane) lookup(view, "#knowledgeLayers");
        tabs.getSelectionModel().select(index);
        settle(view);
    }

    private static Node lookup(AcademicCenterView view, String id) {
        Node node = view.getScene().lookup(id);
        assertTrue(node != null, "the view has no " + id);
        return node;
    }

    @SuppressWarnings("unchecked")
    private static ListView<RegisterEntry> registers(AcademicCenterView view) {
        openLayer(view, 1);
        return (ListView<RegisterEntry>) lookup(view, "#registersList");
    }

    private static RegisterEntry select(AcademicCenterView view, String name) {
        ListView<RegisterEntry> list = registers(view);
        RegisterEntry entry = list.getItems().stream()
                .filter(r -> r.name().equals(name)).findFirst().orElseThrow();
        list.getSelectionModel().select(entry);
        settle(view);
        return entry;
    }

    private static List<String> buttonTextsOfDetail(AcademicCenterView view) {
        Pane detail = (Pane) lookup(view, "#academicDetail");
        return detail.lookupAll(".button").stream()
                .filter(Button.class::isInstance)
                .map(node -> ((Button) node).getText())
                .filter(text -> text != null)
                .toList();
    }

    @Test
    void theCorpusIsOfferedAsThreeLayers() throws Exception {
        List<String> titles = onCenter(view -> {
            TabPane tabs = (TabPane) lookup(view, "#knowledgeLayers");
            return tabs.getTabs().stream().map(tab -> tab.getText()).toList();
        });

        assertEquals(List.of("Nemónicos", "Registros", "Operandos especiales"), titles,
                "the three layers the student browses");
    }

    @Test
    void eachLayerListsItsOwnEntitiesAndNoneIsEmpty() throws Exception {
        record Counts(int mnemonics, int registers, int operands) { }

        Counts counts = onCenter(view -> {
            openLayer(view, 0);
            int mnemonics = ((ListView<?>) lookup(view, "#mnemonicsList")).getItems().size();
            openLayer(view, 1);
            int registers = ((ListView<?>) lookup(view, "#registersList")).getItems().size();
            openLayer(view, 2);
            int operands = ((ListView<?>) lookup(view, "#operandsList")).getItems().size();
            return new Counts(mnemonics, registers, operands);
        });

        assertTrue(counts.mnemonics() > 200, "mnemonics layer: " + counts.mnemonics());
        assertTrue(counts.registers() > 40, "registers layer: " + counts.registers());
        assertTrue(counts.operands() > 10, "special operands layer: " + counts.operands());
    }

    @Test
    void theRegistersLayerCarriesTheThreeWidthsAsBrowsableEntries() throws Exception {
        List<String> names = onCenter(view ->
                registers(view).getItems().stream().map(RegisterEntry::name).toList());

        assertTrue(names.contains("AX"), "the 16-bit register is missing");
        assertTrue(names.contains("EAX"), "the 32-bit register is missing");
        assertTrue(names.contains("RAX"), "the 64-bit register is missing");
    }

    @Test
    void openingOneWidthShowsTheOtherWidthsOfTheSameRegister() throws Exception {
        List<String> family = onCenter(view -> {
            select(view, "AX");
            return buttonTextsOfDetail(view);
        });

        assertTrue(family.containsAll(List.of("RAX", "EAX", "AX", "AH", "AL")),
                "the width family of AX is incomplete: " + family);
    }

    @Test
    void the64BitRegisterAlsoShowsItsNarrowerWidths() throws Exception {
        List<String> family = onCenter(view -> {
            select(view, "RAX");
            return buttonTextsOfDetail(view);
        });

        assertTrue(family.containsAll(List.of("RAX", "EAX", "AX", "AH", "AL")),
                "the width family of RAX is incomplete: " + family);
    }

    @Test
    void theWidthAlreadyOpenIsNotClickable() throws Exception {
        boolean currentIsDisabled = onCenter(view -> {
            select(view, "AX");
            Pane detail = (Pane) lookup(view, "#academicDetail");
            return detail.lookupAll(".button").stream()
                    .filter(Button.class::isInstance)
                    .map(Button.class::cast)
                    .filter(button -> "AX".equals(button.getText()))
                    .allMatch(Button::isDisabled);
        });

        assertTrue(currentIsDisabled, "the width already open must not navigate to itself");
    }

    @Test
    void theRegisterCardExplainsWhatAPartialWriteDoes() throws Exception {
        boolean explains = onCenter(view -> {
            select(view, "RAX");
            Pane detail = (Pane) lookup(view, "#academicDetail");
            return detail.lookupAll(".label").stream()
                    .filter(Label.class::isInstance)
                    .map(node -> ((Label) node).getText())
                    .filter(text -> text != null)
                    .anyMatch(text -> text.toLowerCase(Locale.ROOT).contains("cero"));
        });

        assertTrue(explains, "writing EAX clears the upper half of RAX, and the card must say so");
    }

    @Test
    void theSpecialOperandsLayerCarriesTheSignsAStudentAsksAbout() throws Exception {
        List<String> tokens = onCenter(view -> {
            openLayer(view, 2);
            @SuppressWarnings("unchecked")
            ListView<SyntaxItem> list = (ListView<SyntaxItem>) lookup(view, "#operandsList");
            return list.getItems().stream().map(SyntaxItem::token).toList();
        });

        assertTrue(tokens.stream().anyMatch(token -> token.contains(":")), "the colon is missing: " + tokens);
        assertTrue(tokens.stream().anyMatch(token -> token.contains("[")), "the brackets are missing: " + tokens);
    }

    @Test
    void browsingRegistersDoesNotFilterTheMnemonicLayer() throws Exception {
        boolean intact = onCenter(view -> {
            openLayer(view, 0);
            ListView<?> mnemonics = (ListView<?>) lookup(view, "#mnemonicsList");
            int before = mnemonics.getItems().size();
            registers(view).getSelectionModel().selectFirst();
            settle(view);
            openLayer(view, 0);
            return ((ListView<?>) lookup(view, "#mnemonicsList")).getItems().size() == before;
        });

        assertTrue(intact, "browsing registers must not change the mnemonics layer");
    }
}
