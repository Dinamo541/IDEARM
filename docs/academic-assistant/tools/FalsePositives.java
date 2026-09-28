import io.github.dinamo541.idearm.language.catalog.*;
import java.util.*;

/** Which real mnemonics the linter would mark as a typo, and what it would suggest. */
public class FalsePositives {
    public static void main(String[] args) {
        // Real mnemonics a 32/64-bit NASM project may legitimately use.
        List<String> real = List.of(
                "MOVAPS", "MOVUPS", "ADDPS", "MULPS", "DIVPS", "SQRTPS", "XORPS", "ANDPS",
                "MOVSS", "MOVSD", "CVTSI2SD", "CVTSD2SI", "ADDSD", "MULSD", "COMISD", "UCOMISD",
                "MOVDQA", "MOVDQU", "PADDB", "PADDW", "PADDD", "PXOR", "PCMPEQB", "PSHUFB",
                "EMMS", "MOVD", "MOVQ", "LDMXCSR", "STMXCSR", "FXSAVE", "FXRSTOR",
                "VADDPS", "VMOVAPS", "VZEROUPPER", "VPXOR",
                "LGDT", "LIDT", "LMSW", "SMSW", "SLDT", "STR", "VERR", "VERW", "CLTS", "INVLPG",
                "RDMSR", "WRMSR", "RSM", "SYSENTER", "SYSEXIT", "SWAPGS", "RDTSCP",
                "MOVSQ", "STOSQ", "LODSQ", "SCASQ", "CMPSQ",
                "SETNE", "CMOVNZ", "BSWAP", "POPCNT", "LZCNT", "TZCNT", "ANDN", "BEXTR",
                "PREFETCHT0", "SFENCE", "LFENCE", "MFENCE", "PAUSE", "CLFLUSH", "MONITOR", "MWAIT",
                "CMPXCHG8B", "CMPXCHG16B", "RDRAND", "RDSEED", "ENDBR64", "AESENC", "SHA1RNDS4",
                "REPNZ", "SALC", "ICEBP", "INT1", "INT3", "FCOMI", "FCMOVB", "FUCOM", "FUCOMI",
                "FSIN", "FCOS", "FPTAN", "FPATAN", "F2XM1", "FYL2X", "FSCALE", "FRNDINT", "FPREM",
                "FSTCW", "FLDCW", "FSAVE", "FRSTOR", "FLDL2E", "FLDLG2", "FLDLN2", "FLD2T", "FLDL2T",
                "AAM", "ARPL", "XLATB", "LAHF", "CBW", "CWDE", "CDQE",
                "TIMES", "RESB", "SECTION", "GLOBAL", "EXTERN", "DEFAULT", "STRUC", "ENDSTRUC");
        int flagged = 0;
        System.out.println("mnemonic | known | suggest()");
        for (String m : real) {
            boolean known = InstructionCatalog.isKnownInstruction(m);
            List<String> s = InstructionCatalog.suggest(m);
            if (!known) {
                flagged++;
                System.out.printf("%-12s | NO    | %s%n", m, s);
            }
        }
        System.out.println();
        System.out.println("checked=" + real.size() + "  not-known=" + flagged);
    }
}
