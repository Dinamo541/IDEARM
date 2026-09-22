package io.github.dinamo541.idearm.domain.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The DOSBox dialects the IDE knows and the order it tries them in.
 *
 * <p>{@code [run] environment = "dosbox"} means "pick one": the classic DOSBox 0.74-3 comes first because it is the
 * emulator the course uses, then DOSBox-X, then Staging. A project may name one dialect; it is tried first and the
 * others remain as a fallback, so a project still runs on a machine that lacks the chosen one.
 */
public final class DosBoxDialects {

    /** The "any DOSBox" choice, and the registry role that resolves it. */
    public static final String AUTO = "dosbox";
    public static final String CLASSIC = "dosbox-0.74";
    public static final String X = "dosbox-x";
    public static final String STAGING = "dosbox-staging";

    /** The order "any DOSBox" is resolved in. */
    public static final List<String> PREFERENCE = List.of(CLASSIC, X, STAGING);

    private DosBoxDialects() {
    }

    /** Whether an environment id names DOSBox, either one dialect or the automatic choice. */
    public static boolean isDosBox(String id) {
        return id != null && id.toLowerCase(Locale.ROOT).startsWith(AUTO);
    }

    /**
     * The dialect ids to try, in order: the project's own choice when it names a known dialect, then the automatic
     * preference. A choice that is not a DOSBox dialect (a hand-edited value, "host") is ignored.
     */
    public static List<String> candidates(String projectChoice) {
        var order = new ArrayList<String>();
        if (projectChoice != null) {
            String choice = projectChoice.strip().toLowerCase(Locale.ROOT);
            if (PREFERENCE.contains(choice)) {
                order.add(choice);
            }
        }
        for (String dialect : PREFERENCE) {
            if (!order.contains(dialect)) {
                order.add(dialect);
            }
        }
        return List.copyOf(order);
    }
}
