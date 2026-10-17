/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.iris;

import com.mojang.renderpearl.backend.opengl.GlTexture;
import com.mojang.renderpearl.api.textures.GpuTexture;
import combatant.client.config.MainConfig;
import combatant.client.render.iris.patch.ShaderPatchEngine;
import combatant.client.util.logging.DebugLog;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Generic Iris gbuffer MSAA attachment/resolve bridge enabled by manifest capability. */
public enum IrisShaderpackMsaaIntegration {
    ;
    private static final Map<Integer, TexturePair> TEXTURES = new LinkedHashMap<>();
    private static final Map<Integer, FramebufferState> FRAMEBUFFERS = new HashMap<>();
    private static int colorSeedProgram;
    private static int depthSeedProgram;
    private static int packedResolveProgram;
    private static int depthResolveProgram;
    private static int seedVao;
    private static ShaderPatchEngine.ShaderpackIntegration integration =
            ShaderPatchEngine.ShaderpackIntegration.NONE;

    /** Captures exactly the framebuffer returned by Iris' createGbufferFramebuffer call. */
    public static void captureGbufferFramebuffer(GlFramebuffer framebuffer,
                                                 int[] drawBuffers,
                                                 GpuTexture depthTexture) {
        if (framebuffer == null || drawBuffers == null || drawBuffers.length == 0) return;
        try {
            FramebufferState state = framebuffer(framebuffer.getId());
            state.colorTextures.clear();
            state.colorTargets.clear();
            for (int attachment = 0; attachment < drawBuffers.length; attachment++) {
                int texture = framebuffer.getColorAttachment(attachment);
                if (texture > 0) {
                    state.colorTextures.put(attachment, texture);
                    // drawBuffers contains shaderpack-global colortex indices while GlFramebuffer
                    // exposes local COLOR_ATTACHMENT slots. Preserve both: colortex1/2 are packed
                    // semantic G-buffer payloads and must never be averaged by fixed MSAA resolve.
                    state.colorTargets.put(attachment, drawBuffers[attachment]);
                }
            }
            state.depthTexture = depthTexture instanceof GlTexture glTexture ? glTexture.glId() : 0;
            state.drawBuffers = new int[drawBuffers.length];
            for (int attachment = 0; attachment < drawBuffers.length; attachment++) {
                state.drawBuffers[attachment] = attachment;
            }
            state.readBuffer = 0;
            state.noDrawBuffers = false;
        } catch (Throwable throwable) {
            fail("MSAA gbuffer capture failed", throwable);
        }
    }

    public static void configureDrawBuffers(GlFramebuffer framebuffer, int[] buffers) {
        FramebufferState state = state(framebuffer);
        if (state == null || buffers == null) return;
        state.drawBuffers = buffers.clone();
        state.noDrawBuffers = false;
        if (state.active) configureTwinDrawBuffers(state);
    }

    public static void configureReadBuffer(GlFramebuffer framebuffer, int buffer) {
        FramebufferState state = state(framebuffer);
        if (state == null) return;
        state.readBuffer = buffer;
        if (state.active) configureTwinReadBuffer(state);
    }

    public static void configureNoDrawBuffers(GlFramebuffer framebuffer) {
        FramebufferState state = state(framebuffer);
        if (state == null) return;
        state.drawBuffers = null;
        state.noDrawBuffers = true;
        if (state.active) configureTwinDrawBuffers(state);
    }

    /** Returns true when the normal Iris bind must be cancelled in favor of the single-sample twin. */
    public static boolean bindFallback(GlFramebuffer framebuffer, int target) {
        FramebufferState state = state(framebuffer);
        if (state != null && state.active && wantsMsaa()) {
            // Minecraft normally leaves multisampling enabled, but it is global GL state and a
            // shaderpack/custom pass is allowed to change it. Reassert it at the only place where
            // an Iris raster framebuffer becomes current; otherwise a multisample attachment can
            // receive one broadcast value for every sample and provide no edge coverage at all.
            GL11C.glEnable(GL13C.GL_MULTISAMPLE);
            return false;
        }
        // In TAA/OFF the original Iris framebuffer must remain completely native. The fallback
        // twin is only a short transition safety net if the AA owner changed mid-frame before
        // beginFrame() had a chance to restore the original attachments.
        if (state == null || !state.active || wantsMsaa()) return false;
        GL30C.glBindFramebuffer(target, state.singleSampleFbo);
        return true;
    }

    public static void beforeFrameClear() {
        deactivateAll(false);
    }

    /** Mirrors Iris' real shaderpack color-clear pass into the detached multisample twins. */
    public static void mirrorShaderpackClear(GlFramebuffer framebuffer, int clearFlags,
                                             float r, float g, float b, float a) {
        if (!wantsMsaa() || framebuffer == null
                || (clearFlags & GL11C.GL_COLOR_BUFFER_BIT) == 0) return;
        try {
            Set<Integer> referenced = referencedTextures();
            int maxAttachments = GL11C.glGetInteger(GL30C.GL_MAX_COLOR_ATTACHMENTS);
            for (int attachment = 0; attachment < maxAttachments; attachment++) {
                int singleTexture = framebuffer.getColorAttachment(attachment);
                if (singleTexture <= 0 || !referenced.contains(singleTexture)) continue;
                TexturePair pair = pair(singleTexture, false);
                clearMultisampleColor(pair, r, g, b, a);
            }
        } catch (Throwable throwable) {
            fail("MSAA shaderpack clear mirror failed", throwable);
        }
    }

    /**
     * Activates the shaderpack multisample topology at Iris' post-main-clear boundary.
     *
     * <p>This must run from {@code IrisRenderingPipeline.onBeginClear()}, not from
     * {@code beginLevelRendering()}: 26.2 builds the level frame graph after
     * {@code beginLevelRendering()}, and the real main color/depth clear executes later as the
     * first frame-graph pass. Activating earlier seeds the MS depth from the previous frame and
     * lets the subsequent single-sample clear bypass it, so untouched sky samples contain stale
     * world depth and Photon takes its terrain branch instead of the sky branch.</p>
     */
    public static void beginFrame(long ignoredIntegrationEpoch) {
        // RenderTargets/GlFramebuffer destruction is the authoritative resource lifetime. Do not
        // clear here: Iris can advance Combatant's logical epoch after constructing the new
        // pipeline, which used to discard its captured FBOs before their first frame.

        IrisRuntimeSnapshot runtime = IrisRuntime.snapshot();
        integration = ShaderPatchEngine.profile(runtime.shaderpackName()).integration();
        IrisAaIntegrationState ownership = IrisAaIntegration.resolve(MainConfig.get().getAntialiasing3dMode());
        boolean requestedMsaa = ownership.owner() == IrisAaOwner.COMBATANT_MSAA;
        try {
            if (!requestedMsaa) {
                deactivateAll(true);
                logState(ownership);
                return;
            }
            activateAll();
            refreshTextures();
            verifyFramebuffers();
            seedDepthFromMainClear();
            logState(ownership);
        } catch (Throwable throwable) {
            fail("MSAA frame seed failed", throwable);
        }
    }


    private static void logState(IrisAaIntegrationState ownership) {
        int activeFramebuffers = 0;
        for (FramebufferState state : FRAMEBUFFERS.values()) {
            if (state.active) activeFramebuffers++;
        }
        DebugLog.infoOnChange(
                "iris.shaderpack.msaa.state",
                ownership.owner() + "|" + FRAMEBUFFERS.size() + "|" + activeFramebuffers + "|"
                        + MainConfig.get().getConfiguredMsaa3dSamples(),
                "[IrisCompat] shaderpack MSAA owner=%s capturedFbos=%d activeFbos=%d samples=%dx reason=%s",
                ownership.owner(), FRAMEBUFFERS.size(), activeFramebuffers,
                MainConfig.get().getConfiguredMsaa3dSamples(), ownership.reason());
    }


    /** Refreshes multisample targets after an Iris prepare/deferred stage changed single-sample contents. */
    public static void seedWorld() {
        if (!wantsMsaa()) return;
        try {
            refreshTextures();
            blitReferenced(false);
        } catch (Throwable throwable) {
            fail("MSAA world seed failed", throwable);
        }
    }

    /**
     * Publishes only the single-sample outputs which are subsequently used as multisample forward
     * render targets. In particular this must not touch depth or the opaque packed G-buffer:
     * copying representative sample 0 back into those images destroys the coverage generated by
     * terrain/entities/hand immediately before the deferred pass.
     */
    public static void seedForward() {
        if (!wantsMsaa()) return;
        try {
            refreshTextures();
            for (int texture : referencedTextures()) {
                TexturePair pair = TEXTURES.get(texture);
                if (pair == null || pair.depth || !isForwardTexture(pair.singleTexture)) continue;
                rasterSeed(pair);
            }
        } catch (Throwable throwable) {
            fail("MSAA forward seed failed", throwable);
        }
    }

    public static void resolveWorld() {
        if (!wantsMsaa()) return;
        try {
            refreshTextures();
            blitReferenced(true);
        } catch (Throwable throwable) {
            fail("MSAA world resolve failed", throwable);
        }
    }

    /** Resolves only buffers written by the forward/translucent stage plus live scene depth. */
    public static void resolveForward() {
        if (!wantsMsaa()) {
            // Ownership can change between beginLevelRendering and finalization. Never leave Iris
            // pointing at our multisample attachments after the world-raster interval.
            deactivateAll(false);
            return;
        }
        try {
            refreshTextures();
            for (int texture : referencedTextures()) {
                TexturePair pair = TEXTURES.get(texture);
                if (pair == null) continue;
                if (pair.depth) {
                    rasterResolveDepthSample(pair, 0);
                } else if (isForwardTexture(pair.singleTexture)) {
                    blit(pair.multisampleTexture, GL32C.GL_TEXTURE_2D_MULTISAMPLE,
                            pair.singleTexture, GL11C.GL_TEXTURE_2D,
                            pair.width, pair.height, GL11C.GL_COLOR_BUFFER_BIT);
                }
            }
        } catch (Throwable throwable) {
            fail("MSAA forward resolve failed", throwable);
        } finally {
            // The multisample attachments belong only to Iris' world-raster interval. Keeping
            // them installed through composite/final and into the next frame lets Iris clear and
            // flip the original single-sample targets while later gbuffer binds still address the
            // old multisample images. That is the stale-world data previously visible in sky
            // pixels. Restore Iris' real framebuffer topology as soon as forward rendering ends;
            // beginFrame() installs and seeds the twins again for the next world pass.
            deactivateAll(false);
        }
    }

    /** Resolve only the live scene depth before Iris snapshots pre-hand/pre-translucent depth. */
    public static void resolveDepth() {
        if (!wantsMsaa()) return;
        try {
            refreshTextures();
            for (TexturePair pair : TEXTURES.values()) {
                if (!pair.depth) continue;
                // Keep the single-sample depth snapshot tied to the same concrete coverage sample
                // used for representative packed G-buffer data. A driver-selected depth blit
                // sample can disagree with colortex1/2 at polygon edges and feed impossible
                // depth/normal/material combinations into Photon helper passes.
                rasterResolveDepthSample(pair, 0);
            }
        } catch (Throwable throwable) {
            fail("MSAA depth resolve failed", throwable);
        }
    }

    /**
     * Executes Photon's deferred shading once per coverage sample. The shaderpack stores packed
     * normals/material/light data in colortex1/2, so averaging those attachments before decode is
     * invalid. Instead d4 decodes one real multisample G-buffer sample at a time and this method
     * accumulates the shaded HDR result with equal sample weights into the normal single-sample
     * composite target.
     *
     * @return true when the draw was consumed and repeated per sample.
     */
    public static boolean drawDeferredPerSample(Runnable draw) {
        if (!wantsMsaa() || draw == null) return false;
        int program = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        if (program <= 0) return false;

        int sampleIndexLocation = GL20C.glGetUniformLocation(program, "combatantMsaaSampleIndex");
        int sampleCountLocation = GL20C.glGetUniformLocation(program, "combatantMsaaSampleCount");
        int gbuffer0Location = GL20C.glGetUniformLocation(program, "combatantMsaaGbuffer0");
        int gbuffer1Location = GL20C.glGetUniformLocation(program, "combatantMsaaGbuffer1");
        int overlayLocation = GL20C.glGetUniformLocation(program, "combatantMsaaOverlay");
        int depthLocation = GL20C.glGetUniformLocation(program, "combatantMsaaDepth");
        int activeLocation = GL20C.glGetUniformLocation(program, "combatantMsaaDeferredActive");
        if (sampleIndexLocation < 0 || sampleCountLocation < 0 || gbuffer0Location < 0
                || gbuffer1Location < 0 || depthLocation < 0 || activeLocation < 0) return false;

        TexturePair gbuffer0 = multisamplePairForSampler(program, "colortex1");
        TexturePair gbuffer1 = multisamplePairForSampler(program, "colortex2");
        TexturePair overlay = overlayLocation >= 0 ? multisamplePairForSampler(program, "colortex3") : null;
        TexturePair depth = primaryDepthPair();
        if (gbuffer0 == null || gbuffer1 == null || depth == null || (overlayLocation >= 0 && overlay == null)) {
            DebugLog.warnOnce("iris.msaa.deferred.missing-samples",
                    "[IrisCompat] Photon MSAA deferred sample resources unavailable; using single draw");
            return false;
        }
        int sampleCount = Math.min(gbuffer0.samples, Math.min(gbuffer1.samples, depth.samples));
        if (overlay != null) sampleCount = Math.min(sampleCount, overlay.samples);
        if (sampleCount <= 1) return false;

        int bindingCount = overlay == null ? 3 : 4;
        int[] units = findFreeTextureUnits(program, bindingCount);
        if (units == null) {
            DebugLog.warnOnce("iris.msaa.deferred.no-units",
                    "[IrisCompat] Photon MSAA deferred resolve has no free texture units; using single draw");
            return false;
        }

        int previousActiveTexture = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        int[] previousMsBindings = new int[bindingCount];
        int[] previousSamplerBindings = new int[bindingCount];
        boolean previousBlend = GL11C.glIsEnabled(GL11C.GL_BLEND);
        int previousSrcRgb = GL11C.glGetInteger(GL14C.GL_BLEND_SRC_RGB);
        int previousDstRgb = GL11C.glGetInteger(GL14C.GL_BLEND_DST_RGB);
        int previousSrcAlpha = GL11C.glGetInteger(GL14C.GL_BLEND_SRC_ALPHA);
        int previousDstAlpha = GL11C.glGetInteger(GL14C.GL_BLEND_DST_ALPHA);
        int previousEquationRgb = GL11C.glGetInteger(GL20C.GL_BLEND_EQUATION_RGB);
        int previousEquationAlpha = GL11C.glGetInteger(GL20C.GL_BLEND_EQUATION_ALPHA);
        float[] previousBlendColor = new float[4];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer blendColor = stack.mallocFloat(4);
            GL11C.glGetFloatv(GL14C.GL_BLEND_COLOR, blendColor);
            for (int i = 0; i < 4; i++) previousBlendColor[i] = blendColor.get(i);
        }

        int[] textures = overlay == null
                ? new int[]{gbuffer0.multisampleTexture, gbuffer1.multisampleTexture, depth.multisampleTexture}
                : new int[]{gbuffer0.multisampleTexture, gbuffer1.multisampleTexture, overlay.multisampleTexture, depth.multisampleTexture};
        int[] locations = overlay == null
                ? new int[]{gbuffer0Location, gbuffer1Location, depthLocation}
                : new int[]{gbuffer0Location, gbuffer1Location, overlayLocation, depthLocation};
        try {
            for (int i = 0; i < units.length; i++) {
                GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + units[i]);
                previousMsBindings[i] = GL11C.glGetInteger(GL32C.GL_TEXTURE_BINDING_2D_MULTISAMPLE);
                previousSamplerBindings[i] = GL11C.glGetInteger(GL33C.GL_SAMPLER_BINDING);
                GL33C.glBindSampler(units[i], 0);
                GL11C.glBindTexture(GL32C.GL_TEXTURE_2D_MULTISAMPLE, textures[i]);
                GL20C.glUniform1i(locations[i], units[i]);
            }
            GL20C.glUniform1i(sampleCountLocation, sampleCount);
            GL20C.glUniform1i(activeLocation, 1);

            float weight = 1.0f / sampleCount;
            GL11C.glEnable(GL11C.GL_BLEND);
            GL20C.glBlendEquationSeparate(GL14C.GL_FUNC_ADD, GL14C.GL_FUNC_ADD);
            GL14C.glBlendColor(weight, weight, weight, weight);
            for (int sample = 0; sample < sampleCount; sample++) {
                GL20C.glUniform1i(sampleIndexLocation, sample);
                GL14C.glBlendFuncSeparate(GL14C.GL_CONSTANT_COLOR,
                        sample == 0 ? GL11C.GL_ZERO : GL11C.GL_ONE,
                        GL14C.GL_CONSTANT_ALPHA,
                        sample == 0 ? GL11C.GL_ZERO : GL11C.GL_ONE);
                draw.run();
            }
            DebugLog.infoOnce("iris.msaa.deferred.per-sample." + sampleCount,
                    "[IrisCompat] Photon deferred MSAA resolves packed G-buffer after shading (%dx)", sampleCount);
            return true;
        } finally {
            // The patched d4 shader must never sample the auxiliary multisample samplers unless
            // this wrapper actually bound valid resources. This also makes the normal one-draw
            // fallback deterministic if resource discovery fails on a future Iris build.
            GL20C.glUniform1i(activeLocation, 0);
            GL20C.glBlendEquationSeparate(previousEquationRgb, previousEquationAlpha);
            GL14C.glBlendFuncSeparate(previousSrcRgb, previousDstRgb, previousSrcAlpha, previousDstAlpha);
            GL14C.glBlendColor(previousBlendColor[0], previousBlendColor[1],
                    previousBlendColor[2], previousBlendColor[3]);
            restore(GL11C.GL_BLEND, previousBlend);
            for (int i = 0; i < units.length; i++) {
                GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + units[i]);
                GL11C.glBindTexture(GL32C.GL_TEXTURE_2D_MULTISAMPLE, previousMsBindings[i]);
                GL33C.glBindSampler(units[i], previousSamplerBindings[i]);
            }
            GL13C.glActiveTexture(previousActiveTexture);
        }
    }

    public static void framebufferDestroyed(GlFramebuffer framebuffer) {
        FramebufferState state = state(framebuffer);
        if (state != null) {
            FRAMEBUFFERS.remove(state.msaaFbo);
            if (state.singleSampleFbo > 0) GL30C.glDeleteFramebuffers(state.singleSampleFbo);
        }
    }

    public static void shutdown() {
        // Always put Iris' original single-sample attachments back before deleting our
        // multisample resources. Otherwise the Iris FBO keeps dangling/foreign attachments.
        deactivateAll(false);
        for (FramebufferState state : FRAMEBUFFERS.values()) {
            if (state.singleSampleFbo > 0) GL30C.glDeleteFramebuffers(state.singleSampleFbo);
        }
        FRAMEBUFFERS.clear();
        for (TexturePair pair : TEXTURES.values()) {
            if (pair.multisampleTexture > 0) GL11C.glDeleteTextures(pair.multisampleTexture);
        }
        TEXTURES.clear();
        if (colorSeedProgram > 0) GL20C.glDeleteProgram(colorSeedProgram);
        if (depthSeedProgram > 0) GL20C.glDeleteProgram(depthSeedProgram);
        if (packedResolveProgram > 0) GL20C.glDeleteProgram(packedResolveProgram);
        if (depthResolveProgram > 0) GL20C.glDeleteProgram(depthResolveProgram);
        if (seedVao > 0) GL30C.glDeleteVertexArrays(seedVao);
        colorSeedProgram = 0;
        depthSeedProgram = 0;
        packedResolveProgram = 0;
        depthResolveProgram = 0;
        seedVao = 0;
        integration = ShaderPatchEngine.ShaderpackIntegration.NONE;
    }

    private static TexturePair pair(int singleTexture, boolean depth) {
        TexturePair current = TEXTURES.get(singleTexture);
        if (current != null) return current;
        TextureInfo info = inspect(singleTexture);
        int samples = samples();
        int multisample = GL11C.glGenTextures();
        allocate(multisample, samples, info);
        TexturePair created = new TexturePair(singleTexture, multisample, depth, samples,
                info.width, info.height, info.internalFormat);
        TEXTURES.put(singleTexture, created);
        return created;
    }

    private static void activateAll() {
        boolean activated = false;
        for (FramebufferState state : FRAMEBUFFERS.values()) {
            if (state.active) continue;
            for (Map.Entry<Integer, Integer> entry : state.colorTextures.entrySet()) {
                TexturePair pair = pair(entry.getValue(), false);
                attach(state.msaaFbo, GL30C.GL_COLOR_ATTACHMENT0 + entry.getKey(),
                        GL32C.GL_TEXTURE_2D_MULTISAMPLE, pair.multisampleTexture);
                attach(state.singleSampleFbo, GL30C.GL_COLOR_ATTACHMENT0 + entry.getKey(),
                        GL11C.GL_TEXTURE_2D, entry.getValue());
            }
            if (state.depthTexture > 0) {
                TexturePair pair = pair(state.depthTexture, true);
                attach(state.msaaFbo, GL30C.GL_DEPTH_ATTACHMENT,
                        GL32C.GL_TEXTURE_2D_MULTISAMPLE, pair.multisampleTexture);
                attach(state.singleSampleFbo, GL30C.GL_DEPTH_ATTACHMENT,
                        GL11C.GL_TEXTURE_2D, state.depthTexture);
            }
            state.active = true;
            activated = true;
            configureTwinDrawBuffers(state);
            configureTwinReadBuffer(state);
        }
        if (activated) {
            DebugLog.infoOnce("iris.shaderpack.msaa.topology.activated." + samples(),
                    "[IrisCompat] shaderpack MSAA framebuffer topology activated (%d FBOs, %dx)",
                    FRAMEBUFFERS.size(), samples());
        }
    }

    private static void deactivateAll(boolean preserveContents) {
        boolean anyActive = false;
        for (FramebufferState state : FRAMEBUFFERS.values()) {
            if (state.active) {
                anyActive = true;
                break;
            }
        }
        if (!anyActive) return;

        if (preserveContents) {
            // Preserve the last multisample world contents before returning ownership to Iris.
            blitReferenced(true);
        }
        for (FramebufferState state : FRAMEBUFFERS.values()) {
            if (!state.active) continue;
            if (GL30C.glIsFramebuffer(state.msaaFbo)) {
                for (Map.Entry<Integer, Integer> entry : state.colorTextures.entrySet()) {
                    attach(state.msaaFbo, GL30C.GL_COLOR_ATTACHMENT0 + entry.getKey(),
                            GL11C.GL_TEXTURE_2D, entry.getValue());
                }
                if (state.depthTexture > 0) {
                    attach(state.msaaFbo, GL30C.GL_DEPTH_ATTACHMENT,
                            GL11C.GL_TEXTURE_2D, state.depthTexture);
                }
            }
            state.active = false;
        }
        DebugLog.infoOnce("iris.shaderpack.msaa.topology.deactivated",
                "[IrisCompat] shaderpack MSAA framebuffer topology deactivated (%d FBOs)",
                FRAMEBUFFERS.size());
    }

    private static void configureTwinDrawBuffers(FramebufferState state) {
        if (state == null || !state.active || state.singleSampleFbo <= 0) return;
        if (state.noDrawBuffers) {
            withFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, state.singleSampleFbo,
                    () -> GL11C.glDrawBuffer(GL11C.GL_NONE));
            return;
        }
        if (state.drawBuffers == null) return;
        int[] glBuffers = new int[state.drawBuffers.length];
        for (int i = 0; i < state.drawBuffers.length; i++) {
            glBuffers[i] = GL30C.GL_COLOR_ATTACHMENT0 + state.drawBuffers[i];
        }
        withFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, state.singleSampleFbo,
                () -> GL20C.glDrawBuffers(glBuffers));
    }

    private static void configureTwinReadBuffer(FramebufferState state) {
        if (state == null || !state.active || state.singleSampleFbo <= 0 || state.readBuffer < 0) return;
        withFramebuffer(GL30C.GL_READ_FRAMEBUFFER, state.singleSampleFbo,
                () -> GL11C.glReadBuffer(GL30C.GL_COLOR_ATTACHMENT0 + state.readBuffer));
    }

    private static void refreshTextures() {
        int requested = samples();
        Set<Integer> referenced = referencedTextures();
        var iterator = TEXTURES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, TexturePair> entry = iterator.next();
            TexturePair pair = entry.getValue();
            if (!referenced.contains(entry.getKey())) {
                GL11C.glDeleteTextures(pair.multisampleTexture);
                iterator.remove();
                continue;
            }
            TextureInfo info = inspect(pair.singleTexture);
            if (info.width <= 0 || info.height <= 0) {
                throw new IllegalStateException("shaderpack target has invalid dimensions");
            }
            if (pair.width != info.width || pair.height != info.height
                    || pair.samples != requested || pair.internalFormat != info.internalFormat) {
                allocate(pair.multisampleTexture, requested, info);
                entry.setValue(new TexturePair(pair.singleTexture, pair.multisampleTexture, pair.depth,
                        requested, info.width, info.height, info.internalFormat));
            }
        }
    }

    /**
     * Seeds only the multisample scene depth from Minecraft's depth texture after the real
     * frame-graph clear has executed.
     *
     * <p>Keep this texture in Minecraft's native reverse-Z convention (far/clear = 0) because it is
     * still used as a real raster depth attachment. Shaderpack reads normally get converted by Iris'
     * DepthTransformer; the injected sampler2DMS performs that conversion explicitly in the Photon
     * patch. Copying the actual cleared texture preserves the raster convention while leaving
     * shaderpack color targets on their own ClearPass/history lifecycle.</p>
     */
    private static void seedDepthFromMainClear() {
        int seeded = 0;
        for (int texture : referencedTextures()) {
            TexturePair pair = TEXTURES.get(texture);
            if (pair == null || !pair.depth) continue;
            rasterSeed(pair);
            seeded++;
        }
        DebugLog.infoOnce("iris.shaderpack.msaa.depth-seeded-after-main-clear",
                "[IrisCompat] shaderpack MSAA depth seeded from cleared Iris/Minecraft depth (%d target%s)",
                seeded, seeded == 1 ? "" : "s");
    }

    private static void clearMultisampleColor(TexturePair pair, float r, float g, float b, float a) {
        int framebuffer = GL30C.glGenFramebuffers();
        int previousRead = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        boolean previousScissor = GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, framebuffer);
            GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0,
                    GL32C.GL_TEXTURE_2D_MULTISAMPLE, pair.multisampleTexture, 0);
            GL11C.glDrawBuffer(GL30C.GL_COLOR_ATTACHMENT0);
            GL11C.glReadBuffer(GL30C.GL_COLOR_ATTACHMENT0);
            int status = GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER);
            if (status != GL30C.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("incomplete shaderpack MSAA color-clear framebuffer: 0x"
                        + Integer.toHexString(status));
            }

            // ClearPass has already disabled scissor. Preserve the existing color write mask so
            // this mirror has the same channel semantics as the single-sample clear that just ran.
            GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
            FloatBuffer clearValue = stack.mallocFloat(4);
            clearValue.put(0, r).put(1, g).put(2, b).put(3, a);
            GL30C.glClearBufferfv(GL11C.GL_COLOR, 0, clearValue);
        } finally {
            restore(GL11C.GL_SCISSOR_TEST, previousScissor);
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, previousRead);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, previousDraw);
            GL30C.glDeleteFramebuffers(framebuffer);
        }
    }

    private static void blitReferenced(boolean resolve) {
        for (int texture : referencedTextures()) {
            TexturePair pair = TEXTURES.get(texture);
            if (pair == null) continue;
            int mask = pair.depth ? GL11C.GL_DEPTH_BUFFER_BIT : GL11C.GL_COLOR_BUFFER_BIT;
            if (resolve) {
                if (pair.depth) {
                    rasterResolveDepthSample(pair, 0);
                } else if (isPackedGbufferTexture(pair.singleTexture)) {
                    // colortex1/2 contain 16-bit containers whose components themselves encode
                    // 8-bit pairs, normals and material ids. glBlitFramebuffer MSAA resolve would
                    // arithmetically average those containers and create invalid decoded values
                    // (the red/blue edge fringes). Keep sample 0 intact for single-sample helper
                    // passes; d4 consumes every real sample through sampler2DMS separately.
                    rasterResolveSample(pair, 0);
                } else {
                    blit(pair.multisampleTexture, GL32C.GL_TEXTURE_2D_MULTISAMPLE,
                            pair.singleTexture, GL11C.GL_TEXTURE_2D,
                            pair.width, pair.height, mask);
                }
            } else {
                rasterSeed(pair);
            }
        }
    }

    private static boolean isPackedGbufferTexture(int singleTexture) {
        return hasSemanticTarget(singleTexture, integration.msaaPackedTargets());
    }

    private static boolean isForwardTexture(int singleTexture) {
        return hasSemanticTarget(singleTexture, integration.msaaForwardTargets());
    }

    private static boolean hasSemanticTarget(int singleTexture, Set<Integer> targets) {
        if (targets == null || targets.isEmpty()) return false;
        for (FramebufferState state : FRAMEBUFFERS.values()) {
            for (Map.Entry<Integer, Integer> entry : state.colorTextures.entrySet()) {
                if (entry.getValue() != singleTexture) continue;
                Integer target = state.colorTargets.get(entry.getKey());
                if (target != null && targets.contains(target)) return true;
            }
        }
        return false;
    }

    private static TexturePair multisamplePairForSampler(int program, String uniformName) {
        int location = GL20C.glGetUniformLocation(program, uniformName);
        if (location < 0) return null;
        int unit = GL20C.glGetUniformi(program, location);
        if (unit < 0 || unit >= GL11C.glGetInteger(GL20C.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS)) return null;
        int previousActive = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        try {
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + unit);
            int singleTexture = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
            return TEXTURES.get(singleTexture);
        } finally {
            GL13C.glActiveTexture(previousActive);
        }
    }

    private static TexturePair primaryDepthPair() {
        TexturePair candidate = null;
        for (FramebufferState state : FRAMEBUFFERS.values()) {
            if (!state.active || state.depthTexture <= 0) continue;
            TexturePair pair = TEXTURES.get(state.depthTexture);
            if (pair == null || !pair.depth) continue;
            if (candidate == null) candidate = pair;
            else if (candidate.singleTexture != pair.singleTexture) {
                // RenderTargets normally shares currentDepthTexture across every gbuffer FBO.
                // If that invariant changes, refuse a guessed depth source.
                return null;
            }
        }
        return candidate;
    }

    private static int[] findFreeTextureUnits(int program, int count) {
        int max = GL11C.glGetInteger(GL20C.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS);
        Set<Integer> used = new HashSet<>();
        int activeUniforms = GL20C.glGetProgrami(program, GL20C.GL_ACTIVE_UNIFORMS);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer size = stack.mallocInt(1);
            IntBuffer type = stack.mallocInt(1);
            for (int i = 0; i < activeUniforms; i++) {
                size.put(0, 0);
                type.put(0, 0);
                String name = GL20C.glGetActiveUniform(program, i, size, type);
                if (name == null || !isSamplerType(type.get(0))) continue;
                int location = GL20C.glGetUniformLocation(program, name);
                if (location < 0) continue;
                int arraySize = Math.max(1, size.get(0));
                for (int j = 0; j < arraySize; j++) {
                    int unit = GL20C.glGetUniformi(program, location + j);
                    if (unit >= 0 && unit < max) used.add(unit);
                }
            }
        }
        int[] free = new int[count];
        int found = 0;
        for (int unit = max - 1; unit >= 0 && found < count; unit--) {
            if (!used.contains(unit)) free[found++] = unit;
        }
        return found == count ? free : null;
    }

    private static boolean isSamplerType(int type) {
        return switch (type) {
            case GL20C.GL_SAMPLER_1D, GL20C.GL_SAMPLER_2D, GL20C.GL_SAMPLER_3D,
                    GL20C.GL_SAMPLER_CUBE, GL20C.GL_SAMPLER_1D_SHADOW, GL20C.GL_SAMPLER_2D_SHADOW,
                    GL30C.GL_SAMPLER_1D_ARRAY, GL30C.GL_SAMPLER_2D_ARRAY,
                    GL30C.GL_SAMPLER_1D_ARRAY_SHADOW, GL30C.GL_SAMPLER_2D_ARRAY_SHADOW,
                    GL30C.GL_SAMPLER_CUBE_SHADOW,
                    GL31C.GL_SAMPLER_2D_RECT, GL31C.GL_SAMPLER_2D_RECT_SHADOW, GL31C.GL_SAMPLER_BUFFER,
                    GL32C.GL_SAMPLER_2D_MULTISAMPLE, GL32C.GL_SAMPLER_2D_MULTISAMPLE_ARRAY -> true;
            default -> false;
        };
    }

    private static void rasterResolveSample(TexturePair pair, int sampleIndex) {
        int framebuffer = GL30C.glGenFramebuffers();
        int previousRead = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousProgram = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        int previousVao = GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING);
        int previousActiveTexture = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        boolean depthTest = GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST);
        boolean blend = GL11C.glIsEnabled(GL11C.GL_BLEND);
        boolean scissor = GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST);
        boolean cull = GL11C.glIsEnabled(GL11C.GL_CULL_FACE);
        boolean framebufferSrgb = GL11C.glIsEnabled(GL30C.GL_FRAMEBUFFER_SRGB);
        boolean rasterizerDiscard = GL11C.glIsEnabled(GL30C.GL_RASTERIZER_DISCARD);
        int previousTexture;
        int previousSampler;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer viewport = stack.mallocInt(4);
            IntBuffer polygonMode = stack.mallocInt(2);
            ByteBuffer colorMask = stack.malloc(4);
            GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, viewport);
            GL11C.glGetIntegerv(GL20C.GL_POLYGON_MODE, polygonMode);
            GL11C.glGetBooleanv(GL11C.GL_COLOR_WRITEMASK, colorMask);
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0);
            previousTexture = GL11C.glGetInteger(GL32C.GL_TEXTURE_BINDING_2D_MULTISAMPLE);
            previousSampler = GL11C.glGetInteger(GL33C.GL_SAMPLER_BINDING);
            try {
                GL33C.glBindSampler(0, 0);
                GL11C.glBindTexture(GL32C.GL_TEXTURE_2D_MULTISAMPLE, pair.multisampleTexture);
                GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, framebuffer);
                GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0,
                        GL11C.GL_TEXTURE_2D, pair.singleTexture, 0);
                GL11C.glDrawBuffer(GL30C.GL_COLOR_ATTACHMENT0);
                if (GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER) != GL30C.GL_FRAMEBUFFER_COMPLETE) {
                    throw new IllegalStateException("incomplete packed G-buffer resolve framebuffer");
                }
                GL11C.glViewport(0, 0, pair.width, pair.height);
                GL11C.glDisable(GL11C.GL_DEPTH_TEST);
                GL11C.glDisable(GL11C.GL_BLEND);
                GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
                GL11C.glDisable(GL11C.GL_CULL_FACE);
                GL11C.glDisable(GL30C.GL_FRAMEBUFFER_SRGB);
                GL11C.glDisable(GL30C.GL_RASTERIZER_DISCARD);
                GL11C.glPolygonMode(GL11C.GL_FRONT_AND_BACK, GL11C.GL_FILL);
                GL11C.glColorMask(true, true, true, true);
                int program = packedResolveProgram();
                GL20C.glUseProgram(program);
                GL20C.glUniform1i(GL20C.glGetUniformLocation(program, "sourceTexture"), 0);
                GL20C.glUniform1i(GL20C.glGetUniformLocation(program, "sampleIndex"), sampleIndex);
                GL30C.glBindVertexArray(seedVao());
                GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 3);
            } finally {
                GL11C.glBindTexture(GL32C.GL_TEXTURE_2D_MULTISAMPLE, previousTexture);
                GL33C.glBindSampler(0, previousSampler);
                GL13C.glActiveTexture(previousActiveTexture);
                GL20C.glUseProgram(previousProgram);
                GL30C.glBindVertexArray(previousVao);
                GL11C.glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
                GL11C.glPolygonMode(GL11C.GL_FRONT, polygonMode.get(0));
                GL11C.glPolygonMode(GL11C.GL_BACK, polygonMode.get(1));
                GL11C.glColorMask(colorMask.get(0) != 0, colorMask.get(1) != 0,
                        colorMask.get(2) != 0, colorMask.get(3) != 0);
                restore(GL11C.GL_DEPTH_TEST, depthTest);
                restore(GL11C.GL_BLEND, blend);
                restore(GL11C.GL_SCISSOR_TEST, scissor);
                restore(GL11C.GL_CULL_FACE, cull);
                restore(GL30C.GL_FRAMEBUFFER_SRGB, framebufferSrgb);
                restore(GL30C.GL_RASTERIZER_DISCARD, rasterizerDiscard);
            }
        } finally {
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, previousRead);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, previousDraw);
            GL30C.glDeleteFramebuffers(framebuffer);
        }
    }

    private static void rasterResolveDepthSample(TexturePair pair, int sampleIndex) {
        int framebuffer = GL30C.glGenFramebuffers();
        int previousRead = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousProgram = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        int previousVao = GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING);
        int previousActiveTexture = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        boolean depthTest = GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST);
        boolean blend = GL11C.glIsEnabled(GL11C.GL_BLEND);
        boolean scissor = GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST);
        boolean cull = GL11C.glIsEnabled(GL11C.GL_CULL_FACE);
        boolean framebufferSrgb = GL11C.glIsEnabled(GL30C.GL_FRAMEBUFFER_SRGB);
        boolean rasterizerDiscard = GL11C.glIsEnabled(GL30C.GL_RASTERIZER_DISCARD);
        boolean previousDepthMask = GL11C.glGetBoolean(GL11C.GL_DEPTH_WRITEMASK);
        int previousDepthFunc = GL11C.glGetInteger(GL11C.GL_DEPTH_FUNC);
        int previousTexture;
        int previousSampler;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer viewport = stack.mallocInt(4);
            IntBuffer polygonMode = stack.mallocInt(2);
            ByteBuffer colorMask = stack.malloc(4);
            GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, viewport);
            GL11C.glGetIntegerv(GL20C.GL_POLYGON_MODE, polygonMode);
            GL11C.glGetBooleanv(GL11C.GL_COLOR_WRITEMASK, colorMask);
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0);
            previousTexture = GL11C.glGetInteger(GL32C.GL_TEXTURE_BINDING_2D_MULTISAMPLE);
            previousSampler = GL11C.glGetInteger(GL33C.GL_SAMPLER_BINDING);
            try {
                GL33C.glBindSampler(0, 0);
                GL11C.glBindTexture(GL32C.GL_TEXTURE_2D_MULTISAMPLE, pair.multisampleTexture);
                GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, framebuffer);
                GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_DEPTH_ATTACHMENT,
                        GL11C.GL_TEXTURE_2D, pair.singleTexture, 0);
                GL11C.glDrawBuffer(GL11C.GL_NONE);
                GL11C.glReadBuffer(GL11C.GL_NONE);
                if (GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER) != GL30C.GL_FRAMEBUFFER_COMPLETE) {
                    throw new IllegalStateException("incomplete representative depth resolve framebuffer");
                }
                GL11C.glViewport(0, 0, pair.width, pair.height);
                GL11C.glDisable(GL11C.GL_BLEND);
                GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
                GL11C.glDisable(GL11C.GL_CULL_FACE);
                GL11C.glDisable(GL30C.GL_FRAMEBUFFER_SRGB);
                GL11C.glDisable(GL30C.GL_RASTERIZER_DISCARD);
                GL11C.glEnable(GL11C.GL_DEPTH_TEST);
                GL11C.glDepthFunc(GL11C.GL_ALWAYS);
                GL11C.glDepthMask(true);
                GL11C.glColorMask(false, false, false, false);
                GL11C.glPolygonMode(GL11C.GL_FRONT_AND_BACK, GL11C.GL_FILL);
                int program = depthResolveProgram();
                GL20C.glUseProgram(program);
                GL20C.glUniform1i(GL20C.glGetUniformLocation(program, "sourceTexture"), 0);
                GL20C.glUniform1i(GL20C.glGetUniformLocation(program, "sampleIndex"), sampleIndex);
                GL30C.glBindVertexArray(seedVao());
                GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 3);
            } finally {
                GL11C.glBindTexture(GL32C.GL_TEXTURE_2D_MULTISAMPLE, previousTexture);
                GL33C.glBindSampler(0, previousSampler);
                GL13C.glActiveTexture(previousActiveTexture);
                GL20C.glUseProgram(previousProgram);
                GL30C.glBindVertexArray(previousVao);
                GL11C.glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
                GL11C.glPolygonMode(GL11C.GL_FRONT, polygonMode.get(0));
                GL11C.glPolygonMode(GL11C.GL_BACK, polygonMode.get(1));
                GL11C.glColorMask(colorMask.get(0) != 0, colorMask.get(1) != 0,
                        colorMask.get(2) != 0, colorMask.get(3) != 0);
                GL11C.glDepthMask(previousDepthMask);
                GL11C.glDepthFunc(previousDepthFunc);
                restore(GL11C.GL_DEPTH_TEST, depthTest);
                restore(GL11C.GL_BLEND, blend);
                restore(GL11C.GL_SCISSOR_TEST, scissor);
                restore(GL11C.GL_CULL_FACE, cull);
                restore(GL30C.GL_FRAMEBUFFER_SRGB, framebufferSrgb);
                restore(GL30C.GL_RASTERIZER_DISCARD, rasterizerDiscard);
            }
        } finally {
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, previousRead);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, previousDraw);
            GL30C.glDeleteFramebuffers(framebuffer);
        }
    }

    private static void rasterSeed(TexturePair pair) {
        int framebuffer = GL30C.glGenFramebuffers();
        int previousRead = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousProgram = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        int previousVao = GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING);
        int previousActiveTexture = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        boolean depthTest = GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST);
        boolean blend = GL11C.glIsEnabled(GL11C.GL_BLEND);
        boolean scissor = GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST);
        boolean cull = GL11C.glIsEnabled(GL11C.GL_CULL_FACE);
        boolean multisample = GL11C.glIsEnabled(GL13C.GL_MULTISAMPLE);
        boolean framebufferSrgb = GL11C.glIsEnabled(GL30C.GL_FRAMEBUFFER_SRGB);
        boolean rasterizerDiscard = GL11C.glIsEnabled(GL30C.GL_RASTERIZER_DISCARD);
        boolean previousDepthMask = GL11C.glGetBoolean(GL11C.GL_DEPTH_WRITEMASK);
        int previousDepthFunc = GL11C.glGetInteger(GL11C.GL_DEPTH_FUNC);
        int previousTexture;
        int previousSampler;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer viewport = stack.mallocInt(4);
            IntBuffer polygonMode = stack.mallocInt(2);
            ByteBuffer colorMask = stack.malloc(4);
            GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, viewport);
            GL11C.glGetIntegerv(GL20C.GL_POLYGON_MODE, polygonMode);
            GL11C.glGetBooleanv(GL11C.GL_COLOR_WRITEMASK, colorMask);

            GL13C.glActiveTexture(GL13C.GL_TEXTURE0);
            previousTexture = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
            previousSampler = GL11C.glGetInteger(GL33C.GL_SAMPLER_BINDING);
            try {
                GL33C.glBindSampler(0, 0);
                GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, pair.singleTexture);
                GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, framebuffer);
                int attachment = pair.depth ? GL30C.GL_DEPTH_ATTACHMENT : GL30C.GL_COLOR_ATTACHMENT0;
                GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, attachment,
                        GL32C.GL_TEXTURE_2D_MULTISAMPLE, pair.multisampleTexture, 0);
                if (pair.depth) {
                    GL11C.glDrawBuffer(GL11C.GL_NONE);
                    GL11C.glReadBuffer(GL11C.GL_NONE);
                } else {
                    GL11C.glDrawBuffer(GL30C.GL_COLOR_ATTACHMENT0);
                }
                if (GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER) != GL30C.GL_FRAMEBUFFER_COMPLETE) {
                    throw new IllegalStateException("incomplete shaderpack MSAA seed framebuffer");
                }

                GL11C.glViewport(0, 0, pair.width, pair.height);
                GL11C.glDisable(GL11C.GL_BLEND);
                GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
                GL11C.glDisable(GL11C.GL_CULL_FACE);
                GL11C.glDisable(GL30C.GL_FRAMEBUFFER_SRGB);
                GL11C.glDisable(GL30C.GL_RASTERIZER_DISCARD);
                GL11C.glEnable(GL13C.GL_MULTISAMPLE);
                GL11C.glPolygonMode(GL11C.GL_FRONT_AND_BACK, GL11C.GL_FILL);
                if (pair.depth) {
                    GL11C.glEnable(GL11C.GL_DEPTH_TEST);
                    GL11C.glDepthFunc(GL11C.GL_ALWAYS);
                    GL11C.glDepthMask(true);
                    GL11C.glColorMask(false, false, false, false);
                } else {
                    GL11C.glDisable(GL11C.GL_DEPTH_TEST);
                    GL11C.glColorMask(true, true, true, true);
                }

                int program = pair.depth ? depthSeedProgram() : colorSeedProgram();
                GL20C.glUseProgram(program);
                GL20C.glUniform1i(GL20C.glGetUniformLocation(program, "sourceTexture"), 0);
                GL30C.glBindVertexArray(seedVao());
                GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 3);
            } finally {
                GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, previousTexture);
                GL33C.glBindSampler(0, previousSampler);
                GL13C.glActiveTexture(previousActiveTexture);
                GL20C.glUseProgram(previousProgram);
                GL30C.glBindVertexArray(previousVao);
                GL11C.glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
                GL11C.glPolygonMode(GL11C.GL_FRONT, polygonMode.get(0));
                GL11C.glPolygonMode(GL11C.GL_BACK, polygonMode.get(1));
                GL11C.glColorMask(colorMask.get(0) != 0, colorMask.get(1) != 0,
                        colorMask.get(2) != 0, colorMask.get(3) != 0);
                GL11C.glDepthMask(previousDepthMask);
                GL11C.glDepthFunc(previousDepthFunc);
                restore(GL11C.GL_DEPTH_TEST, depthTest);
                restore(GL11C.GL_BLEND, blend);
                restore(GL11C.GL_SCISSOR_TEST, scissor);
                restore(GL11C.GL_CULL_FACE, cull);
                restore(GL13C.GL_MULTISAMPLE, multisample);
                restore(GL30C.GL_FRAMEBUFFER_SRGB, framebufferSrgb);
                restore(GL30C.GL_RASTERIZER_DISCARD, rasterizerDiscard);
            }
        } finally {
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, previousRead);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, previousDraw);
            GL30C.glDeleteFramebuffers(framebuffer);
        }
    }

    private static int colorSeedProgram() {
        if (colorSeedProgram == 0) colorSeedProgram = createSeedProgram(false);
        return colorSeedProgram;
    }

    private static int depthSeedProgram() {
        if (depthSeedProgram == 0) depthSeedProgram = createSeedProgram(true);
        return depthSeedProgram;
    }

    private static int packedResolveProgram() {
        if (packedResolveProgram == 0) {
            String vertex = """
                    #version 330 core
                    void main() {
                        vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                        gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
                    }
                    """;
            String fragment = """
                    #version 330 core
                    uniform sampler2DMS sourceTexture;
                    uniform int sampleIndex;
                    layout(location = 0) out vec4 outputColor;
                    void main() {
                        outputColor = texelFetch(sourceTexture, ivec2(gl_FragCoord.xy), sampleIndex);
                    }
                    """;
            int vertexShader = compileShader(GL20C.GL_VERTEX_SHADER, vertex);
            int fragmentShader = compileShader(GL20C.GL_FRAGMENT_SHADER, fragment);
            int program = GL20C.glCreateProgram();
            try {
                GL20C.glAttachShader(program, vertexShader);
                GL20C.glAttachShader(program, fragmentShader);
                GL20C.glLinkProgram(program);
                if (GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == GL11C.GL_FALSE) {
                    throw new IllegalStateException("packed G-buffer resolve program link failed: "
                            + GL20C.glGetProgramInfoLog(program));
                }
                packedResolveProgram = program;
            } finally {
                GL20C.glDeleteShader(vertexShader);
                GL20C.glDeleteShader(fragmentShader);
            }
        }
        return packedResolveProgram;
    }

    private static int depthResolveProgram() {
        if (depthResolveProgram == 0) {
            String vertex = """
                    #version 330 core
                    void main() {
                        vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                        gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
                    }
                    """;
            String fragment = """
                    #version 330 core
                    uniform sampler2DMS sourceTexture;
                    uniform int sampleIndex;
                    void main() {
                        gl_FragDepth = texelFetch(sourceTexture, ivec2(gl_FragCoord.xy), sampleIndex).r;
                    }
                    """;
            int vertexShader = compileShader(GL20C.GL_VERTEX_SHADER, vertex);
            int fragmentShader = compileShader(GL20C.GL_FRAGMENT_SHADER, fragment);
            int program = GL20C.glCreateProgram();
            try {
                GL20C.glAttachShader(program, vertexShader);
                GL20C.glAttachShader(program, fragmentShader);
                GL20C.glLinkProgram(program);
                if (GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == GL11C.GL_FALSE) {
                    throw new IllegalStateException("representative depth resolve program link failed: "
                            + GL20C.glGetProgramInfoLog(program));
                }
                depthResolveProgram = program;
            } finally {
                GL20C.glDeleteShader(vertexShader);
                GL20C.glDeleteShader(fragmentShader);
            }
        }
        return depthResolveProgram;
    }

    private static int seedVao() {
        if (seedVao == 0) seedVao = GL30C.glGenVertexArrays();
        return seedVao;
    }

    private static int createSeedProgram(boolean depth) {
        String vertex = """
                #version 330 core
                void main() {
                    vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
                }
                """;
        String fragment = depth ? """
                #version 330 core
                uniform sampler2D sourceTexture;
                void main() {
                    gl_FragDepth = texelFetch(sourceTexture, ivec2(gl_FragCoord.xy), 0).r;
                }
                """ : """
                #version 330 core
                uniform sampler2D sourceTexture;
                layout(location = 0) out vec4 outputColor;
                void main() {
                    outputColor = texelFetch(sourceTexture, ivec2(gl_FragCoord.xy), 0);
                }
                """;
        int vertexShader = compileShader(GL20C.GL_VERTEX_SHADER, vertex);
        int fragmentShader = compileShader(GL20C.GL_FRAGMENT_SHADER, fragment);
        int program = GL20C.glCreateProgram();
        try {
            GL20C.glAttachShader(program, vertexShader);
            GL20C.glAttachShader(program, fragmentShader);
            GL20C.glLinkProgram(program);
            if (GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == GL11C.GL_FALSE) {
                throw new IllegalStateException("MSAA seed program link failed: " + GL20C.glGetProgramInfoLog(program));
            }
            return program;
        } catch (Throwable throwable) {
            GL20C.glDeleteProgram(program);
            throw throwable;
        } finally {
            GL20C.glDeleteShader(vertexShader);
            GL20C.glDeleteShader(fragmentShader);
        }
    }

    private static int compileShader(int type, String source) {
        int shader = GL20C.glCreateShader(type);
        GL20C.glShaderSource(shader, source);
        GL20C.glCompileShader(shader);
        if (GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) == GL11C.GL_FALSE) {
            String log = GL20C.glGetShaderInfoLog(shader);
            GL20C.glDeleteShader(shader);
            throw new IllegalStateException("MSAA seed shader compile failed: " + log);
        }
        return shader;
    }

    private static void restore(int capability, boolean enabled) {
        if (enabled) GL11C.glEnable(capability); else GL11C.glDisable(capability);
    }

    private static Set<Integer> referencedTextures() {
        Set<Integer> referenced = new HashSet<>();
        for (FramebufferState state : FRAMEBUFFERS.values()) {
            referenced.addAll(state.colorTextures.values());
            if (state.depthTexture > 0) referenced.add(state.depthTexture);
        }
        return referenced;
    }

    private static void verifyFramebuffers() {
        int previous = GL11C.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING);
        try {
            for (FramebufferState state : FRAMEBUFFERS.values()) {
                GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, state.msaaFbo);
                int status = GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER);
                if (status != GL30C.GL_FRAMEBUFFER_COMPLETE) {
                    throw new IllegalStateException("multisample framebuffer incomplete: 0x"
                            + Integer.toHexString(status));
                }
            }
        } finally {
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, previous);
        }
    }

    private static void blit(int source, int sourceTarget, int destination, int destinationTarget,
                             int width, int height, int mask) {
        int previousRead = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        int read = GL30C.glGenFramebuffers();
        int draw = GL30C.glGenFramebuffers();
        int attachment = mask == GL11C.GL_DEPTH_BUFFER_BIT
                ? GL30C.GL_DEPTH_ATTACHMENT : GL30C.GL_COLOR_ATTACHMENT0;
        try {
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, read);
            GL30C.glFramebufferTexture2D(GL30C.GL_READ_FRAMEBUFFER, attachment, sourceTarget, source, 0);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, draw);
            GL30C.glFramebufferTexture2D(GL30C.GL_DRAW_FRAMEBUFFER, attachment, destinationTarget, destination, 0);
            if (mask == GL11C.GL_COLOR_BUFFER_BIT) {
                GL11C.glReadBuffer(GL30C.GL_COLOR_ATTACHMENT0);
                GL11C.glDrawBuffer(GL30C.GL_COLOR_ATTACHMENT0);
            } else {
                GL11C.glReadBuffer(GL11C.GL_NONE);
                GL11C.glDrawBuffer(GL11C.GL_NONE);
            }
            GL30C.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, mask, GL11C.GL_NEAREST);
        } finally {
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, previousRead);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, previousDraw);
            GL30C.glDeleteFramebuffers(read);
            GL30C.glDeleteFramebuffers(draw);
        }
    }

    private static TextureInfo inspect(int texture) {
        int previous = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        try {
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture);
            return new TextureInfo(
                    GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_TEXTURE_WIDTH),
                    GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_TEXTURE_HEIGHT),
                    GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_TEXTURE_INTERNAL_FORMAT));
        } finally {
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, previous);
        }
    }

    private static void allocate(int texture, int samples, TextureInfo info) {
        int previous = GL11C.glGetInteger(GL32C.GL_TEXTURE_BINDING_2D_MULTISAMPLE);
        try {
            GL11C.glBindTexture(GL32C.GL_TEXTURE_2D_MULTISAMPLE, texture);
            GL32C.glTexImage2DMultisample(GL32C.GL_TEXTURE_2D_MULTISAMPLE, samples,
                    info.internalFormat, info.width, info.height, true);
        } finally {
            GL11C.glBindTexture(GL32C.GL_TEXTURE_2D_MULTISAMPLE, previous);
        }
    }

    private static FramebufferState framebuffer(int msaaFbo) {
        return FRAMEBUFFERS.computeIfAbsent(msaaFbo,
                ignored -> new FramebufferState(msaaFbo, GL30C.glGenFramebuffers()));
    }

    private static FramebufferState state(GlFramebuffer framebuffer) {
        return framebuffer == null ? null : FRAMEBUFFERS.get(framebuffer.getId());
    }

    private static void attach(int framebuffer, int attachment, int target, int texture) {
        withFramebuffer(GL30C.GL_FRAMEBUFFER, framebuffer,
                () -> GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, attachment, target, texture, 0));
    }

    private static void withFramebuffer(int target, int framebuffer, Runnable operation) {
        int binding = target == GL30C.GL_READ_FRAMEBUFFER
                ? GL30C.GL_READ_FRAMEBUFFER_BINDING
                : target == GL30C.GL_DRAW_FRAMEBUFFER ? GL30C.GL_DRAW_FRAMEBUFFER_BINDING : GL30C.GL_FRAMEBUFFER_BINDING;
        int previous = GL11C.glGetInteger(binding);
        try {
            GL30C.glBindFramebuffer(target, framebuffer);
            operation.run();
        } finally {
            GL30C.glBindFramebuffer(target, previous);
        }
    }

    private static boolean wantsMsaa() {
        return IrisAaIntegration.resolve(MainConfig.get().getAntialiasing3dMode()).owner()
                == IrisAaOwner.COMBATANT_MSAA;
    }

    private static int samples() {
        int requested = Math.max(2, MainConfig.get().getConfiguredMsaa3dSamples());
        return Math.min(requested, Math.max(1, GL11C.glGetInteger(GL30C.GL_MAX_SAMPLES)));
    }

    private static void fail(String action, Throwable throwable) {
        String detail = action + ": " + throwable.getClass().getSimpleName()
                + (throwable.getMessage() == null ? "" : ": " + throwable.getMessage());
        IrisAaIntegration.failMode("msaa", detail);
        DebugLog.error("[IrisCompat] " + action, throwable);
    }

    private record TextureInfo(int width, int height, int internalFormat) { }
    private record TexturePair(int singleTexture, int multisampleTexture, boolean depth,
                               int samples, int width, int height, int internalFormat) { }
    private static final class FramebufferState {
        private final int msaaFbo;
        private final int singleSampleFbo;
        private final Map<Integer, Integer> colorTextures = new HashMap<>();
        private final Map<Integer, Integer> colorTargets = new HashMap<>();
        private int depthTexture;
        private int[] drawBuffers;
        private int readBuffer = -1;
        private boolean noDrawBuffers;
        private boolean active;

        private FramebufferState(int msaaFbo, int singleSampleFbo) {
            this.msaaFbo = msaaFbo;
            this.singleSampleFbo = singleSampleFbo;
        }
    }
}
