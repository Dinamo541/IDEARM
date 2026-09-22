package io.github.dinamo541.idearm.app.i18n;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import java.util.List;

/**
 * A failure to show in the user's language: the code a bundle may translate ({@code diagnostic.<code>}), the
 * values its text mentions, and the English text shown when there is no translation.
 *
 * <p>A problem can be an argument of a {@link Message}; it is translated when the message is displayed.
 */
public record Problem(String code, List<String> arguments, String fallback) {

    public Problem {
        code = code == null ? "" : code;
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
        fallback = fallback == null ? "" : fallback;
    }

    public static Problem of(Diagnostic diagnostic) {
        return new Problem(diagnostic.code(), diagnostic.arguments(), diagnostic.message());
    }

    /** A domain failure is translated by its code; any other failure keeps its own message. */
    public static Problem of(Throwable failure) {
        String text = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        return failure instanceof DomainException domain
                ? new Problem(domain.code(), domain.arguments(), text)
                : new Problem("", List.of(), text);
    }
}
