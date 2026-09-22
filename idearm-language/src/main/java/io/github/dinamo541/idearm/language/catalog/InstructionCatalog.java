package io.github.dinamo541.idearm.language.catalog;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Built-in educational knowledge base of x86 Assembly instructions.
 * Provides syntax forms, affected flags, minimum CPU requirements, and bilingual descriptions (English & Spanish).
 */
public final class InstructionCatalog {

    private static final Map<String, InstructionInfo> CATALOG = new LinkedHashMap<>();

    static {
        // --- DATA TRANSFER ---
        reg("MOV", "Move data", "Copiar datos",
                List.of("MOV reg, reg", "MOV reg, mem", "MOV mem, reg", "MOV reg, imm", "MOV mem, imm"),
                CpuLevel.CPU_8086, FlagSummary.none(),
                "Copies the source operand to the destination operand without modifying flags.",
                "Copia el operando origen al destino sin modificar banderas.",
                "mov ax, @data\nmov ds, ax");

        reg("PUSH", "Push word onto stack", "Apilar palabra en la pila",
                List.of("PUSH reg16", "PUSH mem16", "PUSH segreg", "PUSH imm16 (80186+)"),
                CpuLevel.CPU_8086, FlagSummary.none(),
                "Decrements SP by 2 and copies the operand onto the top of the stack.",
                "Decrementa SP en 2 y almacena el operando en el tope de la pila.",
                "push ax\npush dx");

        reg("POP", "Pop word from stack", "Desapilar palabra de la pila",
                List.of("POP reg16", "POP mem16", "POP segreg"),
                CpuLevel.CPU_8086, FlagSummary.none(),
                "Copies the word from the top of the stack to the destination and increments SP by 2.",
                "Recupera la palabra en el tope de la pila hacia el destino e incrementa SP en 2.",
                "pop dx\npop ax");

        reg("XCHG", "Exchange register/memory with register", "Intercambiar valores",
                List.of("XCHG reg, reg", "XCHG reg, mem"),
                CpuLevel.CPU_8086, FlagSummary.none(),
                "Swaps the contents of the two operands.",
                "Intercambia el contenido de los dos operandos sin usar registros auxiliares.",
                "xchg ax, bx");

        reg("LEA", "Load Effective Address", "Cargar dirección efectiva",
                List.of("LEA reg16, mem"),
                CpuLevel.CPU_8086, FlagSummary.none(),
                "Calculates the offset of the memory operand and stores it in the specified register.",
                "Calcula el desplazamiento (offset) del operando de memoria y lo guarda en el registro.",
                "lea dx, mensaje");

        reg("XLAT", "Table lookup translation", "Traducción por tabla",
                List.of("XLAT", "XLATB"),
                CpuLevel.CPU_8086, FlagSummary.none(),
                "Replaces AL with the byte at DS:[BX + AL]. Useful for character code conversions.",
                "Reemplaza AL con el byte en DS:[BX + AL]. Muy útil para tablas de traducción.",
                "mov bx, offset tabla\nmov al, 5\nxlat");

        reg("IN", "Input from port", "Entrada desde puerto",
                List.of("IN AL, imm8", "IN AL, DX", "IN AX, DX"),
                CpuLevel.CPU_8086, FlagSummary.none(),
                "Reads a byte or word from an I/O port into AL or AX.",
                "Lee un byte o palabra desde un puerto de E/S hacia AL o AX.",
                "in al, 60h");

        reg("OUT", "Output to port", "Salida hacia puerto",
                List.of("OUT imm8, AL", "OUT DX, AL", "OUT DX, AX"),
                CpuLevel.CPU_8086, FlagSummary.none(),
                "Writes a byte or word from AL or AX to an I/O port.",
                "Escribe un byte o palabra desde AL o AX hacia un puerto de E/S.",
                "out 61h, al");

        // --- ARITHMETIC ---
        reg("ADD", "Addition", "Suma",
                List.of("ADD reg, reg", "ADD reg, mem", "ADD mem, reg", "ADD reg, imm", "ADD mem, imm"),
                CpuLevel.CPU_8086, FlagSummary.standardArithmetic(),
                "Adds destination and source operands, storing result in destination.",
                "Suma los dos operandos y guarda el resultado en el destino.",
                "add ax, 5");

        reg("ADC", "Add with Carry", "Suma con acarreo",
                List.of("ADC reg, reg", "ADC reg, mem", "ADC reg, imm"),
                CpuLevel.CPU_8086, FlagSummary.standardArithmetic(),
                "Adds destination, source, and Carry Flag (CF). Useful for multi-word additions.",
                "Suma destino, origen y la bandera de acarreo (CF). Útil para aritmética de precisión múltiple.",
                "adc dx, 0");

        reg("SUB", "Subtraction", "Resta",
                List.of("SUB reg, reg", "SUB reg, mem", "SUB mem, reg", "SUB reg, imm", "SUB mem, imm"),
                CpuLevel.CPU_8086, FlagSummary.standardArithmetic(),
                "Subtracts source from destination, storing result in destination.",
                "Resta el operando origen del destino y guarda el resultado en el destino.",
                "sub cx, 1");

        reg("SBB", "Subtract with Borrow", "Resta con acarreo/préstamo",
                List.of("SBB reg, reg", "SBB reg, mem", "SBB reg, imm"),
                CpuLevel.CPU_8086, FlagSummary.standardArithmetic(),
                "Subtracts source and Carry Flag (CF) from destination.",
                "Resta el operando origen y la bandera de acarreo (CF) del destino.",
                "sbb dx, 0");

        reg("INC", "Increment by 1", "Incrementar en 1",
                List.of("INC reg", "INC mem"),
                CpuLevel.CPU_8086, new FlagSummary(
                FlagEffect.MODIFIED, FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED,
                FlagEffect.UNAFFECTED, FlagEffect.MODIFIED, FlagEffect.MODIFIED,
                FlagEffect.MODIFIED, FlagEffect.MODIFIED, FlagEffect.UNAFFECTED),
                "Adds 1 to destination. Note: Carry Flag (CF) is NOT affected.",
                "Suma 1 al operando. Ojo: La bandera de acarreo (CF) NO se modifica.",
                "inc si");

        reg("DEC", "Decrement by 1", "Decrementar en 1",
                List.of("DEC reg", "DEC mem"),
                CpuLevel.CPU_8086, new FlagSummary(
                FlagEffect.MODIFIED, FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED,
                FlagEffect.UNAFFECTED, FlagEffect.MODIFIED, FlagEffect.MODIFIED,
                FlagEffect.MODIFIED, FlagEffect.MODIFIED, FlagEffect.UNAFFECTED),
                "Subtracts 1 from destination. Note: Carry Flag (CF) is NOT affected.",
                "Resta 1 al operando. Ojo: La bandera de acarreo (CF) NO se modifica.",
                "dec cx");

        reg("NEG", "Two's complement negation", "Negación en complemento a dos",
                List.of("NEG reg", "NEG mem"),
                CpuLevel.CPU_8086, FlagSummary.standardArithmetic(),
                "Replaces operand with its two's complement (subtracts from 0).",
                "Reemplaza el operando por su complemento a dos (equivale a 0 - operando).",
                "neg ax");

        reg("CMP", "Compare two operands", "Comparar dos operandos",
                List.of("CMP reg, reg", "CMP reg, mem", "CMP reg, imm", "CMP mem, imm"),
                CpuLevel.CPU_8086, FlagSummary.standardArithmetic(),
                "Subtracts source from destination only to update flags (operands remain unchanged).",
                "Resta origen de destino sólo para actualizar banderas; no altera los operandos.",
                "cmp ax, bx\nje son_iguales");

        reg("MUL", "Unsigned multiplication", "Multiplicación sin signo",
                List.of("MUL reg8", "MUL mem8", "MUL reg16", "MUL mem16"),
                CpuLevel.CPU_8086, new FlagSummary(
                FlagEffect.MODIFIED, FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED,
                FlagEffect.UNAFFECTED, FlagEffect.UNDEFINED, FlagEffect.UNDEFINED,
                FlagEffect.UNDEFINED, FlagEffect.UNDEFINED, FlagEffect.MODIFIED),
                "Multiplies AL (byte) or AX (word) by operand. 8-bit result in AX; 16-bit result in DX:AX.",
                "Multiplica AL o AX por el operando. Resultado de 8 bits en AX; de 16 bits en DX:AX.",
                "mul bx");

        reg("IMUL", "Signed integer multiplication", "Multiplicación con signo",
                List.of("IMUL reg", "IMUL mem"),
                CpuLevel.CPU_8086, new FlagSummary(
                FlagEffect.MODIFIED, FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED,
                FlagEffect.UNAFFECTED, FlagEffect.UNDEFINED, FlagEffect.UNDEFINED,
                FlagEffect.UNDEFINED, FlagEffect.UNDEFINED, FlagEffect.MODIFIED),
                "Performs signed multiplication of AL or AX by operand.",
                "Realiza multiplicación con signo de AL o AX por el operando.",
                "imul bx");

        reg("DIV", "Unsigned division", "División sin signo",
                List.of("DIV reg8", "DIV mem8", "DIV reg16", "DIV mem16"),
                CpuLevel.CPU_8086, new FlagSummary(
                FlagEffect.UNDEFINED, FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED,
                FlagEffect.UNAFFECTED, FlagEffect.UNDEFINED, FlagEffect.UNDEFINED,
                FlagEffect.UNDEFINED, FlagEffect.UNDEFINED, FlagEffect.UNDEFINED),
                "Divides AX by 8-bit operand (AL=quotient, AH=remainder) or DX:AX by 16-bit operand (AX=quotient, DX=remainder).",
                "Divide AX entre 8 bits (AL=cociente, AH=resto) o DX:AX entre 16 bits (AX=cociente, DX=resto).",
                "div bx");

        reg("IDIV", "Signed division", "División con signo",
                List.of("IDIV reg", "IDIV mem"),
                CpuLevel.CPU_8086, new FlagSummary(
                FlagEffect.UNDEFINED, FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED,
                FlagEffect.UNAFFECTED, FlagEffect.UNDEFINED, FlagEffect.UNDEFINED,
                FlagEffect.UNDEFINED, FlagEffect.UNDEFINED, FlagEffect.UNDEFINED),
                "Signed division of AX or DX:AX.",
                "División con signo de AX o DX:AX.",
                "idiv bx");

        reg("CBW", "Convert Byte to Word", "Convertir Byte a Word (extensión de signo)",
                List.of("CBW"),
                CpuLevel.CPU_8086, FlagSummary.none(),
                "Sign-extends AL into AX (copies bit 7 of AL into all bits of AH).",
                "Extiende con signo AL hacia AX (copia el bit de signo de AL a AH).",
                "cbw");

        reg("CWD", "Convert Word to Doubleword", "Convertir Word a Doubleword (extensión de signo)",
                List.of("CWD"),
                CpuLevel.CPU_8086, FlagSummary.none(),
                "Sign-extends AX into DX:AX (copies bit 15 of AX into all bits of DX).",
                "Extiende con signo AX hacia DX:AX (copia el bit de signo de AX a DX).",
                "cwd");

        // --- LOGIC & BIT SHIFTS ---
        reg("AND", "Logical AND", "Operación lógica AND",
                List.of("AND reg, reg", "AND reg, mem", "AND reg, imm"),
                CpuLevel.CPU_8086, FlagSummary.standardLogic(),
                "Performs bitwise AND. Clears OF and CF; updates SF, ZF, and PF.",
                "Realiza operación AND bit a bit. Limpia OF y CF; actualiza SF, ZF y PF.",
                "and al, 0Fh");

        reg("OR", "Logical OR", "Operación lógica OR",
                List.of("OR reg, reg", "OR reg, mem", "OR reg, imm"),
                CpuLevel.CPU_8086, FlagSummary.standardLogic(),
                "Performs bitwise OR. Clears OF and CF; updates SF, ZF, and PF.",
                "Realiza operación OR bit a bit. Limpia OF y CF; actualiza SF, ZF y PF.",
                "or al, 20h");

        reg("XOR", "Logical Exclusive OR", "Operación lógica XOR",
                List.of("XOR reg, reg", "XOR reg, mem", "XOR reg, imm"),
                CpuLevel.CPU_8086, FlagSummary.standardLogic(),
                "Performs bitwise XOR. Commonly used with same register (e.g. XOR AX, AX) to clear to 0 in fewer bytes.",
                "Realiza XOR bit a bit. Muy común con el mismo registro (xor ax, ax) para poner a cero.",
                "xor ax, ax");

        reg("NOT", "Bitwise One's Complement", "Negación bit a bit (NOT)",
                List.of("NOT reg", "NOT mem"),
                CpuLevel.CPU_8086, FlagSummary.none(),
                "Inverts all bits of the operand. Flags are NOT affected.",
                "Invierte todos los bits del operando. No afecta banderas.",
                "not al");

        reg("TEST", "Logical Compare (AND without storing)", "Comparación lógica (AND sin guardar)",
                List.of("TEST reg, reg", "TEST reg, imm", "TEST mem, imm"),
                CpuLevel.CPU_8086, FlagSummary.standardLogic(),
                "Performs bitwise AND to set flags (SF, ZF, PF) without modifying the operands.",
                "Aplica AND bit a bit para actualizar banderas sin modificar los registros.",
                "test al, 1\njz es_par");

        reg("SHL", "Shift Left Logical", "Desplazamiento lógico a la izquierda",
                List.of("SHL reg, 1", "SHL reg, CL", "SHL reg, imm (80186+)"),
                CpuLevel.CPU_8086, FlagSummary.standardArithmetic(),
                "Shifts bits left, filling lowest bits with 0. Highest bit shifted out into CF.",
                "Desplaza bits a la izquierda rellenando con 0. El bit más alto sale a CF.",
                "shl ax, 1");

        reg("SHR", "Shift Right Logical", "Desplazamiento lógico a la derecha",
                List.of("SHR reg, 1", "SHR reg, CL", "SHR reg, imm (80186+)"),
                CpuLevel.CPU_8086, FlagSummary.standardArithmetic(),
                "Shifts bits right, filling highest bits with 0. Lowest bit shifted out into CF.",
                "Desplaza bits a la derecha rellenando con 0. El bit más bajo sale a CF.",
                "shr ax, 1");

        reg("SAR", "Shift Arithmetic Right", "Desplazamiento aritmético a la derecha",
                List.of("SAR reg, 1", "SAR reg, CL", "SAR reg, imm (80186+)"),
                CpuLevel.CPU_8086, FlagSummary.standardArithmetic(),
                "Shifts bits right preserving the sign bit (copies highest bit).",
                "Desplaza a la derecha preservando el bit de signo (división con signo entre 2).",
                "sar ax, 1");

        reg("ROL", "Rotate Left", "Rotación a la izquierda",
                List.of("ROL reg, 1", "ROL reg, CL"),
                CpuLevel.CPU_8086, new FlagSummary(
                FlagEffect.MODIFIED, FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED,
                FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED,
                FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED, FlagEffect.MODIFIED),
                "Rotates bits left circular fashion. Highest bit wraps around to bit 0 and CF.",
                "Rota bits a la izquierda en círculo. El bit 7/15 pasa al bit 0 y a CF.",
                "rol al, 1");

        reg("ROR", "Rotate Right", "Rotación a la derecha",
                List.of("ROR reg, 1", "ROR reg, CL"),
                CpuLevel.CPU_8086, new FlagSummary(
                FlagEffect.MODIFIED, FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED,
                FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED,
                FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED, FlagEffect.MODIFIED),
                "Rotates bits right circular fashion. Bit 0 wraps around to highest bit and CF.",
                "Rota bits a la derecha en círculo. El bit 0 pasa al bit superior y a CF.",
                "ror al, 1");

        // --- CONTROL FLOW & JUMPS ---
        reg("JMP", "Unconditional Jump", "Salto incondicional",
                List.of("JMP label", "JMP reg", "JMP mem"),
                CpuLevel.CPU_8086, FlagSummary.none(),
                "Transfers program execution unconditionally to target address.",
                "Transfiere la ejecución incondicionalmente a la dirección indicada.",
                "jmp bucle");

        reg("CALL", "Call Procedure", "Llamada a procedimiento",
                List.of("CALL proc_name", "CALL reg", "CALL mem"),
                CpuLevel.CPU_8086, FlagSummary.none(),
                "Pushes return address onto stack and transfers control to the procedure.",
                "Guarda la dirección de retorno en la pila y transfiere control al procedimiento.",
                "call imprimir_mensaje");

        reg("RET", "Return from Procedure", "Retorno de procedimiento",
                List.of("RET", "RET imm16"),
                CpuLevel.CPU_8086, FlagSummary.none(),
                "Pops return address from stack and transfers control back to caller.",
                "Recupera la dirección de retorno de la pila y vuelve a la rutina que llamó.",
                "ret");

        reg("JE", "Jump if Equal / Jump if Zero", "Salto si son iguales / Salto si es cero",
                List.of("JE label", "JZ label"),
                CpuLevel.CPU_8086, FlagSummary.none(),
                "Jumps to target label if ZF=1 (after CMP or arithmetic).",
                "Salta si la bandera Zero está activa (ZF=1).",
                "cmp ax, bx\nje iguales");

        reg("JNE", "Jump if Not Equal / Jump if Not Zero", "Salto si no son iguales / Salto si no es cero",
                List.of("JNE label", "JNZ label"),
                CpuLevel.CPU_8086, FlagSummary.none(),
                "Jumps to target label if ZF=0.",
                "Salta si la bandera Zero está inactiva (ZF=0).",
                "cmp cx, 0\njne continuar");

        reg("JA", "Jump if Above (unsigned >)", "Salto si mayor (sin signo)",
                List.of("JA label", "JNBE label"),
                CpuLevel.CPU_8086, FlagSummary.none(),
                "Jumps if CF=0 and ZF=0.",
                "Salta si no hay acarreo y el resultado no es cero.",
                "cmp ax, bx\nja es_mayor");

        reg("JB", "Jump if Below (unsigned <)", "Salto si menor (sin signo)",
                List.of("JB label", "JC label", "JNAE label"),
                CpuLevel.CPU_8086, FlagSummary.none(),
                "Jumps if CF=1.",
                "Salta si la bandera de acarreo está activa (CF=1).",
                "cmp ax, bx\njb es_menor");

        reg("JG", "Jump if Greater (signed >)", "Salto si mayor (con signo)",
                List.of("JG label", "JNLE label"),
                CpuLevel.CPU_8086, FlagSummary.none(),
                "Jumps if ZF=0 and SF=OF.",
                "Salta si no es cero y las banderas de signo y desbordamiento coinciden.",
                "cmp ax, bx\njg mayor_con_signo");

        reg("JL", "Jump if Less (signed <)", "Salto si menor (con signo)",
                List.of("JL label", "JNGE label"),
                CpuLevel.CPU_8086, FlagSummary.none(),
                "Jumps if SF != OF.",
                "Salta si la bandera de signo difiere de la de desbordamiento.",
                "cmp ax, bx\njl menor_con_signo");

        reg("JCXZ", "Jump if CX is Zero", "Salto si el registro CX es cero",
                List.of("JCXZ label"),
                CpuLevel.CPU_8086, FlagSummary.none(),
                "Jumps if CX register is 0 without altering flags. Great for guarding loops.",
                "Salta si CX vale 0 sin modificar banderas. Ideal para proteger bucles.",
                "jcxz fin_bucle");

        reg("LOOP", "Loop while CX > 0", "Bucle mientras CX > 0",
                List.of("LOOP label"),
                CpuLevel.CPU_8086, FlagSummary.none(),
                "Decrements CX by 1 and jumps if CX != 0.",
                "Decrementa CX en 1 y salta si CX aún no es cero.",
                "mov cx, 10\nbucle:\n  ; cuerpo\n  loop bucle");

        reg("INT", "Software Interrupt", "Interrupción por software",
                List.of("INT imm8"),
                CpuLevel.CPU_8086, new FlagSummary(
                FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED, FlagEffect.CLEARED,
                FlagEffect.CLEARED, FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED,
                FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED),
                "Generates software interrupt. Clears IF and TF. INT 21h invokes DOS API services.",
                "Genera una interrupción software. Desactiva IF y TF. INT 21h invoca la API de DOS.",
                "mov ah, 4Ch\nint 21h");

        reg("IRET", "Interrupt Return", "Retorno de interrupción",
                List.of("IRET"),
                CpuLevel.CPU_8086, FlagSummary.standardArithmetic(),
                "Pops IP, CS, and the FLAGS register from stack to restore pre-interrupt state.",
                "Recupera IP, CS y el registro FLAGS de la pila al volver de una rutina de interrupción.",
                "iret");

        // --- STRINGS ---
        reg("MOVSB", "Move byte from string to string", "Copiar byte de cadena",
                List.of("MOVSB", "REP MOVSB"),
                CpuLevel.CPU_8086, FlagSummary.none(),
                "Copies byte from DS:[SI] to ES:[DI]. Increments/decrements SI and DI based on DF.",
                "Copia un byte de DS:[SI] a ES:[DI], ajustando SI y DI según la bandera de dirección (DF).",
                "rep movsb");

        reg("LODSB", "Load byte from string into AL", "Cargar byte de cadena a AL",
                List.of("LODSB"),
                CpuLevel.CPU_8086, FlagSummary.none(),
                "Loads byte at DS:[SI] into AL and advances SI.",
                "Carga el byte en DS:[SI] hacia AL y avanza SI.",
                "lodsb");

        reg("STOSB", "Store AL byte into string", "Almacenar byte AL en cadena",
                List.of("STOSB", "REP STOSB"),
                CpuLevel.CPU_8086, FlagSummary.none(),
                "Stores byte in AL at ES:[DI] and advances DI.",
                "Escribe el byte de AL en ES:[DI] y avanza DI.",
                "rep stosb");

        reg("CMPSB", "Compare string bytes", "Comparar bytes de cadena",
                List.of("CMPSB", "REPE CMPSB"),
                CpuLevel.CPU_8086, FlagSummary.standardArithmetic(),
                "Compares DS:[SI] with ES:[DI] and updates flags.",
                "Compara DS:[SI] con ES:[DI] actualizando banderas para detectar igualdad.",
                "repe cmpsb");

        reg("SCASB", "Scan string for byte matching AL", "Buscar byte en cadena coincidente con AL",
                List.of("SCASB", "REPNE SCASB"),
                CpuLevel.CPU_8086, FlagSummary.standardArithmetic(),
                "Compares AL with ES:[DI] and advances DI.",
                "Compara AL con el byte en ES:[DI] y avanza DI.",
                "repne scasb");

        // --- FLAGS & PROCESSOR CONTROL ---
        reg("CLC", "Clear Carry Flag", "Limpiar bandera de acarreo (CF=0)",
                List.of("CLC"), CpuLevel.CPU_8086, new FlagSummary(
                FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED,
                FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED,
                FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED, FlagEffect.CLEARED),
                "Sets Carry Flag to 0.", "Pone la bandera de acarreo en 0.", "clc");

        reg("STC", "Set Carry Flag", "Activar bandera de acarreo (CF=1)",
                List.of("STC"), CpuLevel.CPU_8086, new FlagSummary(
                FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED,
                FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED,
                FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED, FlagEffect.SET),
                "Sets Carry Flag to 1.", "Pone la bandera de acarreo en 1.", "stc");

        reg("CLD", "Clear Direction Flag", "Limpiar bandera de dirección (DF=0, autoincremento)",
                List.of("CLD"), CpuLevel.CPU_8086, new FlagSummary(
                FlagEffect.UNAFFECTED, FlagEffect.CLEARED, FlagEffect.UNAFFECTED,
                FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED,
                FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED),
                "Sets Direction Flag to 0 so string operations auto-increment SI and DI.",
                "Pone DF en 0 para que las instrucciones de cadenas avancen de inicio a fin.", "cld");

        reg("STD", "Set Direction Flag", "Activar bandera de dirección (DF=1, autodecremento)",
                List.of("STD"), CpuLevel.CPU_8086, new FlagSummary(
                FlagEffect.UNAFFECTED, FlagEffect.SET, FlagEffect.UNAFFECTED,
                FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED,
                FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED),
                "Sets Direction Flag to 1 so string operations auto-decrement SI and DI.",
                "Pone DF en 1 para que las instrucciones de cadenas retrocedan.", "std");

        reg("CLI", "Clear Interrupt Flag", "Desactivar interrupciones (IF=0)",
                List.of("CLI"), CpuLevel.CPU_8086, new FlagSummary(
                FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED, FlagEffect.CLEARED,
                FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED,
                FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED),
                "Disables maskable hardware interrupts.", "Deshabilita interrupciones enmascarables.", "cli");

        reg("STI", "Set Interrupt Flag", "Activar interrupciones (IF=1)",
                List.of("STI"), CpuLevel.CPU_8086, new FlagSummary(
                FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED, FlagEffect.SET,
                FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED,
                FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED),
                "Enables maskable hardware interrupts.", "Habilita interrupciones de hardware.", "sti");

        reg("NOP", "No Operation", "Sin operación",
                List.of("NOP"), CpuLevel.CPU_8086, FlagSummary.none(),
                "Performs no action. One-byte instruction (0x90) useful for timing or padding.",
                "No hace nada. Ocupa un byte (0x90) y consume ciclos de reloj.", "nop");

        reg("HLT", "Halt Processor", "Detener procesador",
                List.of("HLT"), CpuLevel.CPU_8086, FlagSummary.none(),
                "Stops instruction execution until the next interrupt arrives.",
                "Detiene la CPU hasta la llegada de la próxima interrupción.", "hlt");

        // --- HIGHER CPU BASELINE INSTRUCTIONS ---
        reg("PUSHA", "Push All General Registers", "Apilar todos los registros generales",
                List.of("PUSHA"), CpuLevel.CPU_80186, FlagSummary.none(),
                "Pushes AX, CX, DX, BX, original SP, BP, SI, and DI onto stack. Requires 80186+.",
                "Apila AX, CX, DX, BX, SP original, BP, SI y DI en orden. Requiere CPU 80186+.", "pusha");

        reg("POPA", "Pop All General Registers", "Desapilar todos los registros generales",
                List.of("POPA"), CpuLevel.CPU_80186, FlagSummary.none(),
                "Restores all general registers previously pushed with PUSHA. Requires 80186+.",
                "Recupera todos los registros generales guardados con PUSHA. Requiere CPU 80186+.", "popa");

        reg("ENTER", "Make Stack Frame", "Crear marco de pila para procedimiento",
                List.of("ENTER imm16, imm8"), CpuLevel.CPU_80186, FlagSummary.none(),
                "Creates a stack frame for local variables. Requires 80186+.",
                "Crea un marco de pila reservando espacio para variables locales. Requiere 80186+.", "enter 4, 0");

        reg("LEAVE", "Release Stack Frame", "Liberar marco de pila",
                List.of("LEAVE"), CpuLevel.CPU_80186, FlagSummary.none(),
                "Releases stack frame set up by ENTER. Equivalent to MOV SP, BP / POP BP. Requires 80186+.",
                "Deshace el marco de pila creado por ENTER (equivale a MOV SP, BP / POP BP). Requiere 80186+.", "leave");

        reg("BOUND", "Check Array Index Against Bounds", "Verificar límites de arreglo",
                List.of("BOUND reg16, mem32"), CpuLevel.CPU_80186, FlagSummary.none(),
                "Checks that a signed array index in reg16 is within limits [mem16, mem16+2]. Generates INT 5 if out of bounds.",
                "Verifica que el índice en reg16 esté dentro de los límites en memoria. Genera INT 5 si está fuera de rango.", "bound ax, [bx]");

        reg("INS", "Input String from Port", "Entrada de cadena desde puerto",
                List.of("INS mem8, DX", "INS mem16, DX", "INSB", "INSW"), CpuLevel.CPU_80186, FlagSummary.none(),
                "Transfers a byte or word from the I/O port specified in DX into ES:[DI].",
                "Transfiere un byte o palabra desde el puerto en DX hacia ES:[DI].", "insb");

        reg("OUTS", "Output String to Port", "Salida de cadena hacia puerto",
                List.of("OUTS DX, mem8", "OUTS DX, mem16", "OUTSB", "OUTSW"), CpuLevel.CPU_80186, FlagSummary.none(),
                "Transfers a byte or word from DS:[SI] to the I/O port specified in DX.",
                "Transfiere un byte o palabra desde DS:[SI] hacia el puerto en DX.", "outsb");

        // --- 80286 INSTRUCTIONS ---
        reg("ARPL", "Adjust RPL Field of Selector", "Ajustar campo RPL de selector",
                List.of("ARPL reg16, reg16", "ARPL mem16, reg16"), CpuLevel.CPU_80286, FlagSummary.none(),
                "Adjusts the Requested Privilege Level (RPL) of a selector to ensure it is not higher than caller.",
                "Ajusta el nivel de privilegio requerido de un selector para proteger el sistema.", "arpl ax, bx");

        reg("LAR", "Load Access Rights Byte", "Cargar derechos de acceso",
                List.of("LAR reg16, reg16", "LAR reg16, mem16"), CpuLevel.CPU_80286, FlagSummary.none(),
                "Loads the access rights byte from a segment descriptor into the destination register.",
                "Carga los derechos de acceso del descriptor de segmento en el registro destino.", "lar ax, bx");

        reg("LSL", "Load Segment Limit", "Cargar límite de segmento",
                List.of("LSL reg16, reg16", "LSL reg16, mem16"), CpuLevel.CPU_80286, FlagSummary.none(),
                "Loads the segment limit from a segment descriptor into the destination register.",
                "Carga el límite de segmento desde un descriptor en el registro destino.", "lsl ax, bx");

        // --- 80386 INSTRUCTIONS ---
        reg("MOVSX", "Move with Sign-Extension", "Copiar con extensión de signo",
                List.of("MOVSX reg16, reg8", "MOVSX reg16, mem8", "MOVSX reg32, reg8", "MOVSX reg32, reg16"),
                CpuLevel.CPU_80386, FlagSummary.none(),
                "Copies source operand to destination and sign-extends the value to fill the destination.",
                "Copia el operando origen al destino extendiendo el bit de signo para llenar el registro.", "movsx ax, bl");

        reg("MOVZX", "Move with Zero-Extension", "Copiar con extensión de ceros",
                List.of("MOVZX reg16, reg8", "MOVZX reg16, mem8", "MOVZX reg32, reg8", "MOVZX reg32, reg16"),
                CpuLevel.CPU_80386, FlagSummary.none(),
                "Copies source operand to destination and zero-extends the value to fill the destination.",
                "Copia el operando origen al destino rellenando con ceros los bits superiores.", "movzx ax, bl");

        reg("BSF", "Bit Scan Forward", "Exploración de bits hacia adelante",
                List.of("BSF reg16, reg16", "BSF reg16, mem16"), CpuLevel.CPU_80386, FlagSummary.standardArithmetic(),
                "Scans source operand for first set bit (1) starting from bit 0 and stores index in destination.",
                "Busca el primer bit en 1 desde el bit 0 y guarda su posición en el registro destino.", "bsf ax, bx");

        reg("BSR", "Bit Scan Reverse", "Exploración de bits en reversa",
                List.of("BSR reg16, reg16", "BSR reg16, mem16"), CpuLevel.CPU_80386, FlagSummary.standardArithmetic(),
                "Scans source operand for first set bit (1) starting from MSB and stores index in destination.",
                "Busca el primer bit en 1 comenzando desde el bit más significativo (MSB).", "bsr ax, bx");

        reg("BT", "Bit Test", "Comprobación de bit",
                List.of("BT reg16, imm8", "BT reg16, reg16"), CpuLevel.CPU_80386, FlagSummary.none(),
                "Copies the specified bit of the first operand into the Carry Flag (CF).",
                "Copia el bit indicado del primer operando hacia la bandera de acarreo (CF).", "bt ax, 3");

        reg("BTC", "Bit Test and Complement", "Comprobar bit y complementar",
                List.of("BTC reg16, imm8", "BTC reg16, reg16"), CpuLevel.CPU_80386, FlagSummary.none(),
                "Saves the specified bit into CF and then inverts (complements) that bit in destination.",
                "Guarda el bit indicado en CF y luego lo invierte (0 a 1, o 1 a 0).", "btc ax, 3");

        reg("BTR", "Bit Test and Reset", "Comprobar bit y restablecer a cero",
                List.of("BTR reg16, imm8", "BTR reg16, reg16"), CpuLevel.CPU_80386, FlagSummary.none(),
                "Saves the specified bit into CF and then clears (resets) that bit in destination.",
                "Guarda el bit indicado en CF y luego lo pone en cero.", "btr ax, 3");

        reg("BTS", "Bit Test and Set", "Comprobar bit y activar a uno",
                List.of("BTS reg16, imm8", "BTS reg16, reg16"), CpuLevel.CPU_80386, FlagSummary.none(),
                "Saves the specified bit into CF and then sets that bit to 1 in destination.",
                "Guarda el bit indicado en CF y luego lo pone en 1.", "bts ax, 3");

        reg("CWDE", "Convert Word to Doubleword Extended", "Convertir palabra a doble palabra extendida",
                List.of("CWDE"), CpuLevel.CPU_80386, FlagSummary.none(),
                "Sign-extends AX into EAX. Requires 80386+.",
                "Extiende el signo de AX para llenar todo el registro de 32 bits EAX. Requiere 80386+.", "cwde");

        reg("CDQ", "Convert Doubleword to Quadword", "Convertir doble palabra a cuádruple palabra",
                List.of("CDQ"), CpuLevel.CPU_80386, FlagSummary.none(),
                "Sign-extends EAX into EDX:EAX. Requires 80386+.",
                "Extiende el signo de EAX sobre EDX:EAX (64 bits). Requiere 80386+.", "cdq");

        // --- 64-BIT X86-64 INSTRUCTIONS ---
        reg("SYSCALL", "Fast System Call", "Llamada rápida al sistema",
                List.of("SYSCALL"), CpuLevel.CPU_X86_64, FlagSummary.none(),
                "Fast transition to 64-bit OS kernel (replaces INT 80h / INT 21h in 64-bit mode).",
                "Transición rápida al kernel del sistema operativo en modo de 64 bits.", "mov rax, 60\nxor rdi, rdi\nsyscall");

        reg("SYSRET", "Return from Fast System Call", "Retorno de llamada rápida al sistema",
                List.of("SYSRET"), CpuLevel.CPU_X86_64, FlagSummary.none(),
                "Returns from 64-bit kernel mode to user space.",
                "Retorna desde modo kernel a modo usuario en 64 bits.", "sysret");

        reg("CQO", "Convert Quadword to Octaword", "Convertir cuádruple palabra a octapalabra",
                List.of("CQO"), CpuLevel.CPU_X86_64, FlagSummary.none(),
                "Sign-extends RAX into RDX:RAX (128 bits). Requires x86-64.",
                "Extiende el signo de RAX sobre RDX:RAX (128 bits). Requiere x86-64.", "cqo");

        reg("CDQE", "Convert Doubleword to Quadword Extended", "Convertir doble palabra a cuádruple extendida",
                List.of("CDQE"), CpuLevel.CPU_X86_64, FlagSummary.none(),
                "Sign-extends EAX into RAX. Requires x86-64.",
                "Extiende el signo de EAX a RAX (64 bits). Requiere x86-64.", "cdqe");

        reg("MOVABS", "Move 64-bit absolute value", "Mover valor absoluto de 64 bits",
                List.of("MOVABS reg64, imm64", "MOVABS reg64, [mem64]"), CpuLevel.CPU_X86_64, FlagSummary.none(),
                "Loads a 64-bit immediate or memory absolute address into a 64-bit register.",
                "Carga un valor inmediato de 64 bits o dirección de memoria absoluta en un registro de 64 bits.", "movabs rax, 0x1122334455667788");
    }

    private static void reg(String mnemonic, String summaryEn, String summaryEs,
                            List<String> syntaxVariants, CpuLevel minCpu, FlagSummary flags,
                            String descriptionEn, String descriptionEs, String example) {
        CATALOG.put(mnemonic.toUpperCase(Locale.ROOT), new InstructionInfo(
                mnemonic.toUpperCase(Locale.ROOT), summaryEn, summaryEs,
                syntaxVariants, minCpu, flags, descriptionEn, descriptionEs, example
        ));
    }

    public static Optional<InstructionInfo> find(String mnemonic) {
        if (mnemonic == null) return Optional.empty();
        return Optional.ofNullable(CATALOG.get(mnemonic.toUpperCase(Locale.ROOT)));
    }

    public static List<InstructionInfo> getAll() {
        return List.copyOf(CATALOG.values());
    }

    public static List<InstructionInfo> getForCpu(String targetCpu) {
        return CATALOG.values().stream()
                .filter(i -> i.minCpu().isSupportedOn(targetCpu))
                .toList();
    }

    public static List<InstructionInfo> searchStartingWith(String prefix) {
        if (prefix == null || prefix.isBlank()) return List.copyOf(CATALOG.values());
        String upper = prefix.toUpperCase(Locale.ROOT).trim();
        return CATALOG.values().stream()
                .filter(i -> i.mnemonic().startsWith(upper))
                .toList();
    }
}
