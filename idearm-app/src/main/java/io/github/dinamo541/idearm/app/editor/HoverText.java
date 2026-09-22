package io.github.dinamo541.idearm.app.editor;

import io.github.dinamo541.idearm.app.i18n.Localization;
import io.github.dinamo541.idearm.application.editor.HoverInfo;
import io.github.dinamo541.idearm.application.editor.QueryHover;
import java.util.Locale;

/**
 * Writes the hover of a number or a symbol in the user's language. The application layer gives the facts (the
 * value, where a symbol is defined); the words come from the message bundles (ADR-006). Instruction hovers already
 * come from the bilingual instruction catalog and are shown as they are.
 */
public final class HoverText {

    private HoverText() {
    }

    public static HoverInfo localize(HoverInfo info, Localization localization) {
        if (info.number() != null) {
            return number(info, info.number(), localization);
        }
        if (info.symbol() != null) {
            return symbol(info, info.symbol(), localization);
        }
        return info;
    }

    private static HoverInfo number(HoverInfo info, long value, Localization localization) {
        String hex = "0x" + Long.toHexString(value).toUpperCase(Locale.ROOT);
        var description = new StringBuilder()
                .append(localization.get("hover.number.decimal")).append(": ").append(value).append('\n')
                .append(localization.get("hover.number.hexadecimal")).append(": ").append(hex).append('\n')
                .append(localization.get("hover.number.binary")).append(": ").append(QueryHover.formatBinary(value));
        if (value >= 32 && value <= 126) {
            description.append('\n').append(localization.get("hover.number.ascii")).append(": '")
                    .append((char) value).append('\'');
        }
        String literal = info.title().substring(info.title().lastIndexOf(' ') + 1);
        return new HoverInfo(localization.get("hover.number.title", literal), info.syntax(), description.toString(),
                null, null, info.kind(), info.number(), null);
    }

    private static HoverInfo symbol(HoverInfo info, HoverInfo.SymbolFacts facts, Localization localization) {
        var description = new StringBuilder(localization.get("hover.symbol.definedIn", facts.file(),
                String.valueOf(facts.line())));
        if (facts.liveValue() != null) {
            long live = facts.liveValue();
            description.append("\n\n").append(localization.get("hover.symbol.liveValue",
                    "0x" + Long.toHexString(live).toUpperCase(Locale.ROOT), String.valueOf(live)));
        }
        String title = localization.get("hover.symbol.kind." + facts.kind()) + " " + facts.name();
        return new HoverInfo(title, info.syntax(), description.toString(), null, null, info.kind(), null, facts);
    }
}
