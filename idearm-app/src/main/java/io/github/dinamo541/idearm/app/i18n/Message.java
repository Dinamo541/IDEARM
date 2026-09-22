package io.github.dinamo541.idearm.app.i18n;

import java.util.List;

/**
 * Text a view model wants to show, kept as a key and its arguments instead of a finished sentence.
 *
 * <p>View models never format text: a message resolved only when it is displayed follows the language the user
 * picks, even for a status written before the switch (ADR-006).
 */
public record Message(String key, List<Object> arguments) {

    public static final Message EMPTY = Message.of("status.empty");

    public Message {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("A message key is required.");
        }
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }

    public static Message of(String key, Object... arguments) {
        return new Message(key, arguments == null ? List.of() : List.of(arguments));
    }
}
