/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.text;

import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.ScissorFunction;
import org.lwjgl.glfw.GLFW;

/** Shared single-line editing, selection and horizontal viewport for custom GUI fields. */
public final class SingleLineTextInput {
    private static SingleLineTextInput mouseCapture;
    private final int limit;
    private final java.util.function.IntPredicate allowed;
    private String value = "";
    private int cursor;
    private int anchor;
    private float offset;
    private float viewX;
    private float viewW;
    private float fontSize;
    private TextRenderer font;
    private long lastClickMs;
    private int lastClickIndex = -1;

    public SingleLineTextInput(int limit) {
        this(limit, ch -> !Character.isISOControl(ch));
    }

    public SingleLineTextInput(int limit, java.util.function.IntPredicate allowed) {
        this.limit = Math.max(1, limit);
        this.allowed = allowed != null ? allowed : ch -> !Character.isISOControl(ch);
    }

    public String text() { return value; }
    public int cursor() { return cursor; }
    public int selectionStart() { return Math.min(anchor, cursor); }
    public int selectionEnd() { return Math.max(anchor, cursor); }
    public boolean hasSelection() { return anchor != cursor; }

    public void setText(String next) {
        value = filter(normalize(next));
        if (value.length() > limit) value = value.substring(0, limit);
        cursor = Math.min(cursor, value.length());
        anchor = Math.min(anchor, value.length());
        offset = 0f;
    }

    public void clear() { setText(""); cursor = anchor = 0; }
    public void moveToEnd() { cursor = anchor = value.length(); }
    public void unfocus() { if (mouseCapture == this) mouseCapture = null; }

    private static String normalize(String s) {
        return s == null ? "" : s.replace('\r', ' ').replace('\n', ' ');
    }

    private String filter(String s) {
        StringBuilder result = new StringBuilder(s.length());
        s.codePoints().filter(allowed).forEach(result::appendCodePoint);
        return result.toString();
    }

    public void type(char c) {
        if (allowed.test(c)) insert(String.valueOf(c));
    }

    public void insert(String text) {
        String normalized = filter(normalize(text));
        if (normalized.isEmpty()) return;
        int from = selectionStart(), to = selectionEnd();
        int available = Math.max(0, limit - (value.length() - (to - from)));
        if (available == 0) return;
        if (normalized.length() > available) normalized = normalized.substring(0, available);
        value = value.substring(0, from) + normalized + value.substring(to);
        cursor = anchor = from + normalized.length();
    }

    public void backspace() {
        if (eraseSelection()) return;
        if (cursor == 0) return;
        value = value.substring(0, cursor - 1) + value.substring(cursor);
        cursor = anchor = cursor - 1;
    }

    public void delete() {
        if (eraseSelection()) return;
        if (cursor >= value.length()) return;
        value = value.substring(0, cursor) + value.substring(cursor + 1);
        anchor = cursor;
    }

    private boolean eraseSelection() {
        if (!hasSelection()) return false;
        int from = selectionStart(), to = selectionEnd();
        value = value.substring(0, from) + value.substring(to);
        cursor = anchor = from;
        return true;
    }

    public void selectAll() { anchor = 0; cursor = value.length(); }
    public void copy() { if (hasSelection()) ClipboardUtil.copy(value.substring(selectionStart(), selectionEnd())); }
    public void cut() { if (hasSelection()) { copy(); eraseSelection(); } }

    public void move(int pos, boolean extend) {
        cursor = Math.max(0, Math.min(value.length(), pos));
        if (!extend) anchor = cursor;
    }

    private int wordBoundary(int pos, int direction) {
        int i = Math.max(0, Math.min(value.length(), pos));
        if (direction < 0) {
            while (i > 0 && Character.isWhitespace(value.charAt(i - 1))) i--;
            if (i > 0) {
                boolean word = Character.isLetterOrDigit(value.charAt(i - 1)) || value.charAt(i - 1) == '_';
                while (i > 0 && (Character.isLetterOrDigit(value.charAt(i - 1)) || value.charAt(i - 1) == '_') == word && !Character.isWhitespace(value.charAt(i - 1))) i--;
            }
        } else {
            if (i < value.length()) {
                boolean word = Character.isLetterOrDigit(value.charAt(i)) || value.charAt(i) == '_';
                while (i < value.length() && (Character.isLetterOrDigit(value.charAt(i)) || value.charAt(i) == '_') == word && !Character.isWhitespace(value.charAt(i))) i++;
            }
            while (i < value.length() && Character.isWhitespace(value.charAt(i))) i++;
        }
        return i;
    }

    /** Returns true for keys owned by a focused text field (including unrecognized shortcuts). */
    public boolean keyPressed(int key, int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (ctrl) {
            switch (key) {
                case GLFW.GLFW_KEY_A -> selectAll();
                case GLFW.GLFW_KEY_C -> copy();
                case GLFW.GLFW_KEY_X -> cut();
                case GLFW.GLFW_KEY_V -> insert(ClipboardUtil.get());
                case GLFW.GLFW_KEY_LEFT -> move(wordBoundary(cursor, -1), shift);
                case GLFW.GLFW_KEY_RIGHT -> move(wordBoundary(cursor, 1), shift);
                case GLFW.GLFW_KEY_BACKSPACE -> { if (!hasSelection()) move(wordBoundary(cursor, -1), true); eraseSelection(); }
                case GLFW.GLFW_KEY_DELETE -> { if (!hasSelection()) move(wordBoundary(cursor, 1), true); eraseSelection(); }
                case GLFW.GLFW_KEY_HOME -> move(0, shift);
                case GLFW.GLFW_KEY_END -> move(value.length(), shift);
            }
            return true;
        }
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> backspace();
            case GLFW.GLFW_KEY_DELETE -> delete();
            case GLFW.GLFW_KEY_LEFT -> move(!shift && hasSelection() ? selectionStart() : cursor - 1, shift);
            case GLFW.GLFW_KEY_RIGHT -> move(!shift && hasSelection() ? selectionEnd() : cursor + 1, shift);
            case GLFW.GLFW_KEY_HOME -> move(0, shift);
            case GLFW.GLFW_KEY_END -> move(value.length(), shift);
            default -> { return false; }
        }
        return true;
    }

    public void beginDrag(float mouseX, boolean shift) {
        if (font == null) return;
        int hit = hit(mouseX);
        long now = System.currentTimeMillis();
        boolean doubleClick = !shift && lastClickIndex == hit && now - lastClickMs <= 350;
        if (doubleClick) {
            anchor = wordBoundary(hit, -1);
            cursor = wordBoundary(hit, 1);
        } else {
            if (!shift) anchor = hit;
            cursor = hit;
        }
        lastClickMs = now;
        lastClickIndex = hit;
        mouseCapture = this;
    }

    public static void mouseMoved(float mouseX) {
        if (mouseCapture != null) mouseCapture.move(mouseCapture.hit(mouseX), true);
    }

    public static void mouseReleased() { mouseCapture = null; }

    public void layout(float x, float width, TextRenderer textFont, float size) {
        layout(x, width, textFont, size, false);
    }

    public void layout(float x, float width, TextRenderer textFont, float size, boolean mask) {
        masked = mask;
        viewX = x;
        viewW = Math.max(1f, width);
        font = textFont;
        fontSize = size;
        keepCursorVisible();
    }

    private boolean masked;
    private String displayed() { return masked ? "*".repeat(value.length()) : value; }
    private float width(String s) { return ClickGuiRenderer.textWidth(font, s, fontSize); }
    private float pos(int index) { return width(displayed().substring(0, Math.max(0, Math.min(index, value.length())))); }

    private int hit(float mouseX) {
        if (font == null) return cursor;
        float px = Math.max(0f, mouseX - viewX + offset);
        for (int i = 0; i < value.length(); i++) {
            float a = pos(i), b = pos(i + 1);
            if (px < (a + b) * 0.5f) return i;
        }
        return value.length();
    }

    private void keepCursorVisible() {
        if (font == null) return;
        float x = pos(cursor);
        if (x - offset < 0) offset = x;
        float caretWidth = GuiTextCaret.width(fontSize);
        if (x - offset > viewW - caretWidth) offset = x - viewW + caretWidth;
        offset = Math.max(0f, Math.min(offset, Math.max(0f, width(displayed()) - viewW)));
    }

    /** Draws text and selection in a clipped single-line viewport; y is the text baseline position used by drawText. */
    public void render(float x, float y, float w, float h, TextRenderer textFont, float size,
                       int color, int selectionColor, boolean focused) {
        render(x, y, w, h, textFont, size, color, selectionColor, focused, false);
    }

    public void render(float x, float y, float w, float h, TextRenderer textFont, float size,
                       int color, int selectionColor, boolean focused, boolean mask) {
        layout(x, w, textFont, size, mask);
        float drawX = x - offset;
        boolean clipped = ScissorFunction.pushRaw(x, y - h * 0.35f, Math.max(1f, w), Math.max(1f, h * 1.7f));
        if (focused && hasSelection()) {
            float left = drawX + pos(selectionStart());
            float right = drawX + pos(selectionEnd());
            ClickGuiRenderer.drawRect(left, y - h * 0.12f, Math.max(0f, right - left), h * 0.95f, selectionColor);
        }
        ClickGuiRenderer.drawText(textFont, displayed(), drawX, y, size, color, false);
        if (focused && (System.currentTimeMillis() / 500L) % 2 == 0) {
            GuiTextCaret.draw(drawX + pos(cursor), y, textFont, size, color);
        }
        if (clipped) ScissorFunction.pop();
    }
}
