package io.github.dinamo541.idearm.domain.build;

import io.github.dinamo541.idearm.domain.build.BuildPhase;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.model.*;
import io.github.dinamo541.idearm.domain.port.AssemblerAdapter;
import io.github.dinamo541.idearm.domain.port.LinkerAdapter;
import io.github.dinamo541.idearm.domain.port.ToolRegistry;
import io.github.dinamo541.idearm.domain.port.ToolchainProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BuildPlannerTest {

    private final BuildPlanner planner = new BuildPlanner();

    private final ToolchainProvider borlandProvider = new ToolchainProvider() {
        @Override public String id() { return "borland-tasm"; }
        @Override public Set<TargetSupport> supports() { return Set.of(new TargetSupport(16, "OMF", "MZ", "DOS")); }
        @Override public AssemblerAdapter assembler() {
            return new AssemblerAdapter() {
                @Override public ToolInvocation assemble(AssembleRequest r) {
                    return new ToolInvocation("tasm", BuildPhase.ASSEMBLE, HostKind.DOS_REAL, List.of(r.source()), null, null, List.of(r.objectFile()));
                }
                @Override public io.github.dinamo541.idearm.domain.port.DiagnosticParser diagnostics() { return null; }
                @Override public HostKind host() { return HostKind.DOS_REAL; }
            };
        }
        @Override public LinkerAdapter linker() {
            return new LinkerAdapter() {
                @Override public ToolInvocation link(LinkRequest r) {
                    return new ToolInvocation("tlink", BuildPhase.LINK, HostKind.DOS_REAL, r.objectFiles(), null, null, List.of(r.executable()));
                }
                @Override public io.github.dinamo541.idearm.domain.port.DiagnosticParser diagnostics() { return null; }
                @Override public HostKind host() { return HostKind.DOS_REAL; }
            };
        }
        @Override public ResolvedToolchain resolve(Project project, ToolRegistry registry) { return null; }
    };

    private Project sampleProject() {
        return new Project(
                1,
                new ProjectInfo("HELLO", "0.1.0"),
                new TargetSelection("dos-exe-16", "8086"),
                new ToolchainSelection("borland-tasm", ">=3.2"),
                new Sources("src/main.asm", List.of(), List.of(), List.of()),
                new Resources(List.of()),
                Map.of(
                        "release", new BuildConfiguration(false, true, true, Map.of(), List.of()),
                        "debug", new BuildConfiguration(true, true, true, Map.of(), List.of())
                ),
                new RunConfiguration("dosbox", "required", true, "auto", 16, List.of()),
                new DebugConfiguration("external"),
                new DistConfiguration(true, false)
        );
    }

    @Test
    void plansReleaseBuildSuccessfully() {
        BuildPlan plan = planner.plan(sampleProject(), "release", borlandProvider);
        assertNotNull(plan);
        assertEquals("release", plan.configuration());
        assertEquals("bin/main.exe", plan.executable());
        assertEquals(2, plan.steps().size());
        assertTrue(plan.outputs().contains("obj/main.obj"));
        assertTrue(plan.outputs().contains("bin/main.exe"));
        assertTrue(plan.outputs().contains("lst/main.lst"));
        assertTrue(plan.outputs().contains("map/main.map"));
    }

    @Test
    void generatedNamesKeepTheSourceSpellingWithLowerCaseExtensions() {
        Project legacy = sampleProject();
        legacy = new Project(legacy.schema(), legacy.info(), legacy.target(), legacy.toolchain(),
                new Sources("src/Game.ASM", List.of("src/IO.ASM"), List.of(), List.of()),
                legacy.resources(), legacy.build(), legacy.run(), legacy.debug(), legacy.dist());

        BuildPlan plan = planner.plan(legacy, "release", borlandProvider);

        // An existing upper-case source still builds; nothing the IDE writes has an upper-case extension.
        assertEquals("bin/Game.exe", plan.executable());
        assertEquals(List.of("obj/Game.obj", "lst/Game.lst", "obj/IO.obj", "lst/IO.lst", "bin/Game.exe",
                "map/Game.map"), plan.outputs());
        assertTrue(plan.outputs().stream().allMatch(path -> {
            String extension = path.substring(path.lastIndexOf('.') + 1);
            return extension.equals(extension.toLowerCase(java.util.Locale.ROOT));
        }));
    }

    @Test
    void recordsWhichListingBelongsToEachSource() {
        BuildPlan plan = planner.plan(sampleProject(), "debug", borlandProvider);

        assertEquals(Map.of("src/main.asm", "lst/main.lst"), plan.listings());
    }

    @Test
    void plansDebugBuildSuccessfully() {
        BuildPlan plan = planner.plan(sampleProject(), "debug", borlandProvider);
        assertNotNull(plan);
        assertEquals("debug", plan.configuration());
        assertEquals(2, plan.steps().size());
    }

    @Test
    void rejectsNonAsmEntry() {
        Project p = new Project(
                1,
                new ProjectInfo("HELLO", "0.1.0"),
                new TargetSelection("dos-exe-16", "8086"),
                new ToolchainSelection("borland-tasm", ">=3.2"),
                new Sources("src/MAIN.C", List.of(), List.of(), List.of()),
                new Resources(List.of()),
                Map.of("release", BuildConfiguration.release()),
                new RunConfiguration("dosbox", "required", true, "auto", 16, List.of()),
                new DebugConfiguration("external"),
                new DistConfiguration(true, false)
        );
        assertThrows(DomainException.class, () -> planner.plan(p, "release", borlandProvider));
    }

    @Test
    void rejectsUnknownConfiguration() {
        assertThrows(DomainException.class, () -> planner.plan(sampleProject(), "staging", borlandProvider));
    }

    @Test
    void plansMultiModuleBuildSuccessfully() {
        Project p = new Project(
                1,
                new ProjectInfo("MULTI", "0.1.0"),
                new TargetSelection("dos-exe-16", "8086"),
                new ToolchainSelection("borland-tasm", ">=3.2"),
                new Sources("src/main.asm", List.of("src/extras.asm", "src/utils.asm"), List.of(), List.of()),
                new Resources(List.of()),
                Map.of("release", new BuildConfiguration(false, true, true, Map.of(), List.of())),
                RunConfiguration.defaults(),
                new DebugConfiguration("external"),
                new DistConfiguration(true, false)
        );

        BuildPlan plan = planner.plan(p, "release", borlandProvider);
        assertNotNull(plan);
        assertEquals(4, plan.steps().size(), "Should have 3 assemble steps + 1 link step");

        // Verify assemble steps
        assertEquals("src/main.asm", plan.steps().get(0).arguments().get(0));
        assertEquals("src/extras.asm", plan.steps().get(1).arguments().get(0));
        assertEquals("src/utils.asm", plan.steps().get(2).arguments().get(0));

        // Verify link step
        ToolInvocation linkStep = plan.steps().get(3);
        assertEquals(3, linkStep.arguments().size(), "Linker should receive all 3 object files");
        assertTrue(linkStep.arguments().contains("obj/main.obj"));
        assertTrue(linkStep.arguments().contains("obj/extras.obj"));
        assertTrue(linkStep.arguments().contains("obj/utils.obj"));

        // Verify outputs
        assertTrue(plan.outputs().contains("obj/main.obj"));
        assertTrue(plan.outputs().contains("obj/extras.obj"));
        assertTrue(plan.outputs().contains("obj/utils.obj"));
        assertTrue(plan.outputs().contains("bin/main.exe"));
        assertTrue(plan.outputs().contains("map/main.map"));
    }

    @Test
    void rejectsNonAsmModule() {
        Project p = new Project(
                1,
                new ProjectInfo("MULTI", "0.1.0"),
                new TargetSelection("dos-exe-16", "8086"),
                new ToolchainSelection("borland-tasm", ">=3.2"),
                new Sources("src/MAIN.ASM", List.of("src/EXTRAS.INC"), List.of(), List.of()),
                new Resources(List.of()),
                Map.of("release", BuildConfiguration.release()),
                RunConfiguration.defaults(),
                new DebugConfiguration("external"),
                new DistConfiguration(true, false)
        );
        assertThrows(DomainException.class, () -> planner.plan(p, "release", borlandProvider));
    }

    @Test
    void rejectsCollidingModuleOutputStems() {
        // If two source files share the same stem, their OBJ files collide in OBJ/
        Project p = new Project(
                1,
                new ProjectInfo("MULTI", "0.1.0"),
                new TargetSelection("dos-exe-16", "8086"),
                new ToolchainSelection("borland-tasm", ">=3.2"),
                new Sources("src/MAIN.ASM", List.of("lib/MAIN.ASM"), List.of(), List.of()),
                new Resources(List.of()),
                Map.of("release", BuildConfiguration.release()),
                RunConfiguration.defaults(),
                new DebugConfiguration("external"),
                new DistConfiguration(true, false)
        );
        assertThrows(DomainException.class, () -> planner.plan(p, "release", borlandProvider));
    }
}
