/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

/* HoldMyItems Recode; original project by sapling, CC0-1.0. */
package combatant.client.mixins.hmi_recode;

import combatant.client.features.hmi_recode.HoldMyItems;
import combatant.client.features.hmi_recode.render.HmiModelCommand;
import combatant.client.features.hmi_recode.render.HmiModelQuadList;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;

@Mixin(targets = "net.minecraft.client.renderer.item.ItemStackRenderState$LayerRenderState")
public abstract class HmiItemLayerRenderStateMixin {
    /**
     * Do not redirect SubmitNodeCollector.submitItem here. Fabric Renderer API redirects the exact
     * same invocation to add its extended mesh submit path. Replacing that INVOKE makes whichever
     * redirect runs second fail its injection check.
     *
     * <p>Instead, carry an immutable HMI command snapshot inside the vanilla quad-list argument.
     * The invocation itself remains untouched for Fabric Renderer API and other renderer mods.</p>
     */
    @ModifyArg(
            method = "submit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitItem(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;III[ILjava/util/List;Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;)V"
            ),
            index = 6
    )
    private List<BakedQuad> combatant$hmi$attachModelPose(List<BakedQuad> quads) {
        if (quads.isEmpty()) return quads;

        List<HmiModelCommand> commands = HoldMyItems.snapshotModelCommands();
        if (commands.isEmpty()) return quads;

        return new HmiModelQuadList(quads, commands);
    }
}
