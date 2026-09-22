package io.github.dinamo541.idearm.language.lexer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AssemblyLexerTest {

    private final AssemblyLexer lexer = new AssemblyLexer();

    @Test
    void tokenizesInstructionsAndRegisters() {
        String code = "MOV AX, BX\nADD DX, 10h";
        List<Token> tokens = lexer.tokenize(code);

        assertNotNull(tokens);
        // First line: MOV, AX, COMMA, BX, EOL
        assertEquals(TokenType.INSTRUCTION, tokens.get(0).type());
        assertEquals("MOV", tokens.get(0).text());

        assertEquals(TokenType.REGISTER, tokens.get(1).type());
        assertEquals("AX", tokens.get(1).text());

        assertEquals(TokenType.COMMA, tokens.get(2).type());

        assertEquals(TokenType.REGISTER, tokens.get(3).type());
        assertEquals("BX", tokens.get(3).text());

        assertEquals(TokenType.EOL, tokens.get(4).type());

        // Second line: ADD, DX, COMMA, 10h
        assertEquals(TokenType.INSTRUCTION, tokens.get(5).type());
        assertEquals("ADD", tokens.get(5).text());

        assertEquals(TokenType.REGISTER, tokens.get(6).type());
        assertEquals("DX", tokens.get(6).text());

        assertEquals(TokenType.COMMA, tokens.get(7).type());

        assertEquals(TokenType.NUMBER, tokens.get(8).type());
        assertEquals("10h", tokens.get(8).text());
    }

    @Test
    void tokenizesLabelsAndStringsAndComments() {
        String code = "entrada:\n  mensaje DB 'Hola Mundo', '$' ; saludo";
        List<Token> tokens = lexer.tokenize(code);

        // entrada : EOL
        assertEquals(TokenType.IDENTIFIER, tokens.get(0).type());
        assertEquals("entrada", tokens.get(0).text());
        assertEquals(TokenType.COLON, tokens.get(1).type());

        // Find string and comment
        boolean foundString = tokens.stream().anyMatch(t -> t.is(TokenType.STRING) && t.text().equals("'Hola Mundo'"));
        boolean foundComment = tokens.stream().anyMatch(t -> t.is(TokenType.COMMENT) && t.text().contains("saludo"));

        assertTrue(foundString);
        assertTrue(foundComment);
    }

    @Test
    void tokenizesDirectivesWithDot() {
        String code = ".model small\n.stack 100h\n.data\n.code";
        List<Token> tokens = lexer.tokenize(code);

        long directiveCount = tokens.stream().filter(t -> t.is(TokenType.DIRECTIVE)).count();
        assertEquals(4, directiveCount);
    }

    @Test
    void tokenizesNumericFormats() {
        String code = "123 0x1A 0FFh 1010b 77o";
        List<Token> tokens = lexer.tokenize(code);

        List<Token> numbers = tokens.stream().filter(t -> t.is(TokenType.NUMBER)).toList();
        assertEquals(5, numbers.size());
        assertEquals("123", numbers.get(0).text());
        assertEquals("0x1A", numbers.get(1).text());
        assertEquals("0FFh", numbers.get(2).text());
        assertEquals("1010b", numbers.get(3).text());
        assertEquals("77o", numbers.get(4).text());
    }
}
