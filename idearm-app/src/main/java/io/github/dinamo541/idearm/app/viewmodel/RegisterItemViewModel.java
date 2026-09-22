package io.github.dinamo541.idearm.app.viewmodel;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.Objects;

/**
 * ViewModel for a single CPU register entry.
 */
public final class RegisterItemViewModel {

    private final String name;
    private final StringProperty hexValue = new SimpleStringProperty("0000");
    private final StringProperty decValue = new SimpleStringProperty("0");
    private final BooleanProperty changed = new SimpleBooleanProperty(false);

    public RegisterItemViewModel(String name) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
    }

    public RegisterItemViewModel(String name, int value) {
        this(name);
        setValue(value);
    }

    public String getName() {
        return name;
    }

    public String getHexValue() {
        return hexValue.get();
    }

    public StringProperty hexValueProperty() {
        return hexValue;
    }

    public String getDecValue() {
        return decValue.get();
    }

    public StringProperty decValueProperty() {
        return decValue;
    }

    public boolean isChanged() {
        return changed.get();
    }

    public BooleanProperty changedProperty() {
        return changed;
    }

    public void setValue(int value) {
        setValue(value, 16);
    }

    public void setValue(long value, int bitWidth) {
        String newHex;
        String newDec;
        if (bitWidth >= 64) {
            newHex = "%016X".formatted(value);
            newDec = Long.toUnsignedString(value);
        } else if (bitWidth >= 32) {
            long masked = value & 0xFFFFFFFFL;
            newHex = "%08X".formatted(masked);
            newDec = Long.toUnsignedString(masked);
        } else {
            long masked = value & 0xFFFFL;
            newHex = "%04X".formatted(masked);
            newDec = Long.toString(masked);
        }
        if (!newHex.equals(hexValue.get())) {
            changed.set(true);
        } else {
            changed.set(false);
        }
        hexValue.set(newHex);
        decValue.set(newDec);
    }
}
