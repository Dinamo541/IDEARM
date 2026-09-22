package io.github.dinamo541.idearm.app.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Layer rules for the desktop application (ADR-007).
 *
 * <p>The presentation layer talks to ports only. Naming an adapter is allowed in exactly one place, the
 * composition root, which is what keeps the IDE able to gain a toolchain without any UI change (NFR-08).
 */
class PresentationArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        // Read the compiled module directly: it is imported the same way whether tests run on the class path
        // or the module path.
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPath(Path.of("target", "classes"));
    }

    @Test
    void theModuleWasImported() {
        assertFalse(classes.isEmpty(), "No compiled classes were found: the rules below would pass vacuously.");
    }

    @Test
    void onlyTheCompositionRootMayNameAnAdapter() {
        noClasses()
                .that().resideInAPackage("io.github.dinamo541.idearm.app..")
                .and().resideOutsideOfPackage("io.github.dinamo541.idearm.app.bootstrap..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..infrastructure..", "..toolchain..", "..emu8086..")
                .because("Views and view models receive ports; only the composition root wires adapters (ADR-007)")
                .check(classes);
    }

    @Test
    void viewModelsMustNotDependOnViews() {
        noClasses()
                .that().resideInAPackage("..app.viewmodel..")
                .should().dependOnClassesThat().resideInAPackage("..app.view..")
                .because("MVVM flows one way: a view observes its view model, never the other way around")
                .check(classes);
    }

    @Test
    void viewModelsMustNotFormatUserTextThemselves() {
        noClasses()
                .that().resideInAPackage("..app.viewmodel..")
                .should().dependOnClassesThat().haveFullyQualifiedName("java.util.ResourceBundle")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("io.github.dinamo541.idearm.app.i18n.Localization")
                .because("View models publish message keys so both languages stay switchable at runtime (ADR-006)")
                .check(classes);
    }
}
