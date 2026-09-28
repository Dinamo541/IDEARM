package io.github.dinamo541.idearm.language.knowledge;

public enum Privilege {
    USER("Ring 3 / User space"),
    CPL0("Ring 0 / Supervisor / Kernel space"),
    ANY("Any privilege level"),
    UNKNOWN("Unknown privilege");

    private final String description;

    Privilege(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
