package io.github.dinamo541.idearm.app.viewmodel;

import io.github.dinamo541.idearm.domain.debug.Breakpoint;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.util.Objects;

/**
 * ViewModel for a breakpoint displayed in the debugger table.
 */
public final class BreakpointItemViewModel {

    private final String path;
    private final int line;
    private final BooleanProperty enabled;

    public BreakpointItemViewModel(Breakpoint breakpoint) {
        Objects.requireNonNull(breakpoint, "breakpoint cannot be null");
        this.path = breakpoint.path();
        this.line = breakpoint.line();
        this.enabled = new SimpleBooleanProperty(breakpoint.enabled());
    }

    public BreakpointItemViewModel(String path, int line, boolean enabled) {
        this.path = Objects.requireNonNull(path);
        this.line = line;
        this.enabled = new SimpleBooleanProperty(enabled);
    }

    public String getPath() {
        return path;
    }

    public int getLine() {
        return line;
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public void setEnabled(boolean value) {
        enabled.set(value);
    }

    public BooleanProperty enabledProperty() {
        return enabled;
    }

    public Breakpoint toBreakpoint() {
        return new Breakpoint(path, line, enabled.get());
    }
}
