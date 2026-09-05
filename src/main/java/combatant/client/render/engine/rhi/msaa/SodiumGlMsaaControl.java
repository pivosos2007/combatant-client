/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.msaa;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.GpuOutOfMemoryException;
import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import combatant.client.mixininterface.IGlBackendInfo;
import combatant.client.mixininterface.IMsaaTexture;
import combatant.client.mixins.accessors.GlTextureInvoker;
import combatant.client.render.engine.msaa.MsaaTextureRegistry;
import combatant.client.render.engine.msaa.MsaaWorldTarget;
import combatant.client.render.engine.rhi.backend.gl.GlBackendAccess;
import combatant.client.render.engine.rhi.backend.gl.GlValidation;
import combatant.client.util.logging.DebugLog;

import static org.lwjgl.opengl.GL11C.glDisable;
import static org.lwjgl.opengl.GL11C.glEnable;
import static org.lwjgl.opengl.GL13C.GL_SAMPLE_ALPHA_TO_COVERAGE;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Current MSAA implementation for the GL/Sodium backend.
 * <p>
 * Keep GL ids, GL targets, framebuffer blits, and GlTexture construction contained here. Future Vulkan support should
 * replace this class with a backend-native implementation while preserving the MsaaControl contract.
 */
@SuppressWarnings("deprecation")
public final class SodiumGlMsaaControl implements MsaaControl {
    private static final Identifier ENTITY_SHADER_ID = Identifier.withDefaultNamespace("core/entity");
    private static final int GL_MAX_COLOR_TEXTURE_SAMPLES = 0x910E;
    private static final int GL_MAX_DEPTH_TEXTURE_SAMPLES = 0x910F;
    private static final int MAX_RESOLVE_FRAMEBUFFERS = 32;

    /**
     * Resolve FBOs are pure attachment state. Rebuilding them for every local MSAA resolve
     * turns a cheap blit into gen/attach/validate/delete churn. Keep a small access-ordered
     * cache keyed by the actual texture wrapper objects and resolve aspect.
     */
    private final LinkedHashMap<ResolveFramebufferKey, Integer> resolveFramebuffers =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<ResolveFramebufferKey, Integer> eldest) {
                    if (size() <= MAX_RESOLVE_FRAMEBUFFERS) return false;
                    deleteResolveFramebuffer(eldest.getValue());
                    return true;
                }
            };


    private static int maxSamplesFor(GpuFormat format) {
        int maxSamples = GL11.glGetInteger(GL32.GL_MAX_SAMPLES);
        int formatLimit = isDepthFormat(format)
                ? GL11.glGetInteger(GL_MAX_DEPTH_TEXTURE_SAMPLES)
                : GL11.glGetInteger(GL_MAX_COLOR_TEXTURE_SAMPLES);
        if (formatLimit > 0 && maxSamples > 0) {
            return Math.min(maxSamples, formatLimit);
        }
        return maxSamples > 0 ? maxSamples : formatLimit;
    }

    private static boolean isDepthFormat(GpuFormat format) {
        return format != null && format.toString().contains("DEPTH");
    }

    private static int textureId(GpuTexture texture) {
        return texture instanceof IMsaaTexture glTexture ? glTexture.combatant$getGlId() : 0;
    }

    @Override
    public boolean supported() {
        return GlBackendAccess.current() != null;
    }

    @Override
    public GpuTexture createTexture(String label,
                                    int usage,
                                    GpuFormat format,
                                    int width,
                                    int height,
                                    int samples) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid MSAA texture size: " + width + "x" + height);
        }
        if (samples < 2) samples = 2;
        int maxSamples = maxSamplesFor(format);
        if (maxSamples > 0 && samples > maxSamples) {
            throw new GpuOutOfMemoryException("MSAA " + samples + "x exceeds supported " + maxSamples
                    + "x for " + format + " (" + label + ")");
        }

        GlStateManager.clearGlErrors();
        int glId = GlStateManager._genTexture();
        if (label == null) label = String.valueOf(glId);

        GL32.glBindTexture(GL32.GL_TEXTURE_2D_MULTISAMPLE, glId);
        GL32.glTexImage2DMultisample(
                GL32.GL_TEXTURE_2D_MULTISAMPLE,
                samples,
                GlConst.toGlInternalId(format),
                width,
                height,
                true
        );

        int err = GlStateManager._getError();
        if (err == 1285) {
            throw new GpuOutOfMemoryException("Could not allocate MSAA texture of " + width + "x" + height + " for " + label);
        }
        if (err != 0) {
            throw new IllegalStateException("OpenGL error " + err);
        }

        int actualSamples = GL11.glGetTexLevelParameteri(GL32.GL_TEXTURE_2D_MULTISAMPLE, 0, GL32.GL_TEXTURE_SAMPLES);
        if (actualSamples < 2) {
            GlStateManager._deleteTexture(glId);
            throw new GpuOutOfMemoryException("Driver allocated non-MSAA texture for " + label
                    + " requested=" + samples + " actual=" + actualSamples);
        }

        IGlBackendInfo backend = GlBackendAccess.current();
        if (backend == null) {
            GlStateManager._deleteTexture(glId);
            throw new IllegalStateException("GL backend disappeared while creating MSAA texture: " + label);
        }

        var texture = GlTextureInvoker.combatant$create(usage, label, format, width, height, 1, 1, glId, backend.combatant$frameBufferCache());
        registerTexture(glId, actualSamples);
        if (texture instanceof IMsaaTexture msaa) {
            msaa.combatant$setSamples(actualSamples);
        }
        return texture;
    }

    @Override
    public void registerTexture(int nativeTextureId, int samples) {
        MsaaTextureRegistry.register(nativeTextureId, samples);
    }

    @Override
    public void unregisterTexture(int nativeTextureId) {
        MsaaTextureRegistry.unregister(nativeTextureId);
        if (nativeTextureId > 0 && !resolveFramebuffers.isEmpty()) {
            var it = resolveFramebuffers.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<ResolveFramebufferKey, Integer> entry = it.next();
                if (entry.getKey().referencesNativeTexture(nativeTextureId)) {
                    deleteResolveFramebuffer(entry.getValue());
                    it.remove();
                }
            }
        }
    }

    @Override
    public boolean isMsaaTexture(int nativeTextureId) {
        return MsaaTextureRegistry.isMsaa(nativeTextureId);
    }

    @Override
    public int samples(int nativeTextureId) {
        return MsaaTextureRegistry.getSamples(nativeTextureId);
    }

    @Override
    public int framebufferTextureTarget(int nativeTextureId) {
        return isMsaaTexture(nativeTextureId) ? GL32.GL_TEXTURE_2D_MULTISAMPLE : GL11.GL_TEXTURE_2D;
    }

    @Override
    public boolean setupFramebuffer(int framebuffer, int colorAttachment, int depthAttachment, int mipLevel, int bindTarget) {
        boolean hasColor = colorAttachment > 0;
        boolean hasDepth = depthAttachment > 0;
        boolean colorMsaa = hasColor && isMsaaTexture(colorAttachment);
        boolean depthMsaa = hasDepth && isMsaaTexture(depthAttachment);
        if (!colorMsaa && !depthMsaa) {
            return false;
        }

        int colorSamples = hasColor ? samples(colorAttachment) : 1;
        int depthSamples = hasDepth ? samples(depthAttachment) : colorSamples;
        if (hasColor && hasDepth && colorSamples != depthSamples) {
            DebugLog.warnOnChange("msaa.framebuffer.sample_mismatch", framebuffer + "|" + colorSamples + "|" + depthSamples,
                    "[MSAA/GL] refusing incomplete framebuffer setup. fbo=%d color=%d samples=%d depth=%d samples=%d",
                    framebuffer, colorAttachment, colorSamples, depthAttachment, depthSamples);
            return false;
        }

        int target = bindTarget == 0 ? GL30.GL_DRAW_FRAMEBUFFER : bindTarget;
        int prev = GlStateManager.getFrameBuffer(target);
        GlStateManager._glBindFramebuffer(target, framebuffer);

        int colorTarget = colorMsaa ? GL32.GL_TEXTURE_2D_MULTISAMPLE : GL11.GL_TEXTURE_2D;
        int depthTarget = depthMsaa ? GL32.GL_TEXTURE_2D_MULTISAMPLE : GL11.GL_TEXTURE_2D;

        GlStateManager._glFramebufferTexture2D(target, GL30.GL_COLOR_ATTACHMENT0, colorTarget, colorAttachment, colorMsaa ? 0 : mipLevel);
        GlStateManager._glFramebufferTexture2D(target, GL30.GL_DEPTH_ATTACHMENT, depthTarget, depthAttachment, depthMsaa ? 0 : mipLevel);

        int status = GL30.glCheckFramebufferStatus(target);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            DebugLog.warnOnChange("msaa.framebuffer.incomplete", framebuffer + "|" + status + "|" + colorSamples + "|" + depthSamples,
                    "[MSAA/GL] framebuffer incomplete after setup. fbo=%d status=0x%s color=%d depth=%d colorSamples=%d depthSamples=%d",
                    framebuffer, Integer.toHexString(status), colorAttachment, depthAttachment, colorSamples, depthSamples);
        }

        if (bindTarget == 0) {
            GlStateManager._glBindFramebuffer(target, prev);
        }
        return status == GL30.GL_FRAMEBUFFER_COMPLETE;
    }

    @Override
    public boolean resolve(RenderTarget src, RenderTarget dst, boolean color, boolean depth) {
        RenderSystem.assertOnRenderThread();
        if (src == null || dst == null || GlBackendAccess.current() == null) {
            return false;
        }

        // Keep color and depth resolves independent. A combined multisample blit is legal,
        // but several GL drivers complete its color resolve while leaving depth unchanged.
        if (color && depth) {
            boolean depthResolved = resolve(src, dst, false, true);
            boolean colorResolved = resolve(src, dst, true, false);
            return depthResolved && colorResolved;
        }

        if (color && (src.getColorTexture() == null || dst.getColorTexture() == null)) {
            return false;
        }
        if (depth && (src.getDepthTexture() == null || dst.getDepthTexture() == null)) {
            return false;
        }

        int prevRead = GlStateManager.getFrameBuffer(GL30.GL_READ_FRAMEBUFFER);
        int prevDraw = GlStateManager.getFrameBuffer(GL30.GL_DRAW_FRAMEBUFFER);

        int mask = (color ? GL30.GL_COLOR_BUFFER_BIT : 0) | (depth ? GL30.GL_DEPTH_BUFFER_BIT : 0);

        try {
            int srcFramebuffer = resolveFramebuffer(src, color, depth, "read");
            int dstFramebuffer = resolveFramebuffer(dst, color, depth, "draw");
            if (srcFramebuffer == 0 || dstFramebuffer == 0) {
                return false;
            }

            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, srcFramebuffer);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, dstFramebuffer);
            GL30.glBlitFramebuffer(
                    0, 0, src.width, src.height,
                    0, 0, dst.width, dst.height,
                    mask,
                    GL30.GL_NEAREST
            );
            if (GlValidation.errorsEnabled()) {
                int error = GlStateManager._getError();
                if (error != 0) {
                    DebugLog.warnOnChange("msaa.resolve.gl_error", error + "|" + color + "|" + depth,
                            "[MSAA/GL] resolve blit failed. error=0x%s color=%s depth=%s src=%dx%d dst=%dx%d",
                            Integer.toHexString(error), color, depth, src.width, src.height, dst.width, dst.height);
                    return false;
                }
            }
            return true;
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
        }
    }

    private int resolveFramebuffer(RenderTarget framebuffer, boolean color, boolean depth, String role) {
        GpuTexture colorTexture = color ? framebuffer.getColorTexture() : null;
        GpuTexture depthTexture = depth ? framebuffer.getDepthTexture() : null;
        ResolveFramebufferKey key = new ResolveFramebufferKey(colorTexture, depthTexture, color, depth);
        Integer cached = resolveFramebuffers.get(key);
        if (cached != null && cached != 0) {
            return cached;
        }

        int fbo = createResolveFramebuffer(framebuffer, color, depth, role);
        if (fbo != 0) {
            resolveFramebuffers.put(key, fbo);
        }
        return fbo;
    }

    private int createResolveFramebuffer(RenderTarget framebuffer, boolean color, boolean depth, String role) {
        int fbo = GL30.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);

        if (color) {
            int colorId = textureId(framebuffer.getColorTexture());
            if (colorId <= 0) {
                deleteResolveFramebuffer(fbo);
                return 0;
            }
            GlStateManager._glFramebufferTexture2D(
                    GL30.GL_FRAMEBUFFER,
                    GL30.GL_COLOR_ATTACHMENT0,
                    framebufferTextureTarget(colorId),
                    colorId,
                    0
            );
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        } else {
            GlStateManager._glFramebufferTexture2D(
                    GL30.GL_FRAMEBUFFER,
                    GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D,
                    0,
                    0
            );
            GL11.glReadBuffer(GL11.GL_NONE);
            GL11.glDrawBuffer(GL11.GL_NONE);
        }

        if (depth) {
            int depthId = textureId(framebuffer.getDepthTexture());
            if (depthId <= 0) {
                deleteResolveFramebuffer(fbo);
                return 0;
            }
            GlStateManager._glFramebufferTexture2D(
                    GL30.GL_FRAMEBUFFER,
                    GL30.GL_DEPTH_ATTACHMENT,
                    framebufferTextureTarget(depthId),
                    depthId,
                    0
            );
        }

        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            DebugLog.warnOnChange("msaa.resolve.incomplete", role + "|" + status + "|" + color + "|" + depth,
                    "[MSAA/GL] resolve framebuffer incomplete. role=%s status=0x%s color=%s depth=%s colorSamples=%d depthSamples=%d",
                    role,
                    Integer.toHexString(status),
                    color,
                    depth,
                    color ? samples(textureId(framebuffer.getColorTexture())) : 1,
                    depth ? samples(textureId(framebuffer.getDepthTexture())) : 1);
            deleteResolveFramebuffer(fbo);
            return 0;
        }

        return fbo;
    }

    private static void deleteResolveFramebuffer(Integer framebuffer) {
        if (framebuffer != null && framebuffer != 0) {
            GL30.glDeleteFramebuffers(framebuffer);
        }
    }

    private record ResolveFramebufferKey(GpuTexture colorTexture,
                                         GpuTexture depthTexture,
                                         boolean color,
                                         boolean depth) {
        boolean referencesNativeTexture(int nativeTextureId) {
            return (colorTexture != null && textureId(colorTexture) == nativeTextureId)
                    || (depthTexture != null && textureId(depthTexture) == nativeTextureId);
        }
    }

    @Override
    public void applyPipelineState(RenderPipeline pipeline) {
        if (pipeline != null && MsaaWorldTarget.isActive() && ENTITY_SHADER_ID.equals(pipeline.getFragmentShader())) {
            glEnable(GL_SAMPLE_ALPHA_TO_COVERAGE);
        } else {
            glDisable(GL_SAMPLE_ALPHA_TO_COVERAGE);
        }
    }

    @Override
    public void resetPipelineState() {
        glDisable(GL_SAMPLE_ALPHA_TO_COVERAGE);
    }

    public void close() {
        if (!RenderSystem.isOnRenderThread()) return;
        for (Integer framebuffer : resolveFramebuffers.values()) {
            deleteResolveFramebuffer(framebuffer);
        }
        resolveFramebuffers.clear();
    }
}
