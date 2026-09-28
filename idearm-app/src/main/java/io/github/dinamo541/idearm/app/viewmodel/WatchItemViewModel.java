package io.github.dinamo541.idearm.app.viewmodel;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.Objects;

/**
 * ViewModel for an expression watch entry in the visual debugger.
 */
public final class WatchItemViewModel {

    private final String expression;
    private final StringProperty value;
    /** True when the debugger could not evaluate the expression; the view says so in the user's language. */
    private final BooleanProperty error = new SimpleBooleanProperty(false);

    public WatchItemViewModel(String expression, String value) {
        this.expression = Objects.requireNonNull(expression, "expression cannot be null");
        this.value = new SimpleStringProperty(value != null ? value : "");
    }

    public boolean isError() {
        return error.get();
    }

    public BooleanProperty errorProperty() {
        return error;
    }

    /** Marks the expression as one this debugger cannot evaluate at the current stop. */
    public void setUnevaluated() {
        this.value.set("");
        this.error.set(true);
    }

    public String getExpression() {
        return expression;
    }

    public String getValue() {
        return value.get();
    }

    public void setValue(String val) {
        this.value.set(val != null ? val : "");
        this.error.set(false);
    }

    public StringProperty valueProperty() {
        return value;
    }
}
