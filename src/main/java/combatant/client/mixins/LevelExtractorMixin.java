/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.Freecam;
import combatant.client.mixins.accessors.LevelExtractorAccessor;

@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {

    @Inject(method = "extractVisibleEntities", at = @At("TAIL"))
    private void freecam$addLocalPlayer(
            Camera camera,
            Frustum frustum,
            DeltaTracker tickCounter,
            LevelRenderState renderStates,
            CallbackInfo ci
    ) {
        Freecam fc = Modules.get(Freecam.class);
        Minecraft mc = Minecraft.getInstance();

        if (fc == null || !fc.isEnabled()) return;
        if (mc.player == null) return;

        for (EntityRenderState state : renderStates.entityRenderStates) {
            if (state instanceof AvatarRenderState prs && prs.id == mc.player.getId()) {
                return;
            }
        }

        EntityRenderState state = ((LevelExtractorAccessor) this).combatant$invokeExtractEntity(
                mc.player,
                tickCounter.getGameTimeDeltaPartialTick(false)
        );

        if (state instanceof AvatarRenderState prs) {
            renderStates.entityRenderStates.add(prs);
        }
    }
}
