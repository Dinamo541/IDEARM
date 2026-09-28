package io.github.dinamo541.idearm.language.knowledge;

import java.util.Collections;
import java.util.List;

/**
 * A concrete syntax and encoding form of an instruction.
 */
public record InstructionForm(
        String id,
        List<SyntaxForm> syntax,
        List<Operand> operands,
        List<Operand> implicitOperands,
        Requirement requirement,
        List<FlagEffectSpec> flags,
        List<String> otherState,
        String operation,
        String operationPlain,
        List<ExceptionSpec> exceptions,
        List<BackendAvailability> availability,
        List<String> sources
) {
    public InstructionForm {
        syntax = syntax != null ? Collections.unmodifiableList(syntax) : List.of();
        operands = operands != null ? Collections.unmodifiableList(operands) : List.of();
        implicitOperands = implicitOperands != null ? Collections.unmodifiableList(implicitOperands) : List.of();
        flags = flags != null ? Collections.unmodifiableList(flags) : List.of();
        otherState = otherState != null ? Collections.unmodifiableList(otherState) : List.of();
        exceptions = exceptions != null ? Collections.unmodifiableList(exceptions) : List.of();
        availability = availability != null ? Collections.unmodifiableList(availability) : List.of();
        sources = sources != null ? Collections.unmodifiableList(sources) : List.of();
    }

    public InstructionForm(String id, List<SyntaxForm> syntax, List<Operand> operands,
                           List<Operand> implicitOperands, Requirement requirement,
                           List<FlagEffectSpec> flags, String operation,
                           String operationPlain, List<ExceptionSpec> exceptions,
                           List<BackendAvailability> availability, List<String> sources) {
        this(id, syntax, operands, implicitOperands, requirement, flags, List.of(), operation, operationPlain, exceptions, availability, sources);
    }
}
