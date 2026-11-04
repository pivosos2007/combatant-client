/* Combatant Client, GPL-3.0. */
package combatant.client.render.engine.postprocess;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import combatant.client.render.engine.pipeline.PostProcessRenderPipelines;
import combatant.client.render.engine.renderer.FullScreenRenderer;
import combatant.client.render.engine.uniform.impl.PostProcessUniforms;

/**
 * Shared, single-pass fullscreen blur for the Schematics workspace and KillEffect.
 * Uses the regular postprocess graph's ping-pong targets, uniform allocator and sampler:
 * no UI blur captures, private framebuffers, extra GPU loops or duplicate pipelines.
 */
public final class FullScreenBlur {
    private FullScreenBlur() {}

    public static boolean render(String label, GpuTextureView source, GpuTextureView destination, float strength) {
        if (source == null || destination == null || source == destination || strength <= .001f) return false;
        PostProcessUniforms.update(Math.min(1f, Math.max(0f, strength)), 0f, 0f, 0f);
        FullScreenRenderer.begin(label)
                .attachment(destination)
                .pipeline(PostProcessRenderPipelines.KILL_BLUR)
                .uniform("PostProcess", PostProcessUniforms.get())
                .sampler("u_Texture", source, PostProcessManager.getSampler())
                .end();
        return true;
    }
}
