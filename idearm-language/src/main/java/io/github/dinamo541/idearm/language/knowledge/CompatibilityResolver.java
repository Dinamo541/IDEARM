package io.github.dinamo541.idearm.language.knowledge;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates semantic requirements against a project compatibility context.
 * Implements the ternary resolution algorithm defined in ADR-013 and Annex D §D.3.
 */
public final class CompatibilityResolver {

    private CompatibilityResolver() {
    }

    public static CompatibilityResult resolve(Requirement requirement, CompatibilityContext context) {
        if (requirement == null || context == null) {
            return CompatibilityResult.unknown("missing-context");
        }

        List<String> reasons = new ArrayList<>();

        // 1. Generation check
        if (context.generation() == CpuGeneration.UNKNOWN || requirement.minGeneration() == CpuGeneration.UNKNOWN) {
            return CompatibilityResult.unknown("gen-desconocida");
        }
        if (context.generation().level() < requirement.minGeneration().level()) {
            return CompatibilityResult.unavailable("gen-insuficiente (" + context.generation().displayName() + " < " + requirement.minGeneration().displayName() + ")", Confidence.CERTAIN);
        }

        // 2. Mode validity check
        if (requirement.invalidModes().contains(context.mode())) {
            return CompatibilityResult.unavailable("modo-invalido (" + context.mode() + " no admite esta instrucción)", Confidence.CERTAIN);
        }
        if (!requirement.validModes().isEmpty() && !requirement.validModes().contains(context.mode())) {
            return CompatibilityResult.unavailable("modo-no-valido", Confidence.CERTAIN);
        }

        // 3. Features / extensions check
        for (Feature f : requirement.features()) {
            if (context.features() == null) {
                return CompatibilityResult.unknown("extension-desconocida (" + f.name() + ")");
            }
            if (!context.features().contains(f)) {
                return CompatibilityResult.unavailable("extension-ausente (" + f.name() + ")", Confidence.CERTAIN);
            }
        }

        // 4. Privilege check
        if (requirement.privilege() == Privilege.CPL0 && context.privilege() == Privilege.USER) {
            return CompatibilityResult.unavailable("privilegio-insuficiente (requiere CPL0 / Ring 0)", Confidence.CERTAIN);
        }

        return CompatibilityResult.available();
    }

    public static CompatibilityResult resolve(InstructionForm form, CompatibilityContext context) {
        if (form == null) return CompatibilityResult.unknown("missing-form");
        CompatibilityResult res = resolve(form.requirement(), context);
        if (res.availability() == Availability.UNAVAILABLE) {
            return res;
        }
        if (context != null && context.backend() != null) {
            String b = context.backend().toLowerCase(java.util.Locale.ROOT);
            for (BackendAvailability ba : form.availability()) {
                if (ba.backend().equalsIgnoreCase(b) || ba.backend().equalsIgnoreCase("all")) {
                    if (ba.status() == BackendAvailability.AvailabilityStatus.UNAVAILABLE) {
                        return CompatibilityResult.partial("backend-no-soporta (" + ba.backend() + ": " + (ba.reason() != null ? ba.reason() : "no implementado") + ")");
                    }
                }
            }
        }
        return res;
    }

    public static CompatibilityResult resolve(InstructionEntry entry, CompatibilityContext context) {
        if (entry == null) return CompatibilityResult.unknown("missing-entry");
        if (entry.forms().isEmpty()) {
            return resolve(Requirement.forGeneration(entry.minCpuGen()), context);
        }
        boolean anyAvailable = false;
        boolean anyUnknown = false;
        String firstUnavailableReason = null;
        for (InstructionForm form : entry.forms()) {
            CompatibilityResult res = resolve(form, context);
            if (res.availability() == Availability.AVAILABLE || res.availability() == Availability.PARTIAL) {
                anyAvailable = true;
            } else if (res.availability() == Availability.UNKNOWN) {
                anyUnknown = true;
            } else if (res.availability() == Availability.UNAVAILABLE && firstUnavailableReason == null) {
                firstUnavailableReason = res.reason();
            }
        }
        if (anyAvailable) {
            return CompatibilityResult.available();
        }
        if (anyUnknown) {
            return CompatibilityResult.unknown("desconocido");
        }
        return CompatibilityResult.unavailable(firstUnavailableReason != null ? firstUnavailableReason : "no-forms-available", Confidence.CERTAIN);
    }
}
