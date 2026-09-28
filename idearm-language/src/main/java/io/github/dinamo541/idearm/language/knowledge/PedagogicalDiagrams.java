package io.github.dinamo541.idearm.language.knowledge;

import java.util.Locale;

/**
 * Generates pedagogical diagrams (AA-P6-04) for register hierarchies, stack frames,
 * and real-mode 20-bit segment address translation.
 *
 * <p>Pure JDK implementation (ADR-007) providing accessible ASCII art diagrams
 * and structured markdown representations compatible with light and dark themes.
 */
public final class PedagogicalDiagrams {

    private PedagogicalDiagrams() {}

    /**
     * Renders a hierarchical diagram of an accumulator or general-purpose register
     * and its sub-registers (64-bit RAX, 32-bit EAX, 16-bit AX, 8-bit AH/AL).
     */
    public static String renderRegisterHierarchy(String registerName) {
        String reg = registerName != null ? registerName.trim().toUpperCase(Locale.ROOT) : "RAX";
        char baseChar = 'A';
        if (reg.contains("B")) baseChar = 'B';
        else if (reg.contains("C")) baseChar = 'C';
        else if (reg.contains("D")) baseChar = 'D';

        char xChar = baseChar;
        return """
+-------------------------------------------------------------------------------+
| R%1$cX (64 bits, bits 63..0)                                                    |
+-----------------------------------------------+-------------------------------+
| Bits 63..32 (no accesibles como subregistro)  | E%1$cX (32 bits, bits 31..0)     |
+-----------------------------------------------+---------------+---------------+
                                                | Bits 31..16   | %1$cX (16 bits) |
                                                +---------------+-------+-------+
                                                                | %1$cH   | %1$cL   |
                                                                | 15..8 | 7..0  |
                                                                +-------+-------+

Reglas Arquitectónicas de Escritura:
1. Escribir en %1$cL altera los bits 7..0 de %1$cX/E%1$cX/R%1$cX sin modificar %1$cH ni los bits 63..8.
2. Escribir en %1$cH altera los bits 15..8 de %1$cX/E%1$cX/R%1$cX sin modificar %1$cL ni los bits 63..16.
3. Escribir en %1$cX altera los bits 15..0 de E%1$cX/R%1$cX sin modificar los bits 63..16.
4. Escribir en E%1$cX (en modo de 64 bits) pone automáticamente a CERO los 32 bits superiores (63..32) de R%1$cX.
""".formatted(xChar).trim();
    }

    /**
     * Renders an accessible stack frame diagram showing stack growth from high to low memory.
     */
    public static String renderStackFrame(boolean is32Bit) {
        if (is32Bit) {
            return """
Dirección Alta (+Memoria)
+------------------------------------------+
| Argumento 2: [EBP + 12]                  |
+------------------------------------------+
| Argumento 1: [EBP + 8]                   |
+------------------------------------------+
| Dirección de Retorno (EIP): [EBP + 4]    | <- Empujada automáticamente por CALL
+------------------------------------------+
| EBP Guardado del Invocador: [EBP]        | <- Apuntado por EBP tras 'mov ebp, esp'
+------------------------------------------+
| Variable Local 1: [EBP - 4]              | <- Reservada con 'sub esp, 8'
+------------------------------------------+
| Variable Local 2: [EBP - 8]              |
+------------------------------------------+
| Registros Preservados (EBX, ESI, EDI)    | <- [ESP] Cima actual de la pila
+------------------------------------------+
Dirección Baja (-Memoria, Crecimiento hacia abajo)

Secuencia Estándar de Prólogo y Epílogo:
Prólogo:
    push ebp             ; Guarda el marco anterior
    mov  ebp, esp        ; Establece el nuevo marco base
    sub  esp, 8          ; Reserva espacio para 2 variables locales (8 bytes)
Epílogo:
    mov  esp, ebp        ; Desasigna variables locales
    pop  ebp             ; Restaura el marco anterior
    ret  8               ; Retorna y libera los 8 bytes de argumentos (convención stdcall)
""".trim();
        } else {
            return """
Dirección Alta (+Memoria)
+------------------------------------------+
| Argumento 2: [BP + 6]                    |
+------------------------------------------+
| Argumento 1: [BP + 4]                    |
+------------------------------------------+
| Dirección de Retorno (IP): [BP + 2]      | <- Empujada automáticamente por CALL
+------------------------------------------+
| BP Guardado del Invocador: [BP]          | <- Apuntado por BP tras 'mov bp, sp'
+------------------------------------------+
| Variable Local 1: [BP - 2]               | <- Reservada con 'sub sp, 4'
+------------------------------------------+
| Variable Local 2: [BP - 4]               |
+------------------------------------------+
| Registros Preservados (BX, SI, DI)       | <- [SP] Cima actual de la pila
+------------------------------------------+
Dirección Baja (-Memoria, Crecimiento hacia abajo)

Secuencia Estándar en Modo Real de 16 Bits:
Prólogo:
    push bp              ; Guarda el marco anterior
    mov  bp, sp          ; Establece el nuevo marco base
    sub  sp, 4           ; Reserva espacio para variables locales (4 bytes)
Epílogo:
    mov  sp, bp          ; Desasigna variables locales
    pop  bp              ; Restaura el marco anterior
    ret  4               ; Retorna y libera los 4 bytes de argumentos
""".trim();
        }
    }

    /**
     * Calculates and renders the 20-bit physical address translation in 8086 real mode:
     * Segment:Offset -> (Segment * 16) + Offset.
     */
    public static String renderSegmentation20Bit(int segmentHex, int offsetHex) {
        int shiftedSegment = (segmentHex & 0xFFFF) << 4;
        int physicalAddress = shiftedSegment + (offsetHex & 0xFFFF);

        return """
Cálculo de Dirección Física en Modo Real (8086): %04Xh:%04Xh

  Segmento (%04Xh) * 16:      %05Xh   (Desplazado 4 bits a la izquierda)
+ Desplazamiento (Offset):  +  %04Xh
-------------------------------------
= Dirección Física de 20b:    %05Xh   (Rango direccionable: 00000h a FFFFFh, 1 MB)

Nota de Solapamiento de Segmentos:
Múltiples combinaciones Segmento:Desplazamiento pueden mapear a la misma dirección física.
Por ejemplo, %04Xh:%04Xh y %04Xh:%04Xh apuntan a ubicaciones físicas calculables con la misma fórmula.
""".formatted(
                segmentHex, offsetHex,
                segmentHex, shiftedSegment,
                offsetHex,
                physicalAddress,
                segmentHex, offsetHex,
                (physicalAddress >> 4), (physicalAddress & 0xF)
        ).trim();
    }
}
