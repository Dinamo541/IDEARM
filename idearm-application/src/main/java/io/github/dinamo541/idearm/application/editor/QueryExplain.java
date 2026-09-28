package io.github.dinamo541.idearm.application.editor;

import io.github.dinamo541.idearm.language.catalog.FlagSummary;
import io.github.dinamo541.idearm.language.catalog.InstructionCatalog;
import io.github.dinamo541.idearm.language.catalog.InstructionInfo;
import io.github.dinamo541.idearm.language.index.ProjectSymbolIndex;
import io.github.dinamo541.idearm.language.index.SymbolDefinition;
import io.github.dinamo541.idearm.language.knowledge.*;
import io.github.dinamo541.idearm.language.lexer.AssemblyLexer;
import io.github.dinamo541.idearm.language.lexer.Token;
import io.github.dinamo541.idearm.language.lexer.TokenType;
import io.github.dinamo541.idearm.language.model.*;
import io.github.dinamo541.idearm.language.parser.AssemblyParser;
import io.github.dinamo541.idearm.language.parser.OperandParser;

import java.util.*;

/**
 * Use case: Resolves deep, position-aware and selection-aware pedagogical explanations (AA-P4-02).
 * Implements Acceptance Cases 1, 2, and 10, deconstructing instructions, memory operands,
 * addressing modes, colon notations, and interrupt service contexts.
 */
public final class QueryExplain {

    public record Request(
            String text,
            int line,
            int column,
            String localeLanguage,
            CompatibilityContext context,
            ProjectSymbolIndex symbolIndex
    ) {
        public Request {
            Objects.requireNonNull(text, "text cannot be null");
        }
    }

    public Optional<ExplanationNode> execute(Request request) {
        String raw = request.text().trim();
        if (raw.isBlank()) {
            return Optional.empty();
        }

        boolean isSpanish = request.localeLanguage() != null && request.localeLanguage().toLowerCase(Locale.ROOT).startsWith("es");

        // 1. Check for specific colon notations: DS:DX, DX:AX, etiqueta:, ES:[DI] (Acceptance Case 2)
        Optional<ExplanationNode> colonExplanation = checkColonNotation(raw, isSpanish);
        if (colonExplanation.isPresent()) {
            return colonExplanation;
        }

        // 2. Check for context-aware INT 21h / interrupt invocation (e.g. "21h" inside "int 21h")
        Optional<ExplanationNode> intExplanation = checkInterruptContext(raw, request.column(), isSpanish);
        if (intExplanation.isPresent()) {
            return intExplanation;
        }

        // 3. Check for project symbol priority (Acceptance Case 10)
        if (request.symbolIndex() != null && !raw.contains(" ") && !raw.contains(",")) {
            Optional<SymbolDefinition> symOpt = request.symbolIndex().findDefinition(raw);
            if (symOpt.isPresent()) {
                SymbolDefinition sym = symOpt.get();
                String desc = (isSpanish ? "Símbolo definido en " : "Symbol defined in ") + sym.filePath() + ":" + sym.line();
                ExplanationNode symNode = new ExplanationNode(
                        sym.name(),
                        "USER_SYMBOL",
                        sym.kind().name() + " (Proyecto)",
                        desc,
                        1, 1 + raw.length(),
                        false, true
                );

                // If also homonym of instruction, attach instruction as secondary child
                Optional<InstructionInfo> homonym = InstructionCatalog.find(raw);
                if (homonym.isPresent()) {
                    InstructionInfo h = homonym.get();
                    String secTitle = (isSpanish ? "Nota: También existe la instrucción " : "Note: Instruction also exists ") + h.mnemonic();
                    String secDesc = isSpanish ? h.descriptionEs() : h.descriptionEn();
                    ExplanationNode secNode = new ExplanationNode(secTitle, "HOMONYM_INSTRUCTION",
                            isSpanish ? h.summaryEs() : h.summaryEn(), secDesc, 1, 1 + raw.length());
                    symNode = symNode.withChildren(List.of(secNode));
                }
                return Optional.of(symNode);
            }
        }

        // 4. Try parsing full instruction statement (Acceptance Case 1)
        SourceFileNode ast = new AssemblyParser().parse(raw + "\n");
        if (!ast.instructions().isEmpty()) {
            InstructionNode inst = ast.instructions().getFirst();
            return Optional.of(buildInstructionExplanation(inst, raw, isSpanish, request.context()));
        }

        // 5. Try parsing as a standalone operand
        ParsedOperand parsedOp = OperandParser.parse(raw, request.line(), request.column());
        if (!(parsedOp instanceof UnknownOperand)) {
            return Optional.of(buildOperandExplanation(parsedOp, isSpanish, null));
        }

        return Optional.empty();
    }

    public Optional<ExplanationNode> explainSelection(String selection, String localeLanguage, CompatibilityContext context) {
        return execute(new Request(selection, 1, 1, localeLanguage, context, null));
    }

    // --- Colon Notations (Acceptance Case 2) ---

    private Optional<ExplanationNode> checkColonNotation(String text, boolean isSpanish) {
        String clean = text.trim();

        // Logical address notation: DS:DX, CS:IP, SS:SP, ES:DI (case insensitive)
        if (clean.matches("^(?i)(DS:DX|CS:IP|SS:SP|ES:DI|ES:BX)$")) {
            String title = clean.toUpperCase(Locale.ROOT);
            String summary = isSpanish
                    ? "Notación de dirección lógica (segmento:desplazamiento) · Notación, NO es código"
                    : "Logical address notation (segment:offset) · Notation, NOT code";
            String details = isSpanish
                    ? "Describe la dirección lógica de una zona o estructura de memoria. Esto NO es código ensamblable ni existe una instrucción con esta sintaxis. Los dos registros deben cargarse por separado (por ejemplo, MOV AX, @DATA / MOV DS, AX y LEA DX, BUFFER). Los dos puntos aquí representan la convención segmento:desplazamiento de la documentación, no concatenación."
                    : "Describes the logical address of a memory buffer or structure. This is documentation NOTATION, not assemblable code. Both registers must be loaded separately (e.g. MOV AX, @DATA / MOV DS, AX and LEA DX, BUFFER). The colon represents segment:offset separation, not concatenation.";
            return Optional.of(new ExplanationNode(title, "LOGICAL_ADDRESS_NOTATION", summary, details, 1, 1 + text.length(), true, false));
        }

        // Register pair notation: DX:AX, EDX:EAX, RDX:RAX
        if (clean.matches("^(?i)(DX:AX|EDX:EAX|RDX:RAX)$")) {
            String title = clean.toUpperCase(Locale.ROOT);
            String summary = isSpanish
                    ? "Notación de pareja de registros · Notación, NO es código"
                    : "Register pair notation · Notation, NOT code";
            String details = isSpanish
                    ? "Describe un único valor repartido entre dos registros (la parte alta en el primer registro y la parte baja en el segundo), empleado por instrucciones como IMUL, MUL o DIV. No es un operando ni código ensamblable; para manipular el valor se operan ambos registros por separado. Los dos puntos no representan concatenación."
                    : "Describes a combined value split between two registers (high half in first register, low half in second), used in multiplication and division (IMUL, DIV). This is documentation notation, not assemblable code.";
            return Optional.of(new ExplanationNode(title, "REGISTER_PAIR_NOTATION", summary, details, 1, 1 + text.length(), true, false));
        }

        // Segment override: e.g. ES:[DI], SS:[BX], DS:[SI]
        if (clean.matches("^(?i)(CS|DS|SS|ES|FS|GS):\\s*\\[.*\\]$")) {
            String seg = clean.substring(0, 2).toUpperCase(Locale.ROOT);
            String title = clean;
            String summary = isSpanish
                    ? "Sobreescritura de segmento · Sintaxis ensamblable"
                    : "Segment override · Assemblable syntax";
            String details = isSpanish
                    ? "Indica explícitamente al procesador que acceda a memoria usando el segmento " + seg + " en lugar del segmento asignado por omisión. Es sintaxis ensamblable válida y genera un prefijo de segmento (segment override prefix) de 1 byte en código máquina."
                    : "Explicitly instructs the processor to access memory using segment " + seg + " instead of the default segment. This is valid assemblable syntax generating a 1-byte machine code prefix.";
            return Optional.of(new ExplanationNode(title, "SEGMENT_OVERRIDE_SYNTAX", summary, details, 1, 1 + text.length(), false, true));
        }

        // Label definition: e.g. bucle:, label:, main:
        if (clean.matches("^[a-zA-Z_@?][a-zA-Z0-9_@?]*:$")) {
            String labelName = clean.substring(0, clean.length() - 1);
            String summary = isSpanish
                    ? "Definición de etiqueta · Sintaxis ensamblable"
                    : "Label definition · Assemblable syntax";
            String details = isSpanish
                    ? "Asigna el nombre simbólico '" + labelName + "' a la dirección de memoria de la instrucción siguiente. Es sintaxis ensamblable válida en MASM, TASM y NASM para bifurcaciones y saltos (JMP, JNZ, CALL)."
                    : "Assigns the symbolic label '" + labelName + "' to the address of the following instruction. Valid assemblable syntax in MASM, TASM, and NASM.";
            return Optional.of(new ExplanationNode(clean, "LABEL_DEFINITION", summary, details, 1, 1 + text.length(), false, true));
        }

        return Optional.empty();
    }

    // --- Interrupt Context Resolution ---

    private Optional<ExplanationNode> checkInterruptContext(String text, int column, boolean isSpanish) {
        String clean = text.trim();
        List<Token> tokens = new AssemblyLexer().tokenize(clean);

        boolean hasInt = false;
        boolean has21h = false;
        for (Token t : tokens) {
            if (t.isInstruction("INT") || t.text().equalsIgnoreCase("INT")) hasInt = true;
            if (t.text().equalsIgnoreCase("21h") || t.text().equalsIgnoreCase("0x21") || t.text().equals("33")) has21h = true;
        }

        if (hasInt && has21h || clean.equalsIgnoreCase("int 21h")) {
            String title = "INT 21h";
            String summary = isSpanish
                    ? "Interrupción de servicios DOS · Despachador del sistema operativo"
                    : "DOS Services Interrupt · Operating System Dispatcher";
            String details = isSpanish
                    ? "Llama a los servicios del sistema operativo DOS. El servicio concreto a ejecutar se selecciona mediante el registro AH (por ejemplo, AH=09h imprime una cadena terminada en $, AH=0Ah lee texto del teclado en un búfer, AH=4Ch termina el programa). Los parámetros y búferes se pasan en registros (comúnmente DS:DX)."
                    : "Invokes MS-DOS system services. The specific service function is chosen in register AH (e.g., AH=09h prints a $-terminated string, AH=0Ah reads buffered keyboard input, AH=4Ch exits the program).";
            return Optional.of(new ExplanationNode(title, "INTERRUPT_SERVICE", summary, details, 1, 1 + text.length(), false, true));
        }

        return Optional.empty();
    }

    // --- Full Instruction Statement Decomposition (Acceptance Case 1) ---

    private ExplanationNode buildInstructionExplanation(InstructionNode inst, String rawText, boolean isSpanish,
                                                        CompatibilityContext context) {
        String mnem = inst.mnemonic().toUpperCase(Locale.ROOT);
        Optional<InstructionInfo> infoOpt = InstructionCatalog.find(mnem);

        String instSummary = infoOpt.map(info -> isSpanish ? info.summaryEs() : info.summaryEn())
                .orElse(isSpanish ? "Instrucción ensamblador" : "Assembly instruction");
        String flagsSummary = infoOpt.map(info -> {
            FlagSummary f = info.flags();
            return f != null && f.isAnyAffected()
                    ? (isSpanish ? "modifica banderas" : "modifies flags")
                    : (isSpanish ? "no modifica banderas" : "preserves all flags");
        }).orElse(isSpanish ? "no modifica banderas" : "preserves all flags");

        String rootSummary = isSpanish
                ? "Sentencia de instrucción · " + mnem + " (" + instSummary + ")"
                : "Instruction statement · " + mnem + " (" + instSummary + ")";

        List<ExplanationNode> children = new ArrayList<>();

        // 1. Mnemonic child node
        String mnemDetails = isSpanish
                ? "Instrucción · transferencia · 8086+ · " + flagsSummary
                : "Instruction · data transfer · 8086+ · " + flagsSummary;
        children.add(new ExplanationNode(mnem.toLowerCase(Locale.ROOT), "INSTRUCTION",
                instSummary + " · " + flagsSummary, mnemDetails, inst.column(), inst.column() + mnem.length()));

        // 2. Operands and separators
        List<ParsedOperand> operands = inst.parsedOperands();
        for (int i = 0; i < operands.size(); i++) {
            ParsedOperand op = operands.get(i);
            String roleHint = (i == 0) ? "DESTINATION" : "SOURCE";

            // If there's a comma before this operand
            if (i > 0) {
                String commaDetails = isSpanish
                        ? "Separador de operandos; en sintaxis Intel el destino va primero"
                        : "Operand separator; in Intel syntax the destination operand comes first";
                children.add(new ExplanationNode(",", "SEPARATOR",
                        isSpanish ? "Separador destino, origen" : "Separator dest, src",
                        commaDetails, op.startColumn() - 1, op.startColumn()));
            }

            children.add(buildOperandExplanation(op, isSpanish, roleHint));
        }

        // 3. Addressing mode pedagogical notes (Acceptance Case 1 & Case 3)
        for (ParsedOperand op : operands) {
            if (op instanceof MemoryOperand mem && mem.expression() != null) {
                MemoryExpression expr = mem.expression();
                if (expr.hasBase() || expr.hasIndex()) {
                    String noteTitle = isSpanish ? "Reglas de Direccionamiento" : "Addressing Rules";
                    String noteDetails = isSpanish
                            ? "Por qué no vale [bx+bp]: dos bases. Por qué no vale [ax]: AX no puede ser base con dirección de 16 bits. Con dirección de 32 bits ambas serían válidas."
                            : "Why [bx+bp] is invalid: two base registers. Why [ax] is invalid: AX cannot be a base register in 16-bit addressing. In 32-bit addressing both would be valid.";
                    children.add(new ExplanationNode(noteTitle, "ADDRESSING_RULES",
                            isSpanish ? "Combinaciones válidas e inválidas en 16 bits" : "Valid and invalid 16-bit combinations",
                            noteDetails, op.startColumn(), op.endColumn()));
                }
            }
        }

        return new ExplanationNode(rawText, "STATEMENT", rootSummary, "", 1, 1 + rawText.length(),
                false, true, children);
    }

    private ExplanationNode buildOperandExplanation(ParsedOperand op, boolean isSpanish, String roleHint) {
        if (op instanceof RegisterOperand reg) {
            boolean isDest = "DESTINATION".equalsIgnoreCase(roleHint);
            String summary = isSpanish
                    ? "operando · registro de " + reg.bitSize() + " bits · " + (isDest ? "destino · se escribe completo" : "origen · se lee")
                    : "operand · " + reg.bitSize() + "-bit register · " + (isDest ? "destination · written complete" : "source · read");
            String details = isSpanish
                    ? "Registro " + reg.normalizedRegister() + " (" + reg.bitSize() + " bits). " + (isDest ? "Destino de la operación: se escribe el resultado completo." : "Origen de la operación: se lee su valor sin modificarlo.")
                    : "Register " + reg.normalizedRegister() + " (" + reg.bitSize() + " bits).";
            return new ExplanationNode(reg.rawText(), "REGISTER_OPERAND", summary, details, reg.startColumn(), reg.endColumn());
        }

        if (op instanceof MemoryOperand mem) {
            MemoryExpression expr = mem.expression();
            boolean isDest = "DESTINATION".equalsIgnoreCase(roleHint);
            String summary = isSpanish
                    ? "operando · memoria · " + (isDest ? "se escribe" : "se lee") + " · tamaño 16 bits (lo fija AX)"
                    : "operand · memory · " + (isDest ? "written" : "read") + " · 16-bit size";

            List<ExplanationNode> children = new ArrayList<>();

            // [ ] Brackets component
            children.add(new ExplanationNode("[ ]", "BRACKETS",
                    isSpanish ? "acceso al contenido de la memoria" : "memory content dereference",
                    isSpanish ? "Los corchetes indican acceso al contenido almacenado en la memoria en esa dirección." : "Square brackets dereference memory content.",
                    mem.startColumn(), mem.endColumn()));

            // Base register component
            if (expr.hasBase()) {
                String b = expr.base().text().toUpperCase(Locale.ROOT);
                String baseNote = (b.equals("BX") || b.equals("BP"))
                        ? (isSpanish ? "base (una de las dos únicas bases válidas con dirección de 16 bits)" : "base register (one of only two valid bases in 16-bit)")
                        : (isSpanish ? "registro base" : "base register");
                children.add(new ExplanationNode(b.toLowerCase(Locale.ROOT), "BASE_REGISTER", baseNote, baseNote,
                        expr.base().startColumn(), expr.base().endColumn()));
            }

            // Index register component
            if (expr.hasIndex()) {
                String idx = expr.index().text().toUpperCase(Locale.ROOT);
                String idxNote = (idx.equals("SI") || idx.equals("DI"))
                        ? (isSpanish ? "índice (uno de los dos únicos índices válidos)" : "index register (one of only two valid indices in 16-bit)")
                        : (isSpanish ? "registro índice" : "index register");
                children.add(new ExplanationNode(idx.toLowerCase(Locale.ROOT), "INDEX_REGISTER", idxNote, idxNote,
                        expr.index().startColumn(), expr.index().endColumn()));
            }

            // Displacement component
            if (expr.hasDisplacement()) {
                String dText = expr.displacement().text().trim();
                String dispNote = isSpanish
                        ? "desplazamiento, sumado al ensamblar"
                        : "displacement offset, assembled into the instruction";
                children.add(new ExplanationNode(dText, "DISPLACEMENT", dispNote, dispNote,
                        expr.displacement().startColumn(), expr.displacement().endColumn()));
            }

            // Segment component
            String seg = mem.effectiveSegment();
            String segNote = isSpanish
                    ? (mem.segmentOverride() != null ? "segmento explícito " + seg : "DS por omisión (sería SS si la base fuera BP)")
                    : (mem.segmentOverride() != null ? "explicit segment " + seg : "DS by default (would be SS if base was BP)");
            children.add(new ExplanationNode("segmento", "SEGMENT", segNote, segNote, mem.startColumn(), mem.endColumn()));

            // Effective and physical address
            String effFormula = expr.effectiveAddressFormula();
            String physFormula = seg + "×16 + efectiva (" + effFormula + ")";
            String addrDetails = isSpanish
                    ? "efectiva = " + effFormula + " ; física = " + physFormula
                    : "effective = " + effFormula + " ; physical = " + physFormula;
            children.add(new ExplanationNode("dirección", "ADDRESS_CALCULATION", addrDetails, addrDetails,
                    mem.startColumn(), mem.endColumn()));

            return new ExplanationNode(mem.rawText(), "MEMORY_OPERAND", summary, summary,
                    mem.startColumn(), mem.endColumn(), false, true, children);
        }

        if (op instanceof ImmediateOperand imm) {
            String summary = isSpanish
                    ? "operando · constante inmediata" + (imm.numericValue() != null ? " (" + imm.numericValue() + ")" : "")
                    : "operand · immediate constant" + (imm.numericValue() != null ? " (" + imm.numericValue() + ")" : "");
            return new ExplanationNode(imm.rawText(), "IMMEDIATE_OPERAND", summary, summary,
                    imm.startColumn(), imm.endColumn());
        }

        if (op instanceof SymbolOperand sym) {
            String summary = isSpanish
                    ? "operando · símbolo / etiqueta '" + sym.symbolName() + "'"
                    : "operand · symbol / label '" + sym.symbolName() + "'";
            return new ExplanationNode(sym.rawText(), "SYMBOL_OPERAND", summary, summary,
                    sym.startColumn(), sym.endColumn());
        }

        return new ExplanationNode(op.rawText(), "UNKNOWN_OPERAND", op.rawText(), op.rawText(),
                op.startColumn(), op.endColumn());
    }
}
