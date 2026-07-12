/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.accessors;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {

    @Invoker("bobHurt")
    void invokeTiltViewWhenHurt(CameraRenderState cameraRenderState, PoseStack matrices);

    @Invoker("bobView")
    void invokeBobView(CameraRenderState cameraRenderState, PoseStack matrices);

    @Invoker("renderItemInHand")
    void invokeRenderHand(CameraRenderState cameraRenderState, float tickDelta, Matrix4fc positionMatrix);

    @Accessor("gameRenderState")
    GameRenderState combatant$getGameRenderState();

    @Accessor("handAndScreenSubmitNodeStorage")
    SubmitNodeStorage combatant$getHandAndScreenSubmitNodeStorage();

    @Accessor("mainRenderTarget")
    RenderTarget combatant$getMainRenderTargetRaw();

    @Accessor("guiRenderer")
    GuiRenderer combatant$getGuiRenderer();

    @Accessor("fogRenderer")
    FogRenderer combatant$getFogRenderer();

    @Accessor("spinningEffectTime")
    float combatant$getNauseaEffectTime();

    @Accessor("spinningEffectSpeed")
    float combatant$getNauseaEffectSpeed();
}
