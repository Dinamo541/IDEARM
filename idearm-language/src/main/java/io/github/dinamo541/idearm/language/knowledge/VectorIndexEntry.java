package io.github.dinamo541.idearm.language.knowledge;

/**
 * An entry in the architectural 00h-FFh interrupt vector table index.
 */
public record VectorIndexEntry(
        String vector,
        int vectorNumber,
        VectorStatus status,
        String titleEn,
        String titleEs,
        String descriptionEn,
        String descriptionEs,
        String defaultHandler,
        String primaryServiceId
) {
    public VectorIndexEntry {
        vector = vector != null ? vector : String.format("%02Xh", vectorNumber);
        status = status != null ? status : VectorStatus.RESERVED;
        titleEn = titleEn != null ? titleEn : "";
        titleEs = titleEs != null ? titleEs : "";
        descriptionEn = descriptionEn != null ? descriptionEn : "";
        descriptionEs = descriptionEs != null ? descriptionEs : "";
    }
}
