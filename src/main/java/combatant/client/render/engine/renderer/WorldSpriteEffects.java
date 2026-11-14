/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import combatant.client.render.engine.pipeline.WorldRenderPipelines;
import combatant.client.render.engine.uniform.MeshBuilder;

public final class WorldSpriteEffects {
    private WorldSpriteEffects() {
    }

    public enum Mode {
        CLASSIC(0),
        FRESNEL(1),
        ENERGY_SWEEP(2),
        CHROMATIC(3);

        private final int shaderId;

        Mode(int shaderId) {
            this.shaderId = shaderId;
        }

        public boolean usesShader() {
            return this != CLASSIC;
        }
    }

    public static RenderPipeline pipeline(Mode mode, RenderPipeline classic, boolean depthTest) {
        if (!mode.usesShader()) return classic;
        return depthTest ? WorldRenderPipelines.WORLD_SPRITE_EFFECT_DEPTH
                : WorldRenderPipelines.WORLD_SPRITE_EFFECT;
    }

    /**
     * Encodes the shader mode in the sign of the variant: negative values denote camera-facing geometry.
     */
    public static final class Parameters {
        private float x;
        private float y;
        private float intensity;
        private float variant;

        public Parameters configure(Mode mode, float time, float phase, float strength, boolean cameraFacing) {
            x = 0.0f;
            y = 0.0f;
            intensity = Math.max(0.0f, Math.min(2.0f, strength));
            variant = cameraFacing ? -mode.shaderId : mode.shaderId;
            switch (mode) {
                case ENERGY_SWEEP -> {
                    float angle = time * 3.25f - phase;
                    x = (float) Math.cos(angle);
                    y = (float) Math.sin(angle);
                }
                case CHROMATIC -> x = 0.68f + 0.32f * (float) Math.sin(time * 3.1f + phase);
                default -> {
                }
            }
            return this;
        }
    }

    public static int vertex(MeshBuilder mesh, double x, double y, double z,
                             float u, float v, int color, Parameters parameters) {
        mesh.vec3(x, y, z).vec2(u, v).colorArgb(color);
        if (parameters.variant != 0.0f) {
            mesh.vec4(parameters.x, parameters.y, parameters.intensity, parameters.variant);
        }
        return mesh.next();
    }
}
