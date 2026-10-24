/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.CrosshairTargetUpdateEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Lets use-clicks reach the container behind a sign, banner or item frame that hangs on it. The crosshair is moved to
 * the block behind the obstruction; the redirect is not applied while the attack key is held, so breaking the sign
 * or the frame itself still works.
 */
@ModuleInfo(
        id = "clickthrough",
        displayName = "ClickThrough",
        aliases = {"ContainerThrough", "SignThrough"},
        category = ModuleCategory.PLAYER, subcategory = ModuleSubcategory.AUTOMATION,
        description = "module.clickthrough.description")
public final class ClickThrough extends Module {

    /** A sign hung on a banner on a sign... never needs more than a few layers. */
    private static final int MAX_LAYERS = 4;
    private static final double STEP_PAST = 0.01;

    private final Minecraft mc = Minecraft.getInstance();

    private final BooleanValue signs = bool("clickThroughSigns", "signs", true);
    private final BooleanValue banners = bool("clickThroughBanners", "banners", true);
    private final BooleanValue itemFrames = bool("clickThroughItemFrames", "item_frames", true);
    private final BooleanValue onlyContainers = bool("clickThroughOnlyContainers", "only_containers", true);

    @EventHandler
    private void onCrosshairTarget(CrosshairTargetUpdateEvent event) {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.options.keyAttack.isDown()) return;

        HitResult current = event.getHitResult();
        if (current instanceof EntityHitResult entityHit) {
            if (!itemFrames.get() || !(entityHit.getEntity() instanceof ItemFrame)) return;
            HitResult behind = player.pick(player.blockInteractionRange(), event.getTickDelta(), false);
            if (behind instanceof BlockHitResult block && accepts(level, block)) redirect(event, block);
        } else if (current instanceof BlockHitResult blockHit
                && blockHit.getType() == HitResult.Type.BLOCK
                && isObstruction(level.getBlockState(blockHit.getBlockPos()))) {
            BlockHitResult behind = traceBehind(player, level, blockHit, event.getTickDelta());
            if (behind != null) redirect(event, behind);
        }
    }

    private static void redirect(CrosshairTargetUpdateEvent event, BlockHitResult target) {
        event.setHitResult(target);
        event.setTargetedEntity((Entity) null);
    }

    private boolean isObstruction(BlockState state) {
        return (signs.get() && state.is(BlockTags.ALL_SIGNS)) || (banners.get() && state.is(BlockTags.BANNERS));
    }

    private boolean accepts(ClientLevel level, BlockHitResult hit) {
        if (hit.getType() != HitResult.Type.BLOCK) return false;
        return !onlyContainers.get() || level.getBlockState(hit.getBlockPos()).getMenuProvider(level, hit.getBlockPos()) != null;
    }

    /** Continues the view ray past every sign/banner it meets; the first other block is the candidate. */
    private BlockHitResult traceBehind(LocalPlayer player, ClientLevel level, BlockHitResult first, float tickDelta) {
        // Signs and banners have no collision shape, so a collider clip from the eyes passes through them.
        Vec3 eyes = player.getEyePosition(tickDelta);
        Vec3 end = eyes.add(player.getViewVector(tickDelta).scale(player.blockInteractionRange()));
        HitResult next = level.clip(new ClipContext(
                eyes, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (!(next instanceof BlockHitResult block) || block.getType() != HitResult.Type.BLOCK) return null;
        if (block.getBlockPos().equals(first.getBlockPos())) return null;
        return accepts(level, block) ? block : null;
    }
}
