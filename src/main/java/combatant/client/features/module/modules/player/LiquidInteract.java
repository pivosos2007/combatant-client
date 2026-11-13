/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.events.EventHandler;
import combatant.client.events.impl.CrosshairTargetUpdateEvent;
import combatant.client.events.impl.BlockOutlineShapeEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.render.helpers.TickDelta;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(
        id = "liquidinteract",
        displayName = "LiquidInteract",
        aliases = {"LiquidPlace", "WaterInteract"},
        category = ModuleCategory.PLAYER,
        subcategory = ModuleSubcategory.EXPLOIT,
        description = "module.liquidinteract.description")
public final class LiquidInteract extends Module {
    private final Minecraft mc = Minecraft.getInstance();

    @EventHandler
    private void onBlockOutlineShape(BlockOutlineShapeEvent event) {
        if (!isEnabled() || mc.player == null || !hasBlockInHand(mc.player)
                || !event.getShape().isEmpty()
                || event.getState().getFluidState().isEmpty()) return;
        event.setShape(event.getState().getFluidState().getShape(event.getWorld(), event.getPos()));
    }

    @EventHandler(priority = -1000)
    private void onCrosshairTargetUpdate(CrosshairTargetUpdateEvent event) {
        if (!isEnabled() || mc.level == null || mc.player == null
                || !hasBlockInHand(mc.player) || mc.options.keyAttack.isDown()) return;

        HitResult current = event.getHitResult();
        if (current instanceof EntityHitResult || event.getTargetedEntity() != null) return;

        LocalPlayer player = mc.player;
        Vec3 eyes = player.getEyePosition(event.getTickDelta());
        double reach = player.blockInteractionRange();
        Vec3 end = eyes.add(player.getViewVector(event.getTickDelta()).scale(reach));
        BlockHitResult fluidHit = mc.level.clip(new ClipContext(
                eyes, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));

        if (fluidHit.getType() != HitResult.Type.BLOCK
                || mc.level.getFluidState(fluidHit.getBlockPos()).isEmpty()) return;

        if (current != null && current.getType() == HitResult.Type.BLOCK
                && current.getLocation().distanceToSqr(eyes)
                <= fluidHit.getLocation().distanceToSqr(eyes) + 1.0e-7) return;

        event.setHitResult(fluidHit);
        event.setTargetedEntity(null);
    }

    /**
     * An attack can be processed before the next crosshair update (e.g. immediately after
     * switching items or pressing attack). Do not let a placement-only fluid hit become a
     * START_DIGGING packet for water or lava. Preserve real block targets, including kelp.
     */
    public void restoreVanillaAttackTarget() {
        if (!isEnabled() || mc.level == null || mc.player == null
                || !(mc.hitResult instanceof BlockHitResult target)
                || target.getType() != HitResult.Type.BLOCK) return;

        BlockState state = mc.level.getBlockState(target.getBlockPos());
        if (state.getFluidState().isEmpty()
                || !state.getShape(mc.level, target.getBlockPos()).isEmpty()) return;

        mc.hitResult = mc.player.pick(mc.player.blockInteractionRange(), TickDelta.tickProgress(false), false);
        mc.crosshairPickEntity = null;
    }

    private static boolean hasBlockInHand(LocalPlayer player) {
        return isBlockItem(player.getMainHandItem()) || isBlockItem(player.getOffhandItem());
    }

    private static boolean isBlockItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem;
    }
}
