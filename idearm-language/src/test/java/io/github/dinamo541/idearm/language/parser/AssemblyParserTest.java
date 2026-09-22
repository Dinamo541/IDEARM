package io.github.dinamo541.idearm.language.parser;

import io.github.dinamo541.idearm.language.model.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AssemblyParserTest {

    private final AssemblyParser parser = new AssemblyParser();

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
}
