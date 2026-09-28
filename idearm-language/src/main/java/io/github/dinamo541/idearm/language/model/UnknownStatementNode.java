package io.github.dinamo541.idearm.language.model;

import java.util.Objects;

/**
 * AST node representing a statement whose first word the parser did not recognise, such as {@code MUV AX, 1}.
 *
 * <p>The parser used to drop such a line silently, which left no way for any rule to see a mistyped mnemonic. This
 * node only records "the parser did not recognise this word"; deciding whether it is a mistake belongs to the rules,
 * because the word may still be a macro or a symbol defined elsewhere in the project.
 *
 * @param word   the unrecognised word, as the user typed it
 * @param line   1-based line
 * @param column 1-based column of the first character
 * @param length how many characters the word spans, so an editor can underline exactly it
 */
public record UnknownStatementNode(
        String word,
        int line,
        int column,
        int length
) implements AstNode {
    public UnknownStatementNode {
        Objects.requireNonNull(word, "word cannot be null");
        if (length < 1) {
            throw new IllegalArgumentException("An unrecognised word spans at least one character.");
        }
    }
}
