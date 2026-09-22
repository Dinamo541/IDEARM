package io.github.dinamo541.idearm.language.model;

import java.util.List;

/**
 * Root AST node representing a fully parsed Assembly source file.
 */
public record SourceFileNode(
        List<AstNode> statements,
        List<ProcedureNode> procedures,
        List<LabelNode> labels,
        List<DataNode> dataDefinitions,
        List<ConstantNode> constants,
        List<SegmentNode> segments,
        List<IncludeNode> includes,
        List<InstructionNode> instructions
) implements AstNode {
    public SourceFileNode {
        statements = List.copyOf(statements);
        procedures = List.copyOf(procedures);
        labels = List.copyOf(labels);
        dataDefinitions = List.copyOf(dataDefinitions);
        constants = List.copyOf(constants);
        segments = List.copyOf(segments);
        includes = List.copyOf(includes);
        instructions = List.copyOf(instructions);
    }

    @Override
    public int line() {
        return 1;
    }

    @Override
    public int column() {
        return 1;
    }
}
