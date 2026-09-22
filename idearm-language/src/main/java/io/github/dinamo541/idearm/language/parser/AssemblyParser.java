package io.github.dinamo541.idearm.language.parser;

import io.github.dinamo541.idearm.language.lexer.AssemblyLexer;
import io.github.dinamo541.idearm.language.lexer.Token;
import io.github.dinamo541.idearm.language.lexer.TokenType;
import io.github.dinamo541.idearm.language.model.*;

import java.util.*;

/**
 * Line-oriented parser that builds a {@link SourceFileNode} AST from tokenized Assembly.
 * Includes error tolerance so that typing in the editor produces valid symbol information.
 */
public final class AssemblyParser {

    private final AssemblyLexer lexer;

    public AssemblyParser() {
        this(new AssemblyLexer());
    }

    public AssemblyParser(AssemblyLexer lexer) {
        this.lexer = Objects.requireNonNull(lexer, "lexer cannot be null");
    }

    public SourceFileNode parse(String source) {
        List<Token> tokens = lexer.tokenize(source);
        return parse(tokens);
    }

    public SourceFileNode parse(List<Token> tokens) {
        List<AstNode> statements = new ArrayList<>();
        List<ProcedureNode> procedures = new ArrayList<>();
        List<LabelNode> labels = new ArrayList<>();
        List<DataNode> dataDefinitions = new ArrayList<>();
        List<ConstantNode> constants = new ArrayList<>();
        List<SegmentNode> segments = new ArrayList<>();
        List<IncludeNode> includes = new ArrayList<>();
        List<InstructionNode> instructions = new ArrayList<>();

        Map<String, ProcedureBuilder> openProcedures = new LinkedHashMap<>();
        Map<String, SegmentBuilder> openSegments = new LinkedHashMap<>();

        int i = 0;
        int size = tokens.size();

        while (i < size) {
            Token token = tokens.get(i);
            if (token.is(TokenType.EOF)) break;
            if (token.is(TokenType.EOL) || token.is(TokenType.COMMENT)) {
                i++;
                continue;
            }

            // Collect all tokens for the current line
            int lineStart = i;
            int currentLine = token.line();
            List<Token> lineTokens = new ArrayList<>();
            while (i < size && !tokens.get(i).is(TokenType.EOL) && !tokens.get(i).is(TokenType.EOF)) {
                if (!tokens.get(i).is(TokenType.COMMENT)) {
                    lineTokens.add(tokens.get(i));
                }
                i++;
            }

            if (lineTokens.isEmpty()) {
                continue;
            }

            parseLine(lineTokens, currentLine, statements, procedures, labels,
                    dataDefinitions, constants, segments, includes, instructions,
                    openProcedures, openSegments);
        }

        // Close any procedures left open at EOF
        for (ProcedureBuilder pb : openProcedures.values()) {
            procedures.add(pb.build(tokens.isEmpty() ? 1 : tokens.getLast().line()));
        }

        return new SourceFileNode(
                statements, procedures, labels, dataDefinitions,
                constants, segments, includes, instructions
        );
    }

    private void parseLine(List<Token> line, int lineNum,
                           List<AstNode> statements,
                           List<ProcedureNode> procedures,
                           List<LabelNode> labels,
                           List<DataNode> dataDefinitions,
                           List<ConstantNode> constants,
                           List<SegmentNode> segments,
                           List<IncludeNode> includes,
                           List<InstructionNode> instructions,
                           Map<String, ProcedureBuilder> openProcedures,
                           Map<String, SegmentBuilder> openSegments) {
        int idx = 0;

        // 1. Check for label definition at start of line: "name:"
        if (line.size() >= 2 && line.get(1).is(TokenType.COLON)) {
            Token nameTok = line.get(0);
            if (nameTok.is(TokenType.IDENTIFIER) || nameTok.is(TokenType.INSTRUCTION)) {
                String labelName = nameTok.text();
                boolean isLocal = labelName.startsWith(".") || labelName.startsWith("@@");
                LabelNode labelNode = new LabelNode(labelName, lineNum, nameTok.column(), isLocal);
                labels.add(labelNode);
                statements.add(labelNode);
                idx = 2; // skip name and colon
                if (idx >= line.size()) return;
            }
        }

        Token first = line.get(idx);

        // 2. Simplified segment directives: .CODE, .DATA, .STACK
        if (first.is(TokenType.DIRECTIVE)) {
            String dir = first.normalized();
            if (dir.equals(".CODE") || dir.equals(".DATA") || dir.equals(".STACK")) {
                SegmentNode seg = new SegmentNode(dir.substring(1), dir.substring(1).toLowerCase(Locale.ROOT), lineNum, first.column(), lineNum);
                segments.add(seg);
                statements.add(seg);
                return;
            }

            // .EXIT and .STARTUP generate code (the DOS exit and the DS setup), so they are statements the rules
            // must see, such as "does main end the program?".
            if (dir.equals(".EXIT") || dir.equals(".STARTUP")) {
                InstructionNode inst = new InstructionNode(dir, extractOperands(line, idx + 1), lineNum, first.column(), null);
                instructions.add(inst);
                statements.add(inst);
                return;
            }

            // INCLUDE directive
            if (dir.equals("INCLUDE") && idx + 1 < line.size()) {
                Token pathTok = line.get(idx + 1);
                String path = pathTok.text().replace("\"", "").replace("'", "");
                IncludeNode inc = new IncludeNode(path, lineNum, first.column());
                includes.add(inc);
                statements.add(inc);
                return;
            }
        }

        // 3. Classical SEGMENT ... ENDS
        if (idx + 1 < line.size()) {
            Token second = line.get(idx + 1);
            if (second.isDirective("SEGMENT")) {
                String segName = first.text();
                openSegments.put(segName.toUpperCase(Locale.ROOT), new SegmentBuilder(segName, lineNum, first.column()));
                return;
            }
            if (second.isDirective("ENDS")) {
                String segName = first.text().toUpperCase(Locale.ROOT);
                SegmentBuilder sb = openSegments.remove(segName);
                if (sb != null) {
                    SegmentNode seg = sb.build(lineNum);
                    segments.add(seg);
                    statements.add(seg);
                }
                return;
            }
        }

        // 4. Procedure definition: "name PROC [options]" / "name ENDP"
        if (idx + 1 < line.size()) {
            Token second = line.get(idx + 1);
            if (second.isDirective("PROC")) {
                String procName = first.text();
                boolean isFar = false;
                boolean isPublic = false;
                for (int p = idx + 2; p < line.size(); p++) {
                    String opt = line.get(p).normalized();
                    if (opt.equals("FAR")) isFar = true;
                    if (opt.equals("PUBLIC")) isPublic = true;
                }
                openProcedures.put(procName.toUpperCase(Locale.ROOT),
                        new ProcedureBuilder(procName, lineNum, first.column(), isFar, isPublic));
                return;
            }

            if (second.isDirective("ENDP")) {
                String procName = first.text().toUpperCase(Locale.ROOT);
                ProcedureBuilder pb = openProcedures.remove(procName);
                if (pb != null) {
                    ProcedureNode proc = pb.build(lineNum);
                    procedures.add(proc);
                    statements.add(proc);
                }
                return;
            }
        }

        // 5. Constant definition: "name EQU value" or "name = value"
        if (idx + 2 < line.size()) {
            Token second = line.get(idx + 1);
            if (second.isDirective("EQU") || second.is(TokenType.EQUALS)) {
                String name = first.text();
                StringBuilder val = new StringBuilder();
                for (int v = idx + 2; v < line.size(); v++) {
                    if (!val.isEmpty()) val.append(" ");
                    val.append(line.get(v).text());
                }
                ConstantNode c = new ConstantNode(name, val.toString(), lineNum, first.column());
                constants.add(c);
                statements.add(c);
                return;
            }
        }

        // 6. Data definition: "name DB|DW|DD|DQ|DT value"
        if (idx + 1 < line.size()) {
            Token second = line.get(idx + 1);
            if (isDataDirective(second)) {
                String name = first.text();
                String dir = second.normalized();
                StringBuilder val = new StringBuilder();
                for (int v = idx + 2; v < line.size(); v++) {
                    if (!val.isEmpty()) val.append(" ");
                    val.append(line.get(v).text());
                }
                DataNode d = new DataNode(name, dir, val.toString(), lineNum, first.column());
                dataDefinitions.add(d);
                statements.add(d);
                return;
            }
        }

        // 7. Instruction: "MNEMONIC [operands]"
        if (first.is(TokenType.INSTRUCTION)) {
            String mnemonic = first.normalized();
            List<String> operands = extractOperands(line, idx + 1);
            InstructionNode inst = new InstructionNode(mnemonic, operands, lineNum, first.column(), null);
            instructions.add(inst);
            statements.add(inst);
        }
    }

    private static boolean isDataDirective(Token token) {
        if (!token.is(TokenType.DIRECTIVE)) return false;
        String dir = token.normalized();
        return dir.equals("DB") || dir.equals("DW") || dir.equals("DD") || dir.equals("DQ") || dir.equals("DT");
    }

    private static List<String> extractOperands(List<Token> line, int startIndex) {
        List<String> operands = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (int i = startIndex; i < line.size(); i++) {
            Token tok = line.get(i);
            if (tok.is(TokenType.COMMA)) {
                if (!current.isEmpty()) {
                    operands.add(current.toString().trim());
                    current.setLength(0);
                }
            } else {
                if (!current.isEmpty() && !tok.is(TokenType.COLON) && !tok.is(TokenType.RBRACKET) && !tok.is(TokenType.RPAREN)) {
                    current.append(" ");
                }
                current.append(tok.text());
            }
        }

        if (!current.isEmpty()) {
            operands.add(current.toString().trim());
        }

        return operands;
    }

    private static class ProcedureBuilder {
        final String name;
        final int startLine;
        final int column;
        final boolean isFar;
        final boolean isPublic;

        ProcedureBuilder(String name, int startLine, int column, boolean isFar, boolean isPublic) {
            this.name = name;
            this.startLine = startLine;
            this.column = column;
            this.isFar = isFar;
            this.isPublic = isPublic;
        }

        ProcedureNode build(int endLine) {
            return new ProcedureNode(name, startLine, column, endLine, isFar, isPublic);
        }
    }

    private static class SegmentBuilder {
        final String name;
        final int startLine;
        final int column;

        SegmentBuilder(String name, int startLine, int column) {
            this.name = name;
            this.startLine = startLine;
            this.column = column;
        }

        SegmentNode build(int endLine) {
            return new SegmentNode(name, "segment", startLine, column, endLine);
        }
    }
}
