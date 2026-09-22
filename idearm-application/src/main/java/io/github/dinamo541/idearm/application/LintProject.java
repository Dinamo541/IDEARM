package io.github.dinamo541.idearm.application;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.files.TextDecoding;
import io.github.dinamo541.idearm.domain.model.Project;
import io.github.dinamo541.idearm.domain.model.TargetProfile;
import io.github.dinamo541.idearm.domain.model.TargetProfileCatalog;
import io.github.dinamo541.idearm.language.index.ProjectSymbolIndex;
import io.github.dinamo541.idearm.language.linter.AssemblyLinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Use case: the educational warnings for every source a build uses, such as a program that never returns to DOS
 * or an instruction the selected CPU does not have.
 *
 * <p>The rules describe MASM/TASM programs for DOS, so a native NASM project gets no warnings rather than wrong
 * ones. Paths in the warnings are project-relative, as in build diagnostics.
 */
public final class LintProject {

    private static final long MAX_SOURCE_BYTES = 4L * 1024 * 1024;

    private final AssemblyLinter linter;

    public LintProject() {
        this(new AssemblyLinter());
    }

    public LintProject(AssemblyLinter linter) {
        this.linter = Objects.requireNonNull(linter, "linter");
    }

    public List<Diagnostic> execute(Path projectRoot, Project project, ProjectSymbolIndex index) {
        Objects.requireNonNull(projectRoot, "projectRoot");
        Objects.requireNonNull(project, "project");
        TargetProfile target;
        try {
            target = TargetProfileCatalog.require(project.target().profile());
        } catch (DomainException unsupported) {
            return List.of();
        }
        if (!target.isDos()) {
            return List.of();
        }
        Path root = projectRoot.toAbsolutePath().normalize();
        Project expanded;
        try {
            expanded = SourceSet.expand(project, root);
        } catch (RuntimeException unreadablePatterns) {
            // The build reports broken patterns; warnings are only an extra.
            return List.of();
        }

        var sources = new LinkedHashSet<String>();
        sources.add(expanded.sources().entry());
        sources.addAll(expanded.sources().modules());

        var warnings = new ArrayList<Diagnostic>();
        for (String relative : sources) {
            Path file = root.resolve(relative).normalize();
            try {
                if (!file.startsWith(root) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                        || Files.size(file) > MAX_SOURCE_BYTES) {
                    continue;
                }
                String text = TextDecoding.decode(Files.readAllBytes(file)).text();
                warnings.addAll(linter.lint(text, relative.replace('\\', '/'), project.target().cpu(), index));
            } catch (IOException | RuntimeException unreadable) {
                // A file that cannot be read or parsed simply gets no warnings.
            }
        }
        return List.copyOf(warnings);
    }
}
