package io.github.dinamo541.idearm.app.i18n;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;

/**
 * User interface text. English is the default language and Spanish can be selected at runtime; text obtained
 * through {@link #text(String, Object...)} is a binding that updates as soon as the language changes.
 *
 * <p>Each language has its own bundle ({@code messages_en}, {@code messages_es}) and there is deliberately no base
 * bundle, so requesting English never falls back to the JVM default locale (ADR-006).
 */
public final class Localization {

    public static final Locale ENGLISH = Locale.ENGLISH;
    public static final Locale SPANISH = Locale.of("es");
    public static final List<Locale> SUPPORTED = List.of(ENGLISH, SPANISH);

    private static final String BUNDLE = "io.github.dinamo541.idearm.app.i18n.messages";

    private final ObjectProperty<Locale> locale = new SimpleObjectProperty<>(this, "locale", ENGLISH);

    public ObjectProperty<Locale> localeProperty() {
        return locale;
    }

    /** Text in the current language; arguments are formatted with {@link MessageFormat}. */
    public String get(String key, Object... args) {
        String pattern = ResourceBundle.getBundle(BUNDLE, locale.get()).getString(key);
        return args.length == 0 ? pattern : new MessageFormat(pattern, locale.get()).format(args);
    }

    /** Text that follows the current language. */
    public StringBinding text(String key, Object... args) {
        return Bindings.createStringBinding(() -> get(key, args), locale);
    }

    /** Text of a message produced by a view model; a {@link Problem} argument is translated as well. */
    public String get(Message message) {
        if (message == null) {
            return "";
        }
        Object[] arguments = message.arguments().stream()
                .map(argument -> argument instanceof Problem problem ? describe(problem) : argument)
                .toArray();
        return get(message.key(), arguments);
    }

    /**
     * A problem in the current language: the bundle text for {@code diagnostic.<code>} (or, for a file-name check,
     * the {@code explorer.name.*} text) filled with the problem's values. The original English text is used when
     * there is no translation, or when the translation needs a value the problem does not carry.
     */
    public String describe(Problem problem) {
        if (problem == null) {
            return "";
        }
        if (!problem.code().isBlank()) {
            ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE, locale.get());
            String key = bundle.containsKey("diagnostic." + problem.code()) ? "diagnostic." + problem.code()
                    : problem.code().startsWith("explorer.name.") ? problem.code() : null;
            if (key != null && bundle.containsKey(key)) {
                var format = new MessageFormat(bundle.getString(key), locale.get());
                if (format.getFormatsByArgumentIndex().length <= problem.arguments().size()) {
                    return format.format(problem.arguments().toArray());
                }
            }
        }
        return problem.fallback();
    }

    /** A problem's text that follows the current language. */
    public StringBinding text(Problem problem) {
        return Bindings.createStringBinding(() -> describe(problem), locale);
    }

    /** Text that follows both the current language and a message the view model replaces over time. */
    public StringBinding text(ObservableValue<Message> message) {
        return Bindings.createStringBinding(() -> get(message.getValue()), locale, message);
    }

    /** Each language is named in itself ("English", "Español") so users can find theirs in any UI language. */
    public static String nativeName(Locale language) {
        String name = language.getDisplayLanguage(language);
        return name.isEmpty()
                ? language.toLanguageTag()
                : name.substring(0, 1).toUpperCase(language) + name.substring(1);
    }
}
