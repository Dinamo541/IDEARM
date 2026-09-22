package io.github.dinamo541.idearm.application.editor;

import io.github.dinamo541.idearm.language.model.LabelNode;
import io.github.dinamo541.idearm.language.model.ProcedureNode;
import io.github.dinamo541.idearm.language.model.SegmentNode;
import io.github.dinamo541.idearm.language.model.SourceFileNode;
import io.github.dinamo541.idearm.language.parser.AssemblyParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Use case: Builds a document outline tree (procedures, labels, segments) from source code.
 */
public final class QueryOutline {

    private final AssemblyParser parser = new AssemblyParser();

    public List<OutlineItem> execute(String sourceText) {
        if (sourceText == null || sourceText.isBlank()) {
            return List.of();
        }

        SourceFileNode ast = parser.parse(sourceText);
        List<OutlineItem> rootItems = new ArrayList<>();

        // 1. Add segments
        for (SegmentNode seg : ast.segments()) {
            rootItems.add(new OutlineItem(seg.name(), "segment", seg.line(), seg.column(), List.of()));
        }

        // 2. Add procedures and nest internal labels
        List<LabelNode> remainingLabels = new ArrayList<>(ast.labels());

        for (ProcedureNode proc : ast.procedures()) {
            List<OutlineItem> internalLabels = new ArrayList<>();
            remainingLabels.removeIf(lbl -> {
                if (lbl.line() >= proc.line() && lbl.line() <= proc.endLine()) {
                    internalLabels.add(new OutlineItem(lbl.name(), "label", lbl.line(), lbl.column(), List.of()));
                    return true;
                }
                return false;
            });

            rootItems.add(new OutlineItem(proc.name(), "procedure", proc.line(), proc.column(), internalLabels));
        }

        // 3. Add any standalone labels outside procedures
        for (LabelNode lbl : remainingLabels) {
            rootItems.add(new OutlineItem(lbl.name(), "label", lbl.line(), lbl.column(), List.of()));
        }

        // Sort by line number
        rootItems.sort((a, b) -> Integer.compare(a.line(), b.line()));
        return List.copyOf(rootItems);
    }
}
