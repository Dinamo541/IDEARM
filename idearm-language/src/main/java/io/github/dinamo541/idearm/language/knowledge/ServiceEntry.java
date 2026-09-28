package io.github.dinamo541.idearm.language.knowledge;

import java.util.Collections;
import java.util.List;

/**
 * Operating system or BIOS interrupt service function (e.g. INT 21h AH=09h, INT 21h AH=0Ah, INT 10h AH=0Eh).
 */
public record ServiceEntry(
        String id,
        String environment,
        String vector,
        VectorStatus vectorStatus,
        ServiceSelector selector,
        String sinceVersion,
        List<ServiceInput> inputs,
        List<BufferField> bufferFormat,
        List<ServiceOutput> outputs,
        String flagsAndRegisters,
        List<ServiceError> errors,
        List<BackendAvailability> availability,
        String summaryEn,
        String summaryEs,
        String descriptionEn,
        String descriptionEs,
        String example,
        List<String> sources
) {
    public ServiceEntry {
        inputs = inputs != null ? Collections.unmodifiableList(inputs) : List.of();
        bufferFormat = bufferFormat != null ? Collections.unmodifiableList(bufferFormat) : List.of();
        outputs = outputs != null ? Collections.unmodifiableList(outputs) : List.of();
        errors = errors != null ? Collections.unmodifiableList(errors) : List.of();
        availability = availability != null ? Collections.unmodifiableList(availability) : List.of();
        sources = sources != null ? Collections.unmodifiableList(sources) : List.of();
        vectorStatus = vectorStatus != null ? vectorStatus : VectorStatus.DOS_KERNEL;
        summaryEn = summaryEn != null ? summaryEn : "";
        summaryEs = summaryEs != null ? summaryEs : "";
        descriptionEn = descriptionEn != null ? descriptionEn : "";
        descriptionEs = descriptionEs != null ? descriptionEs : "";
        example = example != null ? example : "";
    }
}
