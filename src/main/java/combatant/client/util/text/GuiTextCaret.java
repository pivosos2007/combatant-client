/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.text;

import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.render.engine.text.TextRenderer;

/**
 * Insertion caret rendered in the same logical projection as the surrounding text.
 *
 * <p>ClickGUI and BetterChat render under beginUnscaledLogical(): sizes here are
 * authored in logical text/layout units, never in framebuffer pixels. The active
 * projection performs the scaling together with the text and its layout.</p>
 */
public final class GuiTextCaret {
    private GuiTextCaret() { }

    /** Thin stroke relative to authored font size, in logical UI coordinates. */
    public static float width(float fontSize) {
        return Math.max(0.55f, Math.min(1.1f, fontSize * 0.055f));
    }

    /** Restrict the caret to the visual text line, rather than the whole input box. */
    public static float height(float fontSize, float glyphHeight) {
        return Math.max(0.5f, Math.min(glyphHeight * 0.76f, fontSize * 0.78f));
    }

    /** textY matches the top-left text origin passed to ClickGuiRenderer.drawText(). */
    public static void draw(float x, float textY, TextRenderer font, float fontSize, int color) {
        if (font == null) return;
        float glyphHeight = ClickGuiRenderer.textHeight(font, fontSize);
        if (glyphHeight <= 0f) return;
        float caretHeight = height(fontSize, glyphHeight);
        float top = textY + (glyphHeight - caretHeight) * 0.5f;
        ClickGuiRenderer.drawRect(x, top, width(fontSize), caretHeight, color);
    }

}
