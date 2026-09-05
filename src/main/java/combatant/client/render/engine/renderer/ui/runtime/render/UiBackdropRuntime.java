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
import combatant.client.render.engine.renderer.ui.runtime.core.UiProps;

import java.util.Locale;

/** Script-facing selection of automatic, sharp or independently blurred UI underlay. */
final class UiBackdropRuntime {
    private UiBackdropRuntime() {
    }

    static void drawLiquidGlass(Renderer2D renderer, UiProps props, Runnable draw) {
        if (renderer == null || draw == null) return;
        String configured = props != null
                ? props.string("uiUnderlay", props.string("uiBackdropMode", "auto"))
                : "auto";
        String modeName = configured == null ? "auto" : configured.trim().toLowerCase(Locale.ROOT);
        if (modeName.isEmpty() || "auto".equals(modeName)) {
            draw.run();
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
        renderer.withLiquidGlassUiUnderlay(mode, quality, offset, mix, draw);
    }
}
