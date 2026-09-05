package combatant.client.features.gui.component.solid;

/** Text/caret/selection state for the shell search field. */
public final class SolidSearchState {
    private String text = "";
    private int cursor;
    private int anchor;

    public String text() { return text; }
    public int cursor() { return cursor; }
    public int selectionStart() { return Math.min(cursor, anchor); }
    public int selectionEnd() { return Math.max(cursor, anchor); }

    public void setText(String value) {
        text = value != null ? value : "";
        cursor = anchor = text.length();
    }

    public boolean type(char character) {
        if (Character.isISOControl(character)) return false;
        replaceSelection(String.valueOf(character));
        return true;
    }

    public boolean keyPressed(int keyCode, boolean shift) {
        return switch (keyCode) {
            case 259 -> { // backspace
                if (hasSelection()) replaceSelection("");
                else if (cursor > 0) { text = text.substring(0, cursor - 1) + text.substring(cursor); cursor--; anchor = cursor; }
                yield true;
            }
            case 261 -> { // delete
                if (hasSelection()) replaceSelection("");
                else if (cursor < text.length()) text = text.substring(0, cursor) + text.substring(cursor + 1);
                yield true;
            }
            case 263 -> { moveTo(Math.max(0, cursor - 1), shift); yield true; }
            case 262 -> { moveTo(Math.min(text.length(), cursor + 1), shift); yield true; }
            case 268 -> { moveTo(0, shift); yield true; }
            case 269 -> { moveTo(text.length(), shift); yield true; }
            default -> false;
        };
    }

    public void placeCursor(int index, boolean extendSelection) {
        moveTo(Math.max(0, Math.min(text.length(), index)), extendSelection);
    }

    private void moveTo(int next, boolean shift) {
        cursor = next;
        if (!shift) anchor = cursor;
    }

    private void replaceSelection(String replacement) {
        int start = selectionStart();
        int end = selectionEnd();
        text = text.substring(0, start) + replacement + text.substring(end);
        cursor = anchor = start + replacement.length();
    }

    private boolean hasSelection() { return cursor != anchor; }
}
