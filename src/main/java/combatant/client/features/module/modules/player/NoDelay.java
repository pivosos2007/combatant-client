/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.mixins.accessors.LivingEntityAccessor;
import combatant.client.mixins.accessors.MinecraftAccessor;
import combatant.client.mixins.accessors.MultiPlayerGameModeAccessor;

import java.util.LinkedHashMap;
import java.util.Map;

@ModuleInfo(
        id = "nodelay",
        displayName = "NoDelay",
        aliases = {"FastUse", "FastBreak", "FastPlace"},
        category = ModuleCategory.PLAYER,
        description = "module.nodelay.description"
)
public final class NoDelay extends Module {

    private static final String SETTING_TYPES = "types";
    private static final String SETTING_USE_DELAY = "use_delay";
    private static final String SETTING_DELAY_MS = "delay_ms";
    private static final String SETTING_INTERVAL_MS = "interval_ms";
    private static final String SETTING_INTERACT_DELAY_MS = "interact_delay_ms";
    private static final String SETTING_INTERACT_INTERVAL_MS = "interact_interval_ms";
    private static final String SETTING_BLOCK_PLACE_COOLDOWN_TICKS = "block_place_cooldown_ticks";
    private static final String SETTING_FAST_BREAK_PROGRESS = "fast_break_progress";

    private static final String TYPE_JUMP = "Jump";
    private static final String TYPE_RIGHT_CLICK = "Right Click";
    private static final String TYPE_BLOCK_PLACE = "Block Place";
    private static final String TYPE_BREAK_COOLDOWN = "Break CoolDown";
    private static final String TYPE_FAST_BREAK = "Fast Break";
    private static final String TYPE_FAST_THROW = "Fast throw";
    private static final String TYPE_FAST_INTERACT = "Fast interact";
    private static final String TYPE_SHIELD_COOLDOWN = "Shield Cooldown";

    private final BooleanMapValue types = group("noDelayTypes", SETTING_TYPES, defaultTypes());
    private final BooleanValue useDelay =
            visibleWhen(bool("noDelayUseDelay", SETTING_USE_DELAY, true), () -> types.get(TYPE_FAST_THROW));
    private final NumberValue<Integer> delayMs =
            visibleWhen(num("noDelayDelayMs", SETTING_DELAY_MS, 250, 10, 250),
                    () -> types.get(TYPE_FAST_THROW) && useDelay.get());
    private final NumberValue<Integer> intervalMs =
            visibleWhen(num("noDelayIntervalMs", SETTING_INTERVAL_MS, 35, 10, 100),
                    () -> types.get(TYPE_FAST_THROW));
    private final NumberValue<Integer> interactDelayMs =
            visibleWhen(num("noDelayInteractDelayMs", SETTING_INTERACT_DELAY_MS, 150, 10, 250),
                    () -> types.get(TYPE_FAST_INTERACT));
    private final NumberValue<Integer> interactIntervalMs =
            visibleWhen(num("noDelayInteractIntervalMs", SETTING_INTERACT_INTERVAL_MS, 45, 10, 150),
                    () -> types.get(TYPE_FAST_INTERACT));
    private final NumberValue<Integer> blockPlaceCooldownTicks =
            visibleWhen(num("noDelayBlockPlaceCooldownTicks", SETTING_BLOCK_PLACE_COOLDOWN_TICKS, 0, 0, 6),
                    () -> types.get(TYPE_BLOCK_PLACE));
    private final NumberValue<Float> fastBreakProgress =
            visibleWhen(num("noDelayFastBreakProgress", SETTING_FAST_BREAK_PROGRESS, 0.5f, 0.1f, 1.0f),
                    () -> types.get(TYPE_FAST_BREAK));

    private final Minecraft mc = Minecraft.getInstance();

    private long pressStartThrow = 0L;
    private long lastThrow = 0L;
    private long pressStartInteract = 0L;
    private long lastInteract = 0L;
    private BlockPos lastBoostedBreakPos;

    private static Map<String, Boolean> defaultTypes() {
        Map<String, Boolean> defaults = new LinkedHashMap<>();
        defaults.put(TYPE_JUMP, true);
        defaults.put(TYPE_RIGHT_CLICK, true);
        defaults.put(TYPE_BLOCK_PLACE, false);
        defaults.put(TYPE_BREAK_COOLDOWN, true);
        defaults.put(TYPE_FAST_BREAK, true);
        defaults.put(TYPE_FAST_THROW, true);
        defaults.put(TYPE_FAST_INTERACT, true);
        defaults.put(TYPE_SHIELD_COOLDOWN, true);
        return defaults;
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc == null || mc.player == null) {
            resetBreakBoost();
            return;
        }

        if (types.get(TYPE_RIGHT_CLICK)) {
            ((MinecraftAccessor) mc).combatant$setItemUseCooldown(0);
        } else if (types.get(TYPE_BLOCK_PLACE)) {
            applyBlockPlaceCooldown();
        }

        if (types.get(TYPE_BREAK_COOLDOWN) && mc.gameMode instanceof MultiPlayerGameModeAccessor accessor) {
            accessor.combatant$setBlockBreakingCooldown(0);
        }

        if (types.get(TYPE_FAST_BREAK)) {
            applyFastBreakBoost();
        } else {
            resetBreakBoost();
        }

        if (types.get(TYPE_JUMP)) {
            ((LivingEntityAccessor) mc.player).combatant$setJumpingCooldown(0);
        }
    }


    public boolean shouldDisableShieldCooldown() {
        return isEnabled() && types.get(TYPE_SHIELD_COOLDOWN);
    }

    @Override
    public void onDisable() {
        pressStartThrow = 0L;
        lastThrow = 0L;
        pressStartInteract = 0L;
        lastInteract = 0L;
        resetBreakBoost();
    }

    public boolean handleFastUse() {
        if (!isEnabled()) return false;
        if (mc == null || mc.player == null || mc.gameMode == null) return false;
        if (!types.get(TYPE_FAST_THROW) && !types.get(TYPE_FAST_INTERACT)) return false;

        LocalPlayer player = mc.player;

        if (!mc.options.keyUse.isDown()) {
            pressStartThrow = 0L;
            pressStartInteract = 0L;
            return false;
        }

        Item main = player.getMainHandItem().getItem();
        Item off = player.getOffhandItem().getItem();
        HitResult hit = mc.hitResult;
        UseContext ctx = resolveFastUseContext(main, off, hit);
        if (ctx == null) return false;

        if (ctx.target == UseTarget.THROWABLE && !types.get(TYPE_FAST_THROW)) return false;
        if ((ctx.target == UseTarget.BONE_MEAL_BLOCK || ctx.target == UseTarget.ITEM_FRAME) && !types.get(TYPE_FAST_INTERACT)) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (ctx.target == UseTarget.THROWABLE) {
            if (pressStartThrow == 0L) {
                pressStartThrow = now;
                return false;
            }
            if (useDelay.get() && (now - pressStartThrow) < delayMs.get()) return true;
            if ((now - lastThrow) < intervalMs.get()) return true;
            lastThrow = now;

            InteractionResult result = performFastUse(player, ctx);
            if (result != null && result.consumesAction()) player.swing(ctx.hand);
            return true;
        }

        if (pressStartInteract == 0L) {
            pressStartInteract = now;
            return false;
        }
        if ((now - pressStartInteract) < interactDelayMs.get()) return true;
        if ((now - lastInteract) < interactIntervalMs.get()) return true;
        lastInteract = now;

        InteractionResult result = performFastUse(player, ctx);
        if (result != null && result.consumesAction()) player.swing(ctx.hand);
        return true;
    }

    private void applyBlockPlaceCooldown() {
        if (mc.player == null || !mc.options.keyUse.isDown()) return;
        if (!isBlockPlaceCandidate(mc.player, mc.hitResult)) return;

        MinecraftAccessor accessor = (MinecraftAccessor) mc;
        int current = accessor.combatant$getItemUseCooldown();
        int maxCooldown = blockPlaceCooldownTicks.get();
        if (current > maxCooldown) {
            accessor.combatant$setItemUseCooldown(maxCooldown);
        }
    }

    private boolean isBlockPlaceCandidate(LocalPlayer player, HitResult hit) {
        if (!(hit instanceof BlockHitResult)) return false;
        Item main = player.getMainHandItem().getItem();
        Item off = player.getOffhandItem().getItem();
        return main instanceof BlockItem || off instanceof BlockItem;
    }

    private void applyFastBreakBoost() {
        if (mc.gameMode == null || mc.getConnection() == null) {
            resetBreakBoost();
            return;
        }
        if (!mc.gameMode.isDestroying()) {
            resetBreakBoost();
            return;
        }
        if (!(mc.gameMode instanceof MultiPlayerGameModeAccessor accessor)) {
            return;
        }

        BlockPos pos = accessor.combatant$getCurrentBreakingPos();
        if (pos == null) return;

        float progress = accessor.combatant$getCurrentBreakingProgress();
        if (progress < fastBreakProgress.get()) return;
        if (pos.equals(lastBoostedBreakPos)) return;

        Direction direction = resolveBreakingDirection(pos);
        mc.getConnection().send(
                new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, direction)
        );
        mc.getConnection().send(
                new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, pos, direction)
        );

        lastBoostedBreakPos = pos.immutable();
    }

    private void resetBreakBoost() {
        lastBoostedBreakPos = null;
    }

    private Direction resolveBreakingDirection(BlockPos breakingPos) {
        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult blockHit && breakingPos.equals(blockHit.getBlockPos())) {
            return blockHit.getDirection();
        }
        return Direction.DOWN;
    }

    private boolean isThrowable(Item item) {
        return item == Items.EXPERIENCE_BOTTLE || item == Items.SNOWBALL || item == Items.EGG;
    }

    private boolean isBoneMeal(Item item) {
        return item == Items.BONE_MEAL;
    }

    private boolean isItemFrameTargeted(HitResult hit) {
        return hit instanceof EntityHitResult ehr && ehr.getEntity() instanceof ItemFrame;
    }

    private UseContext resolveFastUseContext(Item main, Item off, HitResult hit) {
        if (isThrowable(main)) return new UseContext(InteractionHand.MAIN_HAND, UseTarget.THROWABLE, null, null);
        if (isThrowable(off)) return new UseContext(InteractionHand.OFF_HAND, UseTarget.THROWABLE, null, null);

        if (isBoneMeal(main) && hit instanceof BlockHitResult bhr) {
            return new UseContext(InteractionHand.MAIN_HAND, UseTarget.BONE_MEAL_BLOCK, bhr, null);
        }
        if (isBoneMeal(off) && hit instanceof BlockHitResult bhr) {
            return new UseContext(InteractionHand.OFF_HAND, UseTarget.BONE_MEAL_BLOCK, bhr, null);
        }

        if (isItemFrameTargeted(hit)) {
            return new UseContext(InteractionHand.MAIN_HAND, UseTarget.ITEM_FRAME, null, (EntityHitResult) hit);
        }
        return null;
    }

    private InteractionResult performFastUse(LocalPlayer player, UseContext ctx) {
        if (ctx.target == UseTarget.THROWABLE) {
            return mc.gameMode.useItem(player, ctx.hand);
        }
        if (ctx.target == UseTarget.BONE_MEAL_BLOCK) {
            return mc.gameMode.useItemOn(player, ctx.hand, ctx.blockHit);
        }
        if (ctx.target == UseTarget.ITEM_FRAME) {
            return mc.gameMode.interact(player, ctx.entityHit.getEntity(), ctx.entityHit, ctx.hand);
        }
        return InteractionResult.PASS;
    }

    private enum UseTarget {
        THROWABLE,
        BONE_MEAL_BLOCK,
        ITEM_FRAME
    }

    private record UseContext(InteractionHand hand, UseTarget target, BlockHitResult blockHit,
                              EntityHitResult entityHit) {
    }
}
