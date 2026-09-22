package io.github.dinamo541.idearm.language.catalog;

/**
 * Summary of all 9 primary status and control flags:
 * Overflow (O), Direction (D), Interrupt (I), Trap (T), Sign (S), Zero (Z), Auxiliary (A), Parity (P), Carry (C).
 */
public record FlagSummary(
        FlagEffect o,
        FlagEffect d,
        FlagEffect i,
        FlagEffect t,
        FlagEffect s,
        FlagEffect z,
        FlagEffect a,
        FlagEffect p,
        FlagEffect c
) {
    public static FlagSummary none() {
        return new FlagSummary(
                FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED,
                FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED,
                FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED
        );
    }

    public static FlagSummary standardArithmetic() {
        return new FlagSummary(
                FlagEffect.MODIFIED, FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED,
                FlagEffect.UNAFFECTED, FlagEffect.MODIFIED, FlagEffect.MODIFIED,
                FlagEffect.MODIFIED, FlagEffect.MODIFIED, FlagEffect.MODIFIED
        );
    }

    public static FlagSummary standardLogic() {
        return new FlagSummary(
                FlagEffect.CLEARED, FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED,
                FlagEffect.UNAFFECTED, FlagEffect.MODIFIED, FlagEffect.MODIFIED,
                FlagEffect.UNDEFINED, FlagEffect.MODIFIED, FlagEffect.CLEARED
        );
    }

    public String formatTable() {
        return "O D I T S Z A P C\n" +
                o.symbol() + " " + d.symbol() + " " + i.symbol() + " " +
                t.symbol() + " " + s.symbol() + " " + z.symbol() + " " +
                a.symbol() + " " + p.symbol() + " " + c.symbol();
    }
}
