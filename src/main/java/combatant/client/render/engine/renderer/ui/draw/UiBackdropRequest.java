/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

import org.jetbrains.annotations.Nullable;

/**
 * Backend-neutral description of the inputs required by a blur/glass material.
 *
 * <p>The request deliberately contains no framebuffer or texture handles. The UI pass compiler
 * can group compatible requests, while the executor resolves the logical sources to backend
 * resources without leaking GL/Vulkan details into feature code.</p>
 */
public record UiBackdropRequest(SceneSource sceneSource,
                                UiUnderlayMode uiUnderlayMode,
                                BlurParameters sceneBlur,
                                BlurParameters uiBlur,
                                float sceneMix,
                                float uiMix,
                                @Nullable UiRect captureBounds) {
    public static final UiBackdropRequest NONE = new UiBackdropRequest(
            SceneSource.NONE,
            UiUnderlayMode.NONE,
            BlurParameters.NONE,
            BlurParameters.NONE,
            0.0f,
            0.0f,
            null
    );

    public UiBackdropRequest {
        sceneSource = sceneSource != null ? sceneSource : SceneSource.NONE;
        uiUnderlayMode = uiUnderlayMode != null ? uiUnderlayMode : UiUnderlayMode.NONE;
        sceneBlur = sceneBlur != null ? sceneBlur : BlurParameters.NONE;
        uiBlur = uiBlur != null ? uiBlur : BlurParameters.NONE;
        sceneMix = clamp01(sceneMix);
        uiMix = clamp01(uiMix);
        if (captureBounds != null && captureBounds.empty()) captureBounds = null;

        if (sceneSource == SceneSource.NONE) {
            sceneBlur = BlurParameters.NONE;
            sceneMix = 0.0f;
        }
        if (uiUnderlayMode == UiUnderlayMode.NONE) {
            uiBlur = BlurParameters.NONE;
            uiMix = 0.0f;
        } else if (uiUnderlayMode == UiUnderlayMode.PASS_THROUGH) {
            // Pass-through must stay cheap: it never schedules a UI blur chain.
            uiBlur = BlurParameters.NONE;
        } else if (!uiBlur.enabled()) {
            uiBlur = sceneBlur.enabled() ? sceneBlur : BlurParameters.defaults();
        }
    }

    /** Existing rectangular/squircle blur semantics: blur the accumulated target at this point. */
    public static UiBackdropRequest currentTargetBlur(@Nullable UiRect bounds,
                                                      UiBlurQuality quality,
                                                      float offsetPx) {
        BlurParameters blur = BlurParameters.of(quality, offsetPx);
        return new UiBackdropRequest(
                SceneSource.CURRENT_TARGET,
                UiUnderlayMode.BLUR,
                blur,
                blur,
                1.0f,
                1.0f,
                bounds
        );
    }

    /** Existing liquid-glass semantics: use the scene captured before accumulated HUD/UI. */
    public static UiBackdropRequest capturedSceneGlass(@Nullable UiRect bounds,
                                                       UiBlurQuality quality,
                                                       float offsetPx) {
        return new UiBackdropRequest(
                SceneSource.CAPTURED_SCENE,
                UiUnderlayMode.NONE,
                BlurParameters.of(quality, offsetPx),
                BlurParameters.NONE,
                1.0f,
                0.0f,
                bounds
        );
    }

    public UiBackdropRequest withUiUnderlay(UiUnderlayMode mode,
                                            @Nullable BlurParameters blur,
                                            float mix) {
        return new UiBackdropRequest(
                sceneSource,
                mode,
                sceneBlur,
                blur,
                sceneMix,
                mix,
                captureBounds
        );
    }

    public UiBackdropRequest withCaptureBounds(@Nullable UiRect bounds) {
        return new UiBackdropRequest(
                sceneSource,
                uiUnderlayMode,
                sceneBlur,
                uiBlur,
                sceneMix,
                uiMix,
                bounds
        );
    }

    public boolean requiresCapturedScene() {
        return sceneSource == SceneSource.CAPTURED_SCENE;
    }

    public boolean requiresUiUnderlayCapture() {
        return uiUnderlayMode != UiUnderlayMode.NONE;
    }

    /** Compatibility key for sharing one capture/blur preparation across adjacent effects. */
    public boolean compatibleInputs(UiBackdropRequest other) {
        return other != null
                && sceneSource == other.sceneSource
                && uiUnderlayMode == other.uiUnderlayMode
                && sceneBlur.equals(other.sceneBlur)
                && uiBlur.equals(other.uiBlur)
                && Float.compare(sceneMix, other.sceneMix) == 0
                && Float.compare(uiMix, other.uiMix) == 0;
    }

    public enum SceneSource {
        NONE,
        /** Scene/target captured before HUD/UI accumulation. */
        CAPTURED_SCENE,
        /** Color accumulated in the active target before this ordered effect. */
        CURRENT_TARGET
    }

    public enum UiUnderlayMode {
        NONE,
        PASS_THROUGH,
        BLUR
    }

    public record BlurParameters(boolean enabled,
                                 UiBlurQuality quality,
                                 float offsetPx) {
        public static final BlurParameters NONE = new BlurParameters(
                false,
                UiBlurQuality.MEDIUM,
                1.0f
        );

        public BlurParameters {
            quality = quality != null ? quality : UiBlurQuality.MEDIUM;
            offsetPx = Float.isFinite(offsetPx)
                    ? Math.max(0.0f, offsetPx)
                    : 1.0f;
        }

        public static BlurParameters of(UiBlurQuality quality, float offsetPx) {
            return new BlurParameters(true, quality, offsetPx);
        }

        public static BlurParameters defaults() {
            return of(UiBlurQuality.MEDIUM, 1.0f);
        }
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0.0f;
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
