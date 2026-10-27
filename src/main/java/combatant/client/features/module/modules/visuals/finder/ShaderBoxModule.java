/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals.finder;

import net.minecraft.world.phys.AABB;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.features.module.Module;
import combatant.client.render.engine.renderer.GlowSurfaces;
import combatant.client.render.engine.renderer.Renderer3D;

/**
 * Base for box-drawing ESP modules that offer the animated {@link GlowSurfaces} shader next to
 * the plain boxes. Declares {@code render_mode} and the shader options (each visible only in the
 * mode that uses it); subclasses ask {@link #drawShaderBox} and fall back to their own drawing
 * when it returns false.
 *
 * <p>Not a module by itself (abstract, no {@code @ModuleInfo}), so discovery skips it.</p>
 */
public abstract class ShaderBoxModule extends Module {

    public enum RenderMode {
        BOX,
        OUTLINE,
        FILL,
        /** Animated glow surface: translucent fill, glowing rim and a band climbing the faces. */
        GLOW,
        /** Outline and fill like the entity ESP shader mode, with a solid or gradient fill. */
        SHADER
    }

    public enum FillStyle {
        SOLID,
        GRADIENT
    }

    protected final EnumValue<RenderMode> renderMode;

    private final NumberValue<Float> glowFillAlpha;
    private final NumberValue<Float> glowRim;
    private final NumberValue<Float> glowScan;

    private final BooleanValue shaderOutline;
    private final NumberValue<Float> shaderOutlineAlpha;
    private final BooleanValue shaderFill;
    private final EnumValue<FillStyle> shaderFillStyle;
    private final NumberValue<Float> shaderFillAlpha;
    private final RGBAColorValue shaderGradientColor;

    protected ShaderBoxModule(RenderMode defaultMode, RenderMode... modes) {
        renderMode = enumMode("render_mode", defaultMode, modes);

        glowFillAlpha = visibleWhen(num("glow_fill_alpha", 0.16f, 0.0f, 1.0f), this::isGlow);
        glowRim = visibleWhen(num("glow_rim", 0.55f, 0.0f, 1.0f), this::isGlow);
        glowScan = visibleWhen(num("glow_scan", 1.0f, 0.0f, 1.0f), this::isGlow);

        shaderOutline = visibleWhen(bool("shader_outline", true), this::isShader);
        shaderOutlineAlpha = visibleWhen(num("shader_outline_alpha", 0.92f, 0.0f, 1.0f),
                () -> isShader() && shaderOutline.get());
        shaderFill = visibleWhen(bool("shader_fill", true), this::isShader);
        shaderFillStyle = visibleWhen(enumMode("shader_fill_style", FillStyle.SOLID),
                () -> isShader() && shaderFill.get());
        shaderFillAlpha = visibleWhen(num("shader_fill_alpha", 0.30f, 0.0f, 1.0f),
                () -> isShader() && shaderFill.get());
        shaderGradientColor = visibleWhen(color("shader_gradient_color", "#FFB064FF"),
                () -> isShader() && shaderFill.get() && shaderFillStyle.get() == FillStyle.GRADIENT);
    }

    private boolean isGlow() {
        return renderMode.get() == RenderMode.GLOW;
    }

    private boolean isShader() {
        return renderMode.get() == RenderMode.SHADER;
    }

    /**
     * Draws {@code box} with the selected shader mode, tinted by {@code argb} (its alpha scales
     * the shader alphas, so callers can dim single boxes).
     *
     * @return false when the current mode is not a shader mode and nothing was drawn
     */
    public final boolean drawShaderBox(Renderer3D renderer, AABB box, int argb) {
        RenderMode mode = renderMode.get();
        if (mode != RenderMode.GLOW && mode != RenderMode.SHADER) return false;

        float scale = ((argb >>> 24) & 0xFF) / 255.0f;
        int rgb = argb & 0x00FFFFFF;
        if (mode == RenderMode.GLOW) {
            int fill = withAlpha(rgb, glowFillAlpha.get() * scale);
            GlowSurfaces.box(renderer, box, fill, fill, glowRim.get() * scale, glowScan.get(), false, true, false);
            return true;
        }

        float fillAlpha = shaderFill.get() ? shaderFillAlpha.get() * scale : 0.0f;
        int bottom = withAlpha(rgb, fillAlpha);
        int top = shaderFillStyle.get() == FillStyle.GRADIENT
                ? withAlpha(shaderGradientColor.getArgb() & 0x00FFFFFF, fillAlpha)
                : bottom;
        float rim = shaderOutline.get() ? shaderOutlineAlpha.get() * scale : 0.0f;
        GlowSurfaces.box(renderer, box, bottom, top, rim, 0.0f, false, true, false);
        return true;
    }

    private static int withAlpha(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0f)));
        return (a << 24) | (rgb & 0x00FFFFFF);
    }
}
