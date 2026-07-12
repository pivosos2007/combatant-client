/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public enum CooldownRender {
    ;
    private static final Minecraft mc = Minecraft.getInstance();
    private static final float SCALE = 0.75f; // немного меньше для аккуратного влезания в слот

    public static void renderTime(GuiGraphicsExtractor ctx, float progress, int x, int y, float secondsLeft) {
        if (mc.font == null || ctx == null) return;

        // формируем текст
        String textStr = (secondsLeft >= 10)
                ? ((int) secondsLeft) + "с"
                : String.format("%.1fс", secondsLeft);
        Component text = Component.literal(textStr);

        int textWidth = mc.font.width(text);
        int textHeight = mc.font.lineHeight;

        // центрирование по 16x16
        float centerX = x + 8 - (textWidth * SCALE / 2f);
        float centerY = y + 8 - (textHeight * SCALE / 2f);

        var matrices = ctx.pose();
        matrices.pushMatrix();
        matrices.translate(centerX, centerY); // правильный вызов: только x и y
        matrices.scale(SCALE, SCALE);

        ctx.text(mc.font, text, 0, 0, 0xFFFFFFFF, true);

        matrices.popMatrix();
    }
}
