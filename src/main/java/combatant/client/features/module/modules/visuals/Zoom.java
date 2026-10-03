/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import net.minecraft.util.Mth;
import combatant.client.config.values.BindMode;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.gui.clickgui.settings.FunctionBindSetting;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.render.engine.animation.AnimationUtility;

@ModuleInfo(
        id = "zoom",
        displayName = "Zoom",
        category = ModuleCategory.VISUALS, subcategory = ModuleSubcategory.COSMETIC,
        description = "module.zoom.description")
public final class Zoom extends Module {
    private static final String ACTION_ZOOM_HOLD = "zoom_hold";
    private static final float EPSILON = 0.0005f;
    private static final float MAX_DIVISOR = 50.0f;

    private final FunctionBindSetting zoomHoldAction = action(ACTION_ZOOM_HOLD, "C", BindMode.HOLD);
    private final NumberValue<Float> zoomDivisor = num("zoom_divisor", 4.0f, 1.0f, 30.0f);
    private final EnumValue<AnimationMode> animation = enumSetting("zoom_animation", "animation", AnimationMode.EASE_OUT_CUBIC);
    private final NumberValue<Float> smoothIn = num("zoom_smooth_in", "smooth_in", 0.25f, 0.0f, 1.0f);
    private final NumberValue<Float> smoothOut = num("zoom_smooth_out", "smooth_out", 0.25f, 0.0f, 1.0f);
    private final NumberValue<Integer> sensitivityPercent = num(
            "zoom_sensitivity_percent",
            "sensitivity_percent",
            100,
            0,
            100
    );
    private final BooleanValue scrollZoom = bool("zoom_scroll", "scroll_zoom", false);
    private final NumberValue<Integer> scrollStepPercent = visibleWhen(
            num("zoom_scroll_step_percent", "scroll_step_percent", 15, 5, 50), scrollZoom::get);

    private boolean zooming;
    private float progress;
    private float currentDivisor = 1.0f;
    private float scrollFactor = 1.0f;

    @Override
    public void onFrame(float tickDelta) {
        boolean held = isActionHeld(ACTION_ZOOM_HOLD);
        zooming = held;

        float targetProgress = held ? 1.0f : 0.0f;
        float smooth = Mth.clamp(held ? smoothIn.get() : smoothOut.get(), 0.0f, 1.0f);
        if (smooth <= EPSILON) {
            progress = targetProgress;
        } else {
            float dt = AnimationUtility.deltaTime();
            float speed = Mth.lerp(smooth, 5.0f, 28.0f);
            progress = AnimationUtility.approach(progress, targetProgress, dt, speed);
            progress = AnimationUtility.snap(progress, targetProgress, 0.001f);
        }

        progress = AnimationUtility.clamp01(progress);
        // Forget the scrolled level only once fully zoomed out, so releasing the key does not jump.
        if (!held && progress <= EPSILON) scrollFactor = 1.0f;

        float targetDivisor = Mth.clamp(zoomDivisor.get() * scrollFactor, 1.0f, MAX_DIVISOR);
        float eased = animation.get().apply(progress);
        currentDivisor = Math.max(1.0f, AnimationUtility.lerp(1.0f, targetDivisor, eased));
    }

    @Override
    public void onDisable() {
        zooming = false;
        progress = 0.0f;
        currentDivisor = 1.0f;
        scrollFactor = 1.0f;
    }

    /** Mouse wheel while the zoom key is held; returns true when the scroll was used (hotbar stays put). */
    public boolean onScroll(double vertical) {
        if (!isEnabled() || !scrollZoom.get() || !zooming || vertical == 0.0) return false;
        float step = 1.0f + scrollStepPercent.get() / 100.0f;
        float next = vertical > 0.0 ? scrollFactor * step : scrollFactor / step;
        scrollFactor = Mth.clamp(next, 1.0f / zoomDivisor.get(), MAX_DIVISOR / zoomDivisor.get());
        return true;
    }

    public boolean isZooming() {
        return zooming;
    }

    public boolean shouldApplyZoom() {
        return zooming || currentDivisor > 1.0005f || progress > EPSILON;
    }

    public float getZoomDivisor() {
        return Math.max(1.0f, currentDivisor);
    }

    public float getSensitivityPercent() {
        return Mth.clamp(sensitivityPercent.get(), 0, 100);
    }

    public enum AnimationMode {
        LINEAR,
        SMOOTHSTEP,
        EASE_IN_CUBIC,
        EASE_OUT_CUBIC,
        EASE_IN_OUT_CUBIC,
        EASE_OUT_BACK,
        EASE_IN_BACK;

        public float apply(float value) {
            return switch (this) {
                case LINEAR -> AnimationUtility.clamp01(value);
                case SMOOTHSTEP -> AnimationUtility.smoothstep(value);
                case EASE_IN_CUBIC -> AnimationUtility.easeInCubic(value);
                case EASE_OUT_CUBIC -> AnimationUtility.easeOutCubic(value);
                case EASE_IN_OUT_CUBIC -> AnimationUtility.easeInOutCubic(value);
                case EASE_OUT_BACK -> AnimationUtility.easeOutBack(value, 0.95f);
                case EASE_IN_BACK -> AnimationUtility.easeInBack(value, 0.95f);
            };
        }
    }
}
