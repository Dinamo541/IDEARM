package io.github.dinamo541.idearm.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("io.github.dinamo541.idearm");
    }

    @Test
    void domainMustNotDependOnApplicationInfrastructureOrPresentation() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .or().resideInAPackage("..language..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..application..", "..infrastructure..", "..app..", "..cli..", "..toolchain..", "..emu8086..")
                .because("Domain and Language are Layer 3 and must be independent of other layers (ADR-007)")
                .check(classes);
    }

    @Test
    void domainMustNotDependOnFilesystemOrProcessOrJavaFX() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .or().resideInAPackage("..language..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.openjfx..", "javafx..")
                .because("Domain and Language must not depend on JavaFX (ADR-007, NFR-09)")
                .check(classes);

        noClasses()
                .that().resideInAPackage("..domain..")
                .or().resideInAPackage("..language..")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("java.nio.file.Files")
                .orShould().dependOnClassesThat()
                .haveFullyQualifiedName("java.lang.ProcessBuilder")
                .because("Domain and Language must contain no filesystem I/O or process spawning (ADR-007)")
                .check(classes);
    }

    @Test
    void applicationMustNotDependOnInfrastructureOrPresentation() {
        noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..infrastructure..", "..app..", "..cli..", "..toolchain..", "..emu8086..")
                .because("Application is Layer 2 and only coordinates domain and ports (ADR-007)")
                .check(classes);

        noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.openjfx..", "javafx..")
                .because("Application must not depend on JavaFX (ADR-007)")
                .check(classes);
    }

    @Test
    void infrastructureMustNotDependOnPresentation() {
        noClasses()
                .that().resideInAPackage("..infrastructure..")
                .or().resideInAPackage("..toolchain..")
                .or().resideInAPackage("..emu8086..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..app..", "..cli..")
                .because("Infrastructure is Layer 4 and implements ports, never calling presentation (ADR-007)")
                .check(classes);

        noClasses()
                .that().resideInAPackage("..infrastructure..")
                .or().resideInAPackage("..toolchain..")
                .or().resideInAPackage("..emu8086..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.openjfx..", "javafx..")
                .because("Infrastructure must not depend on JavaFX (ADR-007)")
                .check(classes);
    }
}
