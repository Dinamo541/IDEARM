package io.github.dinamo541.idearm.language.model;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Structured breakdown of a memory addressing expression (e.g. {@code [bx+si+4]} or {@code [eax*4+tabla]}).
 */
public record MemoryExpression(
        String rawInner,
        int bracketStartColumn,
        int bracketEndColumn,
        OperandComponent base,
        OperandComponent index,
        int scale,
        OperandComponent scaleComponent,
        OperandComponent displacement,
        Long displacementValue,
        String displacementSymbol,
        List<OperandComponent> components
) {
    public MemoryExpression {
        components = components != null ? List.copyOf(components) : List.of();
    }

    public boolean hasBase() {
        return base != null;
    }

    public boolean hasIndex() {
        return index != null;
    }

    public boolean hasScale() {
        return scale > 1;
    }

    public boolean hasDisplacement() {
        return displacement != null && !displacement.text().isBlank();
    }

    /**
     * Inferred address size in bits (16, 32, or 64).
     */
    public int addressSize() {
        if (base != null) {
            int s = registerBitSize(base.text());
            if (s > 0) return s;
        }
        if (index != null) {
            int s = registerBitSize(index.text());
            if (s > 0) return s;
        }
        return 16;
    }

    /**
     * Default hardware segment in real mode: SS if base register is BP/SP/EBP/ESP, DS otherwise.
     */
    public String defaultSegment() {
        if (base != null) {
            String b = base.text().toUpperCase(Locale.ROOT);
            if (b.equals("BP") || b.equals("EBP") || b.equals("RBP") ||
                b.equals("SP") || b.equals("ESP") || b.equals("RSP")) {
                return "SS";
            }
        }
        return "DS";
    }

    /**
     * Effective address mathematical formula string, e.g. "BX + SI + 4".
     */
    public String effectiveAddressFormula() {
        StringBuilder sb = new StringBuilder();
        if (base != null) {
            sb.append(base.text().toUpperCase(Locale.ROOT));
        }
        if (index != null) {
            if (!sb.isEmpty()) sb.append(" + ");
            if (scale > 1) {
                sb.append(index.text().toUpperCase(Locale.ROOT)).append(" * ").append(scale);
            } else {
                sb.append(index.text().toUpperCase(Locale.ROOT));
            }
        }
        if (displacement != null && !displacement.text().isBlank()) {
            String d = displacement.text().trim();
            if (d.startsWith("+") || d.startsWith("-")) {
                if (!sb.isEmpty()) {
                    sb.append(" ").append(d.charAt(0)).append(" ").append(d.substring(1).trim());
                } else {
                    sb.append(d);
                }
            } else {
                if (!sb.isEmpty()) sb.append(" + ").append(d);
                else sb.append(d);
            }
        }
        return sb.isEmpty() ? "0" : sb.toString();
    }

    /**
     * Real-mode 20-bit physical address formula: Segment * 16 + EffectiveAddress.
     */
    public String physicalAddressFormula(String segmentOverride) {
        String seg = (segmentOverride != null && !segmentOverride.isBlank())
                ? segmentOverride.toUpperCase(Locale.ROOT)
                : defaultSegment();
        return seg + " * 16 + (" + effectiveAddressFormula() + ")";
    }

    /**
     * Checks if this memory expression is a valid 16-bit addressing mode.
     * In 16-bit real mode:
     * - Only BX or BP can be base.
     * - Only SI or DI can be index.
     * - Scale must not be present (scale > 1 is 386+).
     * - Combinations: [BX], [BP], [SI], [DI], [BX+SI], [BX+DI], [BP+SI], [BP+DI], [disp].
     */
    public boolean isValid16Bit() {
        return getInvalid16BitReason("en") == null;
    }

    /**
     * Explains why this expression is not valid in 16-bit addressing, or null if valid.
     */
    public String getInvalid16BitReason(String language) {
        boolean es = "es".equalsIgnoreCase(language);
        if (scale > 1) {
            return es
                    ? "El escalado de registro (*" + scale + ") requiere direccionamiento de 32 o 64 bits (80386+)"
                    : "Register scaling (*" + scale + ") requires 32 or 64-bit addressing (80386+)";
        }

        // Check for invalid registers used as base/index
        for (OperandComponent c : components) {
            if (c.role() == OperandComponent.Role.BASE_REGISTER || c.role() == OperandComponent.Role.INDEX_REGISTER
                    || c.role() == OperandComponent.Role.REGISTER) {
                String reg = c.text().toUpperCase(Locale.ROOT);
                if (registerBitSize(reg) > 16) {
                    return es
                            ? "El registro " + reg + " es de " + registerBitSize(reg) + " bits; requiere direccionamiento de 32/64 bits"
                            : "Register " + reg + " is " + registerBitSize(reg) + "-bit; requires 32/64-bit addressing";
                }
                if (!reg.equals("BX") && !reg.equals("BP") && !reg.equals("SI") && !reg.equals("DI")) {
                    return es
                            ? reg + " no puede ser registro base ni índice con dirección de 16 bits (sólo BX, BP, SI, DI)"
                            : reg + " cannot be used as base or index register in 16-bit addressing (only BX, BP, SI, DI)";
                }
            }
        }

        // Check for two bases or two indices
        int baseCount = 0;
        int indexCount = 0;
        for (OperandComponent c : components) {
            String reg = c.text().toUpperCase(Locale.ROOT);
            if (reg.equals("BX") || reg.equals("BP")) {
                baseCount++;
            } else if (reg.equals("SI") || reg.equals("DI")) {
                indexCount++;
            }
        }

        if (baseCount > 1) {
            return es
                    ? "Dos registros base (no es válida la combinación de dos bases en 16 bits)"
                    : "Two base registers (combining two bases is not valid in 16-bit addressing)";
        }
        if (indexCount > 1) {
            return es
                    ? "Dos registros índice (no es válida la combinación de dos índices en 16 bits)"
                    : "Two index registers (combining two indices is not valid in 16-bit addressing)";
        }

        return null;
    }

    /** Whether a register of 32 or 64 bits takes part in the address, which makes it a 32/64-bit address. */
    public boolean usesWideRegisters() {
        for (OperandComponent c : components) {
            if (registerBitSize(c.text()) > 16) {
                return true;
            }
        }
        return false;
    }

    private static int registerBitSize(String name) {
        if (name == null) return 0;
        String u = name.toUpperCase(Locale.ROOT);
        if (u.matches("^(AL|AH|BL|BH|CL|CH|DL|DH|SIL|DIL|BPL|SPL|R[89]B|R1[0-5]B)$")) return 8;
        if (u.matches("^(AX|BX|CX|DX|SI|DI|BP|SP|IP|FLAGS|CS|DS|SS|ES|FS|GS|R[89]W|R1[0-5]W)$")) return 16;
        if (u.matches("^(EAX|EBX|ECX|EDX|ESI|EDI|EBP|ESP|EIP|EFLAGS|R[89]D|R1[0-5]D)$")) return 32;
        if (u.matches("^(RAX|RBX|RCX|RDX|RSI|RDI|RBP|RSP|R[89]|R1[0-5]|RIP|RFLAGS)$")) return 64;
        return 0;
    }
}
