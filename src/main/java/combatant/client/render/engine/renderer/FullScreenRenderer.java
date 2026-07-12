/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on, adapted from, or implemented
 * with reference to Meteor Client
 * (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 *
 * Licensed under the GNU General Public License v3.0.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.render.engine.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.guard.LegacyRenderPath;
import combatant.client.render.engine.guard.RenderArchitectureGuard;
import combatant.client.render.engine.rhi.FullscreenDrawCommand;
import combatant.client.render.engine.rhi.GpuMeshHandle;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.engine.vertex.CombatantVertexFormats;

/**
 * Compatibility facade for fullscreen rendering.
 * <p>
 * Production fullscreen passes must submit through the RHI persistent fullscreen backend. The legacy CPU
 * MeshBuilder is retained only so old call sites can be detected/migrated, not as the intended draw path.
 */
public enum FullScreenRenderer {
    ;
    public static GpuBuffer vbo;
    public static GpuBuffer ibo;

    /**
     * Legacy CPU mesh kept only for detection of old code paths. Production code must use FullScreenRenderer.begin()
     * or CombatantRHI.fullscreen() directly.
     */
    @Deprecated(forRemoval = false)
    public static MeshBuilder mesh;

    private static boolean initialized;

    public static void init() {
        if (initialized) return;
        GpuMeshHandle quad = CombatantRenderSystem.rhi().fullscreen().quad();
        vbo = quad.vertexBuffer();
        ibo = quad.indexBuffer();

        // Compatibility marker mesh. MeshRenderer detects this object and routes it through the RHI
        // fullscreen backend, so it does not trigger a CPU quad upload anymore.
        mesh = new MeshBuilder(CombatantVertexFormats.POS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES, 4, 6);
        mesh.begin();
        mesh.quad(
                mesh.vec2(-1, -1).next(),
                mesh.vec2(-1, 1).next(),
                mesh.vec2(1, 1).next(),
                mesh.vec2(1, -1).next()
        );
        mesh.end();
        initialized = true;
    }

    public static void ensureInit() {
        if (!initialized) init();
    }

    public static boolean isLegacyFullscreenMesh(MeshBuilder candidate) {
        ensureInit();
        boolean legacy = candidate != null && candidate == mesh;
        if (legacy) {
            RenderArchitectureGuard.requireAllowed(LegacyRenderPath.LEGACY_FULLSCREEN_MESH, "FullScreenRenderer.mesh");
        }
        return legacy;
    }

    public static Draw begin(String label) {
        ensureInit();
        return new Draw(label);
    }

    public static Draw draw(GpuTextureView dst, RenderPipeline pipeline) {
        return begin("Combatant Fullscreen Pass").attachment(dst).pipeline(pipeline);
    }

    public static Draw draw(RenderTarget dst, RenderPipeline pipeline) {
        return begin("Combatant Fullscreen Pass").attachment(dst).pipeline(pipeline);
    }

    public static final class Draw {
        private final FullscreenDrawCommand.Builder builder;

        private Draw(String label) {
            this.builder = FullscreenDrawCommand.builder(label);
        }

        public Draw attachment(GpuTextureView view) {
            builder.colorAttachment(view);
            return this;
        }

        public Draw attachment(RenderTarget framebuffer) {
            if (framebuffer != null) builder.colorAttachment(framebuffer.getColorTextureView());
            return this;
        }

        public Draw pipeline(RenderPipeline pipeline) {
            builder.pipeline(pipeline);
            return this;
        }

        public Draw clearColor(Integer argb) {
            builder.clearColor(argb);
            return this;
        }

        public Draw uniform(String name, GpuBufferSlice slice) {
            builder.uniform(name, slice);
            return this;
        }

        public Draw sampler(String name, GpuTextureView view, GpuSampler sampler) {
            builder.sampler(name, view, sampler);
            return this;
        }

        public void end() {
            CombatantRenderSystem.rhi().drawFullscreen(builder.build());
        }
    }
}
