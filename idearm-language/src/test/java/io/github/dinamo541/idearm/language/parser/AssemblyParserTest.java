package io.github.dinamo541.idearm.language.parser;

import io.github.dinamo541.idearm.language.model.*;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AssemblyParserTest {

    private final AssemblyParser parser = new AssemblyParser();

    /**
     * The lexer reads a line as code, so it splits a file name on its dot and drops DOS backslashes; an INCLUDE
     * therefore has to keep the name as the line writes it, or nothing can resolve the file.
     */
    @Test
    void parsesTheWholeFileNameOfAnInclude() {
        String code = """
                .DATA
                    INCLUDE manzana.inc
                    INCLUDE inc\\macros.inc      ; DOS folders use backslashes
                    INCLUDE "sub/other.inc"
                .CODE
                """;

        SourceFileNode ast = parser.parse(code);

        assertEquals(List.of("manzana.inc", "inc\\macros.inc", "sub/other.inc"),
                ast.includes().stream().map(IncludeNode::path).toList());
        assertEquals(2, ast.includes().getFirst().line());
    }

    @Test
    void parsesProceduresAndLabels() {
        String code = """
                .model small
                .code
                main proc
                inicio:
                    mov ax, 1
                    jmp fin
                fin:
                    ret
                main endp
                end main
                """;

        SourceFileNode ast = parser.parse(code);

        assertNotNull(ast);
        assertEquals(1, ast.procedures().size());
        ProcedureNode proc = ast.procedures().get(0);
        assertEquals("main", proc.name());
        assertEquals(3, proc.line());
        assertEquals(9, proc.endLine());

        assertEquals(2, ast.labels().size());
        assertEquals("inicio", ast.labels().get(0).name());
        assertEquals("fin", ast.labels().get(1).name());

        assertEquals(3, ast.instructions().size());
    }

    @Test
    void parsesDataAndConstants() {
        String code = """
                .data
                MAX_VAL EQU 100
                msg db 'Hello', 0
                nums dw 1, 2, 3, 4
                """;

        SourceFileNode ast = parser.parse(code);

        assertEquals(1, ast.constants().size());
        assertEquals("MAX_VAL", ast.constants().get(0).name());
        assertEquals("100", ast.constants().get(0).value());

        assertEquals(2, ast.dataDefinitions().size());
        DataNode d1 = ast.dataDefinitions().get(0);
        assertEquals("msg", d1.name());
        assertEquals("DB", d1.directive());

        DataNode d2 = ast.dataDefinitions().get(1);
        assertEquals("nums", d2.name());
        assertEquals("DW", d2.directive());
    }

    @Test
    void parsesClassicalSegments() {
        String code = """
                CODE SEGMENT
                start:
                    nop
                CODE ENDS
                END start
                """;

        SourceFileNode ast = parser.parse(code);
        assertEquals(1, ast.segments().size());
        assertEquals("CODE", ast.segments().get(0).name());
        assertEquals(1, ast.labels().size());
    }

    @Test
    void recordsAMacroDefinition() {
        SourceFileNode ast = parser.parse("""
                print_str macro texto
                  mov ah, 9
                endm
                """);

        List<MacroNode> macros = ast.statements().stream()
                .filter(MacroNode.class::isInstance)
                .map(MacroNode.class::cast)
                .toList();
        assertEquals(1, macros.size());
        assertEquals("print_str", macros.getFirst().name());
    }

    @Test
    void recordsAWordItDoesNotRecogniseInInstructionPosition() {
        // This line used to be dropped without a trace, which left no way for a rule to see the mistake.
        SourceFileNode ast = parser.parse("  MUV bx, 1\n");

        List<UnknownStatementNode> unknown = ast.statements().stream()
                .filter(UnknownStatementNode.class::isInstance)
                .map(UnknownStatementNode.class::cast)
                .toList();
        assertEquals(1, unknown.size());
        assertEquals("MUV", unknown.getFirst().word());
        assertEquals(1, unknown.getFirst().line());
        assertEquals(3, unknown.getFirst().column());
        assertEquals(3, unknown.getFirst().length());
    }

    @Test
    void recordsNothingUnknownForLinesItAlreadyUnderstood() {
        SourceFileNode ast = parser.parse("""
                .model small
                .data
                msg db 'hola$'
                count equ 10
                buffer label byte
                .code
                main proc
                start:
                  mov ax, @data
                  jmp start
                main endp
                end main
                """);

        assertTrue(ast.statements().stream().noneMatch(UnknownStatementNode.class::isInstance));
    }
}
