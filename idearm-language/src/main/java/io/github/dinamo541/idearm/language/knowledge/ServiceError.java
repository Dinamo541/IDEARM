package io.github.dinamo541.idearm.language.knowledge;

public record ServiceError(
        String code,
        String condition,
        String meaning
) {
}
