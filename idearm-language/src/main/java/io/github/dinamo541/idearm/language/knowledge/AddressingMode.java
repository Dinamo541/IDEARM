package io.github.dinamo541.idearm.language.knowledge;

import io.github.dinamo541.idearm.language.model.MemoryExpression;

import java.util.List;
import java.util.Locale;

/**
 * Educational representation of x86 addressing modes across 16, 32, and 64-bit architectures (AA-P4-03).
 * Provides the official combinations, pedagogical rationale, and validation engine.
 */
public record AddressingMode(
        String id,
        String nameEn,
        String nameEs,
        int addressSize, // 16, 32, 64
        String pattern,
        String example,
        String defaultSegment,
        String descriptionEn,
        String descriptionEs,
        boolean valid,
        String invalidReasonEn,
        String invalidReasonEs
) {

    public static final List<AddressingMode> MODES_16BIT_VALID = List.of(
            new AddressingMode(
                    "addr.16.bx_si",
                    "Based Indexed (BX + SI)",
                    "Basado con índice (BX + SI)",
                    16,
                    "[BX + SI + disp]",
                    "[bx+si+4]",
                    "DS",
                    "Base register BX with index SI and optional 8/16-bit displacement. Default segment is DS.",
                    "Registro base BX con índice SI y desplazamiento opcional de 8/16 bits. Segmento por omisión DS.",
                    true, null, null
            ),
            new AddressingMode(
                    "addr.16.bx_di",
                    "Based Indexed (BX + DI)",
                    "Basado con índice (BX + DI)",
                    16,
                    "[BX + DI + disp]",
                    "[bx+di+2]",
                    "DS",
                    "Base register BX with index DI and optional displacement. Default segment is DS.",
                    "Registro base BX con índice DI y desplazamiento opcional. Segmento por omisión DS.",
                    true, null, null
            ),
            new AddressingMode(
                    "addr.16.bp_si",
                    "Based Indexed (BP + SI)",
                    "Basado con índice (BP + SI)",
                    16,
                    "[BP + SI + disp]",
                    "[bp+si-4]",
                    "SS",
                    "Base register BP with index SI. Default hardware segment is SS (stack segment).",
                    "Registro base BP con índice SI. El segmento hardware por omisión es SS (segmento de pila).",
                    true, null, null
            ),
            new AddressingMode(
                    "addr.16.bp_di",
                    "Based Indexed (BP + DI)",
                    "Basado con índice (BP + DI)",
                    16,
                    "[BP + DI + disp]",
                    "[bp+di+8]",
                    "SS",
                    "Base register BP with index DI. Default hardware segment is SS (stack segment).",
                    "Registro base BP con índice DI. El segmento hardware por omisión es SS (segmento de pila).",
                    true, null, null
            ),
            new AddressingMode(
                    "addr.16.bx",
                    "Based (BX)",
                    "Basado (BX)",
                    16,
                    "[BX + disp]",
                    "[bx+4]",
                    "DS",
                    "Base register BX alone with optional displacement. Default segment is DS.",
                    "Registro base BX en solitario con desplazamiento opcional. Segmento por omisión DS.",
                    true, null, null
            ),
            new AddressingMode(
                    "addr.16.bp",
                    "Based (BP)",
                    "Basado (BP)",
                    16,
                    "[BP + disp]",
                    "[bp-2]",
                    "SS",
                    "Base register BP alone with optional displacement. Commonly used for stack frames. Default segment is SS.",
                    "Registro base BP en solitario con desplazamiento opcional. Usado habitualmente para marcos de pila. Segmento SS.",
                    true, null, null
            ),
            new AddressingMode(
                    "addr.16.si",
                    "Indexed (SI)",
                    "Indexado (SI)",
                    16,
                    "[SI + disp]",
                    "[si+10]",
                    "DS",
                    "Source Index register alone with optional displacement. Default segment is DS.",
                    "Registro Source Index en solitario con desplazamiento opcional. Segmento por omisión DS.",
                    true, null, null
            ),
            new AddressingMode(
                    "addr.16.di",
                    "Indexed (DI)",
                    "Indexado (DI)",
                    16,
                    "[DI + disp]",
                    "[di]",
                    "DS",
                    "Destination Index register alone with optional displacement. Default segment is DS.",
                    "Registro Destination Index en solitario con desplazamiento opcional. Segmento por omisión DS.",
                    true, null, null
            ),
            new AddressingMode(
                    "addr.16.disp",
                    "Direct (Displacement Only)",
                    "Directo (sólo desplazamiento)",
                    16,
                    "[disp]",
                    "[1234h]",
                    "DS",
                    "Direct memory address with constant 16-bit offset. Default segment is DS.",
                    "Dirección directa de memoria con desplazamiento constante de 16 bits. Segmento por omisión DS.",
                    true, null, null
            )
    );

    public static final List<AddressingMode> MODES_32_64BIT_VALID = List.of(
            new AddressingMode(
                    "addr.32.sib",
                    "Scaled Index with Base (SIB)",
                    "Índice escalado con base (SIB)",
                    32,
                    "[Base + Index * Scale + Disp]",
                    "[eax + ecx*4 + 8]",
                    "DS/SS",
                    "Any 32-bit GPR as base, any 32-bit GPR except ESP as index, scale 1/2/4/8, and 8/32-bit displacement.",
                    "Cualquier registro de 32 bits como base, cualquiera salvo ESP como índice, escala 1/2/4/8 y desplazamiento.",
                    true, null, null
            ),
            new AddressingMode(
                    "addr.32.scaled_index",
                    "Scaled Index without Base",
                    "Índice escalado sin base",
                    32,
                    "[Index * Scale + Disp]",
                    "[edx*8 + array]",
                    "DS",
                    "Scaled index register without base register. Useful for table/array lookup.",
                    "Registro de índice escalado sin registro base. Muy útil para tablas y vectores.",
                    true, null, null
            ),
            new AddressingMode(
                    "addr.64.rip_relative",
                    "RIP-Relative",
                    "Relativo a RIP",
                    64,
                    "[RIP + Disp32]",
                    "[rip + msg]",
                    "CS/DS",
                    "Position-independent code addressing relative to current Instruction Pointer.",
                    "Direccionamiento independiente de la posición relativo al contador de programa actual.",
                    true, null, null
            )
    );

    public static List<AddressingMode> get16BitValidModes() {
        return MODES_16BIT_VALID;
    }

    public static List<AddressingMode> get32And64BitValidModes() {
        return MODES_32_64BIT_VALID;
    }

    /**
     * Validation result representing whether an expression is valid for the target CPU architecture.
     */
    public record Validation(boolean isValid, String reasonEn, String reasonEs) {
        public static Validation valid() {
            return new Validation(true, null, null);
        }

        public static Validation invalid(String reasonEn, String reasonEs) {
            return new Validation(false, reasonEn, reasonEs);
        }
    }

    /**
     * Validates a memory expression against the target CPU level.
     * @param expr The parsed memory expression.
     * @param cpuLevel CPU generation level (0=8086, 1=186, 2=286, 3=386, 4=486, 5=Pentium, 7=x86-64).
     */
    public static Validation validate(MemoryExpression expr, int cpuLevel) {
        if (expr == null) return Validation.valid();

        // 16-bit addresses follow the 8086 rules on every CPU ([ax] and [bx+bp] are invalid on an 80386 too);
        // before the 80386 every address is a 16-bit one.
        if (cpuLevel < 3 || !expr.usesWideRegisters()) {
            String reasonEn = expr.getInvalid16BitReason("en");
            String reasonEs = expr.getInvalid16BitReason("es");
            if (reasonEn != null || reasonEs != null) {
                return Validation.invalid(reasonEn, reasonEs);
            }
            return Validation.valid();
        }

        // 32-bit / 64-bit architecture checks (80386+)
        if (expr.index() != null) {
            String idxReg = expr.index().text().toUpperCase(Locale.ROOT);
            if (idxReg.equals("ESP") || idxReg.equals("SP") || idxReg.equals("RSP")) {
                return Validation.invalid(
                        "Stack pointer (" + idxReg + ") cannot be used as index register in SIB addressing",
                        "El puntero de pila (" + idxReg + ") no puede utilizarse como registro índice en direccionamiento SIB"
                );
            }
        }

        return Validation.valid();
    }
}
