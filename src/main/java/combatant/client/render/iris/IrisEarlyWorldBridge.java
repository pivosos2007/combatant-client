/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.iris;

import com.google.common.collect.ImmutableSet;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.backend.opengl.GlTextureView;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.SeeInvisibles;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.world.EarlyShockwavePass;
import combatant.client.render.engine.world.VanillaBeforeTranslucentPass;
import combatant.client.util.logging.DebugLog;
import net.irisshaders.iris.gl.texture.InternalTextureFormat;
import net.irisshaders.iris.targets.RenderTargets;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryStack;
import java.nio.IntBuffer;

/**
 * Real pre-forward Iris color boundary. Nothing in here writes a player silhouette or changes
 * depth testing for the world. MAIN primitives are drawn before Iris's transparent entity pass;
 * PRE_DEPTH commands remain in Combatant's late renderer so water cannot cover their outlines.
 *
 * Iris's colortex0 is NOT Minecraft.mainRenderTarget while a shaderpack is active. We edit the
 * currently selected (flipped) shaderpack color and preserve its HDR format and alpha channel.
 */
public final class IrisEarlyWorldBridge {
    private static boolean rendering;
    private static TextureTarget sceneTarget;
    private static int program;
    private static int vao;

    private IrisEarlyWorldBridge() { }

    public static boolean isRendering() { return rendering; }
    public static TextureTarget sceneTarget() { return rendering ? sceneTarget : null; }

    public static void render(RenderTargets targets, ImmutableSet<Integer> flipped) {
        if (rendering || !IrisRuntime.isShaderpackRendererActive()
                || IrisRuntime.isRenderingShadowPass() || targets == null || flipped == null) return;
        SeeInvisibles setting = Modules.get(SeeInvisibles.class);
        if (setting == null || !setting.isEnabled()
                || setting.getInvisiblePlayerAlpha01() <= 0.0f
                || setting.getInvisiblePlayerAlpha01() >= 1.0f
                || !IrisSceneDepth.isValid() || IrisSceneDepth.preTranslucentDepthView() == null) return;

        net.irisshaders.iris.targets.RenderTarget irisColor = targets.getOrCreate(0);
        if (irisColor == null) return;
        InternalTextureFormat internal = irisColor.getInternalFormat();
        GpuFormat format;
        if (internal == InternalTextureFormat.RGBA16F) format = GpuFormat.RGBA16_FLOAT;
        else if (internal == InternalTextureFormat.RGBA8 || internal == InternalTextureFormat.RGBA)
            format = GpuFormat.RGBA8_UNORM;
        else {
            DebugLog.warnOnChange("iris.early.world.unsupported.color", String.valueOf(internal),
                    "[IrisCompat] pre-translucent world color format unsupported: %s", internal);
            return;
        }

        int w = targets.getCurrentWidth();
        int h = targets.getCurrentHeight();
        if (irisColor.getWidth() != w || irisColor.getHeight() != h) return;
        int texture = flipped.contains(0) ? irisColor.getAltTexture() : irisColor.getMainTexture();
        if (texture <= 0 || w < 1 || h < 1) return;
        TextureTarget scene = CombatantRenderSystem.resources().persistentFramebuffer(
                "iris-early-scene-color", w, h, false, format, "IrisEarlyWorldBridge");
        TextureTarget overlay = CombatantRenderSystem.resources().persistentFramebuffer(
                "iris-early-world-overlay", w, h, true, GpuFormat.RGBA8_UNORM, "IrisEarlyWorldBridge");
        if (scene == null || overlay == null || scene.getColorTextureView() == null
                || overlay.getColorTextureView() == null || overlay.getDepthTextureView() == null) return;
        GpuTextureView irisDepth = IrisSceneDepth.mainDepthView();
        if (irisDepth == null || irisDepth.texture() == null
                || irisDepth.texture().getFormat() != overlay.getDepthTexture().getFormat()
                || irisDepth.getWidth(0) != w || irisDepth.getHeight(0) != h) return;

        try {
            // The opaque depth is copied to a private attachment. World visuals MUST NOT write
            // to Iris's depthtex0, since Iris's subsequent forward pass owns that depth.
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                    irisDepth.texture(), overlay.getDepthTexture(), 0, 0, 0, 0, 0, w, h);
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(
                    overlay.getColorTexture(), new org.joml.Vector4f(0.0f));
            blit(texture, glId(scene.getColorTextureView()), w, h);

            rendering = true;
            sceneTarget = scene;
            try {
                // Shared depth-policy compiler: the same MAIN commands used in vanilla.
                // Shockwaves are applied to the real HDR scene at this stage, not post-finalization.
                VanillaBeforeTranslucentPass.render(overlay);
            } finally {
                rendering = false;
                sceneTarget = null;
            }

            compositeOverlay(scene.getColorTextureView(), overlay.getColorTextureView(), w, h);
            blit(glId(scene.getColorTextureView()), texture, w, h);
        } catch (RuntimeException failure) {
            DebugLog.warnOnChange("iris.early.world.failed", failure.getClass().getName() + failure.getMessage(),
                    "[IrisCompat] early world composition failed: %s: %s",
                    failure.getClass().getSimpleName(), failure.getMessage());
        }
    }

    private static int glId(GpuTextureView view) {
        if (view instanceof GlTextureView gl && !view.isClosed()) return gl.glId();
        throw new IllegalStateException("Iris pre-translucent integration requires OpenGL texture views");
    }

    private static void blit(int src, int dst, int w, int h) {
        if (src == dst) return;
        int readBinding = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        int drawBinding = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        int read = GL30C.glGenFramebuffers();
        int draw = GL30C.glGenFramebuffers();
        try {
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, read);
            GL30C.glFramebufferTexture2D(GL30C.GL_READ_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0,
                    GL11C.GL_TEXTURE_2D, src, 0);
            GL11C.glReadBuffer(GL30C.GL_COLOR_ATTACHMENT0);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, draw);
            GL30C.glFramebufferTexture2D(GL30C.GL_DRAW_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0,
                    GL11C.GL_TEXTURE_2D, dst, 0);
            GL11C.glDrawBuffer(GL30C.GL_COLOR_ATTACHMENT0);
            if (GL30C.glCheckFramebufferStatus(GL30C.GL_READ_FRAMEBUFFER) != GL30C.GL_FRAMEBUFFER_COMPLETE
                    || GL30C.glCheckFramebufferStatus(GL30C.GL_DRAW_FRAMEBUFFER) != GL30C.GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("incomplete Iris color copy framebuffer");
            GL30C.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11C.GL_COLOR_BUFFER_BIT, GL11C.GL_NEAREST);
        } finally {
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, readBinding);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, drawBinding);
            GL30C.glDeleteFramebuffers(read);
            GL30C.glDeleteFramebuffers(draw);
        }
    }

    /** Translucent 3D overlay is composed while Iris's forward entity pass has not yet begun. */
    private static void compositeOverlay(GpuTextureView destination, GpuTextureView overlay, int w, int h) {
        int oldDraw = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        int oldProgram = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        int oldVao = GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING);
        int oldActive = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0);
        int oldTexture = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        boolean oldBlend = GL11C.glIsEnabled(GL11C.GL_BLEND);
        boolean oldDepth = GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST);
        boolean oldScissor = GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST);
        boolean oldCull = GL11C.glIsEnabled(GL11C.GL_CULL_FACE);
        boolean oldStencil = GL11C.glIsEnabled(GL11C.GL_STENCIL_TEST);
        boolean oldDepthMask = GL11C.glGetBoolean(GL11C.GL_DEPTH_WRITEMASK);
        int oldSrcRGB = GL11C.glGetInteger(GL14C.GL_BLEND_SRC_RGB);
        int oldDstRGB = GL11C.glGetInteger(GL14C.GL_BLEND_DST_RGB);
        int oldSrcAlpha = GL11C.glGetInteger(GL14C.GL_BLEND_SRC_ALPHA);
        int oldDstAlpha = GL11C.glGetInteger(GL14C.GL_BLEND_DST_ALPHA);
        int oldRgbEquation = GL11C.glGetInteger(GL20C.GL_BLEND_EQUATION_RGB);
        int oldAlphaEquation = GL11C.glGetInteger(GL20C.GL_BLEND_EQUATION_ALPHA);
        int fbo = GL30C.glGenFramebuffers();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ensureProgram();
            IntBuffer viewport = stack.mallocInt(4);
            GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, viewport);
            java.nio.ByteBuffer colorMask = stack.malloc(4);
            GL11C.glGetBooleanv(GL11C.GL_COLOR_WRITEMASK, colorMask);
            try {
                GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, fbo);
                GL30C.glFramebufferTexture2D(GL30C.GL_DRAW_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0,
                        GL11C.GL_TEXTURE_2D, glId(destination), 0);
                GL11C.glDrawBuffer(GL30C.GL_COLOR_ATTACHMENT0);
                if (GL30C.glCheckFramebufferStatus(GL30C.GL_DRAW_FRAMEBUFFER) != GL30C.GL_FRAMEBUFFER_COMPLETE)
                    throw new IllegalStateException("incomplete Iris world overlay framebuffer");
                GL11C.glViewport(0, 0, w, h);
                GL11C.glColorMask(true, true, true, true);
                GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
                GL11C.glDisable(GL11C.GL_CULL_FACE);
                GL11C.glDisable(GL11C.GL_STENCIL_TEST);
                GL11C.glDisable(GL11C.GL_DEPTH_TEST);
                GL11C.glDepthMask(false);
                GL11C.glEnable(GL11C.GL_BLEND);
                // Preserve Iris's colortex alpha: packs may encode TAA metadata there.
                // The RGBA8 intermediate contains premultiplied RGB over a transparent clear.
                GL20C.glBlendEquationSeparate(GL14C.GL_FUNC_ADD, GL14C.GL_FUNC_ADD);
                GL14C.glBlendFuncSeparate(GL11C.GL_ONE, GL11C.GL_ONE_MINUS_SRC_ALPHA,
                        GL11C.GL_ZERO, GL11C.GL_ONE);
                GL20C.glUseProgram(program);
                GL30C.glBindVertexArray(vao);
                GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, glId(overlay));
                GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 3);
            } finally {
                GL11C.glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
                GL11C.glColorMask(colorMask.get(0) != 0, colorMask.get(1) != 0,
                        colorMask.get(2) != 0, colorMask.get(3) != 0);
            }
        } finally {
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, oldDraw);
            GL20C.glUseProgram(oldProgram);
            GL30C.glBindVertexArray(oldVao);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, oldTexture);
            GL13C.glActiveTexture(oldActive);
            GL14C.glBlendFuncSeparate(oldSrcRGB, oldDstRGB, oldSrcAlpha, oldDstAlpha);
            GL20C.glBlendEquationSeparate(oldRgbEquation, oldAlphaEquation);
            if (oldBlend) GL11C.glEnable(GL11C.GL_BLEND); else GL11C.glDisable(GL11C.GL_BLEND);
            if (oldDepth) GL11C.glEnable(GL11C.GL_DEPTH_TEST); else GL11C.glDisable(GL11C.GL_DEPTH_TEST);
            if (oldScissor) GL11C.glEnable(GL11C.GL_SCISSOR_TEST); else GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
            if (oldCull) GL11C.glEnable(GL11C.GL_CULL_FACE); else GL11C.glDisable(GL11C.GL_CULL_FACE);
            if (oldStencil) GL11C.glEnable(GL11C.GL_STENCIL_TEST); else GL11C.glDisable(GL11C.GL_STENCIL_TEST);
            GL11C.glDepthMask(oldDepthMask);
            GL30C.glDeleteFramebuffers(fbo);
        }
    }

    private static void ensureProgram() {
        if (program != 0 && GL20C.glIsProgram(program)) return;
        program = 0;
        vao = 0;
        int vertex = compile(GL20C.GL_VERTEX_SHADER, "#version 330 core\n"
                + "out vec2 uv;\nvoid main() { vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);"
                + " uv = p; gl_Position = vec4(p*2.0-1.0,0.0,1.0); }\n");
        int fragment = compile(GL20C.GL_FRAGMENT_SHADER, "#version 330 core\n"
                + "in vec2 uv; uniform sampler2D u_Overlay; out vec4 frag;\n"
                + "void main() { frag = texture(u_Overlay, uv); }\n");
        program = GL20C.glCreateProgram();
        GL20C.glAttachShader(program, vertex);
        GL20C.glAttachShader(program, fragment);
        GL20C.glLinkProgram(program);
        GL20C.glDeleteShader(vertex);
        GL20C.glDeleteShader(fragment);
        if (GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == 0)
            throw new IllegalStateException(GL20C.glGetProgramInfoLog(program));
        // Sampler uniforms default to texture unit 0; do not perturb the current GL program.
        vao = GL30C.glGenVertexArrays();
    }

    private static int compile(int kind, String shader) {
        int id = GL20C.glCreateShader(kind);
        GL20C.glShaderSource(id, shader);
        GL20C.glCompileShader(id);
        if (GL20C.glGetShaderi(id, GL20C.GL_COMPILE_STATUS) == 0)
            throw new IllegalStateException(GL20C.glGetShaderInfoLog(id));
        return id;
    }
}
