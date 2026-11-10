/* Copyright (c) 2026 pivosos2007. GPL-3.0-only. */
package combatant.client.mixins.sodium;

import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.textures.GpuSampler;
import combatant.client.render.engine.deferred.DevDeferredRuntime;
import combatant.client.render.engine.material.MaterialAtlasManager;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.minecraft.client.renderer.oit.OitStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Binds companion PBR atlases only to active Sodium opaque/cutout draws. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer", remap = false)
public abstract class DevDeferredSodiumGeometryBindingMixin {
    @Inject(method = "render", at = @At(value = "INVOKE", shift = At.Shift.AFTER, target =
            "Lnet/caffeinemc/mods/sodium/client/render/chunk/ShaderChunkRenderer;begin(Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;Lnet/caffeinemc/mods/sodium/client/util/FogParameters;Lcom/mojang/renderpearl/api/textures/GpuSampler;Lnet/minecraft/client/renderer/oit/OitStage;)V"),
            remap = false)
    private void combatant$bindGbufferMaterials(ChunkRenderMatrices matrices,
            ChunkRenderListIterable lists, TerrainRenderPass pass, CameraTransform camera,
            FogParameters fog, boolean reverseOrder, RenderPass renderPass, GpuSampler sampler,
            GpuBufferSlice globals, GpuBuffer sectionInfo, OitStage oitStage, CallbackInfo ci) {
        if (pass.isTranslucent() || !DevDeferredRuntime.world().geometryCaptureEnabled()) return;
        DevDeferredRuntime.world().captureLightingState(
                new combatant.client.render.engine.deferred.DeferredWorldPipeline.LightingState(
                        fog.red(), fog.green(), fog.blue(), fog.alpha(),
                        fog.environmentalStart(), fog.environmentalEnd(), fog.renderStart(), fog.renderEnd()));
        MaterialAtlasManager.global().bind(renderPass, sampler, pass.getAtlas());
    }
}
