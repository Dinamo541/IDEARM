package io.github.dinamo541.idearm.language.knowledge;

import java.util.Collections;
import java.util.List;

public record CompatibilityResult(
        Availability availability,
        List<String> reasons,
        Confidence confidence
) {
    public CompatibilityResult {
        reasons = reasons != null ? Collections.unmodifiableList(reasons) : List.of();
        confidence = confidence != null ? confidence : Confidence.CERTAIN;
    }

    public static CompatibilityResult available() {
        return new CompatibilityResult(Availability.AVAILABLE, List.of(), Confidence.CERTAIN);
    }

    public static CompatibilityResult unavailable(String reason, Confidence confidence) {
        return new CompatibilityResult(Availability.UNAVAILABLE, List.of(reason), confidence);
    }

    public static CompatibilityResult unknown(String reason) {
        return new CompatibilityResult(Availability.UNKNOWN, List.of(reason), Confidence.CERTAIN);
    }

    public static CompatibilityResult partial(String reason) {
        return new CompatibilityResult(Availability.PARTIAL, List.of(reason), Confidence.CERTAIN);
    }

    public Availability status() {
        return availability;
    }

    public String reason() {
        return reasons.isEmpty() ? null : reasons.get(0);
    }
}
