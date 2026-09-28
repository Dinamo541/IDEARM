package io.github.dinamo541.idearm.language.knowledge;

public record ExampleEntry(
        String id,
        String titleEn,
        String titleEs,
        ExampleKind kind,
        PedagogicalLevel level,
        String context,
        String initialState,
        String code,
        String finalState,
        VerificationStatus verification,
        String verifiedTool,
        String verifiedDate,
        String explanationEn,
        String explanationEs
) {
    public ExampleEntry {
        kind = kind != null ? kind : ExampleKind.FRAGMENT;
        level = level != null ? level : PedagogicalLevel.BASIC;
        verification = verification != null ? verification : VerificationStatus.NOT_RUN;
        initialState = initialState != null ? initialState : "";
        finalState = finalState != null ? finalState : "";
        explanationEn = explanationEn != null ? explanationEn : "";
        explanationEs = explanationEs != null ? explanationEs : "";
    }
}
