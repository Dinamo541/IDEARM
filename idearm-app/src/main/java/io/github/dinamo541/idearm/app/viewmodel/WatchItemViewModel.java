package io.github.dinamo541.idearm.app.viewmodel;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.Objects;

/**
 * ViewModel for an expression watch entry in the visual debugger.
 */
public final class WatchItemViewModel {

    private final String expression;
    private final StringProperty value;

    public WatchItemViewModel(String expression, String value) {
        this.expression = Objects.requireNonNull(expression, "expression cannot be null");
        this.value = new SimpleStringProperty(value != null ? value : "");
    }

    public String getExpression() {
        return expression;
    }

    public String getValue() {
        return value.get();
    }

    public void setValue(String val) {
        this.value.set(val != null ? val : "");
    }

    public StringProperty valueProperty() {
        return value;
    }
}
