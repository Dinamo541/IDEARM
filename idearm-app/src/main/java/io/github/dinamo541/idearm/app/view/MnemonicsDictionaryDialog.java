package io.github.dinamo541.idearm.app.view;

import io.github.dinamo541.idearm.app.i18n.Localization;
import io.github.dinamo541.idearm.app.viewmodel.KnowledgeViewModel;
import io.github.dinamo541.idearm.app.viewmodel.MnemonicsDictionaryViewModel;
import javafx.stage.Window;

/**
 * Academic Assistance: Interactive Assembly Mnemonics Dictionary and Command Reference.
 * Maintained as an extension of {@link AcademicCenterView} for seamless backward compatibility.
 */
public class MnemonicsDictionaryDialog extends AcademicCenterView {

    public MnemonicsDictionaryDialog(Window owner, Localization localization) {
        super(owner, localization);
    }

    public MnemonicsDictionaryDialog(Window owner, Localization localization, MnemonicsDictionaryViewModel viewModel) {
        super(owner, localization, viewModel);
    }

    public MnemonicsDictionaryDialog(Window owner, Localization localization, KnowledgeViewModel viewModel) {
        super(owner, localization, viewModel);
    }
}
