/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.autoanchor;

import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.text.TextRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import java.util.Locale;

public final class AutoAnchorRenderUtil {
    private AutoAnchorRenderUtil() {
    }

    public static String formatDamage(float value) {
        if (value <= 0.0f) return "";
        String text = String.format(Locale.US, "%.2f", value);
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == '0') end--;
        if (end > 0 && text.charAt(end - 1) == '.') end--;
        return text.substring(0, Math.max(end, 0));
    }

    public static double measureWidth(TextRenderer renderer, String text, double scale) {
        if (renderer == null || text == null || text.isEmpty()) return 0.0;
        renderer.begin(scale, true, false);
        try {
            return renderer.getWidth(text, false);
        } finally {
            renderer.end();
        }
    }

    public static AABB lerpBox(AABB from, AABB to, float progress) {
        float t = Mth.clamp(progress, 0.0f, 1.0f);
        return new AABB(
                Mth.lerp(t, from.minX, to.minX),
                Mth.lerp(t, from.minY, to.minY),
                Mth.lerp(t, from.minZ, to.minZ),
                Mth.lerp(t, from.maxX, to.maxX),
                Mth.lerp(t, from.maxY, to.maxY),
                Mth.lerp(t, from.maxZ, to.maxZ)
        );
    }

    public static int applyOpacity(int argb, float opacity) {
        opacity = Math.max(0.0f, Math.min(1.0f, opacity));
        int alpha = (argb >>> 24) & 0xFF;
        int outAlpha = Mth.clamp((int) (alpha * opacity), 0, 255);
        return (outAlpha << 24) | (argb & 0x00FFFFFF);
    }

    public static void addFilledBox(Renderer3D renderer, AABB box, int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;

        renderer.quad(minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        renderer.quad(minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, r, g, b, a);
        renderer.quad(minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        renderer.quad(minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, r, g, b, a);
        renderer.quad(maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, r, g, b, a);
        renderer.quad(minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);
    }

    public static void addOutlineBox(Renderer3D renderer, AABB box, int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;

        renderer.line(minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        renderer.line(maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        renderer.line(maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        renderer.line(minX, minY, maxZ, minX, minY, minZ, r, g, b, a);

        renderer.line(minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        renderer.line(maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        renderer.line(maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        renderer.line(minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);

        renderer.line(minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        renderer.line(maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        renderer.line(maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        renderer.line(minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }
}
