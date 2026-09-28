package io.github.dinamo541.idearm.language.knowledge;

public record BackendAvailability(
        String backend,
        AvailabilityStatus status,
        String caveat,
        String reason
) {
    public enum AvailabilityStatus {
        AVAILABLE,
        UNAVAILABLE,
        PARTIAL,
        UNKNOWN
    }

    public BackendAvailability(String backend, AvailabilityStatus status) {
        this(backend, status, null, null);
    }
}
