package io.github.dinamo541.idearm.language.knowledge;

import io.github.dinamo541.idearm.language.catalog.FlagEffect;

/**
 * Flag effect specification for an instruction form, including conditional triggers.
 */
public record FlagEffectSpec(
        String flagId,
        FlagEffect effect,
        String condition,
        String note
) {
    public FlagEffectSpec(String flagId, FlagEffect effect) {
        this(flagId, effect, null, null);
    }
}
