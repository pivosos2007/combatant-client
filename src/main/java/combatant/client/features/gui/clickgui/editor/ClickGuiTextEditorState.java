/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.editor;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.settings.TextEditorOwner;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.util.text.ClipboardUtil;
import combatant.client.util.text.TextSelection;

import static combatant.client.features.theme.Theme.theme;

public final class ClickGuiTextEditorState {
    private static final float WIDTH = 360f;
    private static final float HEIGHT = 240f;
    private static final float PADDING = 14f;
    private static final float TEXT_PADDING = 8f;
    private static final float BUTTON_W = 70f;
    private static final float BUTTON_H = 24f;
    private static final float LINE_H = 18f;

    private final TextEditorOwner owner;
    private final String title;
    private final StringBuilder buffer;
    private final boolean singleLine;
    private final float anchorX;
    private final float anchorY;
    private final float anchorW;
    private final TextSelection selection = new TextSelection();
    private int caret;
    private float modalX, modalY;
    private float textX, textY, textW, textH;
    private float saveX, saveY, saveW, saveH;
    private float cancelX, cancelY, cancelW, cancelH;
    private float scroll;
    private float scrollX;
    private int preferredColumn = -1;
    private boolean dragging = false;

    public ClickGuiTextEditorState(TextEditorOwner owner, String title, String seed, float anchorX, float anchorY, float anchorW) {
        this.owner = owner;
        this.title = title == null ? "Edit list" : title;
        this.singleLine = owner != null && owner.isSingleLine();
        String normalizedSeed = singleLine ? normalizeSingleLine(seed) : seed;
        this.buffer = new StringBuilder(normalizedSeed == null ? "" : normalizedSeed);
        this.caret = buffer.length();
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorW = anchorW;
        this.scroll = 0f;
        this.scrollX = 0f;
    }

    private static boolean inside(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String normalizeSingleLine(String text) {
        if (text == null) return "";
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        return normalized.replace('\n', ' ');
    }

    private static int[] caretLineAndColumn(String[] lines, int caretIndex) {
        int remaining = caretIndex;
        for (int i = 0; i < lines.length; i++) {
            int len = lines[i].length();
            if (remaining <= len) {
                return new int[]{i, remaining};
            }
            remaining -= len;
            if (i < lines.length - 1) remaining -= 1;
        }
        return new int[]{Math.max(0, lines.length - 1), lines.length == 0 ? 0 : lines[lines.length - 1].length()};
    }

    private static String safeSubstring(String s, int from, int to) {
        int start = Math.max(0, Math.min(s.length(), from));
        int end = Math.max(start, Math.min(s.length(), to));
        return s.substring(start, end);
    }

    public void handleMouseDown(float mx, float my, int button) {
        if (button != 0) return;

        layout(ClickGuiRenderer.framebufferWidth(), ClickGuiRenderer.framebufferHeight());

        if (!inside(mx, my, modalX, modalY, WIDTH, HEIGHT)) {
            ClickGuiRenderer.closeTextEditor(false);
            return;
        }

        if (inside(mx, my, saveX, saveY, saveW, saveH)) {
            ClickGuiRenderer.closeTextEditor(true);
            return;
        }

        if (inside(mx, my, cancelX, cancelY, cancelW, cancelH)) {
            ClickGuiRenderer.closeTextEditor(false);
            return;
        }

        if (inside(mx, my, textX, textY, textW, textH)) {
            int target = caretFromPoint(mx, my);
            if (isShiftDown()) {
                if (!selection.appliesToLine(0)) selection.update(0, caret);
                selection.updateCaret(target);
            } else {
                selection.clear();
            }
            caret = target;
            preferredColumn = -1;
            ensureCaretVisible();
            dragging = true;
            return;
        }

        selection.clear();
    }

    public void handleMouseUp(float mx, float my, int button) {
        if (button == 0) {
            dragging = false;
        }
    }

    public void handleMouseMove(float mx, float my) {
        if (!dragging) return;
        int target = caretFromPoint(mx, my);
        selection.update(0, target);
        caret = target;
        preferredColumn = -1;
        ensureCaretVisible();
    }

    public void insertChar(char c) {
        if (Character.isISOControl(c)) return;
        if (deleteSelection()) {
        }
        buffer.insert(caret, c);
        caret++;
        selection.clear();
        preferredColumn = -1;
        ensureCaretVisible();
        dragging = false;
    }

    public void insertNewline() {
        if (singleLine) return;
        if (deleteSelection()) {
        }
        buffer.insert(caret, '\n');
        caret++;
        selection.clear();
        preferredColumn = -1;
        ensureCaretVisible();
        dragging = false;
    }

    public void pasteFromClipboard() {
        String clip = ClipboardUtil.get();
        if (clip == null || clip.isEmpty()) return;
        String normalized = clip.replace("\r\n", "\n").replace('\r', '\n');
        if (singleLine) normalized = normalizeSingleLine(normalized);
        insertText(normalized);
    }

    public void backspace() {
        if (deleteSelection()) {
            preferredColumn = -1;
            ensureCaretVisible();
            return;
        }
        if (caret == 0) return;
        buffer.deleteCharAt(caret - 1);
        caret--;
        selection.clear();
        preferredColumn = -1;
        ensureCaretVisible();
        dragging = false;
    }

    public void deleteForward() {
        if (deleteSelection()) {
            preferredColumn = -1;
            ensureCaretVisible();
            return;
        }
        if (caret >= buffer.length()) return;
        buffer.deleteCharAt(caret);
        selection.clear();
        preferredColumn = -1;
        ensureCaretVisible();
        dragging = false;
    }

    public void moveCaret(int delta, boolean extend) {
        int target = clamp(caret + delta, 0, buffer.length());
        updateSelectionForMove(target, extend);
        caret = target;
        preferredColumn = -1;
        ensureCaretVisible();
    }

    public void moveCaretToStart(boolean extend) {
        updateSelectionForMove(0, extend);
        caret = 0;
        preferredColumn = -1;
        ensureCaretVisible();
    }

    public void moveCaretToEnd(boolean extend) {
        int end = buffer.length();
        updateSelectionForMove(end, extend);
        caret = end;
        preferredColumn = -1;
        ensureCaretVisible();
    }

    public void moveCaretVertical(int dir, boolean extend) {
        String[] lines = buffer.toString().split("\\n", -1);
        int[] pos = caretLineAndColumn(lines, caret);
        int currentCol = pos[1];
        if (preferredColumn < 0) preferredColumn = currentCol;
        int targetLine = clamp(pos[0] + dir, 0, Math.max(0, lines.length - 1));
        int targetCol = Math.min(preferredColumn, lines[targetLine].length());
        int targetIndex = indexForLineColumn(lines, targetLine, targetCol);
        updateSelectionForMove(targetIndex, extend);
        caret = targetIndex;
        ensureCaretVisible();
    }

    public void copySelection() {
        if (selection.appliesToLine(0) && selection.hasRange()) {
            int start = Math.min(selection.start(), selection.end());
            int end = Math.max(selection.start(), selection.end()) + 1;
            start = clamp(start, 0, buffer.length());
            end = clamp(end, 0, buffer.length());
            if (end > start) {
                ClipboardUtil.copy(buffer.substring(start, end));
                return;
            }
        }
        if (buffer.length() > 0) {
            ClipboardUtil.copy(buffer.toString());
        }
    }

    public void selectAll() {
        selection.update(0, 0);
        selection.updateCaret(buffer.length());
        caret = buffer.length();
        preferredColumn = -1;
        ensureCaretVisible();
    }

    public void save() {
        owner.applyEditorText(buffer.toString());
    }

    public void scroll(double delta) {
        String[] lines = buffer.toString().split("\\n", -1);
        float visible = textH - TEXT_PADDING * 2f;
        float content = lines.length * LINE_H;
        float minScroll = Math.min(0f, visible - content);
        scroll += (float) (delta * LINE_H * 1.5f);
        scroll = clamp(scroll, minScroll, 0f);
    }

    public void render(int fbw, int fbh) {
        layout(fbw, fbh);

        ClickGuiRenderer.drawRoundedRect(modalX, modalY, WIDTH, HEIGHT, 12f, theme().windowBg());
        ClickGuiRenderer.drawRoundedRectStroke(modalX, modalY, WIDTH, HEIGHT, 12f, 1.2f, theme().windowStroke());

        TextRenderer titleFont = ClickGuiRenderer.getIosevkaRegular();
        float titleSize = 16f;
        ClickGuiRenderer.drawText(titleFont, title, modalX + PADDING, modalY + 12f, titleSize, theme().textPrimary(), false);
        if (!singleLine) {
            ClickGuiRenderer.drawText(ClickGuiRenderer.getIosevkaRegular(), "One entry per line", modalX + PADDING, modalY + 30f, 12f, theme().textMuted(), false);
        }

        ClickGuiRenderer.drawRoundedRect(textX, textY, textW, textH, 8f, theme().surface());
        ClickGuiRenderer.drawRoundedRectStroke(textX, textY, textW, textH, 8f, 1.0f, theme().strokeSoft());

        String[] lines = buffer.toString().split("\\n", -1);
        TextRenderer textFont = ClickGuiRenderer.getIosevkaRegular();
        float fontSize = 15f;
        float viewX = textX + TEXT_PADDING - scrollX;
        float viewY = textY + TEXT_PADDING + scroll;
        float lineTop = viewY;

        float scissorX = textX + TEXT_PADDING;
        float scissorY = textY + TEXT_PADDING;
        float scissorW = textW - TEXT_PADDING * 2f;
        float scissorH = textH - TEXT_PADDING * 2f;

        boolean clipped = ScissorFunction.pushRaw(scissorX, scissorY, scissorW, scissorH);
        int globalOffset = 0;
        for (int idx = 0; idx < lines.length; idx++) {
            String line = lines[idx];
            if (selection.appliesToLine(0) && selection.hasRange()) {
                int selStart = Math.min(selection.start(), selection.end());
                int selEnd = Math.max(selection.start(), selection.end()) + 1;
                int selA = Math.max(globalOffset, selStart);
                int selB = Math.min(globalOffset + line.length(), selEnd);
                if (selB > selA) {
                    int colA = selA - globalOffset;
                    int colB = selB - globalOffset;
                    float x0 = viewX + ClickGuiRenderer.textWidth(textFont, safeSubstring(line, 0, colA), fontSize);
                    float x1 = viewX + ClickGuiRenderer.textWidth(textFont, safeSubstring(line, 0, colB), fontSize);
                    float y0 = lineTop;
                    ClickGuiRenderer.drawRoundedRect(x0, y0, Math.max(1f, x1 - x0), LINE_H, 3f, theme().accentSoft());
                }
            }
            float textY = lineTop + (LINE_H - ClickGuiRenderer.textHeight(textFont, fontSize)) * 0.5f;
            ClickGuiRenderer.drawText(textFont, line, viewX, textY, fontSize, theme().textPrimary(), false);
            lineTop += LINE_H;
            globalOffset += line.length();
            if (idx < lines.length - 1) globalOffset++;
        }

        int[] caretPos = caretLineAndColumn(lines, caret);
        float caretLineTop = viewY + caretPos[0] * LINE_H;
        String caretLine = caretPos[0] < lines.length ? lines[caretPos[0]] : "";
        float caretOffsetX = ClickGuiRenderer.textWidth(textFont, safeSubstring(caretLine, 0, caretPos[1]), fontSize);
        float cx = viewX + caretOffsetX;
        float cy0 = caretLineTop + 2f;
        float cy1 = caretLineTop + LINE_H - 2f;
        if (AnimationUtility.blink(500L)) {
            ClickGuiRenderer.drawLine(cx, cy0, cx, cy1, theme().textPrimary());
        }
        if (clipped) ScissorFunction.pop();

        float contentHeight = lines.length * LINE_H;
        float visibleHeight = scissorH;
        if (contentHeight > visibleHeight + 0.5f) {
            float trackX = scissorX + scissorW - 6f;
            float trackY = scissorY;
            float trackW = 4f;
            float trackH = scissorH;

            float thumbH = Math.max(18f, trackH * (visibleHeight / contentHeight));
            float maxScroll = contentHeight - visibleHeight;
            float scrollNorm = Math.min(1f, Math.max(0f, -scroll / maxScroll));
            float thumbY = trackY + (trackH - thumbH) * scrollNorm;

            ClickGuiRenderer.drawRoundedRect(trackX, trackY, trackW, trackH, 3f, theme().surface());
            ClickGuiRenderer.drawRoundedRect(trackX, thumbY, trackW, thumbH, 3f, theme().strokeSoft());
        }

        int saveBg = theme().accent();
        int cancelBg = theme().surface();
        float mx = ClickGuiRenderer.getMouseX();
        float my = ClickGuiRenderer.getMouseY();
        boolean hoverCancel = inside(mx, my, cancelX, cancelY, cancelW, cancelH);
        boolean hoverSave = inside(mx, my, saveX, saveY, saveW, saveH);

        int cancelFill = hoverCancel ? theme().surfaceHover() : cancelBg;
        int saveFill = hoverSave ? ClickGuiRenderer.mixColor(saveBg, theme().surfaceHover(), 0.35f) : saveBg;

        ClickGuiRenderer.drawRoundedRect(cancelX, cancelY, cancelW, cancelH, 8f, cancelFill);
        ClickGuiRenderer.drawRoundedRectStroke(cancelX, cancelY, cancelW, cancelH, 8f, 1.05f, theme().strokeSoft());
        float cancelTextY = cancelY + (cancelH - ClickGuiRenderer.textHeight(textFont, 14f)) * 0.5f;
        ClickGuiRenderer.drawText(textFont, "Cancel", cancelX + 14f, cancelTextY, 14f, theme().textPrimary(), false);

        ClickGuiRenderer.drawRoundedRect(saveX, saveY, saveW, saveH, 8f, saveFill);
        ClickGuiRenderer.drawRoundedRectStroke(saveX, saveY, saveW, saveH, 8f, 1.05f, theme().strokeSoft());
        float saveTextY = saveY + (saveH - ClickGuiRenderer.textHeight(textFont, 14f)) * 0.5f;
        ClickGuiRenderer.drawText(textFont, "Save", saveX + 20f, saveTextY, 14f, theme().textPrimary(), false);
    }

    private void layout(int fbw, int fbh) {
        float margin = 12f;
        modalX = clamp(anchorX - WIDTH + anchorW + margin, margin, fbw - WIDTH - margin);
        modalY = clamp(anchorY - HEIGHT / 2f, margin, fbh - HEIGHT - margin);

        textX = modalX + PADDING;
        textY = modalY + 58f;
        textW = WIDTH - PADDING * 2f;
        textH = HEIGHT - PADDING * 3f - BUTTON_H;

        saveW = BUTTON_W;
        saveH = BUTTON_H;
        saveX = modalX + WIDTH - PADDING - saveW - 14f;
        saveY = modalY + HEIGHT - PADDING - saveH;

        cancelW = BUTTON_W;
        cancelH = BUTTON_H;
        cancelX = saveX - cancelW - 8f;
        cancelY = saveY;
    }

    private boolean isShiftDown() {
        var window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT) ||
                InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    private int caretFromPoint(float mx, float my) {
        String[] lines = buffer.toString().split("\\n", -1);
        float localY = my - (textY + TEXT_PADDING) - scroll;
        int lineIdx = clamp((int) Math.floor(localY / LINE_H), 0, Math.max(0, lines.length - 1));
        int lineStart = 0;
        for (int i = 0; i < lineIdx; i++) {
            lineStart += lines[i].length();
            if (i < lines.length - 1) lineStart++;
        }
        String line = lines.length > 0 ? lines[lineIdx] : "";
        float relX = mx - (textX + TEXT_PADDING) + scrollX;
        if (relX < 0f) relX = 0f;
        int col = columnForX(line, relX);
        return Math.min(buffer.length(), lineStart + col);
    }

    private int columnForX(String line, float relX) {
        if (line == null || line.isEmpty()) return 0;
        TextRenderer tr = ClickGuiRenderer.getIosevkaRegular();
        float size = 15f;
        float acc = 0f;
        for (int i = 0; i < line.length(); i++) {
            String ch = String.valueOf(line.charAt(i));
            float w = ClickGuiRenderer.textWidth(tr, ch, size);
            if (acc + w * 0.5f >= relX) return i;
            acc += w;
        }
        return line.length();
    }

    private void insertText(String text) {
        if (text == null || text.isEmpty()) return;
        String insert = singleLine ? normalizeSingleLine(text) : text;
        if (deleteSelection()) {
        }
        buffer.insert(caret, insert);
        caret += insert.length();
        selection.clear();
        preferredColumn = -1;
        ensureCaretVisible();
        dragging = false;
    }

    private int indexForLineColumn(String[] lines, int line, int column) {
        int idx = 0;
        for (int i = 0; i < lines.length; i++) {
            if (i == line) {
                return idx + column;
            }
            idx += lines[i].length();
            if (i < lines.length - 1) idx++;
        }
        return buffer.length();
    }

    private void updateSelectionForMove(int target, boolean extend) {
        if (extend) {
            if (!selection.appliesToLine(0)) selection.update(0, caret);
            selection.updateCaret(target);
        } else {
            selection.clear();
        }
    }

    private boolean deleteSelection() {
        if (!selection.appliesToLine(0) || !selection.hasRange()) return false;
        int start = Math.min(selection.start(), selection.end());
        int end = Math.max(selection.start(), selection.end()) + 1;
        start = clamp(start, 0, buffer.length());
        end = clamp(end, 0, buffer.length());
        if (end <= start) {
            selection.clear();
            return false;
        }
        buffer.delete(start, end);
        caret = start;
        selection.clear();
        return true;
    }

    private void ensureCaretVisible() {
        String[] lines = buffer.toString().split("\\n", -1);
        float visibleY = textH - TEXT_PADDING * 2f;
        float contentY = lines.length * LINE_H;
        float minScrollY = Math.min(0f, visibleY - contentY);
        int[] caretPos = caretLineAndColumn(lines, caret);
        float caretY = caretPos[0] * LINE_H + scroll;
        float bottom = caretY + LINE_H;

        if (caretY < 0f) {
            scroll = clamp(scroll - caretY, minScrollY, 0f);
        } else if (bottom > visibleY) {
            float diff = bottom - visibleY;
            scroll = clamp(scroll - diff, minScrollY, 0f);
        } else {
            scroll = clamp(scroll, minScrollY, 0f);
        }

        TextRenderer tr = ClickGuiRenderer.getIosevkaRegular();
        float size = 15f;
        float visibleW = Math.max(1f, textW - TEXT_PADDING * 2f);
        float caretOffsetX = 0f;
        if (lines.length > 0 && caretPos[0] >= 0 && caretPos[0] < lines.length) {
            caretOffsetX = ClickGuiRenderer.textWidth(tr, safeSubstring(lines[caretPos[0]], 0, caretPos[1]), size);
        }
        float maxWidth = 0f;
        for (String line : lines) {
            float w = ClickGuiRenderer.textWidth(tr, line, size);
            if (w > maxWidth) maxWidth = w;
        }
        float maxScrollX = Math.max(0f, maxWidth - visibleW);
        float caretRelativeX = caretOffsetX - scrollX;
        if (caretRelativeX < 0f) {
            scrollX = clamp(scrollX + caretRelativeX, 0f, maxScrollX);
        } else if (caretRelativeX > visibleW) {
            scrollX = clamp(scrollX + (caretRelativeX - visibleW), 0f, maxScrollX);
        } else {
            scrollX = clamp(scrollX, 0f, maxScrollX);
        }
    }
}
