package io.github.dinamo541.idearm.app.view;

import atlantafx.base.theme.Styles;
import io.github.dinamo541.idearm.app.i18n.Localization;
import io.github.dinamo541.idearm.app.ui.BrandLogo;
import io.github.dinamo541.idearm.app.ui.HoverHelp;
import io.github.dinamo541.idearm.app.ui.WorkbenchIcons;
import io.github.dinamo541.idearm.app.viewmodel.KnowledgeViewModel;
import io.github.dinamo541.idearm.app.viewmodel.MnemonicsDictionaryViewModel;
import io.github.dinamo541.idearm.application.knowledge.KnowledgeSearchResult;
import io.github.dinamo541.idearm.application.knowledge.SearchResultGroup;
import io.github.dinamo541.idearm.language.catalog.CpuLevel;
import io.github.dinamo541.idearm.language.catalog.FlagEffect;
import io.github.dinamo541.idearm.language.catalog.FlagSummary;
import io.github.dinamo541.idearm.language.catalog.InstructionCatalog;
import io.github.dinamo541.idearm.language.catalog.InstructionCategory;
import io.github.dinamo541.idearm.language.catalog.InstructionInfo;
import io.github.dinamo541.idearm.language.knowledge.*;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

import java.util.List;
import java.util.Locale;

/**
 * Academic Center (Centro Académico) View.
 * Provides a bilingual multi-entity knowledge browser, transversal search,
 * topic navigation, and deep linking across x86 instructions, registers, syntax,
 * interrupt services, and pedagogical concepts.
 */
public class AcademicCenterView extends Stage {

    protected final Localization localization;
    protected final KnowledgeViewModel viewModel;

    // --- Controls ---
    private final TextField searchField = new TextField();
    private final Button clearSearchButton = new Button();
    private final Button backButton = new Button(null, WorkbenchIcons.LEFT.create());
    private final Button forwardButton = new Button(null, WorkbenchIcons.RIGHT.create());
    private final Button homeButton = new Button(null, WorkbenchIcons.HOME.create());

    private final ComboBox<CategoryItem> categoryCombo = new ComboBox<>();
    private final ComboBox<CpuItem> cpuCombo = new ComboBox<>();
    private final ComboBox<DialectItem> dialectCombo = new ComboBox<>();
    private final Button resetFiltersButton = new Button();
    private final Label countLabel = new Label();

    private final ListView<InstructionInfo> instructionListView = new ListView<>();
    private final ListView<RegisterEntry> registerListView = new ListView<>();
    private final ListView<SyntaxItem> syntaxListView = new ListView<>();
    private final ListView<KnowledgeSearchResult> searchResultListView = new ListView<>();

    /** The three layers of the corpus a student browses: mnemonics, registers and special operands. */
    private final TabPane knowledgeTabs = new TabPane();
    private final Tab mnemonicsTab = new Tab();
    private final Tab registersTab = new Tab();
    private final Tab operandsTab = new Tab();

    private final StackPane leftPaneStack = new StackPane();

    private final VBox detailPane = new VBox(14);
    private final ScrollPane detailScrollPane = new ScrollPane(detailPane);

    private final Button prevButton = new Button();
    private final Button nextButton = new Button();
    private final Button backToEditorButton = new Button();
    private final Button openExampleButton = new Button();

    private boolean isUpdatingLanguage = false;
    private final ChangeListener<Locale> localeListener = (obs, oldLocale, newLocale) -> {
        Platform.runLater(this::updateLanguageDependentControls);
    };

    public record CategoryItem(InstructionCategory category) {
        public boolean isAll() {
            return category == null;
        }
    }

    public record CpuItem(CpuLevel cpu) {
        public boolean isAll() {
            return cpu == null;
        }
    }

    public record DialectItem(Dialect dialect) {
        public boolean isAll() {
            return dialect == null || dialect == Dialect.COMMON;
        }
    }

    public AcademicCenterView(Window owner, Localization localization) {
        this(owner, localization, new KnowledgeViewModel());
    }

    public AcademicCenterView(Window owner, Localization localization, KnowledgeViewModel viewModel) {
        this.localization = localization;
        this.viewModel = viewModel;

        if (owner != null) {
            initOwner(owner);
        }
        initModality(Modality.NONE);
        titleProperty().bind(localization.text("dialog.academic.title"));
        setUserData(localization.get("dialog.dictionary.title"));

        BrandLogo.apply(this);
        buildUi();
        bindViewModel();

        localization.localeProperty().addListener(localeListener);
        addEventHandler(WindowEvent.WINDOW_HIDDEN, e -> localization.localeProperty().removeListener(localeListener));
    }

    public AcademicCenterView(Window owner, Localization localization, MnemonicsDictionaryViewModel legacyVm) {
        this(owner, localization, new KnowledgeViewModel());
    }

    private void buildUi() {
        var root = new BorderPane();
        io.github.dinamo541.idearm.app.ui.DialogWindow.theme(root, getOwner());
        root.getStyleClass().add("academic-center");
        root.getStyleClass().add("dictionary-dialog");
        for (var button : List.of(backButton, forwardButton, homeButton, clearSearchButton, prevButton, nextButton)) {
            button.getStyleClass().add("icon-button");
        }

        // --- TOP TOOLBAR ---
        var topBar = new VBox(8);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("dictionary-toolbar");

        // Navigation row
        backButton.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.ROUNDED, Styles.FLAT);
        backButton.disableProperty().bind(viewModel.canNavigateBackProperty().not());
        backButton.setOnAction(e -> viewModel.navigateBack());
        HoverHelp.install(backButton, localization, "dialog.academic.back", "");

        forwardButton.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.ROUNDED, Styles.FLAT);
        forwardButton.disableProperty().bind(viewModel.canNavigateForwardProperty().not());
        forwardButton.setOnAction(e -> viewModel.navigateForward());
        HoverHelp.install(forwardButton, localization, "dialog.academic.forward", "");

        homeButton.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.ROUNDED, Styles.FLAT);
        homeButton.setOnAction(e -> viewModel.navigateHome());
        HoverHelp.install(homeButton, localization, "dialog.academic.home", "");

        searchField.promptTextProperty().bind(localization.text("dialog.academic.search"));
        searchField.setPrefWidth(300);
        searchField.setTooltip(new Tooltip(localization.get("dialog.dictionary.search")));
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchField.getStyleClass().add(Styles.ROUNDED);

        clearSearchButton.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.ROUNDED, Styles.FLAT);
        clearSearchButton.setText(null);
        clearSearchButton.setGraphic(WorkbenchIcons.CLOSE.create());
        clearSearchButton.setOnAction(e -> viewModel.clearSearch());
        clearSearchButton.visibleProperty().bind(searchField.textProperty().isNotEmpty());
        clearSearchButton.managedProperty().bind(clearSearchButton.visibleProperty());
        HoverHelp.install(clearSearchButton, localization, "dialog.dictionary.clearSearch", "");

        var searchBox = new HBox(4, searchField, clearSearchButton);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchBox, Priority.ALWAYS);

        countLabel.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);

        var navRow = new HBox(8, backButton, forwardButton, homeButton, searchBox, countLabel);
        navRow.setAlignment(Pos.CENTER_LEFT);

        // Facets row
        var catLabel = new Label();
        catLabel.textProperty().bind(localization.text("dialog.dictionary.category"));
        catLabel.getStyleClass().addAll(Styles.TEXT_BOLD, Styles.TEXT_MUTED, Styles.TEXT_SMALL);

        populateCategoryCombo();
        categoryCombo.setPrefWidth(240);
        categoryCombo.getStyleClass().add(Styles.ROUNDED);

        var cpuLabel = new Label();
        cpuLabel.textProperty().bind(localization.text("dialog.dictionary.cpu"));
        cpuLabel.getStyleClass().addAll(Styles.TEXT_BOLD, Styles.TEXT_MUTED, Styles.TEXT_SMALL);

        populateCpuCombo();
        cpuCombo.setPrefWidth(180);
        cpuCombo.getStyleClass().add(Styles.ROUNDED);

        var dialectLabel = new Label();
        dialectLabel.textProperty().bind(localization.text("dialog.academic.dialect"));
        dialectLabel.getStyleClass().addAll(Styles.TEXT_BOLD, Styles.TEXT_MUTED, Styles.TEXT_SMALL);

        populateDialectCombo();
        dialectCombo.setPrefWidth(180);
        dialectCombo.getStyleClass().add(Styles.ROUNDED);

        resetFiltersButton.textProperty().bind(localization.text("dialog.dictionary.resetFilters"));
        resetFiltersButton.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SMALL, Styles.ROUNDED);
        resetFiltersButton.setOnAction(e -> viewModel.resetFilters());

        var filters = new FlowPane(12, 8, filterField(catLabel, categoryCombo),
                filterField(cpuLabel, cpuCombo), filterField(dialectLabel, dialectCombo), resetFiltersButton);
        filters.setAlignment(Pos.CENTER_LEFT);

        topBar.getChildren().addAll(navRow, filters);
        root.setTop(new VBox(io.github.dinamo541.idearm.app.ui.DialogWindow.heading(localization,
                "dialog.academic.title", "dialog.dictionary.subtitle", WorkbenchIcons.BOOK), topBar));

        // --- LEFT PANE (Master List / Search Results) ---
        instructionListView.setItems(viewModel.getFilteredInstructions());
        instructionListView.getStyleClass().add("dictionary-list");
        instructionListView.setCellFactory(lv -> new InstructionListCell());

        registerListView.setItems(viewModel.getFilteredRegisters());
        registerListView.getStyleClass().add("dictionary-list");
        registerListView.setCellFactory(lv -> new RegisterListCell());

        syntaxListView.setItems(viewModel.getFilteredSyntaxItems());
        syntaxListView.getStyleClass().add("dictionary-list");
        syntaxListView.setCellFactory(lv -> new SyntaxListCell());

        instructionListView.setId("mnemonicsList");
        registerListView.setId("registersList");
        syntaxListView.setId("operandsList");
        knowledgeTabs.setId("knowledgeLayers");
        detailPane.setId("academicDetail");

        mnemonicsTab.setContent(instructionListView);
        mnemonicsTab.setClosable(false);
        registersTab.setContent(registerListView);
        registersTab.setClosable(false);
        operandsTab.setContent(syntaxListView);
        operandsTab.setClosable(false);

        knowledgeTabs.getTabs().addAll(mnemonicsTab, registersTab, operandsTab);
        knowledgeTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        knowledgeTabs.getStyleClass().add("academic-layers");
        knowledgeTabs.setPrefWidth(320);
        knowledgeTabs.setMinWidth(260);

        searchResultListView.setPrefWidth(280);
        searchResultListView.getStyleClass().add("dictionary-list");
        searchResultListView.setMinWidth(220);
        searchResultListView.setCellFactory(lv -> new SearchResultListCell());

        leftPaneStack.getChildren().addAll(knowledgeTabs, searchResultListView);
        searchResultListView.visibleProperty().bind(viewModel.isSearchingProperty());
        knowledgeTabs.visibleProperty().bind(viewModel.isSearchingProperty().not());

        // --- RIGHT PANE (Detail View) ---
        detailScrollPane.setFitToWidth(true);
        detailScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        detailPane.getStyleClass().add("dictionary-detail");
        detailPane.setFillWidth(true);

        var splitPane = new SplitPane(leftPaneStack, detailScrollPane);
        splitPane.setOrientation(Orientation.HORIZONTAL);
        splitPane.setDividerPositions(0.33);
        SplitPane.setResizableWithParent(leftPaneStack, false);

        root.setCenter(splitPane);

        // --- BOTTOM BAR ---
        var bottomBar = new HBox(12);
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        bottomBar.setPadding(new Insets(10, 16, 10, 16));
        bottomBar.getStyleClass().add("dictionary-toolbar");

        backToEditorButton.textProperty().bind(localization.text("dialog.academic.backToEditor"));
        backToEditorButton.setGraphic(WorkbenchIcons.LEFT.create());
        backToEditorButton.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.ROUNDED);
        backToEditorButton.setOnAction(e -> {
            close();
            if (getOwner() != null) getOwner().requestFocus();
        });

        openExampleButton.textProperty().bind(localization.text("dialog.academic.openExample"));
        openExampleButton.setGraphic(WorkbenchIcons.NEW_FILE.create());
        openExampleButton.getStyleClass().addAll(Styles.ACCENT, Styles.ROUNDED);
        openExampleButton.setOnAction(e -> handleOpenExample());

        var bottomSpacer = new Region();
        HBox.setHgrow(bottomSpacer, Priority.ALWAYS);

        prevButton.setOnAction(e -> viewModel.selectPrevious());
        prevButton.disableProperty().bind(viewModel.canSelectPreviousProperty().not());
        prevButton.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.ROUNDED, Styles.FLAT);
        prevButton.setGraphic(WorkbenchIcons.UP.create());
        HoverHelp.install(prevButton, localization, "dialog.dictionary.previous", "Alt+Left");

        nextButton.setOnAction(e -> viewModel.selectNext());
        nextButton.disableProperty().bind(viewModel.canSelectNextProperty().not());
        nextButton.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.ROUNDED, Styles.FLAT);
        nextButton.setGraphic(WorkbenchIcons.DOWN.create());
        HoverHelp.install(nextButton, localization, "dialog.dictionary.next", "Alt+Right");

        bottomBar.getChildren().addAll(backToEditorButton, openExampleButton, bottomSpacer, prevButton, nextButton);
        root.setBottom(bottomBar);

        Scene scene = new Scene(root, 1060, 720);
        setScene(scene);
        setMinWidth(840);
        setMinHeight(560);

        setupKeyboardShortcuts(scene);
    }

    private void setupKeyboardShortcuts(Scene scene) {
        var keyCtrlK = new KeyCodeCombination(KeyCode.K, KeyCombination.CONTROL_DOWN);
        var keyCtrlF = new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN);
        var keyAltLeft = new KeyCodeCombination(KeyCode.LEFT, KeyCombination.ALT_DOWN);
        var keyAltRight = new KeyCodeCombination(KeyCode.RIGHT, KeyCombination.ALT_DOWN);

        scene.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                close();
                event.consume();
            } else if (keyCtrlK.match(event) || keyCtrlF.match(event)) {
                searchField.requestFocus();
                searchField.selectAll();
                event.consume();
            } else if (keyAltLeft.match(event)) {
                if (viewModel.canNavigateBackProperty().get()) {
                    viewModel.navigateBack();
                } else if (viewModel.canSelectPreviousProperty().get()) {
                    viewModel.selectPrevious();
                }
                event.consume();
            } else if (keyAltRight.match(event)) {
                if (viewModel.canNavigateForwardProperty().get()) {
                    viewModel.navigateForward();
                } else if (viewModel.canSelectNextProperty().get()) {
                    viewModel.selectNext();
                }
                event.consume();
            }
        });
    }

    /** A wrapped filter row must keep its label next to the control it describes. */
    private HBox filterField(Label label, ComboBox<?> control) {
        label.setLabelFor(control);
        var field = new HBox(8, label, control);
        field.setAlignment(Pos.CENTER_LEFT);
        return field;
    }

    private void bindViewModel() {
        searchField.textProperty().bindBidirectional(viewModel.searchQueryProperty());

        categoryCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (!isUpdatingLanguage) {
                viewModel.selectedCategoryProperty().set(newVal != null ? newVal.category() : null);
            }
        });

        cpuCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (!isUpdatingLanguage) {
                viewModel.selectedCpuProperty().set(newVal != null ? newVal.cpu() : null);
            }
        });

        dialectCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (!isUpdatingLanguage) {
                viewModel.selectedDialectProperty().set(newVal != null ? newVal.dialect() : Dialect.COMMON);
            }
        });

        mnemonicsTab.textProperty().bind(localization.text("dialog.academic.tab.mnemonics"));
        registersTab.textProperty().bind(localization.text("dialog.academic.tab.registers"));
        operandsTab.textProperty().bind(localization.text("dialog.academic.tab.operands"));
        // The full name stays reachable even if a narrow window shortens the tab.
        mnemonicsTab.setTooltip(new Tooltip());
        mnemonicsTab.getTooltip().textProperty().bind(localization.text("dialog.academic.tab.mnemonics"));
        registersTab.setTooltip(new Tooltip());
        registersTab.getTooltip().textProperty().bind(localization.text("dialog.academic.tab.registers"));
        operandsTab.setTooltip(new Tooltip());
        operandsTab.getTooltip().textProperty().bind(localization.text("dialog.academic.tab.operands"));

        instructionListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !viewModel.isSearchingProperty().get()) {
                viewModel.selectInstruction(newVal);
            }
        });

        registerListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !viewModel.isSearchingProperty().get()) {
                viewModel.selectEntity(newVal);
            }
        });

        syntaxListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !viewModel.isSearchingProperty().get()) {
                viewModel.selectEntity(newVal);
            }
        });

        // Selecting a layer shows its first entry, so a tab is never opened empty.
        knowledgeTabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (viewModel.isSearchingProperty().get()) {
                return;
            }
            if (newTab == registersTab && registerListView.getSelectionModel().getSelectedItem() == null
                    && !registerListView.getItems().isEmpty()) {
                registerListView.getSelectionModel().selectFirst();
            } else if (newTab == operandsTab && syntaxListView.getSelectionModel().getSelectedItem() == null
                    && !syntaxListView.getItems().isEmpty()) {
                syntaxListView.getSelectionModel().selectFirst();
            } else if (newTab == mnemonicsTab && instructionListView.getSelectionModel().getSelectedItem() == null
                    && !instructionListView.getItems().isEmpty()) {
                instructionListView.getSelectionModel().selectFirst();
            }
        });

        searchResultListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && viewModel.isSearchingProperty().get()) {
                viewModel.selectedSearchResultProperty().set(newVal);
                viewModel.selectEntity(newVal.rawEntity());
            }
        });

        viewModel.selectedInstructionProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !viewModel.isSearchingProperty().get()) {
                instructionListView.getSelectionModel().select(newVal);
            }
        });

        viewModel.selectedEntityProperty().addListener((obs, oldVal, newVal) -> {
            renderEntityDetail(newVal);
            revealLayerOf(newVal);
        });

        viewModel.matchCountProperty().addListener((obs, oldVal, newVal) -> {
            countLabel.setText(localization.get("dialog.dictionary.instructionsFound", newVal));
        });

        viewModel.getSearchResultGroups().addListener((javafx.collections.ListChangeListener<SearchResultGroup>) c -> {
            searchResultListView.getItems().clear();
            for (SearchResultGroup group : viewModel.getSearchResultGroups()) {
                searchResultListView.getItems().addAll(group.items());
            }
        });

        countLabel.setText(localization.get("dialog.dictionary.instructionsFound", viewModel.matchCountProperty().get()));
        viewModel.languageProperty().set(localization.localeProperty().get().getLanguage());

        Object initialEntity = viewModel.selectedEntityProperty().get();
        if (initialEntity != null) {
            renderEntityDetail(initialEntity);
        }
    }

    /**
     * Brings the layer that owns this entity to the front and selects it there, so that reaching a register from a
     * search, a deep link or a related link also moves the list on the left. Does nothing while searching, because
     * the results list is what the student is looking at then.
     */
    private void revealLayerOf(Object entity) {
        if (entity == null || viewModel.isSearchingProperty().get()) {
            return;
        }
        if (entity instanceof RegisterEntry register) {
            knowledgeTabs.getSelectionModel().select(registersTab);
            if (registerListView.getItems().contains(register)) {
                registerListView.getSelectionModel().select(register);
                registerListView.scrollTo(register);
            }
        } else if (entity instanceof SyntaxItem syntax) {
            knowledgeTabs.getSelectionModel().select(operandsTab);
            if (syntaxListView.getItems().contains(syntax)) {
                syntaxListView.getSelectionModel().select(syntax);
                syntaxListView.scrollTo(syntax);
            }
        } else if (entity instanceof InstructionInfo || entity instanceof InstructionEntry) {
            knowledgeTabs.getSelectionModel().select(mnemonicsTab);
        }
    }

    private void renderEntityDetail(Object entity) {
        detailPane.getChildren().clear();
        if (entity == null) {
            openExampleButton.setDisable(true);
            renderEmptyState(viewModel.searchQueryProperty().get());
            return;
        }

        if (entity instanceof InstructionInfo info) {
            renderInstructionInfoDetail(info);
        } else if (entity instanceof InstructionEntry entry) {
            renderInstructionEntryDetail(entry);
        } else if (entity instanceof RegisterEntry reg) {
            renderRegisterDetail(reg);
        } else if (entity instanceof SyntaxItem syntax) {
            renderSyntaxDetail(syntax);
        } else if (entity instanceof ServiceEntry service) {
            renderServiceDetail(service);
        } else if (entity instanceof ConceptEntry concept) {
            renderConceptDetail(concept);
        }
    }

    private void renderInstructionInfoDetail(InstructionInfo info) {
        String lang = localization.localeProperty().get().getLanguage();

        var mnemonicLabel = new Label(info.mnemonic());
        mnemonicLabel.getStyleClass().addAll(Styles.TITLE_1, Styles.TEXT_BOLD);
        mnemonicLabel.getStyleClass().add("dictionary-mnemonic");

        var categoryChip = new Button(localization.get("category." + info.category().name()));
        categoryChip.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.ROUNDED, Styles.BUTTON_OUTLINED, Styles.SMALL);
        categoryChip.setGraphic(categoryIcon(info.category()).create());
        categoryChip.setOnAction(e -> viewModel.selectedCategoryProperty().set(info.category()));

        var cpuChip = new Button(localization.get("dialog.dictionary.cpuBaseline") + " " + info.minCpu().displayName());
        cpuChip.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.ROUNDED, Styles.BG_SUBTLE, Styles.SMALL);
        cpuChip.setOnAction(e -> viewModel.selectedCpuProperty().set(info.minCpu()));

        cpuChip.setGraphic(WorkbenchIcons.CHIP.create());

        var summaryLabel = new Label("es".equalsIgnoreCase(lang) ? info.summaryEs() : info.summaryEn());
        summaryLabel.getStyleClass().addAll(Styles.TITLE_4, Styles.TEXT_MUTED);
        summaryLabel.setWrapText(true);

        detailPane.getChildren().add(detailHeader(mnemonicLabel, summaryLabel, categoryChip, cpuChip));

        // Syntax Variants
        if (info.syntaxVariants() != null && !info.syntaxVariants().isEmpty()) {
            var syntaxSection = createSectionTitle(localization.get("dialog.dictionary.syntax"));
            var syntaxBox = new VBox(6);
            for (String syntax : info.syntaxVariants()) {
                var syntaxLabel = new Label(syntax);
                syntaxLabel.getStyleClass().addAll("code-font", Styles.TEXT_BOLD);
                syntaxLabel.setWrapText(true);
                syntaxBox.getChildren().add(syntaxLabel);
            }
            var operandLegendTitle = new Label(localization.get("dialog.dictionary.operandLegend"));
            operandLegendTitle.getStyleClass().addAll(Styles.TEXT_BOLD, Styles.TEXT_MUTED, Styles.TEXT_SMALL);
            var operandLegend = new Label(localization.get("dialog.dictionary.operandLegendDetail"));
            operandLegend.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);
            operandLegend.setWrapText(true);
            syntaxBox.getChildren().addAll(new Separator(), operandLegendTitle, operandLegend);
            detailPane.getChildren().addAll(syntaxSection, syntaxBox);
        }

        // Description
        var descSection = createSectionTitle(localization.get("dialog.dictionary.description"));
        var descLabel = new Label("es".equalsIgnoreCase(lang) ? info.descriptionEs() : info.descriptionEn());
        descLabel.setWrapText(true);
        detailPane.getChildren().addAll(descSection, descLabel);

        // Flags
        renderFlagsSection(info.flags());

        // Example
        if (info.example() != null && !info.example().isBlank()) {
            renderExampleSection(info.example());
            openExampleButton.setDisable(false);
        } else {
            openExampleButton.setDisable(true);
        }
    }

    private void renderInstructionEntryDetail(InstructionEntry entry) {
        String lang = localization.localeProperty().get().getLanguage();
        boolean isEs = "es".equalsIgnoreCase(lang);

        var mnemonicLabel = new Label(entry.mnemonic());
        mnemonicLabel.getStyleClass().addAll(Styles.TITLE_1, Styles.TEXT_BOLD);
        mnemonicLabel.getStyleClass().add("dictionary-mnemonic");

        var categoryChip = new Button(localization.get("category." + entry.category().name()));
        categoryChip.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.ROUNDED, Styles.BUTTON_OUTLINED, Styles.SMALL);
        categoryChip.setGraphic(categoryIcon(entry.category()).create());
        categoryChip.setOnAction(e -> viewModel.selectedCategoryProperty().set(entry.category()));

        var cpuChip = new Label(entry.minCpuGen().displayName());
        cpuChip.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.ROUNDED, Styles.BG_SUBTLE);

        cpuChip.setGraphic(WorkbenchIcons.CHIP.create());

        var summaryLabel = new Label(isEs ? entry.summaryEs() : entry.summaryEn());
        summaryLabel.getStyleClass().addAll(Styles.TITLE_4, Styles.TEXT_MUTED);
        summaryLabel.setWrapText(true);

        detailPane.getChildren().add(detailHeader(mnemonicLabel, summaryLabel, categoryChip, cpuChip));

        // Forms and Operands
        if (!entry.forms().isEmpty()) {
            var formsSection = createSectionTitle(localization.get("dialog.academic.forms"));
            var formsBox = new VBox(10);
            for (InstructionForm form : entry.forms()) {
                var formCard = new VBox(4);
                formCard.getStyleClass().addAll(Styles.BG_SUBTLE, Styles.ROUNDED);
                formCard.setPadding(new Insets(8, 12, 8, 12));

                var formTitle = new Label(form.operationPlain());
                formTitle.getStyleClass().addAll(Styles.TEXT_BOLD, "code-font");

                var formReq = new Label(localization.get("dialog.dictionary.cpuBaseline") + " " + form.requirement().minGeneration().displayName());
                formReq.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);

                formCard.getChildren().addAll(formTitle, formReq);

                if (!form.operands().isEmpty()) {
                    var opsTitle = new Label(localization.get("dialog.academic.operands") + ":");
                    opsTitle.getStyleClass().addAll(Styles.TEXT_BOLD, Styles.TEXT_SMALL);
                    formCard.getChildren().add(opsTitle);
                    for (Operand op : form.operands()) {
                        String opDesc = op.register() != null ? op.register() : op.kind().name();
                        var opLabel = new Label("• #" + op.position() + " " + opDesc + " (" + op.role() + ", " + op.access() + ")");
                        opLabel.getStyleClass().add(Styles.TEXT_SMALL);
                        formCard.getChildren().add(opLabel);
                    }
                }

                if (!form.implicitOperands().isEmpty()) {
                    var implTitle = new Label(localization.get("dialog.academic.implicitOperands") + ":");
                    implTitle.getStyleClass().addAll(Styles.TEXT_BOLD, Styles.TEXT_SMALL);
                    formCard.getChildren().add(implTitle);
                    for (Operand op : form.implicitOperands()) {
                        String implDesc = op.register() != null ? op.register() : op.kind().name();
                        var opLabel = new Label("• " + implDesc + " (" + op.access() + (op.note() != null ? ": " + text(op.note()) : "") + ")");
                        opLabel.getStyleClass().add(Styles.TEXT_SMALL);
                        formCard.getChildren().add(opLabel);
                    }
                }

                formsBox.getChildren().add(formCard);
            }
            detailPane.getChildren().addAll(formsSection, formsBox);
        }

        // Flags
        renderFlagsSection(entry.flagSummary());

        // Pitfalls
        if (!entry.pitfalls().isEmpty()) {
            var pitfallsSection = createSectionTitle(localization.get("dialog.academic.pitfalls"));
            var pitfallBox = new VBox(4);
            for (String pitfall : entry.pitfalls()) {
                var pLabel = new Label("• " + text(pitfall));
                pLabel.setWrapText(true);
                pitfallBox.getChildren().add(pLabel);
            }
            detailPane.getChildren().addAll(pitfallsSection, pitfallBox);
        }

        // Sources
        if (!entry.sources().isEmpty()) {
            var sourcesSection = createSectionTitle(localization.get("dialog.academic.sources"));
            var sourcesBox = new VBox(2);
            for (String src : entry.sources()) {
                var sLabel = new Label("• " + src);
                sLabel.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);
                sourcesBox.getChildren().add(sLabel);
            }
            detailPane.getChildren().addAll(sourcesSection, sourcesBox);
        }

        // Pedagogical Level
        if (entry.pedagogicalLevel() != null) {
            var pedNotes = new Label(localization.get("dialog.academic.pedagogicalNotes") + ": " + entry.pedagogicalLevel().name());
            pedNotes.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);
            detailPane.getChildren().add(pedNotes);
        }

        // Example
        if (entry.example() != null && !entry.example().isBlank()) {
            renderExampleSection(entry.example());
            openExampleButton.setDisable(false);
        } else {
            openExampleButton.setDisable(true);
        }
    }

    private void renderRegisterDetail(RegisterEntry reg) {
        var nameLabel = new Label(reg.name());
        nameLabel.getStyleClass().addAll(Styles.TITLE_1, Styles.TEXT_BOLD);
        nameLabel.getStyleClass().add("dictionary-mnemonic");

        var groupBadge = new Label(localization.get("register.group." + reg.group().name()));
        groupBadge.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.ROUNDED, Styles.BUTTON_OUTLINED);

        var sizeBadge = new Label(reg.sizeBits() + " bits");
        sizeBadge.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.ROUNDED, Styles.BG_SUBTLE);

        groupBadge.setGraphic(registerIcon(reg.group()).create());

        var useLabel = new Label(text(reg.conventionalUse()));
        useLabel.getStyleClass().addAll(Styles.TITLE_4, Styles.TEXT_MUTED);
        useLabel.setWrapText(true);

        detailPane.getChildren().add(detailHeader(nameLabel, useLabel, groupBadge, sizeBadge));

        // The width family: the same storage seen at 64, 32, 16 and 8 bits.
        List<RegisterView> family = viewModel.registerFamily(reg);
        if (!family.isEmpty()) {
            var familySection = createSectionTitle(localization.get("dialog.academic.registerFamily"));
            var familyBox = new VBox(6);

            for (RegisterView member : family) {
                boolean isCurrent = member.name().equalsIgnoreCase(reg.name());

                var row = new FlowPane(12, 6);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("register-family-row");
                row.setPadding(new Insets(6, 10, 6, 10));

                var widthLabel = new Label(member.sizeBits() + " bits");
                widthLabel.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);
                widthLabel.setMinWidth(64);

                // The name navigates to that width's own card, unless it is the one already open.
                var nameButton = new Button(member.name());
                nameButton.getStyleClass().addAll(Styles.SMALL, Styles.ROUNDED, "code-font", "register-family-button",
                        isCurrent ? Styles.ACCENT : Styles.BUTTON_OUTLINED);
                nameButton.setDisable(isCurrent);
                nameButton.setOnAction(e -> viewModel.navigateTo(member.id()));

                var bitsLabel = new Label(localization.get("dialog.academic.registerBits",
                        member.offsetBits(), member.offsetBits() + member.sizeBits() - 1));
                bitsLabel.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);

                row.getChildren().addAll(widthLabel, nameButton, bitsLabel);

                if (isCurrent) {
                    var here = new Label(localization.get("dialog.academic.registerCurrent"));
                    here.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_BOLD, Styles.ACCENT);
                    row.getChildren().add(here);
                }

                familyBox.getChildren().add(row);
            }

            var hint = new Label(localization.get("dialog.academic.registerFamily.hint"));
            hint.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);
            hint.setWrapText(true);
            familyBox.getChildren().add(hint);

            detailPane.getChildren().addAll(familySection, familyBox);
        }

        // What happens to the rest of the register when a narrower view is written.
        if (reg.writeSemantics() != null && !reg.writeSemantics().isBlank()) {
            var writeSection = createSectionTitle(localization.get("dialog.academic.writeSemantics"));
            var writeLabel = new Label(text(reg.writeSemantics()));
            writeLabel.setWrapText(true);
            detailPane.getChildren().addAll(writeSection, writeLabel);
        }

        // Flag Fields
        if (!reg.fields().isEmpty()) {
            var fieldsSection = createSectionTitle(localization.get("dialog.academic.flagFields"));
            var fieldsBox = new VBox(6);
            for (FlagField field : reg.fields()) {
                var fRow = new HBox(12);
                fRow.setAlignment(Pos.CENTER_LEFT);
                fRow.getStyleClass().addAll(Styles.BG_SUBTLE, Styles.ROUNDED);
                fRow.setPadding(new Insets(6, 10, 6, 10));

                var fName = new Label("Bit " + field.bitPosition() + " [" + field.name() + "]");
                fName.getStyleClass().addAll(Styles.TEXT_BOLD, "code-font");
                var fMeaning = new Label(text(field.meaning()));
                fMeaning.setWrapText(true);

                fRow.getChildren().addAll(fName, fMeaning);
                fieldsBox.getChildren().add(fRow);
            }
            detailPane.getChildren().addAll(fieldsSection, fieldsBox);
        }

        openExampleButton.setDisable(true);
    }

    private void renderSyntaxDetail(SyntaxItem syntax) {
        String lang = localization.localeProperty().get().getLanguage();
        boolean isEs = "es".equalsIgnoreCase(lang);

        var tokenLabel = new Label(syntax.token());
        tokenLabel.getStyleClass().addAll(Styles.TITLE_1, Styles.TEXT_BOLD, "code-font");

        var classBadge = new Label(localization.get("syntax.class." + syntax.syntaxClass().name()));
        classBadge.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.ROUNDED, Styles.BUTTON_OUTLINED);

        var statusBadge = new Label(localization.get(syntax.assemblable()
                ? "dialog.academic.assemblable" : "dialog.academic.notationOnly"));
        statusBadge.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.ROUNDED, syntax.assemblable() ? Styles.SUCCESS : Styles.WARNING);

        classBadge.setGraphic(syntaxIcon(syntax).create());

        var summaryLabel = new Label(isEs ? syntax.summaryEs() : syntax.summaryEn());
        summaryLabel.getStyleClass().addAll(Styles.TITLE_4, Styles.TEXT_MUTED);
        summaryLabel.setWrapText(true);

        detailPane.getChildren().add(detailHeader(tokenLabel, summaryLabel, classBadge, statusBadge));

        // Description
        var descSection = createSectionTitle(localization.get("dialog.dictionary.description"));
        var descLabel = new Label(isEs ? syntax.descriptionEs() : syntax.descriptionEn());
        descLabel.setWrapText(true);
        detailPane.getChildren().addAll(descSection, descLabel);

        // Example
        if (syntax.example() != null && !syntax.example().isBlank()) {
            renderExampleSection(syntax.example());
            openExampleButton.setDisable(false);
        } else {
            openExampleButton.setDisable(true);
        }
    }

    private void renderServiceDetail(ServiceEntry srv) {
        String lang = localization.localeProperty().get().getLanguage();
        boolean isEs = "es".equalsIgnoreCase(lang);

        String selVal = srv.selector() != null ? " AH=" + srv.selector().value() : "";
        var titleLabel = new Label("INT " + srv.vector() + selVal);
        titleLabel.getStyleClass().addAll(Styles.TITLE_1, Styles.TEXT_BOLD, "code-font");

        var envBadge = new Label(srv.environment() + " (" + srv.vectorStatus().description() + ")");
        envBadge.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.ROUNDED, Styles.BUTTON_OUTLINED);

        envBadge.setGraphic(WorkbenchIcons.SYSTEM_INTERRUPTS.create());

        var summaryLabel = new Label(isEs ? srv.summaryEs() : srv.summaryEn());
        summaryLabel.getStyleClass().addAll(Styles.TITLE_4, Styles.TEXT_MUTED);
        summaryLabel.setWrapText(true);

        detailPane.getChildren().add(detailHeader(titleLabel, summaryLabel, envBadge));

        // Inputs
        if (!srv.inputs().isEmpty()) {
            var inputsSection = createSectionTitle(localization.get("dialog.academic.inputs"));
            var inputsBox = new VBox(4);
            for (ServiceInput in : srv.inputs()) {
                var row = new Label("• " + in.register() + (in.value().isBlank() ? "" : " = " + text(in.value())) + ": " + text(in.meaning()));
                row.setWrapText(true);
                inputsBox.getChildren().add(row);
            }
            detailPane.getChildren().addAll(inputsSection, inputsBox);
        }

        // Buffer format
        if (!srv.bufferFormat().isEmpty()) {
            var bufSection = createSectionTitle(localization.get("dialog.academic.bufferFormat"));
            var bufBox = new VBox(4);
            for (BufferField bf : srv.bufferFormat()) {
                var row = new Label("• Offset +" + bf.offset() + " [" + bf.size() + "] (" + bf.direction() + "): " + text(bf.meaning()));
                row.setWrapText(true);
                bufBox.getChildren().add(row);
            }
            detailPane.getChildren().addAll(bufSection, bufBox);
        }

        // Outputs
        if (!srv.outputs().isEmpty()) {
            var outputsSection = createSectionTitle(localization.get("dialog.academic.outputs"));
            var outputsBox = new VBox(4);
            for (ServiceOutput out : srv.outputs()) {
                var row = new Label("• " + out.target() + ": " + text(out.meaning()));
                row.setWrapText(true);
                outputsBox.getChildren().add(row);
            }
            detailPane.getChildren().addAll(outputsSection, outputsBox);
        }

        // Availability
        if (!srv.availability().isEmpty()) {
            var availSection = createSectionTitle(localization.get("dialog.academic.availability"));
            var availBox = new VBox(4);
            for (BackendAvailability ba : srv.availability()) {
                String statusStr = ba.status() == BackendAvailability.AvailabilityStatus.AVAILABLE
                        ? localization.get("dialog.academic.compatible") : ba.status().name();
                var aLabel = new Label("• " + ba.backend() + ": " + statusStr + (ba.caveat() != null ? " (" + text(ba.caveat()) + ")" : ""));
                aLabel.setWrapText(true);
                availBox.getChildren().add(aLabel);
            }
            detailPane.getChildren().addAll(availSection, availBox);
        }

        // Availability caveat
        for (BackendAvailability ba : srv.availability()) {
            if ("emu8086".equalsIgnoreCase(ba.backend()) && ba.caveat() != null) {
                var alertBox = new HBox(8);
                alertBox.getStyleClass().addAll(Styles.ROUNDED, Styles.WARNING);
                alertBox.setPadding(new Insets(10, 14, 10, 14));
                var warnLabel = new Label(localization.get("dialog.academic.emulatorCaveat", text(ba.caveat())));
                warnLabel.setWrapText(true);
                alertBox.getChildren().add(warnLabel);
                detailPane.getChildren().add(alertBox);
            }
        }

        // Example
        if (srv.example() != null && !srv.example().isBlank()) {
            renderExampleSection(srv.example());
            openExampleButton.setDisable(false);
        } else {
            openExampleButton.setDisable(true);
        }
    }

    private void renderConceptDetail(ConceptEntry concept) {
        String lang = localization.localeProperty().get().getLanguage();
        boolean isEs = "es".equalsIgnoreCase(lang);

        var titleLabel = new Label(isEs ? concept.titleEs() : concept.titleEn());
        titleLabel.getStyleClass().addAll(Styles.TITLE_1, Styles.TEXT_BOLD);

        var catBadge = new Label(concept.category() + " • " + concept.level().name());
        catBadge.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.ROUNDED, Styles.BG_SUBTLE);

        var summaryLabel = new Label(isEs ? concept.summaryEs() : concept.summaryEn());
        summaryLabel.getStyleClass().addAll(Styles.TITLE_4, Styles.TEXT_MUTED);
        summaryLabel.setWrapText(true);

        catBadge.setGraphic(WorkbenchIcons.BOOK.create());
        detailPane.getChildren().add(detailHeader(titleLabel, summaryLabel, catBadge));

        var topicSection = createSectionTitle(localization.get("dialog.academic.topic"));
        detailPane.getChildren().add(topicSection);

        var contentSection = createSectionTitle(localization.get("dialog.dictionary.description"));
        var contentLabel = new Label(isEs ? concept.contentEs() : concept.contentEn());
        contentLabel.setWrapText(true);
        detailPane.getChildren().addAll(contentSection, contentLabel);

        openExampleButton.setDisable(true);
    }

    private void renderFlagsSection(FlagSummary flags) {
        var flagsSection = createSectionTitle(localization.get("dialog.dictionary.flags"));
        if (flags == null || !flags.isAnyAffected()) {
            var noFlagsLabel = new Label(localization.get("dialog.dictionary.flags.none"));
            noFlagsLabel.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_ITALIC);
            detailPane.getChildren().addAll(flagsSection, noFlagsLabel);
        } else {
            var legendLabel = new Label(localization.get("dialog.dictionary.flags.legend"));
            legendLabel.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);
            legendLabel.setWrapText(true);
            detailPane.getChildren().addAll(flagsSection, createFlagGrid(flags), legendLabel);
        }
    }

    private FlowPane createFlagGrid(FlagSummary flags) {
        var box = new FlowPane(6, 6);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(4, 0, 4, 0));

        String[] flagNames = {"O", "D", "I", "T", "S", "Z", "A", "P", "C"};
        FlagEffect[] effects = {
                flags.o(), flags.d(), flags.i(), flags.t(),
                flags.s(), flags.z(), flags.a(), flags.p(), flags.c()
        };

        for (int i = 0; i < flagNames.length; i++) {
            String name = flagNames[i];
            FlagEffect eff = effects[i];

            var flagPill = new VBox(2);
            flagPill.setAlignment(Pos.CENTER);
            flagPill.setStyle("-fx-background-color: -color-bg-subtle; -fx-background-radius: 4px; -fx-padding: 4px 8px; -fx-min-width: 32px;");

            var nameLabel = new Label(name);
            nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");

            var effLabel = new Label(eff.symbol());
            effLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");

            switch (eff) {
                case MODIFIED -> effLabel.setStyle("-fx-text-fill: -color-warning-fg; -fx-font-weight: bold;");
                case SET -> effLabel.setStyle("-fx-text-fill: -color-success-fg; -fx-font-weight: bold;");
                case CLEARED -> effLabel.setStyle("-fx-text-fill: -color-accent-fg; -fx-font-weight: bold;");
                case UNDEFINED -> effLabel.setStyle("-fx-text-fill: -color-danger-fg; -fx-font-weight: bold;");
                case UNAFFECTED -> effLabel.setStyle("-fx-text-fill: -color-fg-muted;");
            }

            // Rich educational tooltip on each flag pill
            String fullName = localization.get("flag.full." + name);
            String effectDesc = localization.get("flag.effect." + eff.name());
            var pillTooltip = new Tooltip(fullName + "\n" + effectDesc);
            Tooltip.install(flagPill, pillTooltip);

            flagPill.getChildren().addAll(nameLabel, effLabel);
            box.getChildren().add(flagPill);
        }

        return box;
    }

    private void renderEmptyState(String query) {
        detailPane.getChildren().clear();
        var emptyBox = new VBox(10);
        emptyBox.setAlignment(Pos.CENTER);
        emptyBox.setPadding(new Insets(60, 20, 20, 20));

        var msg = new Label(localization.get("dialog.dictionary.noResults", query != null ? query : ""));
        msg.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TITLE_4);

        emptyBox.getChildren().add(msg);
        detailPane.getChildren().add(emptyBox);
    }

    private void renderExampleSection(String exampleText) {
        var exampleSection = createSectionTitle(localization.get("dialog.dictionary.example"));

        var exampleBox = new VBox(8);
        exampleBox.getStyleClass().add("code-example");

        var codeArea = new TextArea(exampleText);
        codeArea.setEditable(false);
        codeArea.setWrapText(false);
        codeArea.getStyleClass().addAll("code-font", Styles.TEXT_SMALL);
        codeArea.setPrefRowCount(Math.min(14, exampleText.split("\n").length + 2));

        var copyBtn = new Button(localization.get("dialog.dictionary.copyExample"));
        copyBtn.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SMALL, Styles.ROUNDED);
        copyBtn.setGraphic(WorkbenchIcons.COPY.create());
        copyBtn.setOnAction(e -> {
            var content = new ClipboardContent();
            content.putString(exampleText);
            Clipboard.getSystemClipboard().setContent(content);
            copyBtn.setText(localization.get("dialog.dictionary.copied"));
            var pause = new PauseTransition(Duration.seconds(2));
            pause.setOnFinished(ev -> copyBtn.setText(localization.get("dialog.dictionary.copyExample")));
            pause.play();
        });

        exampleBox.getChildren().addAll(codeArea, copyBtn);
        detailPane.getChildren().addAll(exampleSection, exampleBox);
    }

    /** A single-language corpus text in the current UI language. */
    private String text(String corpusText) {
        return viewModel.localize(corpusText, localization.localeProperty().get());
    }

    private Label createSectionTitle(String title) {
        var lbl = new Label(title);
        lbl.getStyleClass().addAll(Styles.TITLE_4, Styles.TEXT_BOLD);
        lbl.getStyleClass().add("academic-section-title");
        lbl.setWrapText(true);
        return lbl;
    }

    private java.util.function.BiConsumer<String, String> exampleProjectOpener;

    public void setExampleProjectOpener(java.util.function.Consumer<String> opener) {
        if (opener == null) {
            this.exampleProjectOpener = null;
        } else {
            this.exampleProjectOpener = (name, code) -> opener.accept(code);
        }
    }

    public void setExampleProjectOpener(java.util.function.BiConsumer<String, String> opener) {
        this.exampleProjectOpener = opener;
    }

    private void handleOpenExample() {
        String exampleCode = getCurrentExampleCode();
        if (exampleCode == null || exampleCode.isBlank()) {
            return;
        }
        if (exampleProjectOpener != null) {
            exampleProjectOpener.accept(getCurrentEntityName(), exampleCode);
            close();
        } else {
            var content = new ClipboardContent();
            content.putString(exampleCode);
            Clipboard.getSystemClipboard().setContent(content);
            openExampleButton.setText(localization.get("dialog.dictionary.copied"));
            var pause = new PauseTransition(Duration.seconds(2));
            pause.setOnFinished(ev -> openExampleButton.textProperty().bind(localization.text("dialog.academic.openExample")));
            pause.play();
        }
    }

    private String getCurrentEntityName() {
        Object entity = viewModel.selectedEntityProperty().get();
        if (entity instanceof InstructionInfo info) return "example_" + info.mnemonic().toLowerCase();
        if (entity instanceof InstructionEntry entry) return "example_" + entry.mnemonic().toLowerCase();
        if (entity instanceof SyntaxItem syntax) return "example_" + (syntax.token() != null ? syntax.token() : syntax.id()).toLowerCase().replaceAll("[^a-zA-Z0-9_]", "_");
        if (entity instanceof ServiceEntry srv) return "example_" + srv.id().toLowerCase().replaceAll("[^a-zA-Z0-9_]", "_");
        return "academic_example";
    }

    private String getCurrentExampleCode() {
        Object entity = viewModel.selectedEntityProperty().get();
        if (entity instanceof InstructionInfo info) {
            return info.example();
        } else if (entity instanceof InstructionEntry entry) {
            return entry.example();
        } else if (entity instanceof SyntaxItem syntax) {
            return syntax.example();
        } else if (entity instanceof ServiceEntry srv) {
            return srv.example();
        }
        return null;
    }

    public void selectMnemonic(String mnemonic) {
        if (mnemonic == null || mnemonic.isBlank()) {
            return;
        }
        String clean = mnemonic.trim().toUpperCase(Locale.ROOT);
        var found = InstructionCatalog.find(clean);
        if (found.isPresent()) {
            InstructionInfo target = found.get();
            viewModel.resetFilters();
            viewModel.selectInstruction(target);
            instructionListView.getSelectionModel().select(target);
            instructionListView.scrollTo(target);
            return;
        }
        viewModel.resetFilters();
        viewModel.searchQueryProperty().set(clean);
        for (InstructionInfo info : viewModel.getFilteredInstructions()) {
            if (info.mnemonic().equalsIgnoreCase(clean)) {
                viewModel.selectInstruction(info);
                instructionListView.getSelectionModel().select(info);
                instructionListView.scrollTo(info);
                break;
            }
        }
    }

    public void selectInstruction(InstructionInfo info) {
        if (info != null) {
            viewModel.selectInstruction(info);
            instructionListView.getSelectionModel().select(info);
            instructionListView.scrollTo(info);
        }
    }

    private void populateCategoryCombo() {
        categoryCombo.getItems().clear();
        categoryCombo.getItems().add(new CategoryItem(null));
        for (InstructionCategory cat : InstructionCategory.values()) {
            categoryCombo.getItems().add(new CategoryItem(cat));
        }
        categoryCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(CategoryItem item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null :
                        (item.isAll() ? WorkbenchIcons.BOOK : categoryIcon(item.category())).create());
                setGraphicTextGap(8);
                if (empty || item == null) setText(null);
                else if (item.isAll()) setText(localization.get("dialog.dictionary.allCategories", viewModel.totalCount()));
                else setText(localization.get("category." + item.category().name()));
            }
        });
        categoryCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(CategoryItem item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null :
                        (item.isAll() ? WorkbenchIcons.BOOK : categoryIcon(item.category())).create());
                setGraphicTextGap(8);
                if (empty || item == null) setText(null);
                else if (item.isAll()) setText(localization.get("dialog.dictionary.allCategories", viewModel.totalCount()));
                else setText(localization.get("category." + item.category().name()));
            }
        });
        categoryCombo.getSelectionModel().select(0);
    }

    private void populateCpuCombo() {
        cpuCombo.getItems().clear();
        cpuCombo.getItems().add(new CpuItem(null));
        for (CpuLevel cpu : CpuLevel.values()) {
            cpuCombo.getItems().add(new CpuItem(cpu));
        }
        cpuCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(CpuItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else if (item.isAll()) setText(localization.get("dialog.dictionary.allCpus"));
                else setText(item.cpu().displayName());
            }
        });
        cpuCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(CpuItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else if (item.isAll()) setText(localization.get("dialog.dictionary.allCpus"));
                else setText(item.cpu().displayName());
            }
        });
        cpuCombo.getSelectionModel().select(0);
    }

    private void populateDialectCombo() {
        dialectCombo.getItems().clear();
        dialectCombo.getItems().add(new DialectItem(Dialect.COMMON));
        for (Dialect d : Dialect.values()) {
            if (d != Dialect.COMMON) {
                dialectCombo.getItems().add(new DialectItem(d));
            }
        }
        dialectCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(DialectItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else if (item.isAll()) setText(localization.get("dialog.academic.allDialects"));
                else setText(item.dialect().displayName());
            }
        });
        dialectCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(DialectItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else if (item.isAll()) setText(localization.get("dialog.academic.allDialects"));
                else setText(item.dialect().displayName());
            }
        });
        dialectCombo.getSelectionModel().select(0);
    }

    private void updateLanguageDependentControls() {
        viewModel.languageProperty().set(localization.localeProperty().get().getLanguage());
        isUpdatingLanguage = true;
        try {
            int catIdx = categoryCombo.getSelectionModel().getSelectedIndex();
            populateCategoryCombo();
            if (catIdx >= 0 && catIdx < categoryCombo.getItems().size()) categoryCombo.getSelectionModel().select(catIdx);

            int cpuIdx = cpuCombo.getSelectionModel().getSelectedIndex();
            populateCpuCombo();
            if (cpuIdx >= 0 && cpuIdx < cpuCombo.getItems().size()) cpuCombo.getSelectionModel().select(cpuIdx);

            int dIdx = dialectCombo.getSelectionModel().getSelectedIndex();
            populateDialectCombo();
            if (dIdx >= 0 && dIdx < dialectCombo.getItems().size()) dialectCombo.getSelectionModel().select(dIdx);
        } finally {
            isUpdatingLanguage = false;
        }

        countLabel.setText(localization.get("dialog.dictionary.instructionsFound", viewModel.matchCountProperty().get()));
        instructionListView.refresh();
        registerListView.refresh();
        syntaxListView.refresh();
        searchResultListView.refresh();

        Object sel = viewModel.selectedEntityProperty().get();
        if (sel != null) renderEntityDetail(sel);
    }

    private VBox detailHeader(Label title, Label summary, Labeled... badges) {
        title.setWrapText(true);
        summary.setWrapText(true);
        var metadata = new FlowPane(8, 8);
        metadata.setAlignment(Pos.CENTER_LEFT);
        for (var badge : badges) {
            badge.getStyleClass().add("academic-badge");
            badge.setWrapText(true);
            metadata.getChildren().add(badge);
        }
        var header = new VBox(12, title, metadata, summary);
        header.getStyleClass().add("instruction-detail-header");
        header.setMinWidth(0);
        return header;
    }

    private static WorkbenchIcons categoryIcon(InstructionCategory category) {
        return switch (category) {
            case DATA_TRANSFER -> WorkbenchIcons.DATA_TRANSFER;
            case ARITHMETIC -> WorkbenchIcons.ARITHMETIC;
            case LOGIC -> WorkbenchIcons.LOGIC;
            case CONTROL_FLOW -> WorkbenchIcons.CONTROL_FLOW;
            case STRINGS -> WorkbenchIcons.STRINGS;
            case FLAGS_CONTROL -> WorkbenchIcons.FLAGS_CONTROL;
            case STACK_PROCEDURES -> WorkbenchIcons.STACK_PROCEDURES;
            case SYSTEM_INTERRUPTS -> WorkbenchIcons.SYSTEM_INTERRUPTS;
            case IO_PORTS -> WorkbenchIcons.IO_PORTS;
            case BIT_MANIPULATION -> WorkbenchIcons.BIT_MANIPULATION;
            case FLOATING_POINT -> WorkbenchIcons.FLOATING_POINT;
        };
    }

    private static WorkbenchIcons registerIcon(RegisterGroup group) {
        return switch (group) {
            case G1_GENERAL -> WorkbenchIcons.REGISTER_GENERAL;
            case G2_POINTERS_INDEXES -> WorkbenchIcons.REGISTER_POINTER;
            case G3_SEGMENT -> WorkbenchIcons.REGISTER_SEGMENT;
            case G4_EXECUTION_CONTROL -> WorkbenchIcons.REGISTER_EXECUTION;
            case G5_X87_SIMD -> WorkbenchIcons.REGISTER_VECTOR;
            case G6_SYSTEM -> WorkbenchIcons.REGISTER_SYSTEM;
        };
    }

    private static WorkbenchIcons syntaxIcon(SyntaxItem syntax) {
        return switch (syntax.syntaxClass()) {
            case PUNCTUATION -> WorkbenchIcons.REFERENCES;
            case OPERATOR -> WorkbenchIcons.ARITHMETIC;
            case DIRECTIVE, PREPROCESSOR -> WorkbenchIcons.CONFIG;
            case PREDEFINED_SYMBOL -> WorkbenchIcons.INCLUDE;
            case SEGMENT_OVERRIDE -> WorkbenchIcons.REGISTER_SEGMENT;
        };
    }

    private static WorkbenchIcons entityIcon(Object entity) {
        if (entity instanceof InstructionInfo info) return categoryIcon(info.category());
        if (entity instanceof InstructionEntry entry) return categoryIcon(entry.category());
        if (entity instanceof RegisterEntry register) return registerIcon(register.group());
        if (entity instanceof SyntaxItem syntax) return syntaxIcon(syntax);
        if (entity instanceof ServiceEntry) return WorkbenchIcons.SYSTEM_INTERRUPTS;
        return WorkbenchIcons.BOOK;
    }

    /** Fixed icon and metadata; the two text lines elide within the remaining list width. */
    private HBox entityRow(String name, String description, String metadata, WorkbenchIcons icon) {
        var title = new Label(name);
        title.getStyleClass().addAll(Styles.TEXT_BOLD, "code-font");
        title.setMinWidth(0);
        title.setMaxWidth(Double.MAX_VALUE);
        title.setTextOverrun(OverrunStyle.ELLIPSIS);
        HBox.setHgrow(title, Priority.ALWAYS);
        var badge = new Label(metadata);
        badge.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
        badge.setMinWidth(Region.USE_PREF_SIZE);
        var heading = new HBox(8, title, badge);
        heading.setAlignment(Pos.CENTER_LEFT);
        var summary = new Label(description);
        summary.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
        summary.setMinWidth(0);
        summary.setMaxWidth(Double.MAX_VALUE);
        summary.setTextOverrun(OverrunStyle.ELLIPSIS);
        var labels = new VBox(3, heading, summary);
        labels.setMinWidth(0);
        HBox.setHgrow(labels, Priority.ALWAYS);
        var row = new HBox(10, icon.create(), labels);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("academic-entity-row");
        return row;
    }

    // --- List Cells ---

    /** The family widths stay visible while the localized group can elide. */
    private final class RegisterListCell extends ListCell<RegisterEntry> {
        @Override
        protected void updateItem(RegisterEntry item, boolean empty) {
            super.updateItem(item, empty);
            setText(null);
            setGraphic(null);
            setTooltip(null);
            setPrefWidth(0);
            if (empty || item == null) return;
            String group = localization.get("register.group." + item.group().name());
            setGraphic(entityRow(item.name(), group, widthSummary(item), registerIcon(item.group())));
            setTooltip(new Tooltip(item.name() + " — " + group + " · " + item.sizeBits() + " bits"));
        }
    }

    private final class SyntaxListCell extends ListCell<SyntaxItem> {
        @Override
        protected void updateItem(SyntaxItem item, boolean empty) {
            super.updateItem(item, empty);
            setText(null);
            setGraphic(null);
            setTooltip(null);
            setPrefWidth(0);
            if (empty || item == null) return;
            String summary = isSpanish() ? item.summaryEs() : item.summaryEn();
            String status = localization.get(item.assemblable()
                    ? "dialog.academic.assemblable" : "dialog.academic.notationOnly");
            setGraphic(entityRow(item.token(), summary, status, syntaxIcon(item)));
            setTooltip(new Tooltip(item.token() + " — " + summary));
        }
    }

    /** "64/32/16/8" for a register whose family covers those widths, or its own width when it has no family. */
    private String widthSummary(RegisterEntry register) {
        var widths = new java.util.LinkedHashSet<Integer>();
        for (RegisterView member : viewModel.registerFamily(register)) {
            widths.add(member.sizeBits());
        }
        if (widths.isEmpty()) {
            widths.add(register.sizeBits());
        }
        return widths.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining("/"));
    }

    private boolean isSpanish() {
        var language = localization.localeProperty().get().getLanguage();
        return language != null && language.toLowerCase(Locale.ROOT).startsWith("es");
    }

    private final class InstructionListCell extends ListCell<InstructionInfo> {
        @Override
        protected void updateItem(InstructionInfo item, boolean empty) {
            super.updateItem(item, empty);
            setText(null);
            setGraphic(null);
            setTooltip(null);
            setPrefWidth(0);
            if (empty || item == null) return;
            String category = localization.get("category." + item.category().name());
            setGraphic(entityRow(item.mnemonic(), category, item.minCpu().displayName(), categoryIcon(item.category())));
            setTooltip(new Tooltip(item.mnemonic() + " — " + category));
        }
    }

    private final class SearchResultListCell extends ListCell<KnowledgeSearchResult> {
        @Override
        protected void updateItem(KnowledgeSearchResult item, boolean empty) {
            super.updateItem(item, empty);
            setText(null);
            setGraphic(null);
            setTooltip(null);
            setPrefWidth(0);
            if (empty || item == null) return;
            setGraphic(entityRow(item.title(), item.summary(),
                    item.kind().label(localization.localeProperty().get().getLanguage()), entityIcon(item.rawEntity())));
            setTooltip(new Tooltip(item.title() + " — " + item.summary()));
        }
    }
}
