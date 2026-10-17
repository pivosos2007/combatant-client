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

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.rhi.FullscreenDrawCommand;

/** Fullscreen rendering facade backed by the persistent RHI fullscreen path. */
public enum FullScreenRenderer {
    ;
    private static boolean initialized;

    public static void init() {
        if (initialized) return;
        // Materialize the persistent backend-owned fullscreen quad once.
        CombatantRenderSystem.rhi().fullscreen().quad();
        initialized = true;
    }

    public static void ensureInit() {
        if (!initialized) init();
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

        public Draw depthAttachment(GpuTextureView view) {
            builder.depthAttachment(view);
            return this;
        }

        public Draw depthAttachment(RenderTarget framebuffer) {
            if (framebuffer != null) builder.depthAttachment(framebuffer.getDepthTextureView());
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
