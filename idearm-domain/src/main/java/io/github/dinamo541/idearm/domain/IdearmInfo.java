package io.github.dinamo541.idearm.domain;

/** Identity and version of IDEARM, available without depending on the user interface. */
public final class IdearmInfo {

    public static final String NAME = "IDEARM";

    private IdearmInfo() {
    }

    /** Version of the domain module, or "dev" when it runs without being packaged as a module. */
    public static String version() {
        var descriptor = IdearmInfo.class.getModule().getDescriptor();
        return descriptor == null ? "dev" : descriptor.rawVersion().orElse("dev");
    }
}
