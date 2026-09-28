package io.github.dinamo541.idearm.language.catalog;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Educational category / type for x86 Assembly instructions.
 * Used for filtering and categorizing commands in academic assistance and tooltips.
 */
public enum InstructionCategory {
    DATA_TRANSFER("Data Transfer", "Transferencia de Datos"),
    ARITHMETIC("Arithmetic", "Aritmética"),
    LOGIC("Logic & Bitwise", "Lógica y Bits"),
    CONTROL_FLOW("Control Flow & Jumps", "Control de Flujo y Saltos"),
    STRINGS("String Operations", "Operaciones de Cadenas"),
    FLAGS_CONTROL("Flags & Processor Control", "Banderas y Control de CPU"),
    STACK_PROCEDURES("Stack & Procedures", "Pila y Procedimientos"),
    SYSTEM_INTERRUPTS("System & Interrupts", "Sistema e Interrupciones"),
    IO_PORTS("I/O & Ports", "Entrada/Salida y Puertos"),
    BIT_MANIPULATION("Bit Scanning & Manipulation", "Exploración y Manipulación de Bits"),
    FLOATING_POINT("Floating Point (x87)", "Coma Flotante (x87)");

    private final String displayNameEn;
    private final String displayNameEs;

    InstructionCategory(String displayNameEn, String displayNameEs) {
        this.displayNameEn = Objects.requireNonNull(displayNameEn, "displayNameEn cannot be null");
        this.displayNameEs = Objects.requireNonNull(displayNameEs, "displayNameEs cannot be null");
    }

    /**
     * Returns the localized display name according to the provided language code (e.g. "es", "es_MX", "en").
     * Safely defaults to English if the code is null or not recognized as Spanish.
     */
    public String displayName(String languageCode) {
        if (languageCode != null && languageCode.toLowerCase(Locale.ROOT).startsWith("es")) {
            return displayNameEs;
        }
        return displayNameEn;
    }

    public String displayNameEn() {
        return displayNameEn;
    }

    public String displayNameEs() {
        return displayNameEs;
    }

    /**
     * Returns all instructions belonging to this category.
     */
    public List<InstructionInfo> instructions() {
        return InstructionCatalog.getByCategory(this);
    }

    /**
     * Attempts to parse an InstructionCategory from its enum name or its display names in either language.
     */
    public static Optional<InstructionCategory> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String clean = text.trim();
        for (InstructionCategory cat : values()) {
            if (cat.name().equalsIgnoreCase(clean)
                    || cat.displayNameEn.equalsIgnoreCase(clean)
                    || cat.displayNameEs.equalsIgnoreCase(clean)) {
                return Optional.of(cat);
            }
        }
        return Optional.empty();
    }
}
