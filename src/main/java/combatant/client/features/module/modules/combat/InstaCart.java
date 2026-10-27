/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.values.BindMode;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.KeyBindValue;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.RotationUpdateEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Notifier;
import combatant.client.input.CombatantInput;
import combatant.client.input.InputScope;
import combatant.client.util.aiming.RestrictedSingleUseAction;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.player.inventory.InventoryActionKind;
import combatant.client.util.player.inventory.InventorySearchScope;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.inventory.InventorySwapRequest;
import combatant.client.util.player.inventory.InventorySwapVisibility;
import combatant.client.util.player.inventory.SlotResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Predicate;

/**
 * One-key TNT minecart trap: aim at the ground (or an existing rail) and press the trigger key.
 * The module places a rail, puts a TNT minecart on it with silent swaps, then shoots the cart
 * with a crossbow (instant) or bow so a Flame arrow lights it. Every action runs behind a
 * silent rotation in {@link RotationUpdateEvent.Type#PRE}.
 */
@ModuleInfo(
        id = "instacart",
        displayName = "InstaCart",
        aliases = {"CartBomb"},
        category = ModuleCategory.COMBAT,
        subcategory = ModuleSubcategory.ATTACK,
        description = "module.instacart.description")
public final class InstaCart extends Module {

    private static final int ROTATION_PRIORITY = 36;
    private static final int PHASE_TIMEOUT_TICKS = 30;
    private static final int COOLDOWN_TICKS = 10;

    private final Minecraft mc = Minecraft.getInstance();

    private final KeyBindValue triggerKey = bind("instaCartTriggerKey", "trigger_key", "NONE", BindMode.PRESS);
    private final EnumValue<Shooter> shooter = enumSetting("instaCartShooter", "shooter", Shooter.CROSSBOW, Shooter.values());
    private final NumberValue<Double> range = num("instaCartRange", "range", 4.5, 2.0, 6.0);
    private final NumberValue<Double> safeDistance = num("instaCartSafeDistance", "safe_distance", 3.0, 0.0, 6.0);
    private final NumberValue<Integer> bowChargeTicks = num("instaCartBowCharge", "bow_charge_ticks", 6, 3, 20);
    private final BooleanValue igniteFire = bool("instaCartIgnite", "ignite_fire", false);

    private Phase phase = Phase.IDLE;
    private BlockPos supportPos;
    private BlockPos firePos;
    private BlockPos railPos;
    private boolean acted;
    private int phaseTicks;
    private int cooldown;
    private int previousSlot = -1;
    private String lastCombo = "NONE";

    @Override
    public void onEnable() {
        reset(false);
        ensureBind();
    }

    @Override
    public void onDisable() {
        reset(true);
        CombatantInput.bindings().unbind(bindingName());
        lastCombo = "NONE";
    }

    @EventHandler(priority = 22)
    private void onRotationUpdate(RotationUpdateEvent event) {
        if (event.getType() != RotationUpdateEvent.Type.PRE || !isEnabled()) return;
        ensureBind();

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) {
            reset(true);
            return;
        }
        if (cooldown > 0) cooldown--;

        if (phase == Phase.IDLE) {
            if (CombatantInput.bindings().consumePress(bindingName(), InputScope.GAME) && cooldown == 0) begin(player);
            return;
        }

        if (++phaseTicks > PHASE_TIMEOUT_TICKS) {
            Notifier.warning(I18n.get("notification.instacart.timeout"));
            reset(true);
            return;
        }

        switch (phase) {
            case RAIL -> stepRail(player);
            case CART -> stepCart(player);
            case FIRE -> stepFire(player);
            case SHOOT -> stepShoot(player);
            default -> { }
        }
    }

    private void begin(LocalPlayer player) {
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            Notifier.warning(I18n.get("notification.instacart.no_target"));
            return;
        }
        BlockPos clicked = hit.getBlockPos();
        BlockState clickedState = mc.level.getBlockState(clicked);
        if (clickedState.getBlock() instanceof BaseRailBlock) {
            supportPos = null;
            railPos = clicked;
        } else if (hit.getDirection() == Direction.UP) {
            BlockPos above = clicked.above();
            if (!mc.level.getBlockState(above).canBeReplaced()
                    || !clickedState.isFaceSturdy(mc.level, clicked, Direction.UP)) {
                Notifier.warning(I18n.get("notification.instacart.bad_surface"));
                return;
            }
            supportPos = clicked;
            railPos = above;
        } else {
            Notifier.warning(I18n.get("notification.instacart.bad_surface"));
            return;
        }

        Vec3 center = Vec3.atCenterOf(railPos);
        double distance = player.getEyePosition().distanceTo(center);
        if (distance > range.get()) {
            Notifier.warning(I18n.get("notification.instacart.too_far"));
            return;
        }
        if (distance < safeDistance.get()) {
            Notifier.warning(I18n.get("notification.instacart.too_close"));
            return;
        }
        if (supportPos != null && !has(InventorySearchScope.FULL, InstaCart::isRail)) {
            Notifier.warning(I18n.get("notification.instacart.no_rail"));
            return;
        }
        if (!has(InventorySearchScope.FULL, stack -> stack.is(Items.TNT_MINECART))) {
            Notifier.warning(I18n.get("notification.instacart.no_cart"));
            return;
        }
        if (igniteFire.get() && !has(InventorySearchScope.FULL, InstaCart::isFireSource)) {
            Notifier.warning(I18n.get("notification.instacart.no_fire"));
            return;
        }
        if (!shooterAvailable()) {
            Notifier.warning(I18n.get(shooter.get() == Shooter.CROSSBOW
                    ? "notification.instacart.no_crossbow" : "notification.instacart.no_bow"));
            return;
        }

        advance(supportPos != null ? Phase.RAIL : Phase.CART);
    }

    private void stepRail(LocalPlayer player) {
        if (mc.level.getBlockState(railPos).getBlock() instanceof BaseRailBlock) {
            advance(Phase.CART);
            return;
        }
        if (acted) {
            // The place packet was sent but the rail has not shown up yet; allow one retry later.
            acted = false;
            return;
        }
        BlockHitResult hit = new BlockHitResult(
                new Vec3(supportPos.getX() + 0.5, supportPos.getY() + 1.0, supportPos.getZ() + 0.5),
                Direction.UP, supportPos, false);
        aim(player, Vec3.atCenterOf(railPos), () -> useOnBlock(InstaCart::isRail, hit));
    }

    private void stepCart(LocalPlayer player) {
        if (findCart() != null) {
            advance(igniteFire.get() ? Phase.FIRE : Phase.SHOOT);
            return;
        }
        if (acted) {
            acted = false;
            return;
        }
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(railPos), Direction.UP, railPos, false);
        aim(player, Vec3.atCenterOf(railPos), () -> useOnBlock(stack -> stack.is(Items.TNT_MINECART), hit));
    }

    /** Lights a fire on the player-facing side of the cart so the arrow flies through it and ignites. */
    private void stepFire(LocalPlayer player) {
        if (firePos == null) firePos = chooseFirePos(player);
        if (firePos == null || mc.level.getBlockState(firePos).is(BlockTags.FIRE)) {
            advance(Phase.SHOOT);
            return;
        }
        if (acted) {
            acted = false;
            return;
        }
        BlockHitResult hit = new BlockHitResult(
                new Vec3(firePos.getX() + 0.5, firePos.getY(), firePos.getZ() + 0.5),
                Direction.UP, firePos.below(), false);
        aim(player, hit.getLocation(), () -> useOnBlock(InstaCart::isFireSource, hit));
    }

    private BlockPos chooseFirePos(LocalPlayer player) {
        Vec3 toPlayer = player.position().subtract(Vec3.atCenterOf(railPos));
        BlockPos best = null;
        double bestDot = -Double.MAX_VALUE;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos candidate = railPos.relative(direction);
            if (!mc.level.getBlockState(candidate).canBeReplaced()
                    || !mc.level.getBlockState(candidate.below()).isFaceSturdy(mc.level, candidate.below(), Direction.UP)) continue;
            double dot = direction.getStepX() * toPlayer.x + direction.getStepZ() * toPlayer.z;
            if (dot > bestDot) {
                bestDot = dot;
                best = candidate;
            }
        }
        return best;
    }

    private static boolean isFireSource(ItemStack stack) {
        return stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE);
    }

    private void stepShoot(LocalPlayer player) {
        Entity cart = findCart();
        if (cart == null) {
            reset(true);
            return;
        }
        Vec3 point = cart.getBoundingBox().getCenter();
        if (shooter.get() == Shooter.CROSSBOW) {
            aim(player, point, () -> {
                fireCrossbow();
                finish();
            });
            return;
        }

        // Bow: hold the slot while drawing, release once charged, then hand the slot back.
        if (!acted) {
            aim(player, point, this::startBowDraw);
            return;
        }
        if (!player.isUsingItem()) {
            // Draw got interrupted before release; give up cleanly.
            finish();
            return;
        }
        if (player.getTicksUsingItem() >= bowChargeTicks.get()) {
            aim(player, point, () -> {
                mc.gameMode.releaseUsingItem(mc.player);
                finish();
            });
        } else {
            aim(player, point, () -> { });
        }
    }

    private void aim(LocalPlayer player, Vec3 point, Runnable action) {
        RotationTarget target = new RotationTarget(
                Rotation.lookingAt(point, player.getEyePosition()).normalize(),
                player,
                List.of(),
                1,
                4.0F,
                false,
                MovementCorrection.SILENT,
                new RestrictedSingleUseAction(action));
        RotationManager.INSTANCE.setRotationTarget(target, ROTATION_PRIORITY, this);
    }

    private void useOnBlock(Predicate<ItemStack> item, BlockHitResult hit) {
        InventorySwapRequest request = InventorySwapRequest.builder(item, () -> {
                    LocalPlayer player = mc.player;
                    if (player == null || mc.gameMode == null) return;
                    if (mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction()) {
                        player.swing(InteractionHand.MAIN_HAND, player.getItemInHand(InteractionHand.MAIN_HAND).getInteractAnimation(), false);
                    }
                })
                .scope(InventorySearchScope.FULL)
                .visibility(InventorySwapVisibility.SILENT)
                .restore(true)
                .actionKind(InventoryActionKind.BLOCK_INTERACT)
                .build();
        acted = InventorySwap.INSTANCE.execute(request);
    }

    private void fireCrossbow() {
        InventorySwapRequest request = InventorySwapRequest.builder(InstaCart::isChargedCrossbow, () -> {
                    LocalPlayer player = mc.player;
                    if (player == null) return;
                    if (InventorySwap.INSTANCE.useItem(InteractionHand.MAIN_HAND).consumesAction()) {
                        player.swing(InteractionHand.MAIN_HAND, player.getItemInHand(InteractionHand.MAIN_HAND).getInteractAnimation(), false);
                    }
                })
                .scope(InventorySearchScope.FULL)
                .visibility(InventorySwapVisibility.SILENT)
                .restore(true)
                .actionKind(InventoryActionKind.USE_ITEM)
                .build();
        InventorySwap.INSTANCE.execute(request);
    }

    private void startBowDraw() {
        SlotResult bow = InventorySwap.INSTANCE.findHotbar(stack -> stack.is(Items.BOW));
        if (!bow.found()) {
            reset(true);
            return;
        }
        previousSlot = InventorySwap.INSTANCE.clientSelectedSlot();
        if (!InventorySwap.INSTANCE.selectHotbar(bow.slot())) {
            InventorySwap.INSTANCE.selectHotbar(previousSlot);
            previousSlot = -1;
            reset(true);
            return;
        }
        acted = InventorySwap.INSTANCE.useItem(InteractionHand.MAIN_HAND).consumesAction();
        if (!acted) {
            restoreSlot();
            reset(true);
        }
    }

    private void finish() {
        restoreSlot();
        cooldown = COOLDOWN_TICKS;
        reset(true);
    }

    private void restoreSlot() {
        if (previousSlot >= 0) InventorySwap.INSTANCE.restoreHotbar(previousSlot);
        previousSlot = -1;
    }

    private Entity findCart() {
        AABB box = new AABB(railPos).inflate(0.5);
        List<Entity> carts = mc.level.getEntities((Entity) null, box, e -> e.getType() == EntityTypes.TNT_MINECART);
        return carts.isEmpty() ? null : carts.get(0);
    }

    private boolean shooterAvailable() {
        if (shooter.get() == Shooter.CROSSBOW) return has(InventorySearchScope.FULL, InstaCart::isChargedCrossbow);
        return InventorySwap.INSTANCE.findHotbar(stack -> stack.is(Items.BOW)).found();
    }

    private static boolean has(InventorySearchScope scope, Predicate<ItemStack> predicate) {
        InventorySwap swap = InventorySwap.INSTANCE;
        return switch (scope) {
            case HOTBAR -> swap.findHotbar(predicate).found();
            case INVENTORY -> swap.findInventory(predicate).found();
            case FULL -> swap.findHotbar(predicate).found() || swap.findInventory(predicate).found();
        };
    }

    private static boolean isRail(ItemStack stack) {
        return stack.is(ItemTags.RAILS);
    }

    private static boolean isChargedCrossbow(ItemStack stack) {
        return stack.is(Items.CROSSBOW) && CrossbowItem.isCharged(stack);
    }

    private void advance(Phase next) {
        phase = next;
        phaseTicks = 0;
        acted = false;
    }

    private void reset(boolean releaseRotation) {
        if (previousSlot >= 0) {
            // Switching the slot cancels the draw server-side without firing; release would shoot.
            if (mc.player != null && mc.player.isUsingItem()) mc.player.stopUsingItem();
            restoreSlot();
        }
        phase = Phase.IDLE;
        phaseTicks = 0;
        acted = false;
        supportPos = null;
        railPos = null;
        firePos = null;
        if (releaseRotation) RotationManager.INSTANCE.release(this);
    }

    private String bindingName() {
        return name() + ":" + triggerKey.getName();
    }

    private void ensureBind() {
        String combo = triggerKey.get();
        if (combo == null) combo = "NONE";
        if (combo.equalsIgnoreCase(lastCombo)) return;
        String name = bindingName();
        CombatantInput.bindings().unbind(name);
        if (!triggerKey.isNone()) CombatantInput.bindings().bind(name, combo);
        lastCombo = combo;
    }

    private enum Phase {
        IDLE,
        RAIL,
        CART,
        FIRE,
        SHOOT
    }

    public enum Shooter {
        CROSSBOW,
        BOW
    }
}
