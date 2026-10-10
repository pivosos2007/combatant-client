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
import combatant.client.render.engine.renderer.ui.draw.UiMaterialCatalog;
import combatant.client.render.engine.renderer.ui.draw.UiGlassInteraction;
import combatant.client.render.engine.renderer.ui.draw.UiRect;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNode;
import combatant.client.render.engine.core.ViewportContext;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import combatant.client.render.engine.renderer.ui.runtime.core.UiProps;
import combatant.client.render.engine.renderer.ui.runtime.style.UiColor;

import java.util.Locale;

/** Script-facing selection of automatic, sharp or independently blurred UI underlay. */
final class UiBackdropRuntime {
    private UiBackdropRuntime() {
    }

    static void drawLiquidGlass(Renderer2D renderer, UiProps props, Runnable draw) {
        drawLiquidGlass(renderer, null, null, props, draw);
    }

    static void drawLiquidGlass(Renderer2D renderer, UiNode node, UiRect bounds,
                                UiProps props, Runnable draw) {
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
        UiGlassOpticsSpec baseOptics = UiMaterialCatalog.glass(opticsName);
        float interactionStrength = props == null || !props.bool("glassInteraction", false)
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
                props != null ? props.number("glassCleanReveal", baseOptics.cleanReveal()) : baseOptics.cleanReveal(),
                props != null ? props.number("glassAberration", baseOptics.chromaticAberration()) : baseOptics.chromaticAberration(),
                props != null ? props.number("glassMagnification", baseOptics.magnification()) : baseOptics.magnification(),
                props != null && props.bool("glassDeformShape", false),
                props != null ? props.number("glassTiltX", baseOptics.tiltXDegrees()) : baseOptics.tiltXDegrees(),
                props != null ? props.number("glassTiltY", baseOptics.tiltYDegrees()) : baseOptics.tiltYDegrees(),
                props == null || props.bool("glassSpecular", baseOptics.specular())
        );
        UiLiquidGlassMaterial material = new UiLiquidGlassMaterial(
                optics, frostedJitter, innerGlow, innerGlowSize, innerGlowColor);
        if (node != null && bounds != null && optics.interactionStrength() > 0.0f
                && optics.mode() != UiGlassOpticsSpec.Mode.FRESNEL_GLASS) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getWindow() != null && mc.mouseHandler != null) {
                ViewportContext viewport = ViewportContext.current();
                float viewportW = viewport != null ? Math.max(1f, viewport.width())
                        : Math.max(1f, mc.getWindow().getScreenWidth());
                float viewportH = viewport != null ? Math.max(1f, viewport.height())
                        : Math.max(1f, mc.getWindow().getScreenHeight());
                float pointerX = (float) mc.mouseHandler.xpos()
                        / Math.max(1, mc.getWindow().getScreenWidth()) * viewportW;
                float pointerY = (float) mc.mouseHandler.ypos()
                        / Math.max(1, mc.getWindow().getScreenHeight()) * viewportH;
                boolean pressed = GLFW.glfwGetMouseButton(mc.getWindow().handle(),
                        GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
                UiGlassInteraction interaction = node.state().materialInteraction().sample(
                        pointerX, pointerY, pressed,
                        (float) bounds.x(), (float) bounds.y(),
                        (float) bounds.width(), (float) bounds.height(),
                        optics.interactionViscosity(), optics.interactionStrength(), System.nanoTime());
                material = material.withInteraction(interaction);
            }
        }

        final UiLiquidGlassMaterial resolvedMaterial = material;
        String configured = props != null
                ? props.string("uiUnderlay", props.string("uiBackdropMode", "auto"))
                : "auto";
        String modeName = configured == null ? "auto" : configured.trim().toLowerCase(Locale.ROOT);
        Runnable glassDraw = () -> renderer.withLiquidGlassMaterial(resolvedMaterial, draw);
        if (props != null && props.bool("glassStacked", false)) {
            // Ordered CURRENT_TARGET snapshot: lower UI/glass layers are refracted, no feedback.
            Runnable sourceDraw = glassDraw;
            glassDraw = () -> renderer.withLiquidGlassSceneSource(
                    UiBackdropRequest.SceneSource.CURRENT_TARGET, sourceDraw);
        }
        if (modeName.isEmpty() || "auto".equals(modeName)) {
            glassDraw.run();
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
        Runnable finalDraw = glassDraw;
        renderer.withLiquidGlassUiUnderlay(mode, quality, offset, mix, finalDraw);
    }

    private static int materialColor(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String text) return UiColor.parse(text, fallback);
        return fallback;
    }
}
