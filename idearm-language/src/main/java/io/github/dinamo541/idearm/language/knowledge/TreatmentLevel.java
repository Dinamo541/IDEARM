package io.github.dinamo541.idearm.language.knowledge;

/**
 * Editorial treatment level for an instruction in the Academic Assistant corpus (AA-P5-01).
 * Defined in Annex B §B.3.5 and Annex F §F.6.
 */
public enum TreatmentLevel {
    /**
     * All mandatory fields populated: typed forms, flags per form, progressive verified examples, sources.
     */
    COMPLETE("Complete"),

    /**
     * Identifier, mnemonic, family, summary, description, requirement, flags if applicable, source.
     */
    MINIMAL("Minimal"),

    /**
     * Outline entry with CPUID bit, requirement, recognizedBy, availability, and explicit disclaimer.
     */
    OUTLINE("Outline"),

    /**
     * Explicitly excluded from execution or classified as specific non-Intel dialect (e.g. GNU as movabs).
     */
    EXCLUDED("Excluded");

    private final String displayName;

    TreatmentLevel(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
