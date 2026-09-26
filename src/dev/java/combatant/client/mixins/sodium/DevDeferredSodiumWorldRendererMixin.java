/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.sodium;

import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.sodium.SodiumWorldVisibilityView;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Non-invasive Sodium visibility observer used by renderer services. */
@Mixin(value = SodiumWorldRenderer.class, remap = false)
public abstract class DevDeferredSodiumWorldRendererMixin implements SodiumWorldVisibilityView {
    @Shadow(remap = false)
    public abstract boolean isBoxVisible(double minX, double minY, double minZ,
                                         double maxX, double maxY, double maxZ);

    @Override
    public boolean combatant$isBoxVisible(AABB box) {
        return isBoxVisible(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    @Inject(method = "setupTerrain(Lnet/minecraft/client/Camera;Lnet/caffeinemc/mods/sodium/client/render/viewport/Viewport;Lnet/caffeinemc/mods/sodium/client/util/FogParameters;ZZLorg/joml/Matrix4f;)V", at = @At("HEAD"), remap = false)
    private void combatant$primaryVisibilityPending(Camera camera, Viewport viewport, FogParameters fog,
                                                    boolean useOcclusionCulling, boolean updateChunksImmediately,
                                                    org.joml.Matrix4f projection, CallbackInfo ci) {
        CombatantRenderSystem.sodium().markPrimaryVisibilityPending();
    }

    @Inject(method = "setupTerrain(Lnet/minecraft/client/Camera;Lnet/caffeinemc/mods/sodium/client/render/viewport/Viewport;Lnet/caffeinemc/mods/sodium/client/util/FogParameters;ZZLorg/joml/Matrix4f;)V", at = @At("RETURN"), remap = false)
    private void combatant$primaryVisibilityReady(Camera camera, Viewport viewport, FogParameters fog,
                                                  boolean useOcclusionCulling, boolean updateChunksImmediately,
                                                  org.joml.Matrix4f projection, CallbackInfo ci) {
        CombatantRenderSystem.sodium().markPrimaryVisibilityReady();
    }
}
