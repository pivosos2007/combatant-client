/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.accessors;

import com.mojang.blaze3d.textures.GpuSampler;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {

    @Accessor("levelRenderState")
    LevelRenderState combatant$getWorldRenderState();

    @Accessor("skyRenderer")
    SkyRenderer combatant$getSkyRendering();

    @Accessor("submitNodeStorage")
    SubmitNodeStorage combatant$getEntityRenderCommandQueue();

    @Accessor("featureRenderDispatcher")
    FeatureRenderDispatcher combatant$getEntityRenderDispatcher();

    @Accessor("renderBuffers")
    RenderBuffers combatant$getRenderBuffers();

    @Accessor("renderBuffers")
    void combatant$setRenderBuffers(RenderBuffers renderBuffers);

    @Accessor("chunkLayerSampler")
    GpuSampler combatant$getTerrainSampler();

    @Accessor("chunkLayerSampler")
    void combatant$setTerrainSampler(GpuSampler terrainSampler);

    @Invoker("submitEntities")
    void combatant$invokeSubmitEntities(com.mojang.blaze3d.vertex.PoseStack poseStack,
                                        LevelRenderState levelRenderState,
                                        SubmitNodeCollector collector);

}
