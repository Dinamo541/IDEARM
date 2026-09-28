package io.github.dinamo541.idearm.language.knowledge;

import io.github.dinamo541.idearm.language.lexer.AssemblyLexer;
import io.github.dinamo541.idearm.language.lexer.Token;
import io.github.dinamo541.idearm.language.lexer.TokenType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DialectSyntaxTest {

    @Test
    void testSectionDirectiveDiscriminationByDialect() {
        AssemblyLexer dosLexer = new AssemblyLexer(Dialect.TASM);
        List<Token> dosTokens = dosLexer.tokenizeLine("section .text", 1);
        Token dosFirstToken = dosTokens.getFirst();
        assertNotEquals(TokenType.DIRECTIVE, dosFirstToken.type(),
                "In TASM/MASM (dos-exe-16), 'section' should NOT be recognized as a directive");
        assertEquals(TokenType.IDENTIFIER, dosFirstToken.type());

        AssemblyLexer nasmLexer = new AssemblyLexer(Dialect.NASM);
        List<Token> nasmTokens = nasmLexer.tokenizeLine("section .text", 1);
        Token nasmFirstToken = nasmTokens.getFirst();
        assertEquals(TokenType.DIRECTIVE, nasmFirstToken.type(),
                "In NASM (win-pe64-console), 'section' MUST be recognized as a directive");
    }

    @Test
    void testNasmMacroPreprocessorDirective() {
        AssemblyLexer nasmLexer = new AssemblyLexer(Dialect.NASM);
        List<Token> tokens = nasmLexer.tokenizeLine("%macro imprime 1", 1);
        Token first = tokens.getFirst();
        assertEquals(TokenType.DIRECTIVE, first.type(), "%macro should be tokenized as a directive");
        assertEquals("%macro", first.text());
    }

    @Test
    void testOffsetDocumentationEntity() {
        Corpus corpus = Corpus.get();
        Optional<SyntaxItem> optOffset = corpus.findSyntaxItem("syntax.masm.op.offset");
        assertTrue(optOffset.isPresent(), "Expected syntax item syntax.masm.op.offset to be present");

        SyntaxItem offset = optOffset.get();
        assertEquals("OFFSET", offset.token());
        assertEquals(SyntaxClass.OPERATOR, offset.syntaxClass());
        assertTrue(offset.assemblable());
        assertFalse(offset.notation());

        // Check dialect & version
        assertTrue(offset.dialects().stream().anyMatch(d -> d.dialect() == Dialect.MASM && "6.11".equals(d.version())));
        assertTrue(offset.dialects().stream().anyMatch(d -> d.dialect() == Dialect.TASM && "4.1".equals(d.version())));

        // Check description mentions LENGTH and SIZE as legacy forms
        assertTrue(offset.descriptionEn().contains("LENGTH") && offset.descriptionEn().contains("SIZE"),
                "English description should mention legacy LENGTH and SIZE operators");
        assertTrue(offset.descriptionEs().contains("LENGTH") && offset.descriptionEs().contains("SIZE"),
                "Spanish description should mention legacy LENGTH and SIZE operators");
    }

    @Test
    void testColonVariantsDiscrimination() {
        Corpus corpus = Corpus.get();
        List<SyntaxItem> colons = corpus.findSyntaxItemsByToken(":");
        assertTrue(colons.size() >= 4, "Expected at least 4 syntax items for colon ':' token");

        boolean hasLabel = colons.stream().anyMatch(c -> c.id().contains("label") && c.assemblable() && !c.notation());
        boolean hasSegOverride = colons.stream().anyMatch(c -> c.id().contains("segoverride") && c.assemblable());
        boolean hasLogicalAddr = colons.stream().anyMatch(c -> c.id().contains("logicaladdress") && c.notation() && !c.assemblable());
        boolean hasRegisterPair = colons.stream().anyMatch(c -> c.id().contains("registerpair") && c.notation() && !c.assemblable());

        assertTrue(hasLabel, "Colon label definition should be present and assemblable");
        assertTrue(hasSegOverride, "Colon segment override should be present and assemblable");
        assertTrue(hasLogicalAddr, "Colon logical address should be present as notation (not assemblable)");
        assertTrue(hasRegisterPair, "Colon register pair should be present as notation (not assemblable)");
    }

    @Test
    void testStrucAndEndstrucDirectives() {
        AssemblyLexer nasmLexer = new AssemblyLexer(Dialect.NASM);
        List<Token> tokens = nasmLexer.tokenizeLine("endstruc", 1);
        assertEquals(TokenType.DIRECTIVE, tokens.getFirst().type());

        AssemblyLexer tasmLexer = new AssemblyLexer(Dialect.TASM);
        List<Token> tasmTokens = tasmLexer.tokenizeLine("endstruc", 1);
        assertNotEquals(TokenType.DIRECTIVE, tasmTokens.getFirst().type(),
                "endstruc should not be a directive in TASM");
    }
}
