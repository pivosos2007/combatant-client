/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.iris;

import net.irisshaders.iris.pathways.HandRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(value = HandRenderer.class, remap = false)
public interface IrisHandRendererAccessor {
    @Accessor(value = "renderingSolid", remap = false)
    void combatant$setRenderingSolid(boolean renderingSolid);

    @Invoker(value = "setupGlState", remap = false)
    PoseStack combatant$invokeSetupGlState(GameRenderer renderer,
                                           CameraRenderState cameraRenderState,
                                           Matrix4fc positionMatrix,
                                           float tickDelta);
}
