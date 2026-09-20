/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.sodium;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.GpuSampler;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.deferred.DevDeferredRuntime;
import combatant.client.render.engine.material.MaterialAtlasManager;
import combatant.client.render.sodium.SodiumSecondaryTerrainContext;
import combatant.client.util.logging.DebugLog;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.minecraft.client.Minecraft;
import org.joml.Vector4fc;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.system.MemoryStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;
import java.util.OptionalDouble;
import java.nio.IntBuffer;
import java.util.function.Supplier;

/** Connects Sodium's already-batched terrain phase to Combatant's geometry attachments. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer", remap = false)
public abstract class SodiumDefaultChunkRendererMixin {
    @WrapOperation(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/CommandEncoder;createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/Optional;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/blaze3d/systems/RenderPass;"
            )
    )
    private RenderPass combatant$openDeferredTerrainPass(
            CommandEncoder encoder,
            Supplier<String> label,
            GpuTextureView color,
            Optional<Vector4fc> clearColor,
            GpuTextureView depth,
            OptionalDouble clearDepth,
            Operation<RenderPass> original,
            @Local(argsOnly = true) ChunkRenderMatrices matrices,
            @Local(argsOnly = true) TerrainRenderPass terrainPass
    ) {
        SodiumSecondaryTerrainContext.State secondary = SodiumSecondaryTerrainContext.current();
        if (secondary != null) {
            return secondary.openPass(encoder, label);
        }
        if (terrainPass.isTranslucent() || !DevDeferredRuntime.world().enabled()) {
            return original.call(encoder, label, color, clearColor, depth, clearDepth);
        }
        RenderPass pass = DevDeferredRuntime.world().openGeometryPass(
                encoder, label, color, clearColor, depth, clearDepth
        );
        combatant$logPrimaryCaptureContract(matrices, color, depth);
        return pass;
    }

    /**
     * One-shot producer-side contract dump. This runs after Blaze3D opened the actual MRT pass, so
     * the queried OpenGL viewport is the viewport used by the following Sodium terrain draws, not
     * stale state left by a secondary atlas pass.
     */
    private static void combatant$logPrimaryCaptureContract(ChunkRenderMatrices matrices,
                                                            GpuTextureView color,
                                                            GpuTextureView depth) {
        if (matrices == null || color == null) return;
        int viewportX = -1;
        int viewportY = -1;
        int viewportWidth = -1;
        int viewportHeight = -1;
        String backend = RenderSystem.getDevice().getDeviceInfo().backendName();
        if (backend != null && backend.toLowerCase(java.util.Locale.ROOT).contains("opengl")) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer viewport = stack.mallocInt(4);
                GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, viewport);
                viewportX = viewport.get(0);
                viewportY = viewport.get(1);
                viewportWidth = viewport.get(2);
                viewportHeight = viewport.get(3);
            }
        }

        Minecraft minecraft = Minecraft.getInstance();
        int windowWidth = minecraft != null && minecraft.getWindow() != null
                ? minecraft.getWindow().getWidth() : -1;
        int windowHeight = minecraft != null && minecraft.getWindow() != null
                ? minecraft.getWindow().getHeight() : -1;
        int depthWidth = depth != null ? depth.getWidth(0) : -1;
        int depthHeight = depth != null ? depth.getHeight(0) : -1;
        org.joml.Matrix4fc p = matrices.projection();
        org.joml.Matrix4fc v = matrices.modelView();
        DebugLog.infoOnce(
                "combatant.deferred.primary-capture-contract",
                "[Deferred][PrimaryCapture] target=%dx%d depth=%dx%d viewport=%d,%d %dx%d window=%dx%d "
                        + "projection=[%f,%f,%f,%f;%f,%f,%f,%f;%f,%f,%f,%f;%f,%f,%f,%f] "
                        + "modelView=[%f,%f,%f,%f;%f,%f,%f,%f;%f,%f,%f,%f;%f,%f,%f,%f]",
                color.getWidth(0), color.getHeight(0), depthWidth, depthHeight,
                viewportX, viewportY, viewportWidth, viewportHeight, windowWidth, windowHeight,
                p.m00(), p.m01(), p.m02(), p.m03(),
                p.m10(), p.m11(), p.m12(), p.m13(),
                p.m20(), p.m21(), p.m22(), p.m23(),
                p.m30(), p.m31(), p.m32(), p.m33(),
                v.m00(), v.m01(), v.m02(), v.m03(),
                v.m10(), v.m11(), v.m12(), v.m13(),
                v.m20(), v.m21(), v.m22(), v.m23(),
                v.m30(), v.m31(), v.m32(), v.m33()
        );
    }
    @WrapOperation(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderPass;bindTexture(Ljava/lang/String;Lcom/mojang/blaze3d/textures/GpuTextureView;Lcom/mojang/blaze3d/textures/GpuSampler;)V"
            )
    )
    private void combatant$bindMaterialAtlases(
            RenderPass pass,
            String name,
            GpuTextureView view,
            GpuSampler sampler,
            Operation<Void> original,
            @Local(argsOnly = true) TerrainRenderPass terrainPass
    ) {
        original.call(pass, name, view, sampler);
        if (!"u_BlockTex".equals(name)) return;
        if (terrainPass.isTranslucent() || SodiumSecondaryTerrainContext.active()) return;
        if (!DevDeferredRuntime.world().enabled()) return;
        MaterialAtlasManager.global().bind(pass, sampler, view);
    }

    @ModifyExpressionValue(
            method = "render",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/caffeinemc/mods/sodium/client/gui/SodiumOptions$PerformanceSettings;useBlockFaceCulling:Z",
                    opcode = Opcodes.GETFIELD)
    )
    private boolean combatant$disablePrimaryCameraFaceCullingForSecondaryView(boolean original) {
        return SodiumSecondaryTerrainContext.active() ? false : original;
    }

}
