package io.github.dinamo541.idearm.language.knowledge;

import java.util.Collections;
import java.util.List;

/**
 * Operand description for an instruction form (both explicit and implicit).
 */
public record Operand(
        int position,
        OperandKind kind,
        List<Integer> sizes,
        OperandAccess access,
        OperandRole role,
        String defaultSegment,
        OperandExtension extension,
        List<String> constraints,
        String note,
        String register
) {
    public Operand {
        sizes = sizes != null ? Collections.unmodifiableList(sizes) : List.of();
        constraints = constraints != null ? Collections.unmodifiableList(constraints) : List.of();
        access = access != null ? access : OperandAccess.READ;
        role = role != null ? role : OperandRole.EXPLICIT;
        extension = extension != null ? extension : OperandExtension.NONE;
    }

    public static Operand explicit(int position, OperandKind kind, List<Integer> sizes, OperandAccess access) {
        return new Operand(position, kind, sizes, access, OperandRole.EXPLICIT, null, OperandExtension.NONE, List.of(), null, null);
    }

    public static Operand implicit(String register, List<Integer> sizes, OperandAccess access, String note) {
        return new Operand(0, OperandKind.IMPLICIT_REG, sizes, access, OperandRole.IMPLICIT, null, OperandExtension.NONE, List.of(), note, register);
    }
}
