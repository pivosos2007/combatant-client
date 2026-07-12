/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.color;

import java.awt.*;

public enum ColorUtils {
    ;

    public static Color rainbow(int speed, int index, float saturation, float brightness, float opacity) {
        int angle = (int) ((System.currentTimeMillis() / speed + index) % 360);
        float hue = angle / 360f;
        Color color = new Color(Color.HSBtoRGB(hue, saturation, brightness));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), clampAlpha(opacity));
    }

    public static Color skyRainbow(int speed, int index) {
        int angle = (int) ((System.currentTimeMillis() / speed + index) % 360);
        float hue = ((angle %= 360) / 360.0) < 0.5 ? -((float) (angle / 360.0)) : (float) (angle / 360.0);
        return Color.getHSBColor(hue, 0.5f, 1.0f);
    }

    public static Color fade(int speed, int index, Color color, float alpha) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        int angle = (int) ((System.currentTimeMillis() / speed + index) % 360);
        angle = (angle > 180 ? 360 - angle : angle) + 180;
        Color colorHSB = new Color(Color.HSBtoRGB(hsb[0], hsb[1], angle / 360f));
        return new Color(colorHSB.getRed(), colorHSB.getGreen(), colorHSB.getBlue(), clampAlpha(alpha));
    }

    public static Color twoColorEffect(Color c1, Color c2, double speed, double count) {
        int angle = (int) (((System.currentTimeMillis()) / speed + count) % 360);
        angle = (angle >= 180 ? 360 - angle : angle) * 2;
        return interpolateColorC(c1, c2, angle / 360f);
    }

    public static Color getAnalogousColor(Color color) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        float degree = 0.84f;
        float newHueSubtracted = hsb[0] - degree;
        return new Color(Color.HSBtoRGB(newHueSubtracted, hsb[1], hsb[2]));
    }

    public static Color interpolateColorsBackAndForth(int speed, int index, Color start, Color end, boolean trueColor) {
        int angle = (int) (((System.currentTimeMillis()) / speed + index) % 360);
        angle = (angle >= 180 ? 360 - angle : angle) * 2;
        return trueColor ? interpolateColorHue(start, end, angle / 360f) : interpolateColorC(start, end, angle / 360f);
    }

    public static Color interpolateColorC(Color color1, Color color2, float amount) {
        amount = Math.min(1f, Math.max(0f, amount));
        return new Color(
                interpolateInt(color1.getRed(), color2.getRed(), amount),
                interpolateInt(color1.getGreen(), color2.getGreen(), amount),
                interpolateInt(color1.getBlue(), color2.getBlue(), amount),
                interpolateInt(color1.getAlpha(), color2.getAlpha(), amount)
        );
    }

    public static Color interpolateColorHue(Color color1, Color color2, float amount) {
        amount = Math.min(1f, Math.max(0f, amount));
        float[] c1 = Color.RGBtoHSB(color1.getRed(), color1.getGreen(), color1.getBlue(), null);
        float[] c2 = Color.RGBtoHSB(color2.getRed(), color2.getGreen(), color2.getBlue(), null);
        Color result = Color.getHSBColor(interpolateFloat(c1[0], c2[0], amount), interpolateFloat(c1[1], c2[1], amount), interpolateFloat(c1[2], c2[2], amount));
        return new Color(result.getRed(), result.getGreen(), result.getBlue(), interpolateInt(color1.getAlpha(), color2.getAlpha(), amount));
    }

    private static int interpolateInt(int oldValue, int newValue, double t) {
        return (int) (oldValue + (newValue - oldValue) * t);
    }

    private static float interpolateFloat(float oldValue, float newValue, double t) {
        return (float) (oldValue + (newValue - oldValue) * t);
    }

    private static int clampAlpha(float opacity) {
        return Math.max(0, Math.min(255, (int) (opacity * 255)));
    }
}
