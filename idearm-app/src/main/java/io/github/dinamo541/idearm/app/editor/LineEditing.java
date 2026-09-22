package io.github.dinamo541.idearm.app.editor;

/** Pure line transformations. Edits replace only the affected block, preserving the editor undo history. */
public final class LineEditing {
    private LineEditing() {}
    public record Edit(int start, int end, String text, int anchor, int caret) {}
    public record Lines(int start, int end) {}

    public static Lines lines(String text, int anchor, int caret) {
        int low = Math.min(anchor, caret), high = Math.max(anchor, caret);
        int start = text.lastIndexOf('\n', low - 1) + 1;
        if (high > low && text.charAt(high - 1) == '\n') high--;
        int end = text.indexOf('\n', high);
        return new Lines(start, end < 0 ? text.length() : end);
    }

    public static Edit move(String text, int anchor, int caret, boolean down) {
        var block = lines(text, anchor, caret);
        String selected = text.substring(block.start(), block.end());
        if (down) {
            if (block.end() == text.length()) return null;
            int end = text.indexOf('\n', block.end() + 1);
            if (end < 0) end = text.length();
            String next = text.substring(block.end() + 1, end);
            int delta = next.length() + 1;
            return new Edit(block.start(), end, next + "\n" + selected,
                    Math.min(text.length(), anchor + delta), Math.min(text.length(), caret + delta));
        }
        if (block.start() == 0) return null;
        int start = text.lastIndexOf('\n', block.start() - 2) + 1;
        String previous = text.substring(start, block.start() - 1);
        int delta = block.start() - start;
        return new Edit(start, block.end(), selected + "\n" + previous, anchor - delta, caret - delta);
    }

    public static Edit duplicate(String text, int anchor, int caret, boolean down) {
        var block = lines(text, anchor, caret);
        String selected = text.substring(block.start(), block.end());
        int delta = down ? selected.length() + 1 : 0;
        return new Edit(block.start(), block.end(), selected + "\n" + selected, anchor + delta, caret + delta);
    }

    public static Edit delete(String text, int anchor, int caret) {
        var block = lines(text, anchor, caret);
        int start = block.start(), end = block.end();
        if (end < text.length()) end++;
        else if (start > 0) start--;
        return new Edit(start, end, "", start, start);
    }

    public static Edit insert(String text, int caret, boolean above) {
        var block = lines(text, caret, caret);
        String line = text.substring(block.start(), block.end());
        String indent = line.substring(0, line.length() - line.stripLeading().length());
        int position = above ? block.start() : block.end();
        String replacement = above ? indent + "\n" : "\n" + indent;
        int target = position + indent.length() + (above ? 0 : 1);
        return new Edit(position, position, replacement, target, target);
    }

    public static Edit transform(String text, int anchor, int caret, String operation) {
        var block = lines(text, anchor, caret);
        String[] rows = text.substring(block.start(), block.end()).split("\n", -1);
        boolean uncomment = java.util.Arrays.stream(rows).filter(s -> !s.isBlank())
                .allMatch(s -> s.stripLeading().startsWith(";"));
        for (int i = 0; i < rows.length; i++) {
            String row = rows[i];
            if (operation.equals("indent")) rows[i] = "    " + row;
            else if (operation.equals("outdent")) rows[i] = row.startsWith("\t") ? row.substring(1)
                    : row.substring(Math.min(4, row.length() - row.stripLeading().length()));
            else if (!row.isBlank()) {
                int at = row.length() - row.stripLeading().length();
                int remove = uncomment ? (row.startsWith("; ", at) ? 2 : 1) : 0;
                rows[i] = row.substring(0, at) + (uncomment ? "" : "; ") + row.substring(at + remove);
            }
        }
        String changed = String.join("\n", rows);
        return new Edit(block.start(), block.end(), changed, block.start(), block.start() + changed.length());
    }
}
