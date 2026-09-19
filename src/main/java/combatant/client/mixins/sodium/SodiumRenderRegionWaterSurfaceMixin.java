/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.sodium;

import combatant.client.render.sodium.fluid.WaterSurfaceExtractor;
import net.caffeinemc.mods.sodium.client.render.chunk.UniformBufferManager;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.BuilderTaskOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

/** Publishes staged patch geometry only after Sodium has installed the matching build results. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager", remap = false)
public abstract class SodiumRenderRegionWaterSurfaceMixin {
    @Inject(
            method = "uploadResults(Ljava/util/Collection;Lnet/caffeinemc/mods/sodium/client/render/chunk/UniformBufferManager;)V",
            at = @At("RETURN"),
            remap = false
    )
    private void combatant$publishUploadedSurfaces(Collection<BuilderTaskOutput> outputs,
                                                     UniformBufferManager uniformBuffers,
                                                     CallbackInfo ci) {
        WaterSurfaceExtractor.publishUploadedBuildResults(outputs);
    }
}
