package io.github.dinamo541.idearm.application.editor;

import io.github.dinamo541.idearm.language.catalog.InstructionCatalog;
import io.github.dinamo541.idearm.language.catalog.InstructionInfo;
import io.github.dinamo541.idearm.language.index.ProjectSymbolIndex;
import io.github.dinamo541.idearm.language.index.SymbolDefinition;
import io.github.dinamo541.idearm.language.index.SymbolKind;
import io.github.dinamo541.idearm.language.knowledge.*;
import io.github.dinamo541.idearm.language.lexer.NumericLiteral;

import java.util.Locale;
import java.util.Optional;

/**
 * Use case: Resolves rich educational hover content across the entire language:
 * instructions, directives, reserved keywords, registers, number conversions,
 * predefined symbols, interrupt services, and symbol definitions.
 *
 * <p>Instruction, directive, and register texts come from the bilingual knowledge corpus.
 * Numbers and symbols are described with facts and localized for the user's language (ADR-006).
 */
public final class QueryHover {

    @FunctionalInterface
    public interface VariableEvaluator {
        Optional<Long> evaluate(String filePath, int line, int byteSize);
    }

    public Optional<HoverInfo> execute(String word, String localeLanguage, ProjectSymbolIndex index) {
        return execute(word, localeLanguage, index, null);
    }

    public Optional<HoverInfo> execute(String word, String localeLanguage, ProjectSymbolIndex index, VariableEvaluator evaluator) {
        if (word == null || word.isBlank()) {
            return Optional.empty();
        }

        String trimmed = word.trim();
        boolean isSpanish = localeLanguage != null && localeLanguage.toLowerCase(Locale.ROOT).startsWith("es");

        // 1. A numeric literal starts with a digit, as the assembler reads it: 0Ah is a number, AH a register.
        Optional<HoverInfo> numHover = tryNumericConversion(trimmed, isSpanish);
        if (numHover.isPresent()) {
            return numHover;
        }

        // 2. Try project symbol index FIRST (AA-P1-05)
        Optional<SymbolDefinition> symOpt = (index != null) ? index.findDefinition(trimmed) : Optional.empty();
        Optional<InstructionInfo> instOpt = InstructionCatalog.find(trimmed);

        if (symOpt.isPresent()) {
            HoverInfo primary = symbolHover(symOpt.get(), evaluator);
            if (instOpt.isPresent()) {
                InstructionInfo inst = instOpt.get();
                String title = inst.mnemonic() + " — " + (isSpanish ? inst.summaryEs() : inst.summaryEn());
                String syntax = String.join("\n", inst.syntaxVariants());
                String desc = isSpanish ? inst.descriptionEs() : inst.descriptionEn();
                String flags = inst.flags().formatTable();
                String example = inst.example();
                HoverInfo sec = new HoverInfo(title, syntax, desc, flags, example, HoverKind.INSTRUCTION);

                return Optional.of(new HoverInfo(
                        primary.title(),
                        primary.syntax(),
                        primary.description(),
                        primary.flagsTable(),
                        primary.example(),
                        primary.kind(),
                        primary.number(),
                        primary.symbol(),
                        sec
                ));
            }
            Optional<HoverInfo> synOpt = trySyntaxItemHover(trimmed, isSpanish);
            if (synOpt.isPresent()) {
                return Optional.of(new HoverInfo(
                        primary.title(),
                        primary.syntax(),
                        primary.description(),
                        primary.flagsTable(),
                        primary.example(),
                        primary.kind(),
                        primary.number(),
                        primary.symbol(),
                        synOpt.get()
                ));
            }
            return Optional.of(primary);
        }

        // 3. Try instruction catalog if no user symbol matched
        if (instOpt.isPresent()) {
            InstructionInfo inst = instOpt.get();
            String title = inst.mnemonic() + " — " + (isSpanish ? inst.summaryEs() : inst.summaryEn());
            String syntax = String.join("\n", inst.syntaxVariants());
            String desc = isSpanish ? inst.descriptionEs() : inst.descriptionEn();
            String flags = inst.flags().formatTable();
            String example = inst.example();

            return Optional.of(new HoverInfo(title, syntax, desc, flags, example, HoverKind.INSTRUCTION));
        }

        // 4. Try special punctuation & operators (:, [, ], $) first for unified educational synthesis
        Optional<HoverInfo> punctHover = tryPunctuationHover(trimmed, isSpanish);
        if (punctHover.isPresent()) {
            return punctHover;
        }

        // 5. Try syntax items: directives, operators, predefined symbols, keywords
        Optional<HoverInfo> synHover = trySyntaxItemHover(trimmed, isSpanish);
        if (synHover.isPresent()) {
            return synHover;
        }

        // 6. Try register catalog (AX, AH, AL, BX, CX, DX, SI, DI, BP, SP, CS, DS, ES, SS, FLAGS, EAX, RAX...)
        Optional<HoverInfo> regHover = tryRegisterHover(trimmed, isSpanish);
        if (regHover.isPresent()) {
            return regHover;
        }

        // 7. Try concepts & fundamentals from concepts.json
        Optional<HoverInfo> conceptHover = tryConceptHover(trimmed, isSpanish);
        if (conceptHover.isPresent()) {
            return conceptHover;
        }

        return Optional.empty();
    }

    private static HoverInfo symbolHover(SymbolDefinition sym, VariableEvaluator evaluator) {
        Long live = null;
        if (evaluator != null && sym.kind() == SymbolKind.VARIABLE) {
            int size = isByteData(sym.signature()) ? 1 : 2;
            live = evaluator.evaluate(sym.filePath(), sym.line(), size).orElse(null);
        }
        var description = new StringBuilder("Defined in ").append(sym.filePath()).append(':').append(sym.line());
        if (live != null) {
            description.append("\n\nLive memory value: 0x").append(Long.toHexString(live).toUpperCase(Locale.ROOT))
                    .append(" (").append(live).append(')');
        }
        String syntax = sym.signature() != null ? sym.signature() : sym.name();
        return new HoverInfo(sym.kind().name() + " " + sym.name(), syntax, description.toString(), null, null,
                HoverKind.SYMBOL_INFO, null,
                new HoverInfo.SymbolFacts(sym.name(), sym.kind().name(), sym.filePath(), sym.line(), live));
    }

    /** A variable defined with DB holds bytes; the signature reads "name DB ...". */
    private static boolean isByteData(String signature) {
        if (signature == null) {
            return false;
        }
        String[] words = signature.trim().split("\\s+");
        return words.length >= 2 && words[1].equalsIgnoreCase("DB");
    }

    private static Optional<HoverInfo> tryNumericConversion(String text, boolean isSpanish) {
        Long val = parseNumber(text);
        if (val == null) {
            return Optional.empty();
        }

        long n = val;
        String hex = "0x" + Long.toHexString(n).toUpperCase(Locale.ROOT);
        String dec = Long.toUnsignedString(n);
        String bin = formatBinary(n);

        StringBuilder desc = new StringBuilder();
        desc.append("Base Conversion:\n");
        desc.append("  Decimal:     ").append(dec).append("\n");
        desc.append("  Hexadecimal: ").append(hex).append("\n");
        desc.append("  Binary:      ").append(bin);

        if (n >= 32 && n <= 126) {
            desc.append("\n  ASCII:       '").append((char) n).append("'");
        }

        HoverInfo primary = new HoverInfo("Numeric Literal: " + text, hex + " = " + dec, desc.toString(), null, null,
                HoverKind.NUMBER_CONVERSION, n, null);

        // If it's a prominent interrupt vector, attach service documentation as secondary card
        if (n == 0x21) {
            String secTitle = isSpanish
                    ? "INT 21h — Despachador de Servicios del Sistema MS-DOS"
                    : "INT 21h — MS-DOS System Services Dispatcher";
            String secSyntax = "mov ah, <servicio>\nint 21h";
            String secDesc = isSpanish
                    ? "Vector de interrupción principal de MS-DOS. La función se especifica en AH:\n• AH=01h: Leer carácter con eco (AL)\n• AH=02h: Escribir carácter (en DL)\n• AH=09h: Escribir cadena terminada en '$' (en DS:DX)\n• AH=0Ah: Entrada de cadena por búfer (en DS:DX)\n• AH=4Ch: Terminar programa con código de retorno (en AL)"
                    : "Primary MS-DOS interrupt vector. Service function loaded in AH:\n• AH=01h: Read character with echo (AL)\n• AH=02h: Write character (in DL)\n• AH=09h: Write '$'-terminated string (in DS:DX)\n• AH=0Ah: Buffered string input (in DS:DX)\n• AH=4Ch: Terminate program with return code (in AL)";
            HoverInfo sec = new HoverInfo(secTitle, secSyntax, secDesc, null, "mov ah, 09h\nmov dx, OFFSET msg\nint 21h", HoverKind.INTERRUPT_SERVICE);
            return Optional.of(new HoverInfo(primary.title(), primary.syntax(), primary.description(),
                    primary.flagsTable(), primary.example(), primary.kind(), primary.number(), primary.symbol(), sec));
        } else if (n == 0x10) {
            String secTitle = isSpanish
                    ? "INT 10h — Servicios de Video BIOS"
                    : "INT 10h — BIOS Video Services";
            String secSyntax = "mov ah, <servicio>\nint 10h";
            String secDesc = isSpanish
                    ? "Servicios de pantalla y video de la BIOS:\n• AH=0Eh: Salida de carácter teletipo (en AL)\n• AH=00h: Fijar modo de video (en AL, ej. 03h para texto 80x25)\n• AH=0Fh: Obtener modo de video actual"
                    : "BIOS screen and display services:\n• AH=0Eh: Teletype character output (in AL)\n• AH=00h: Set video mode (in AL, e.g. 03h for 80x25 text)\n• AH=0Fh: Get current video mode";
            HoverInfo sec = new HoverInfo(secTitle, secSyntax, secDesc, null, "mov ah, 0Eh\nmov al, 'A'\nint 10h", HoverKind.INTERRUPT_SERVICE);
            return Optional.of(new HoverInfo(primary.title(), primary.syntax(), primary.description(),
                    primary.flagsTable(), primary.example(), primary.kind(), primary.number(), primary.symbol(), sec));
        } else if (n == 0x16) {
            String secTitle = isSpanish
                    ? "INT 16h — Servicios de Teclado BIOS"
                    : "INT 16h — BIOS Keyboard Services";
            String secSyntax = "mov ah, <servicio>\nint 16h";
            String secDesc = isSpanish
                    ? "Servicios de teclado directo de la BIOS:\n• AH=00h: Leer pulsación de tecla bloqueante (AL=ASCII, AH=scan code)\n• AH=01h: Comprobar buffer de teclado (ZF=1 si está vacío)"
                    : "BIOS keyboard services:\n• AH=00h: Blocking read keystroke (AL=ASCII, AH=scan code)\n• AH=01h: Check keyboard buffer (ZF=1 if empty)";
            HoverInfo sec = new HoverInfo(secTitle, secSyntax, secDesc, null, "mov ah, 00h\nint 16h", HoverKind.INTERRUPT_SERVICE);
            return Optional.of(new HoverInfo(primary.title(), primary.syntax(), primary.description(),
                    primary.flagsTable(), primary.example(), primary.kind(), primary.number(), primary.symbol(), sec));
        }

        return Optional.of(primary);
    }

    private static Optional<HoverInfo> trySyntaxItemHover(String token, boolean isSpanish) {
        Corpus corpus = Corpus.get();
        Optional<SyntaxItem> itemOpt = corpus.findSyntaxItem(token);
        if (itemOpt.isEmpty()) {
            if (!token.startsWith(".")) {
                itemOpt = corpus.findSyntaxItem("." + token);
            }
        }
        if (itemOpt.isEmpty() && token.startsWith(".")) {
            itemOpt = corpus.findSyntaxItem(token.substring(1));
        }
        if (itemOpt.isEmpty()) {
            if (!token.startsWith("%")) {
                itemOpt = corpus.findSyntaxItem("%" + token);
            }
        }
        if (itemOpt.isEmpty() && token.startsWith("%")) {
            itemOpt = corpus.findSyntaxItem(token.substring(1));
        }
        if (itemOpt.isEmpty()) {
            return Optional.empty();
        }

        SyntaxItem item = itemOpt.get();
        HoverKind kind = switch (item.syntaxClass()) {
            case DIRECTIVE -> HoverKind.DIRECTIVE;
            case OPERATOR -> HoverKind.OPERATOR;
            case PREDEFINED_SYMBOL -> HoverKind.PREDEFINED_SYMBOL;
            case PREPROCESSOR -> HoverKind.DIRECTIVE;
            default -> HoverKind.KEYWORD;
        };

        String title = item.token() + " — " + (isSpanish ? item.summaryEs() : item.summaryEn());
        String syntax = (item.example() != null && !item.example().isBlank())
                ? item.example().lines().findFirst().orElse(item.token())
                : item.token();
        String desc = isSpanish ? item.descriptionEs() : item.descriptionEn();
        String example = item.example();

        return Optional.of(new HoverInfo(title, syntax, desc, null, example, kind));
    }

    private static Optional<HoverInfo> tryRegisterHover(String token, boolean isSpanish) {
        Corpus corpus = Corpus.get();
        String upper = token.trim().toUpperCase(Locale.ROOT);
        Optional<RegisterEntry> regOpt = corpus.findRegister(upper);
        if (regOpt.isEmpty()) {
            return Optional.empty();
        }

        RegisterEntry reg = regOpt.get();
        RegisterView view = reg.views().stream()
                .filter(v -> v.name().equalsIgnoreCase(upper))
                .findFirst()
                .orElse(null);

        String regName = view != null ? view.name() : reg.name();
        int bitSize = view != null ? view.sizeBits() : reg.sizeBits();

        String title = regName + " — " + (isSpanish ? registerSummaryEs(regName, reg, view) : registerSummaryEn(regName, reg, view));
        String syntax = formatRegisterFamily(regName, reg, corpus, isSpanish);

        StringBuilder desc = new StringBuilder();
        if (view != null && !view.name().equalsIgnoreCase(reg.name())) {
            if (isSpanish) {
                desc.append("Subregistro de ").append(bitSize).append(" bits perteneciente al contenedor ")
                        .append(reg.name()).append(" (desplazamiento bit ").append(view.offsetBits()).append(").\n\n");
            } else {
                desc.append(bitSize).append("-bit subregister of container ")
                        .append(reg.name()).append(" (bit offset ").append(view.offsetBits()).append(").\n\n");
            }
        } else if (reg.parentId() != null && !reg.parentId().isBlank()) {
            String parentName = reg.parentId().replace("x86.reg.", "").toUpperCase(Locale.ROOT);
            if (isSpanish) {
                desc.append("Subregistro de ").append(bitSize).append(" bits perteneciente al contenedor ")
                        .append(parentName).append(".\n\n");
            } else {
                desc.append(bitSize).append("-bit subregister of container ")
                        .append(parentName).append(".\n\n");
            }
        }
        Locale textLocale = isSpanish ? Locale.forLanguageTag("es") : Locale.ENGLISH;
        desc.append(corpus.localize(reg.conventionalUse(), textLocale));
        if (reg.writeSemantics() != null && !reg.writeSemantics().isBlank()) {
            desc.append("\n\n").append(isSpanish ? "Semántica de escritura: " : "Write semantics: ").append(corpus.localize(reg.writeSemantics(), textLocale));
        }

        String flagsTable = null;
        if (regName.contains("FLAG")) {
            flagsTable = formatFlagsRegisterTable(reg);
        }

        String example = canonicalRegisterExample(regName);
        return Optional.of(new HoverInfo(title, syntax, desc.toString(), flagsTable, example, HoverKind.REGISTER));
    }

    private static Optional<HoverInfo> tryPunctuationHover(String token, boolean isSpanish) {
        if (":".equals(token)) {
            String title = isSpanish
                    ? ": — Operador de dos puntos en ensamblador x86"
                    : ": — Colon operator in x86 Assembly";
            String syntax = "etiqueta:  |  ES:[DI]  |  CS:IP (notación)  |  DX:AX (pareja)";
            String desc = isSpanish
                    ? "En ensamblador x86, los dos puntos cumplen varias funciones según el contexto:\n"
                    + "1. Terminador de etiqueta: 'bucle:' define un destino de salto o llamada.\n"
                    + "2. Prefijo de segmento: 'ES:[DI]' especifica un registro de segmento explícito en memoria.\n"
                    + "3. Notación explicativa: 'CS:IP' o '1000:0100' denota una dirección lógica segmento:desplazamiento.\n"
                    + "4. Pareja de registros: 'DX:AX' denota un valor concatenado de doble ancho (p. ej. en MUL/DIV)."
                    : "In x86 Assembly, the colon serves multiple distinct roles depending on context:\n"
                    + "1. Label terminator: 'loop:' defines a jump or call target.\n"
                    + "2. Segment override: 'ES:[DI]' specifies an explicit segment register in memory.\n"
                    + "3. Explanatory notation: 'CS:IP' or '1000:0100' denotes a logical segment:offset address.\n"
                    + "4. Register pair: 'DX:AX' denotes a concatenated double-width value (e.g. in MUL/DIV).";
            String example = "bucle:\n    mov ax, es:[di]\n    loop bucle";
            return Optional.of(new HoverInfo(title, syntax, desc, null, example, HoverKind.KEYWORD));
        }

        if ("[".equals(token) || "]".equals(token) || "[]".equals(token)) {
            String title = isSpanish
                    ? "[ ] — Corchetes de direccionamiento indirecto / memoria"
                    : "[ ] — Memory indirection brackets";
            String syntax = "[ registro_base + registro_indice + desplazamiento ]";
            String desc = isSpanish
                    ? "Los corchetes indican acceso al contenido almacenado en memoria en la dirección efectiva calculada.\n"
                    + "• En modo real de 16 bits, sólo BX, BP, SI y DI son válidos dentro de corchetes.\n"
                    + "• BX y BP actúan como registros base (BX usa DS por omisión, BP usa SS).\n"
                    + "• SI y DI actúan como registros índice.\n"
                    + "• Combinaciones válidas: [bx], [bp], [si], [di], [bx+si], [bx+di], [bp+si], [bp+di], más desplazamiento opcional."
                    : "Square brackets indicate memory dereference at the calculated effective address.\n"
                    + "• In 16-bit real mode, only BX, BP, SI, and DI can be used inside brackets.\n"
                    + "• BX and BP act as base registers (BX defaults to DS, BP defaults to SS).\n"
                    + "• SI and DI act as index registers.\n"
                    + "• Valid 16-bit forms: [bx], [bp], [si], [di], [bx+si], [bx+di], [bp+si], [bp+di], plus optional displacement.";
            String example = "mov ax, [bx+si+4]\nmov [di], al";
            return Optional.of(new HoverInfo(title, syntax, desc, null, example, HoverKind.OPERATOR));
        }

        if ("$".equals(token)) {
            String title = isSpanish
                    ? "$ — Contador de posición actual / Terminador de cadena DOS"
                    : "$ — Current location counter / DOS string terminator";
            String syntax = "longitud EQU $ - mensaje  |  msg DB 'Hola$', 0";
            String desc = isSpanish
                    ? "El símbolo '$' tiene dos usos fundamentales:\n"
                    + "1. En expresiones de ensamblado: representa el desplazamiento actual en el segmento. '$ - etiqueta' calcula la longitud en bytes.\n"
                    + "2. En servicios MS-DOS (INT 21h, AH=09h): actúa como carácter centinela de fin de cadena (ASCII 24h)."
                    : "The '$' symbol serves two primary roles:\n"
                    + "1. In assembly expressions: represents the current segment offset. '$ - label' calculates length in bytes.\n"
                    + "2. In MS-DOS services (INT 21h, AH=09h): serves as the string termination sentinel (ASCII 24h).";
            String example = "mensaje DB 'Hola Mundo$', 0\nlongitud EQU $ - mensaje";
            return Optional.of(new HoverInfo(title, syntax, desc, null, example, HoverKind.PREDEFINED_SYMBOL));
        }

        return Optional.empty();
    }

    private static Optional<HoverInfo> tryConceptHover(String token, boolean isSpanish) {
        Corpus corpus = Corpus.get();
        for (ConceptEntry c : corpus.getAllConcepts()) {
            if (c.id().equalsIgnoreCase(token) || c.id().endsWith("." + token.toLowerCase(Locale.ROOT))) {
                String title = (isSpanish ? c.titleEs() : c.titleEn());
                String desc = (isSpanish ? c.contentEs() : c.contentEn());
                return Optional.of(new HoverInfo(title, c.category(), desc, null, null, HoverKind.CONCEPT));
            }
        }
        return Optional.empty();
    }

    private static String registerSummaryEs(String regName, RegisterEntry reg, RegisterView view) {
        return switch (regName.toUpperCase(Locale.ROOT)) {
            case "AX" -> "Registro acumulador principal de 16 bits (AH:AL)";
            case "AH" -> "Byte alto de 8 bits del acumulador (bits 15..8 de AX, servicios DOS)";
            case "AL" -> "Byte bajo de 8 bits del acumulador (bits 7..0 de AX, código de retorno)";
            case "BX" -> "Registro base de 16 bits (direccionamiento de memoria [BX])";
            case "BH" -> "Byte alto de 8 bits del registro base (bits 15..8 de BX)";
            case "BL" -> "Byte bajo de 8 bits del registro base (bits 7..0 de BX)";
            case "CX" -> "Registro contador de 16 bits (bucles LOOP y desplazamientos de bits)";
            case "CH" -> "Byte alto de 8 bits del contador (bits 15..8 de CX)";
            case "CL" -> "Byte bajo de 8 bits del contador (conteo para SHL, SHR, ROL, ROR)";
            case "DX" -> "Registro de datos de 16 bits (puertos de E/S, multiplicación DX:AX, INT 21h)";
            case "DH" -> "Byte alto de 8 bits de datos (bits 15..8 de DX)";
            case "DL" -> "Byte bajo de 8 bits de datos (bits 7..0 de DX, salida de carácter en INT 21h AH=02h)";
            case "SI" -> "Registro índice fuente de 16 bits (direccionamiento [BX+SI] y cadenas DS:SI)";
            case "DI" -> "Registro índice destino de 16 bits (direccionamiento [BX+DI] y cadenas ES:DI)";
            case "BP" -> "Registro puntero base de 16 bits (marco de pila SS:BP y parámetros)";
            case "SP" -> "Registro puntero de pila de 16 bits (cima de la pila SS:SP)";
            case "CS" -> "Registro de segmento de código de 16 bits (base para CS:IP)";
            case "DS" -> "Registro de segmento de datos de 16 bits (base para variables globales)";
            case "SS" -> "Registro de segmento de pila de 16 bits (base para pila y variables locales)";
            case "ES" -> "Registro de segmento extra de 16 bits (destino de cadenas ES:DI)";
            case "FS" -> "Registro de segmento adicional de 16 bits (introducido en 80386)";
            case "GS" -> "Registro de segmento adicional de 16 bits (introducido en 80386)";
            case "IP" -> "Puntero de instrucción de 16 bits (desplazamiento de próxima instrucción)";
            case "FLAGS" -> "Registro de banderas de estado y control de 16 bits (CF, ZF, SF, OF...)";
            case "EAX" -> "Registro acumulador extendido de 32 bits (80386+)";
            case "EBX" -> "Registro base extendido de 32 bits (80386+)";
            case "ECX" -> "Registro contador extendido de 32 bits (80386+)";
            case "EDX" -> "Registro de datos extendido de 32 bits (80386+)";
            case "ESI" -> "Registro índice fuente extendido de 32 bits (80386+)";
            case "EDI" -> "Registro índice destino extendido de 32 bits (80386+)";
            case "EBP" -> "Registro puntero base extendido de 32 bits (80386+)";
            case "ESP" -> "Registro puntero de pila extendido de 32 bits (80386+)";
            case "EIP" -> "Puntero de instrucción extendido de 32 bits (80386+)";
            case "EFLAGS" -> "Registro de banderas extendidas de 32 bits (80386+)";
            case "RAX" -> "Registro acumulador de 64 bits (x86-64)";
            case "RBX" -> "Registro base de 64 bits (x86-64)";
            case "RCX" -> "Registro contador de 64 bits / argumento 1 en Win64 (x86-64)";
            case "RDX" -> "Registro de datos de 64 bits / argumento 2 en Win64 (x86-64)";
            case "RSI" -> "Registro índice fuente de 64 bits (x86-64)";
            case "RDI" -> "Registro índice destino de 64 bits (x86-64)";
            case "RBP" -> "Registro puntero base de 64 bits (x86-64)";
            case "RSP" -> "Registro puntero de pila de 64 bits (x86-64)";
            case "RIP" -> "Puntero de instrucción de 64 bits (direccionamiento relativo a RIP)";
            case "RFLAGS" -> "Registro de banderas de 64 bits (x86-64)";
            default -> (view != null ? view.sizeBits() : reg.sizeBits()) + " bits · " + reg.group().name();
        };
    }

    private static String registerSummaryEn(String regName, RegisterEntry reg, RegisterView view) {
        return switch (regName.toUpperCase(Locale.ROOT)) {
            case "AX" -> "16-bit Primary Accumulator Register (AH:AL)";
            case "AH" -> "High 8-bit accumulator register (bits 15..8 of AX, DOS services)";
            case "AL" -> "Low 8-bit accumulator register (bits 7..0 of AX, return code)";
            case "BX" -> "16-bit Base Register (memory addressing [BX])";
            case "BH" -> "High 8-bit base register (bits 15..8 of BX)";
            case "BL" -> "Low 8-bit base register (bits 7..0 of BX)";
            case "CX" -> "16-bit Counter Register (LOOP instructions and bit shifts)";
            case "CH" -> "High 8-bit counter register (bits 15..8 of CX)";
            case "CL" -> "Low 8-bit counter register (shift count for SHL, SHR, ROL, ROR)";
            case "DX" -> "16-bit Data Register (I/O ports, DX:AX multiply/divide, INT 21h)";
            case "DH" -> "High 8-bit data register (bits 15..8 of DX)";
            case "DL" -> "Low 8-bit data register (bits 7..0 of DX, char output in INT 21h AH=02h)";
            case "SI" -> "16-bit Source Index Register ([BX+SI] addressing, DS:SI strings)";
            case "DI" -> "16-bit Destination Index Register ([BX+DI] addressing, ES:DI strings)";
            case "BP" -> "16-bit Base Pointer Register (SS:BP stack frame and parameters)";
            case "SP" -> "16-bit Stack Pointer Register (SS:SP stack top)";
            case "CS" -> "16-bit Code Segment Register (base for CS:IP)";
            case "DS" -> "16-bit Data Segment Register (base for global variables)";
            case "SS" -> "16-bit Stack Segment Register (base for stack and local variables)";
            case "ES" -> "16-bit Extra Segment Register (destination for string ops ES:DI)";
            case "FS" -> "16-bit Additional Segment Register (introduced in 80386)";
            case "GS" -> "16-bit Additional Segment Register (introduced in 80386)";
            case "IP" -> "16-bit Instruction Pointer (offset of next instruction)";
            case "FLAGS" -> "16-bit Status and Control Flags Register (CF, ZF, SF, OF...)";
            case "EAX" -> "32-bit Extended Accumulator Register (80386+)";
            case "EBX" -> "32-bit Extended Base Register (80386+)";
            case "ECX" -> "32-bit Extended Counter Register (80386+)";
            case "EDX" -> "32-bit Extended Data Register (80386+)";
            case "ESI" -> "32-bit Extended Source Index Register (80386+)";
            case "EDI" -> "32-bit Extended Destination Index Register (80386+)";
            case "EBP" -> "32-bit Extended Base Pointer Register (80386+)";
            case "ESP" -> "32-bit Extended Stack Pointer Register (80386+)";
            case "EIP" -> "32-bit Extended Instruction Pointer (80386+)";
            case "EFLAGS" -> "32-bit Extended Flags Register (80386+)";
            case "RAX" -> "64-bit Accumulator Register (x86-64)";
            case "RBX" -> "64-bit Base Register (x86-64)";
            case "RCX" -> "64-bit Counter Register / Argument 1 in Win64 (x86-64)";
            case "RDX" -> "64-bit Data Register / Argument 2 in Win64 (x86-64)";
            case "RSI" -> "64-bit Source Index Register (x86-64)";
            case "RDI" -> "64-bit Destination Index Register (x86-64)";
            case "RBP" -> "64-bit Base Pointer Register (x86-64)";
            case "RSP" -> "64-bit Stack Pointer Register (x86-64)";
            case "RIP" -> "64-bit Instruction Pointer (RIP-relative addressing)";
            case "RFLAGS" -> "64-bit Flags Register (x86-64)";
            default -> (view != null ? view.sizeBits() : reg.sizeBits()) + "-bit · " + reg.group().name();
        };
    }

    private static String formatRegisterFamily(String regName, RegisterEntry reg, Corpus corpus, boolean isSpanish) {
        String u = regName.toUpperCase(Locale.ROOT);
        if (u.equals("AX") || u.equals("AH") || u.equals("AL") || u.equals("EAX") || u.equals("RAX")) {
            return "RAX [64] > EAX [32] > AX [16] > AH [15..8] : AL [7..0]";
        }
        if (u.equals("BX") || u.equals("BH") || u.equals("BL") || u.equals("EBX") || u.equals("RBX")) {
            return "RBX [64] > EBX [32] > BX [16] > BH [15..8] : BL [7..0]";
        }
        if (u.equals("CX") || u.equals("CH") || u.equals("CL") || u.equals("ECX") || u.equals("RCX")) {
            return "RCX [64] > ECX [32] > CX [16] > CH [15..8] : CL [7..0]";
        }
        if (u.equals("DX") || u.equals("DH") || u.equals("DL") || u.equals("EDX") || u.equals("RDX")) {
            return "RDX [64] > EDX [32] > DX [16] > DH [15..8] : DL [7..0]";
        }
        if (u.equals("SI") || u.equals("ESI") || u.equals("RSI")) {
            return "RSI [64] > ESI [32] > SI [16] > SIL [7..0]";
        }
        if (u.equals("DI") || u.equals("EDI") || u.equals("RDI")) {
            return "RDI [64] > EDI [32] > DI [16] > DIL [7..0]";
        }
        if (u.equals("BP") || u.equals("EBP") || u.equals("RBP")) {
            return "RBP [64] > EBP [32] > BP [16] > BPL [7..0]";
        }
        if (u.equals("SP") || u.equals("ESP") || u.equals("RSP")) {
            return "RSP [64] > ESP [32] > SP [16] > SPL [7..0]";
        }
        if (reg.group() == RegisterGroup.G3_SEGMENT) {
            return isSpanish
                    ? reg.name() + " (16 bits) · Segmento (Dirección física = Segmento * 16 + Desplazamiento)"
                    : reg.name() + " (16 bits) · Segment register (Physical address = Segment * 16 + Offset)";
        }
        return reg.name() + " (" + reg.sizeBits() + "-bit, " + reg.group().name() + ")";
    }

    private static String formatFlagsRegisterTable(RegisterEntry reg) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bit  | Flag | Meaning\n");
        sb.append("-----+------+--------------------------------\n");
        sb.append("0    | CF   | Carry Flag (Acarreo no firmado)\n");
        sb.append("2    | PF   | Parity Flag (Paridad en byte bajo)\n");
        sb.append("4    | AF   | Auxiliary Carry (Acarreo BCD)\n");
        sb.append("6    | ZF   | Zero Flag (Resultado cero)\n");
        sb.append("7    | SF   | Sign Flag (Signo, bit más significativo)\n");
        sb.append("8    | TF   | Trap Flag (Paso a paso)\n");
        sb.append("9    | IF   | Interrupt Enable Flag (Interrupciones)\n");
        sb.append("10   | DF   | Direction Flag (Dirección de cadenas)\n");
        sb.append("11   | OF   | Overflow Flag (Desbordamiento con signo)");
        return sb.toString();
    }

    private static String canonicalRegisterExample(String regName) {
        return switch (regName.toUpperCase(Locale.ROOT)) {
            case "AX" -> "mov ax, @data\nmov ds, ax";
            case "AH" -> "mov ah, 09h\nint 21h";
            case "AL" -> "mov al, 0\nmov ah, 4ch\nint 21h";
            case "BX" -> "mov bx, OFFSET tabla\nmov al, [bx]";
            case "CX" -> "mov cx, 10\nbucle:\n    loop bucle";
            case "CL" -> "mov cl, 4\nshl ax, cl";
            case "DX" -> "mov dx, OFFSET mensaje\nmov ah, 09h\nint 21h";
            case "SI" -> "mov si, OFFSET origen\nlodsb";
            case "DI" -> "mov di, OFFSET destino\nstosb";
            case "BP" -> "push bp\nmov bp, sp\nmov ax, [bp+4]";
            case "SP" -> "push ax\npop bx";
            case "CS" -> "jmp far ptr destino";
            case "DS" -> "mov ax, @data\nmov ds, ax";
            case "SS" -> "mov ax, @data\nmov ss, ax\nmov sp, 0100h";
            case "ES" -> "mov ax, @data\nmov es, ax";
            case "FLAGS", "EFLAGS", "RFLAGS" -> "pushf\npop ax";
            case "EAX" -> "mov eax, 12345678h";
            case "RAX" -> "mov rax, 60  ; sys_exit\nsyscall";
            default -> "mov " + regName.toLowerCase(Locale.ROOT) + ", 0";
        };
    }

    /** The unsigned value of a numeric literal ({@link NumericLiteral}), or {@code null} when the word is not one. */
    static Long parseNumber(String s) {
        return NumericLiteral.parse(s);
    }

    /** Binary digits in groups of four, with the assembler's {@code b} suffix: {@code 100 1100b}. */
    public static String formatBinary(long n) {
        String raw = Long.toBinaryString(n);
        StringBuilder sb = new StringBuilder();
        int len = raw.length();
        for (int i = 0; i < len; i++) {
            if (i > 0 && (len - i) % 4 == 0) {
                sb.append(" ");
            }
            sb.append(raw.charAt(i));
        }
        sb.append("b");
        return sb.toString();
    }
}

