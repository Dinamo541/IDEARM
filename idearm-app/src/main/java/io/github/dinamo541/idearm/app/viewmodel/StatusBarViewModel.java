package io.github.dinamo541.idearm.app.viewmodel;

import io.github.dinamo541.idearm.app.i18n.Message;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * State of the status bar at the bottom of the workbench window.
 *
 * <p>The status and the caret position are published as {@link Message} values instead of finished sentences:
 * the view resolves them in the language in use, so switching to Spanish also translates a status that is
 * already on screen.
 */
public final class StatusBarViewModel {

    private final ObjectProperty<Message> status =
            new SimpleObjectProperty<>(this, "status", Message.of("status.ready"));
    private final ObjectProperty<Message> caretPosition =
            new SimpleObjectProperty<>(this, "caretPosition", Message.of("status.caret", 1, 1));
    private final StringProperty targetProfile = new SimpleStringProperty("");
    private final StringProperty toolchain = new SimpleStringProperty("");
    private final StringProperty encoding = new SimpleStringProperty("UTF-8");

    public ReadOnlyObjectProperty<Message> statusProperty() {
        return status;
    }

    public Message getStatus() {
        return status.get();
    }

    public void setStatus(Message message) {
        FxDispatch.run(() -> status.set(message == null ? Message.EMPTY : message));
    }

    public ReadOnlyObjectProperty<Message> caretPositionProperty() {
        return caretPosition;
    }

    public Message getCaretPosition() {
        return caretPosition.get();
    }

    public void setCaretPosition(int line, int column) {
        FxDispatch.run(() -> caretPosition.set(Message.of("status.caret", line, column)));
    }

    public StringProperty targetProfileProperty() {
        return targetProfile;
    }

    public String getTargetProfile() {
        return targetProfile.get();
    }

    public void setTargetProfile(String profile) {
        FxDispatch.run(() -> targetProfile.set(profile != null ? profile : ""));
    }

    public StringProperty toolchainProperty() {
        return toolchain;
    }

    public String getToolchain() {
        return toolchain.get();
    }

    public void setToolchain(String toolchainId) {
        FxDispatch.run(() -> toolchain.set(toolchainId != null ? toolchainId : ""));
    }

    public StringProperty encodingProperty() {
        return encoding;
    }

    public String getEncoding() {
        return encoding.get();
    }

    public void setEncoding(String charset) {
        FxDispatch.run(() -> encoding.set(charset != null ? charset : ""));
    }
}
