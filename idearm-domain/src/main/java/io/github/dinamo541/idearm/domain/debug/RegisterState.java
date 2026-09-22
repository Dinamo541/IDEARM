package io.github.dinamo541.idearm.domain.debug;

/**
 * CPU register snapshot.
 *
 * <p>The named fields are the 16-bit 8086 registers, stored as unsigned 16-bit integers (masked to 0xFFFF).
 * Byte accessors extract high and low 8-bit registers. Flag bit accessors extract individual condition codes
 * according to the standard 8086 FLAGS layout. A 32/64-bit program also reports its full registers in
 * {@code extended}, in the order the debugger lists them.
 */
public record RegisterState(
        int ax, int bx, int cx, int dx,
        int si, int di, int bp, int sp, int ip,
        int cs, int ds, int es, int ss,
        int flags,
        java.util.Map<String, Long> extended) {

    public RegisterState {
        ax &= 0xFFFF; bx &= 0xFFFF; cx &= 0xFFFF; dx &= 0xFFFF;
        si &= 0xFFFF; di &= 0xFFFF; bp &= 0xFFFF; sp &= 0xFFFF; ip &= 0xFFFF;
        cs &= 0xFFFF; ds &= 0xFFFF; es &= 0xFFFF; ss &= 0xFFFF;
        flags &= 0xFFFF;
        extended = extended == null ? java.util.Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(extended));
    }

    public RegisterState(
            int ax, int bx, int cx, int dx,
            int si, int di, int bp, int sp, int ip,
            int cs, int ds, int es, int ss,
            int flags) {
        this(ax, bx, cx, dx, si, di, bp, sp, ip, cs, ds, es, ss, flags, java.util.Map.of());
    }

    // 8-bit byte registers
    public int ah() { return (ax >> 8) & 0xFF; }
    public int al() { return ax & 0xFF; }
    public int bh() { return (bx >> 8) & 0xFF; }
    public int bl() { return bx & 0xFF; }
    public int ch() { return (cx >> 8) & 0xFF; }
    public int cl() { return cx & 0xFF; }
    public int dh() { return (dx >> 8) & 0xFF; }
    public int dl() { return dx & 0xFF; }

    // 8086 Flag bits
    public boolean cf() { return (flags & 0x0001) != 0; } // Carry Flag (bit 0)
    public boolean pf() { return (flags & 0x0004) != 0; } // Parity Flag (bit 2)
    public boolean af() { return (flags & 0x0010) != 0; } // Auxiliary Carry Flag (bit 4)
    public boolean zf() { return (flags & 0x0040) != 0; } // Zero Flag (bit 6)
    public boolean sf() { return (flags & 0x0080) != 0; } // Sign Flag (bit 7)
    public boolean tf() { return (flags & 0x0100) != 0; } // Trap Flag (bit 8)
    public boolean ifFlag() { return (flags & 0x0200) != 0; } // Interrupt Flag (bit 9)
    public boolean df() { return (flags & 0x0400) != 0; } // Direction Flag (bit 10)
    public boolean of() { return (flags & 0x0800) != 0; } // Overflow Flag (bit 11)

    public static RegisterState empty() {
        return new RegisterState(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x0202);
    }

    public static RegisterState initialDosState() {
        // Typical initial real-mode DOS state: IF=1, reserved bit 1 set, other flags 0
        return new RegisterState(0, 0, 0, 0, 0, 0, 0, 0xFFFE, 0x0100, 0x0710, 0x0710, 0x0710, 0x0710, 0x0202);
    }

    public static RegisterState fromExtended(java.util.Map<String, Long> regs) {
        if (regs == null || regs.isEmpty()) {
            return empty();
        }
        int ax = (int) (regs.getOrDefault("RAX", regs.getOrDefault("EAX", regs.getOrDefault("AX", 0L))) & 0xFFFF);
        int bx = (int) (regs.getOrDefault("RBX", regs.getOrDefault("EBX", regs.getOrDefault("BX", 0L))) & 0xFFFF);
        int cx = (int) (regs.getOrDefault("RCX", regs.getOrDefault("ECX", regs.getOrDefault("CX", 0L))) & 0xFFFF);
        int dx = (int) (regs.getOrDefault("RDX", regs.getOrDefault("EDX", regs.getOrDefault("DX", 0L))) & 0xFFFF);
        int si = (int) (regs.getOrDefault("RSI", regs.getOrDefault("ESI", regs.getOrDefault("SI", 0L))) & 0xFFFF);
        int di = (int) (regs.getOrDefault("RDI", regs.getOrDefault("EDI", regs.getOrDefault("DI", 0L))) & 0xFFFF);
        int bp = (int) (regs.getOrDefault("RBP", regs.getOrDefault("EBP", regs.getOrDefault("BP", 0L))) & 0xFFFF);
        int sp = (int) (regs.getOrDefault("RSP", regs.getOrDefault("ESP", regs.getOrDefault("SP", 0L))) & 0xFFFF);
        int ip = (int) (regs.getOrDefault("RIP", regs.getOrDefault("EIP", regs.getOrDefault("IP", 0L))) & 0xFFFF);
        int cs = (int) (regs.getOrDefault("CS", 0L) & 0xFFFF);
        int ds = (int) (regs.getOrDefault("DS", 0L) & 0xFFFF);
        int es = (int) (regs.getOrDefault("ES", 0L) & 0xFFFF);
        int ss = (int) (regs.getOrDefault("SS", 0L) & 0xFFFF);
        int flags = (int) (regs.getOrDefault("RFLAGS", regs.getOrDefault("EFLAGS", regs.getOrDefault("FLAGS", 0x0202L))) & 0xFFFF);
        return new RegisterState(ax, bx, cx, dx, si, di, bp, sp, ip, cs, ds, es, ss, flags, regs);
    }

    public long getExtended(String name, long defaultValue) {
        if (extended == null || name == null) return defaultValue;
        return extended.getOrDefault(name.toUpperCase(java.util.Locale.ROOT), defaultValue);
    }

    /**
     * Checks if the specified register or flag changed compared to a previous state.
     *
     * @param name Register name (e.g. "AX", "IP", "RAX") or flag name (e.g. "CF", "ZF")
     * @param previous Previous register state to compare against, or null
     * @return true if changed or if previous is null
     */
    public boolean isChanged(String name, RegisterState previous) {
        if (previous == null) return false;
        if (name == null) return false;
        String upper = name.toUpperCase(java.util.Locale.ROOT);
        if (extended != null && (extended.containsKey(upper) || (previous.extended != null && previous.extended.containsKey(upper)))) {
            return !java.util.Objects.equals(
                    extended.get(upper),
                    previous.extended == null ? null : previous.extended.get(upper));
        }
        return switch (upper) {
            case "AX" -> this.ax != previous.ax;
            case "BX" -> this.bx != previous.bx;
            case "CX" -> this.cx != previous.cx;
            case "DX" -> this.dx != previous.dx;
            case "SI" -> this.si != previous.si;
            case "DI" -> this.di != previous.di;
            case "BP" -> this.bp != previous.bp;
            case "SP" -> this.sp != previous.sp;
            case "IP" -> this.ip != previous.ip;
            case "CS" -> this.cs != previous.cs;
            case "DS" -> this.ds != previous.ds;
            case "ES" -> this.es != previous.es;
            case "SS" -> this.ss != previous.ss;
            case "FLAGS" -> this.flags != previous.flags;
            case "CF" -> this.cf() != previous.cf();
            case "ZF" -> this.zf() != previous.zf();
            case "SF" -> this.sf() != previous.sf();
            case "OF" -> this.of() != previous.of();
            case "PF" -> this.pf() != previous.pf();
            case "AF" -> this.af() != previous.af();
            case "IF" -> this.ifFlag() != previous.ifFlag();
            case "DF" -> this.df() != previous.df();
            case "TF" -> this.tf() != previous.tf();
            default -> false;
        };
    }

    /**
     * Returns the set of all register and flag names that differ from the previous state.
     */
    public java.util.Set<String> changedRegisters(RegisterState previous) {
        if (previous == null) return java.util.Set.of();
        java.util.Set<String> diff = new java.util.HashSet<>();
        String[] keys = {"AX", "BX", "CX", "DX", "SI", "DI", "BP", "SP", "IP", "CS", "DS", "ES", "SS", "FLAGS",
                         "CF", "ZF", "SF", "OF", "PF", "AF", "IF", "DF", "TF"};
        for (String key : keys) {
            if (isChanged(key, previous)) {
                diff.add(key);
            }
        }
        if (extended != null) {
            for (String extKey : extended.keySet()) {
                if (isChanged(extKey, previous)) {
                    diff.add(extKey);
                }
            }
        }
        if (previous.extended != null) {
            for (String extKey : previous.extended.keySet()) {
                if (isChanged(extKey, previous)) {
                    diff.add(extKey);
                }
            }
        }
        return java.util.Collections.unmodifiableSet(diff);
    }
}
