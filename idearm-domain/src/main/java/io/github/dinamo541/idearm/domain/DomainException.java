package io.github.dinamo541.idearm.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A failure the IDE explains to the user: a stable code, an English explanation for logs and developers, and the
 * values the explanation mentions (a path, a tool name).
 *
 * <p>The user interface shows the text its language bundle defines for {@code diagnostic.<code>}, filled with
 * {@link #arguments()}; the English message is the fallback for codes it does not translate.
 */
public final class DomainException extends RuntimeException {

    private final String code;
    private final List<String> arguments;

    public DomainException(String code, String message) {
        this(code, message, (Throwable) null);
    }

    public DomainException(String code, String message, Object... arguments) {
        this(code, message, (Throwable) null, arguments);
    }

    public DomainException(String code, String message, Throwable cause) {
        this(code, message, cause, new Object[0]);
    }

    public DomainException(String code, String message, Throwable cause, Object... arguments) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
        // A value may be missing (an exception without a message); it is shown as empty text.
        this.arguments = arguments == null ? List.of()
                : Arrays.stream(arguments).map(value -> Objects.toString(value, "")).toList();
    }

    public String code() {
        return code;
    }

    /** The values the message mentions, in the order the translated text uses them ({0}, {1}, ...). */
    public List<String> arguments() {
        return arguments;
    }
}
