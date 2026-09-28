package io.github.dinamo541.idearm.application.knowledge;

/**
 * Kind of knowledge entity returned by transversal search queries.
 */
public enum KnowledgeEntityKind {
    INSTRUCTION("Instrucción", "Instruction"),
    REGISTER("Registro / Bandera", "Register / Flag"),
    SYNTAX("Sintaxis y Signos", "Syntax & Punctuation"),
    SERVICE("Servicio de Interrupción", "Interrupt Service"),
    CONCEPT("Concepto", "Concept");

    private final String labelEs;
    private final String labelEn;

    KnowledgeEntityKind(String labelEs, String labelEn) {
        this.labelEs = labelEs;
        this.labelEn = labelEn;
    }

    public String label(String language) {
        return "es".equalsIgnoreCase(language) ? labelEs : labelEn;
    }
}
