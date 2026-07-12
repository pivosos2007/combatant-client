/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.render.engine.postprocess.*;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.postprocess.*;
import combatant.client.render.engine.renderer.FullScreenRenderer;
import combatant.client.render.engine.uniform.impl.MotionBlurUniforms;
import combatant.client.util.logging.DebugLog;

//todo Description
@ModuleInfo(
        id = "motionblur",
        displayName = "MotionBlur",
        category = ModuleCategory.VISUALS
)
public final class MotionBlur extends Module implements PostProcessPass {
    private static final float TARGET_FRAME_SECONDS = 1.0f / 60.0f;
    private static final float HORIZONTAL_DEGREES_PER_SCREEN = 90.0f;
    private static final float VERTICAL_DEGREES_PER_SCREEN = 70.0f;
    private static final int SHADER_TAPS = 11;

    private final Minecraft mc = Minecraft.getInstance();
    private final MotionBlurResources resources = new MotionBlurResources();

    private final NumberValue<Float> strength =
            num("motionBlurStrength", "strength", 0.42f, 0.0f, 1.5f);
    private final NumberValue<Integer> maxBlurPixels =
            num("motionBlurMaxBlurPixels", "max_blur_pixels", 18, 2, 64);
    private final NumberValue<Float> yawMultiplier =
            num("motionBlurYawMultiplier", "yaw_multiplier", 1.0f, 0.0f, 2.0f);
    private final NumberValue<Float> pitchMultiplier =
            num("motionBlurPitchMultiplier", "pitch_multiplier", 0.32f, 0.0f, 1.5f);
    private final NumberValue<Float> minMotionPixels =
            num("motionBlurMinMotionPixels", "min_motion_pixels", 0.45f, 0.0f, 4.0f);
    private final NumberValue<Float> thirdPersonMultiplier =
            num("motionBlurThirdPersonMultiplier", "third_person_multiplier", 0.28f, 0.0f, 1.0f);
    private final NumberValue<Float> frameBlend =
            num("motionBlurFrameBlend", "frame_blend", 0.48f, 0.0f, 0.85f);
    private final NumberValue<Float> historyClamp =
            num("motionBlurHistoryClamp", "history_clamp", 0.75f, 0.0f, 1.0f);
    private final NumberValue<Float> exposureFrames =
            num("motionBlurExposureFrames", "exposure_frames", 2.35f, 1.0f, 6.0f);
    private final EnumValue<MotionBlurMode> mode =
            enumMode("motionBlurMode", MotionBlurMode.WORLD_ONLY, MotionBlurMode.values());

    private Object previousWorld;
    private CameraType previousPerspective;
    private boolean previousFrameValid;
    private long previousFrameNs;
    private float previousYaw;
    private float previousPitch;

    public MotionBlur() {
        PostProcessManager.register(this);
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        if (edge0 == edge1) return x >= edge1 ? 1.0f : 0.0f;
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    @Override
    public boolean isActive() {
        return isEnabled() && mc != null && mc.player != null && mc.level != null;
    }

    @Override
    public int getPriority() {
        return 4;
    }

    @Override
    public Phase getPhase() {
        return mode.get() == MotionBlurMode.WORLD_ONLY ? Phase.PRE_HAND : Phase.POST_HAND;
    }

    @Override
    public boolean render(GpuTextureView src, GpuTextureView dst, float tickDelta) {
        return false;
    }

    @Override
    public boolean render(PostProcessContext context, GpuTextureView src, GpuTextureView dst) {
        if (!isActive() || context == null || src == null || dst == null) {
            invalidateHistory();
            return false;
        }

        int width = Math.max(1, context.width());
        int height = Math.max(1, context.height());
        float currentYaw = RenderState.cameraYaw;
        float currentPitch = RenderState.cameraPitch;
        long nowNs = System.nanoTime();

        if (!resources.ensure(width, height)) {
            invalidateHistory();
            return false;
        }

        boolean reset = shouldReset(nowNs);
        if (reset) {
            resources.invalidate();
            resources.captureCurrent(src);
            capturePose(currentYaw, currentPitch, nowNs);
            return false;
        }

        CameraMotion motion = computeCameraMotion(width, height, currentYaw, currentPitch, nowNs);
        boolean hasHistory = resources.hasPreviousColor();
        GpuTextureView previousColor = hasHistory ? resources.previousColorView() : src;
        capturePose(currentYaw, currentPitch, nowNs);

        float spatialLengthPx = motion.spatial().lengthPixels();
        float historyLengthPx = motion.history().lengthPixels();
        float minPixels = Math.max(0.0f, minMotionPixels.get());
        boolean shouldApply = strength.get() > 0.0001f
                && (spatialLengthPx >= minPixels || (hasHistory && historyLengthPx >= minPixels * 0.35f));

        if (!shouldApply) {
            resources.captureCurrent(src);
            return false;
        }

        float temporalBlend = computeHistoryBlend(motion, hasHistory);
        MotionBlurUniforms.update(
                width,
                height,
                motion.spatial().xPixels(),
                motion.spatial().yPixels(),
                motion.history().xPixels(),
                motion.history().yPixels(),
                maxBlurPixels.get(),
                minPixels,
                SHADER_TAPS,
                temporalBlend,
                hasHistory,
                historyClamp.get()
        );

        try {
            FullScreenRenderer.ensureInit();
            FullScreenRenderer.begin("Combatant Temporal Camera MotionBlur")
                    .attachment(dst)
                    .pipeline(CombatantRenderPipelines.MOTION_BLUR)
                    .uniform("MotionBlur", MotionBlurUniforms.get())
                    .sampler("u_Texture", src, PostProcessManager.getSampler())
                    .sampler("u_PreviousColor", previousColor, PostProcessManager.getSampler())
                    .end();
            resources.captureCurrent(dst);
            return true;
        } catch (Throwable t) {
            resources.invalidate();
            DebugLog.error("[Combatant] MotionBlur render failed", t);
            return false;
        }
    }

    @Override
    public void onEnable() {
        invalidateHistory();
    }

    @Override
    public void onDisable() {
        invalidateHistory();
    }

    private CameraMotion computeCameraMotion(int width, int height, float currentYaw, float currentPitch, long nowNs) {
        float yawDelta = Mth.wrapDegrees(currentYaw - previousYaw);
        float pitchDelta = currentPitch - previousPitch;
        if (!Float.isFinite(yawDelta)) yawDelta = 0.0f;
        if (!Float.isFinite(pitchDelta)) pitchDelta = 0.0f;

        float perspectiveScale = currentPerspective().isFirstPerson()
                ? 1.0f
                : Mth.clamp(thirdPersonMultiplier.get(), 0.0f, 1.0f);
        float baseStrength = Math.max(0.0f, strength.get()) * perspectiveScale;

        float pixelsPerYawDegree = width / HORIZONTAL_DEGREES_PER_SCREEN;
        float pixelsPerPitchDegree = height / VERTICAL_DEGREES_PER_SCREEN;

        float actualX = -yawDelta
                * pixelsPerYawDegree
                * Math.max(0.0f, yawMultiplier.get())
                * baseStrength;
        float actualY = -pitchDelta
                * pixelsPerPitchDegree
                * Math.max(0.0f, pitchMultiplier.get())
                * baseStrength;

        if (!Float.isFinite(actualX)) actualX = 0.0f;
        if (!Float.isFinite(actualY)) actualY = 0.0f;

        float frameScale = computeFrameScale(nowNs);
        float exposure = Mth.clamp(exposureFrames.get(), 1.0f, 6.0f);
        float shutterScale = Mth.clamp(frameScale, 0.35f, exposure) * exposureBias(frameScale, exposure);

        return new CameraMotion(
                new MotionVector(actualX * shutterScale, actualY * shutterScale),
                new MotionVector(actualX, actualY),
                frameScale
        );
    }

    private float exposureBias(float frameScale, float exposure) {
        if (!Float.isFinite(frameScale) || frameScale <= 1.0f) {
            return 1.0f;
        }
        float highFps = Mth.clamp((frameScale - 1.0f) / Math.max(0.001f, exposure - 1.0f), 0.0f, 1.0f);
        return 1.0f + highFps * 0.35f;
    }

    private float computeHistoryBlend(CameraMotion motion, boolean hasHistory) {
        if (!hasHistory) return 0.0f;

        float base = Mth.clamp(frameBlend.get(), 0.0f, 0.85f);
        if (base <= 0.0001f) return 0.0f;

        float minPixels = Math.max(0.0f, minMotionPixels.get());
        float spatialResponse = smoothstep(
                Math.max(0.05f, minPixels * 0.5f),
                Math.max(0.35f, minPixels * 2.75f),
                motion.spatial().lengthPixels()
        );
        float fpsResponse = smoothstep(1.0f, Math.max(1.5f, exposureFrames.get()), motion.frameScale());
        float response = Math.max(spatialResponse * 0.65f, fpsResponse * spatialResponse);
        return Mth.clamp(base * response, 0.0f, 0.85f);
    }

    private float computeFrameScale(long nowNs) {
        if (previousFrameNs == 0L || nowNs <= previousFrameNs) {
            return 1.0f;
        }

        float dt = (nowNs - previousFrameNs) / 1_000_000_000.0f;
        if (dt <= 0.000001f || !Float.isFinite(dt)) {
            return 1.0f;
        }
        return Mth.clamp(TARGET_FRAME_SECONDS / dt, 0.25f, 6.0f);
    }

    private boolean shouldReset(long nowNs) {
        if (mc == null || mc.level == null || mc.options == null) return true;
        if (!previousFrameValid || previousFrameNs == 0L) return true;
        if (previousWorld != mc.level) return true;

        CameraType perspective = currentPerspective();
        if (previousPerspective != null && previousPerspective != perspective) return true;

        long dt = nowNs - previousFrameNs;
        return dt <= 0L || dt > 250_000_000L;
    }

    private CameraType currentPerspective() {
        return mc != null && mc.options != null ? mc.options.getCameraType() : CameraType.FIRST_PERSON;
    }

    private void capturePose(float yaw, float pitch, long nowNs) {
        previousYaw = yaw;
        previousPitch = pitch;
        previousWorld = mc != null ? mc.level : null;
        previousPerspective = currentPerspective();
        previousFrameNs = nowNs;
        previousFrameValid = true;
    }

    private void invalidateHistory() {
        previousFrameValid = false;
        previousFrameNs = 0L;
        previousWorld = null;
        previousPerspective = null;
        resources.invalidate();
    }

    private record CameraMotion(MotionVector spatial, MotionVector history, float frameScale) {
    }

    private record MotionVector(float xPixels, float yPixels) {
        float lengthPixels() {
            return (float) Math.sqrt(xPixels * xPixels + yPixels * yPixels);
        }
    }
}
