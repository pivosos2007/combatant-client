/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud;

import combatant.client.util.text.FastFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public enum CooldownRender {
    ;
    private static final Minecraft mc = Minecraft.getInstance();
    private static final float SCALE = 0.75f; 

    public static void renderTime(GuiGraphicsExtractor ctx, float progress, int x, int y, float secondsLeft) {
        if (mc.font == null || ctx == null) return;

        String textStr = (secondsLeft >= 10)
                ? ((int) secondsLeft) + "с"
                : FastFormat.oneDecimal(secondsLeft) + "с";
        Component text = Component.literal(textStr);

        int textWidth = mc.font.width(text);
        int textHeight = mc.font.lineHeight;

        float centerX = x + 8 - (textWidth * SCALE / 2f);
        float centerY = y + 8 - (textHeight * SCALE / 2f);

        var matrices = ctx.pose();
        matrices.pushMatrix();
        matrices.translate(centerX, centerY); 
        matrices.scale(SCALE, SCALE);

        ctx.text(mc.font, text, 0, 0, 0xFFFFFFFF, true);

        matrices.popMatrix();
    }
}
