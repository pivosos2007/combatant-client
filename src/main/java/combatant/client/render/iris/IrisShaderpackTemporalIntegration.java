/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.iris;

import com.google.common.collect.ImmutableSet;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.backend.opengl.GlTextureView;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import combatant.client.config.MainConfig;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.postprocess.TemporalAntiAliasingPass;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.RhiStorageImage;
import combatant.client.render.engine.rhi.shader.StorageAccess;
import combatant.client.render.engine.rhi.shader.StorageImageDescriptor;
import combatant.client.render.iris.patch.ShaderPatchEngine;
import combatant.client.util.logging.DebugLog;
import net.irisshaders.iris.targets.RenderTarget;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.uniforms.CameraUniforms;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.world.phys.Vec3;

/** Shaderpack-agnostic temporal runtime. All pass/resource mappings come from the active manifest adapter. */
public enum IrisShaderpackTemporalIntegration {
    ;
    private static RenderTargets targets;
    private static ImmutableSet<Integer> pendingStageReadsFromAlt;
    private static RhiStorageImage sceneInput;
    private static RhiStorageImage resolvedOutput;
    private static CombatantRhi resourceOwner;
    private static long resourceEpoch = -1L;
    private static IrisAaOwner previousOwner = IrisAaOwner.NONE;
    private static int width;
    private static int height;

    public static void beginFrame(RenderTargets currentTargets, long integrationEpoch) {
        targets = currentTargets;
        pendingStageReadsFromAlt = null;
        IrisAaOwner owner = state().owner();
        if (owner != previousOwner || integrationEpoch != resourceEpoch) {
            TemporalAntiAliasingPass.INSTANCE.invalidateHistory();
            IrisAaIntegration.markNativeTemporalBypass(false);
        }
        previousOwner = owner;
        if (resourceEpoch != integrationEpoch) {
            closeImages();
            resourceEpoch = integrationEpoch;
        }
    }

    /** Called at Pass.setupState HEAD, i.e. after the preceding pass draw and before this pass binds. */
    public static void beforePass(String passName, ImmutableSet<Integer> stageReadsFromAlt) {
        if (pendingStageReadsFromAlt != null) {
            resolvePending();
        }
        ShaderPatchEngine.ShaderpackIntegration adapter = adapter();
        if (adapter.hasTemporalMapping() && adapter.temporalPass().equals(passName) && ownsReplacement()) {
            pendingStageReadsFromAlt = stageReadsFromAlt == null ? ImmutableSet.of() : stageReadsFromAlt;
            IrisAaIntegration.markNativeTemporalBypass(true);
        }
    }

    /** Runs before Iris prepares mipmaps or state for the following composite pass. */
    public static void afterPassDraw() {
        if (pendingStageReadsFromAlt != null) resolvePending();
    }

    /** Called at CompositeRenderer.renderAll TAIL for the no-following-pass case. */
    public static void endRenderer() {
        if (pendingStageReadsFromAlt != null) resolvePending();
    }

    public static void shutdown() {
        pendingStageReadsFromAlt = null;
        targets = null;
        previousOwner = IrisAaOwner.NONE;
        resourceEpoch = -1L;
        IrisAaIntegration.resetRuntimeState();
        TemporalAntiAliasingPass.INSTANCE.invalidateHistory();
        closeImages();
    }

    private static void resolvePending() {
        ImmutableSet<Integer> temporalReadsFromAlt = pendingStageReadsFromAlt;
        pendingStageReadsFromAlt = null;
        IrisAaIntegrationState ownership = state();
        if (ownership.owner() != IrisAaOwner.COMBATANT_TAA) return;
        if (targets == null || !IrisSceneDepth.isValid()) {
            fail("shaderpack scene targets/depth unavailable at temporal boundary");
            return;
        }

        try {
            ShaderPatchEngine.ShaderpackIntegration adapter = adapter();
            RenderTarget scene = targets.get(adapter.sceneColorTarget());
            RenderTarget history = targets.get(adapter.historyTarget());
            if (scene == null || history == null) {
                fail("shaderpack scene/history targets unavailable");
                return;
            }
            int sceneTexture = outputTexture(scene, temporalReadsFromAlt.contains(adapter.sceneColorTarget()));
            int historyTexture = outputTexture(history, temporalReadsFromAlt.contains(adapter.historyTarget()));
            int w = scene.getWidth();
            int h = scene.getHeight();
            if (history.getWidth() != w || history.getHeight() != h) {
                fail("shaderpack scene/history target dimensions do not match");
                return;
            }
            ensureImages(w, h);
            copyRawTexture(sceneTexture, glId(sceneInput), w, h);

            CapturedRenderingState irisState = CapturedRenderingState.INSTANCE;
            Matrix4fc irisView = irisState.getGbufferModelView();
            Matrix4fc irisProjection = irisState.getGbufferProjection();
            Vector3d irisCamera = CameraUniforms.getUnshiftedCameraPosition();
            if (irisView == null || irisProjection == null || irisCamera == null) {
                fail("Iris shaderpack temporal matrices unavailable");
                return;
            }

            boolean rendered = TemporalAntiAliasingPass.INSTANCE.renderShaderpack(
                    resourceOwner, resolvedOutput, sceneInput.view(), IrisSceneDepth.mainDepthView(),
                    irisView, irisProjection, new Vec3(irisCamera.x, irisCamera.y, irisCamera.z));
            if (!rendered) {
                fail("Combatant TAA backend rejected shaderpack composite resources");
                return;
            }
            int resolvedTexture = glId(resolvedOutput);
            copyRawTexture(resolvedTexture, sceneTexture, w, h);

            // Photon colortex5 is not a presentation buffer. It is the canonical temporal
            // history consumed by the next frame's object SSR and by Photon's later HDR passes.
            // Keep it bit-for-bit on the same temporal branch as Combatant's own history instead
            // of feeding post-TAA FXAA/CAS back into SSR. Its alpha also carries per-pixel history
            // age, except for the origin texel where Photon stores exposure.
            GpuTextureView canonicalHistory = TemporalAntiAliasingPass.INSTANCE.currentHistoryColorView();
            if (canonicalHistory == null) {
                fail("Combatant TAA canonical history unavailable at shaderpack temporal boundary");
                return;
            }
            int canonicalHistoryTexture = glId(canonicalHistory);
            float historyOriginAlpha = adapter.preserveHistoryAlphaOrigin()
                    ? readOriginAlpha(historyTexture) : 0.0f;
            copyRawTexture(canonicalHistoryTexture, historyTexture, w, h);
            if (adapter.preserveHistoryAlphaOrigin()) {
                restoreOriginAlpha(historyTexture, historyOriginAlpha);
            }
        } catch (Throwable throwable) {
            fail("shaderpack TAA runtime failure: " + throwable.getClass().getSimpleName()
                    + (throwable.getMessage() == null ? "" : ": " + throwable.getMessage()));
            DebugLog.error("[IrisCompat] shaderpack temporal integration failed", throwable);
        }
    }

    private static int outputTexture(RenderTarget target, boolean readFromAltBeforePass) {
        // Iris creates this pass framebuffer with stageReadsFromAlt as stageWritesToMain.
        return readFromAltBeforePass ? target.getMainTexture() : target.getAltTexture();
    }

    private static void ensureImages(int w, int h) {
        CombatantRhi rhi = CombatantRenderSystem.rhi();
        if (resourceOwner != rhi) {
            closeImages();
            resourceOwner = rhi;
        }
        if (sceneInput != null && resolvedOutput != null && width == w && height == h) return;
        closeImages();
        resourceOwner = rhi;
        width = w;
        height = h;
        GpuFormat format = colorFormat(adapter().colorFormat());
        sceneInput = rhi.advancedShaders().createStorageImage(new StorageImageDescriptor(
                "combatant-shaderpack-taa-scene", w, h, format,
                StorageAccess.READ_WRITE, true, false));
        resolvedOutput = rhi.advancedShaders().createStorageImage(new StorageImageDescriptor(
                "combatant-shaderpack-taa-resolved", w, h, format,
                StorageAccess.READ_WRITE, true, false));
    }

    private static int glId(RhiStorageImage image) {
        if (image == null) {
            throw new IllegalStateException("shaderpack integration requires live OpenGL storage images");
        }
        return glId(image.view());
    }

    private static int glId(GpuTextureView image) {
        if (!(image instanceof GlTextureView view) || view.isClosed()) {
            throw new IllegalStateException("shaderpack integration requires live OpenGL texture views");
        }
        return view.glId();
    }

    private static void copyRawTexture(int source, int destination, int w, int h) {
        int previousRead = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        int read = GL30C.glGenFramebuffers();
        int draw = GL30C.glGenFramebuffers();
        try {
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, read);
            GL30C.glFramebufferTexture2D(GL30C.GL_READ_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0,
                    GL11C.GL_TEXTURE_2D, source, 0);
            GL30C.glReadBuffer(GL30C.GL_COLOR_ATTACHMENT0);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, draw);
            GL30C.glFramebufferTexture2D(GL30C.GL_DRAW_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0,
                    GL11C.GL_TEXTURE_2D, destination, 0);
            GL30C.glDrawBuffer(GL30C.GL_COLOR_ATTACHMENT0);
            if (GL30C.glCheckFramebufferStatus(GL30C.GL_READ_FRAMEBUFFER) != GL30C.GL_FRAMEBUFFER_COMPLETE
                    || GL30C.glCheckFramebufferStatus(GL30C.GL_DRAW_FRAMEBUFFER) != GL30C.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("incomplete shaderpack TAA copy framebuffer");
            }
            GL30C.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11C.GL_COLOR_BUFFER_BIT, GL11C.GL_NEAREST);
        } finally {
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, previousRead);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, previousDraw);
            GL30C.glDeleteFramebuffers(read);
            GL30C.glDeleteFramebuffers(draw);
        }
    }

    private static float readOriginAlpha(int texture) {
        int previousRead = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        int framebuffer = GL30C.glGenFramebuffers();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer pixel = stack.mallocFloat(4);
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, framebuffer);
            GL30C.glFramebufferTexture2D(GL30C.GL_READ_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0,
                    GL11C.GL_TEXTURE_2D, texture, 0);
            GL11C.glReadBuffer(GL30C.GL_COLOR_ATTACHMENT0);
            if (GL30C.glCheckFramebufferStatus(GL30C.GL_READ_FRAMEBUFFER) != GL30C.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("incomplete shaderpack history metadata framebuffer");
            }
            GL11C.glReadPixels(0, 0, 1, 1, GL11C.GL_RGBA, GL11C.GL_FLOAT, pixel);
            return pixel.get(3);
        } finally {
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, previousRead);
            GL30C.glDeleteFramebuffers(framebuffer);
        }
    }

    private static void restoreOriginAlpha(int texture, float alpha) {
        int previousDraw = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        boolean scissorEnabled = GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST);
        boolean framebufferSrgb = GL11C.glIsEnabled(GL30C.GL_FRAMEBUFFER_SRGB);
        int framebuffer = GL30C.glGenFramebuffers();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer scissor = stack.mallocInt(4);
            ByteBuffer colorMask = stack.malloc(4);
            FloatBuffer clear = stack.floats(0.0f, 0.0f, 0.0f, alpha);
            GL11C.glGetIntegerv(GL11C.GL_SCISSOR_BOX, scissor);
            GL11C.glGetBooleanv(GL11C.GL_COLOR_WRITEMASK, colorMask);
            try {
                GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, framebuffer);
                GL30C.glFramebufferTexture2D(GL30C.GL_DRAW_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0,
                        GL11C.GL_TEXTURE_2D, texture, 0);
                GL11C.glDrawBuffer(GL30C.GL_COLOR_ATTACHMENT0);
                if (GL30C.glCheckFramebufferStatus(GL30C.GL_DRAW_FRAMEBUFFER) != GL30C.GL_FRAMEBUFFER_COMPLETE) {
                    throw new IllegalStateException("incomplete shaderpack history metadata restore framebuffer");
                }
                GL11C.glEnable(GL11C.GL_SCISSOR_TEST);
                GL11C.glScissor(0, 0, 1, 1);
                GL11C.glDisable(GL30C.GL_FRAMEBUFFER_SRGB);
                GL11C.glColorMask(false, false, false, true);
                GL30C.glClearBufferfv(GL11C.GL_COLOR, 0, clear);
            } finally {
                GL11C.glColorMask(colorMask.get(0) != 0, colorMask.get(1) != 0,
                        colorMask.get(2) != 0, colorMask.get(3) != 0);
                GL11C.glScissor(scissor.get(0), scissor.get(1), scissor.get(2), scissor.get(3));
                if (!scissorEnabled) GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
                if (framebufferSrgb) GL11C.glEnable(GL30C.GL_FRAMEBUFFER_SRGB);
            }
        } finally {
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, previousDraw);
            GL30C.glDeleteFramebuffers(framebuffer);
        }
    }

    private static boolean ownsReplacement() {
        IrisAaOwner owner = state().owner();
        return owner == IrisAaOwner.COMBATANT_TAA || owner == IrisAaOwner.COMBATANT_MSAA;
    }

    private static IrisAaIntegrationState state() {
        return IrisAaIntegration.resolve(MainConfig.get().getAntialiasing3dMode());
    }

    private static ShaderPatchEngine.ShaderpackIntegration adapter() {
        return ShaderPatchEngine.profile(IrisRuntime.snapshot().shaderpackName()).integration();
    }

    private static GpuFormat colorFormat(String value) {
        if (value == null) return GpuFormat.RGBA16_FLOAT;
        return switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "rgba8", "rgba8_unorm" -> GpuFormat.RGBA8_UNORM;
            default -> GpuFormat.RGBA16_FLOAT;
        };
    }

    private static void fail(String reason) {
        IrisAaIntegration.fail(reason);
        TemporalAntiAliasingPass.INSTANCE.invalidateHistory();
    }

    private static void closeImages() {
        sceneInput = close(sceneInput);
        resolvedOutput = close(resolvedOutput);
        resourceOwner = null;
        width = 0;
        height = 0;
    }

    private static RhiStorageImage close(RhiStorageImage image) {
        if (image != null) try { image.close(); } catch (Throwable ignored) { }
        return null;
    }
}
