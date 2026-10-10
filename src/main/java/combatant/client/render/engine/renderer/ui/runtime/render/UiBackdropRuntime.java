/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.render;

import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.draw.UiBackdropRequest;
import combatant.client.render.engine.renderer.ui.draw.UiBlurQuality;
import combatant.client.render.engine.renderer.ui.draw.UiLiquidGlassMaterial;
import combatant.client.render.engine.renderer.ui.draw.UiGlassOpticsSpec;
import combatant.client.render.engine.renderer.ui.runtime.core.UiProps;
import combatant.client.render.engine.renderer.ui.runtime.style.UiColor;

import java.util.Locale;

/** Script-facing selection of automatic, sharp or independently blurred UI underlay. */
final class UiBackdropRuntime {
    private UiBackdropRuntime() {
    }

    static void drawLiquidGlass(Renderer2D renderer, UiProps props, Runnable draw) {
        if (renderer == null || draw == null) return;

        float frostedJitter = props != null ? props.number("glassFrostedJitter", 0.0f) : 0.0f;
        if (props != null && props.bool("glassFrosted", false) && frostedJitter <= 0.001f) {
            frostedJitter = 0.75f;
        }
        float innerGlow = props != null ? props.number("glassInnerGlow", 0.0f) : 0.0f;
        float innerGlowSize = props != null ? props.number("glassInnerGlowSize", 10.0f) : 10.0f;
        int innerGlowColor = materialColor(props != null ? props.get("glassInnerGlowColor") : null, 0xFFFFFFFF);
        String opticsName = props != null
                ? props.string("glassOptics", props.string("materialMode", "reactive-refraction"))
                : "reactive-refraction";
        UiGlassOpticsSpec baseOptics = switch (opticsName == null
                ? "liquid"
                : opticsName.trim().toLowerCase(Locale.ROOT)) {
            case "fresnel", "fresnel-glass", "fresnel_glass",
                 "fresnel-frosted", "fresnel_frosted", "mirror-frosted", "mirror_frosted" ->
                    UiGlassOpticsSpec.FRESNEL_GLASS;
            default -> UiGlassOpticsSpec.LIQUID;
        };
        float interactionStrength = props != null && !props.bool("glassInteraction", true)
                ? 0.0f
                : props != null
                ? props.number("glassInteractionStrength", baseOptics.interactionStrength())
                : baseOptics.interactionStrength();
        UiGlassOpticsSpec optics = new UiGlassOpticsSpec(
                baseOptics.mode(),
                props != null ? props.number("glassRefraction", baseOptics.refraction()) : baseOptics.refraction(),
                props != null ? props.number("glassBevelWidth", baseOptics.bevelWidth()) : baseOptics.bevelWidth(),
                props != null ? props.number("glassBevelDepth", baseOptics.bevelDepth()) : baseOptics.bevelDepth(),
                interactionStrength,
                props != null ? props.number("glassInteractionRadius", baseOptics.interactionRadius()) : baseOptics.interactionRadius(),
                props != null ? props.number("glassInteractionViscosity", baseOptics.interactionViscosity()) : baseOptics.interactionViscosity(),
                props != null ? props.number("glassCleanReveal", baseOptics.cleanReveal()) : baseOptics.cleanReveal()
        );
        UiLiquidGlassMaterial material = new UiLiquidGlassMaterial(
                optics,
                frostedJitter,
                innerGlow,
                innerGlowSize,
                innerGlowColor
        );

        String configured = props != null
                ? props.string("uiUnderlay", props.string("uiBackdropMode", "auto"))
                : "auto";
        String modeName = configured == null ? "auto" : configured.trim().toLowerCase(Locale.ROOT);
        if (modeName.isEmpty() || "auto".equals(modeName)) {
            renderer.withLiquidGlassMaterial(material, draw);
            return;
        }

        UiBackdropRequest.UiUnderlayMode mode = switch (modeName) {
            case "blur", "cheap-blur", "cheap_blur" -> UiBackdropRequest.UiUnderlayMode.BLUR;
            case "pass", "pass-through", "pass_through", "sharp" ->
                    UiBackdropRequest.UiUnderlayMode.PASS_THROUGH;
            default -> UiBackdropRequest.UiUnderlayMode.NONE;
        };
        String qualityName = props != null ? props.string("uiUnderlayBlurQuality", "low") : "low";
        UiBlurQuality quality = switch (qualityName.trim().toLowerCase(Locale.ROOT)) {
            case "medium" -> UiBlurQuality.MEDIUM;
            case "high" -> UiBlurQuality.HIGH;
            case "ultra" -> UiBlurQuality.ULTRA;
            default -> UiBlurQuality.LOW;
        };
        float offset = props != null ? props.number("uiUnderlayBlurOffset", 0.85f) : 0.85f;
        float mix = props != null ? props.number("uiUnderlayMix", 1.0f) : 1.0f;
        renderer.withLiquidGlassMaterial(material, () ->
                renderer.withLiquidGlassUiUnderlay(mode, quality, offset, mix, draw));
    }

    private static int materialColor(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String text) return UiColor.parse(text, fallback);
        return fallback;
    }
}
