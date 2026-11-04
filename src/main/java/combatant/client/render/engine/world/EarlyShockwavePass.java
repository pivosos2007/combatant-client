/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world;

import com.mojang.blaze3d.pipeline.RenderTarget;
import combatant.client.features.module.ModuleManager;
import combatant.client.render.effects.shockwave.WorldBlastShockwaves;
import combatant.client.render.effects.shockwave.WorldJumpShockwaves;
import combatant.client.render.engine.postprocess.PostProcessManager;
import combatant.client.render.engine.postprocess.PostProcessPass;
import net.minecraft.client.Minecraft;

/** Runs the world-space refractive shockwaves BEFORE translucent entity color is composed. */
public final class EarlyShockwavePass {
    private static boolean rendered;

    private EarlyShockwavePass() { }

    public static void reset() { rendered = false; }
    public static boolean renderedThisFrame() { return rendered; }

    public static boolean isShockwave(PostProcessPass pass) {
        return pass == WorldJumpShockwaves.INSTANCE || pass == WorldBlastShockwaves.INSTANCE;
    }

    public static void render(RenderTarget target, float tickDelta) {
        if (rendered || target == null || target.getColorTextureView() == null) return;
        // The LevelRenderer frame graph can own a distinct main target. Use the
        // target supplied by the actual opaque-to-translucent boundary, not
        // GameRenderer.mainRenderTarget() which may not yet contain this frame.
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameRenderer == null) return;
        if (target.getColorTexture().getFormat() != com.mojang.renderpearl.api.GpuFormat.RGBA8_UNORM
                && target.getColorTexture().getFormat() != com.mojang.renderpearl.api.GpuFormat.RGBA16_FLOAT) return;

        ModuleManager.prepareWorldPostProcess(tickDelta);
        // The subsequent PRE_HAND graph must not reapply these two passes after
        // the invisible player's alpha has already been composed.
        rendered = PostProcessManager.renderSelectedOn(PostProcessPass.Phase.PRE_HAND, tickDelta,
                EarlyShockwavePass::isShockwave, target);
    }
}
