/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.RotationUpdateEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.relations.CategoryRules;
import combatant.client.features.relations.CategoryType;
import combatant.client.util.aiming.RestrictedSingleUseAction;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.projectile.ProjectileImpactPrediction;
import combatant.client.util.projectile.ProjectileTarget;
import combatant.client.util.projectile.SituationalProjectileAngleCalculator;
import combatant.client.util.projectile.TrajectoryInfo;
import combatant.client.util.target.TargetManager;
import combatant.client.util.player.inventory.InventoryActionKind;
import combatant.client.util.player.inventory.InventorySearchScope;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.inventory.InventorySwapPolicy;
import combatant.client.util.player.inventory.InventorySwapRequest;
import combatant.client.util.player.inventory.InventorySwapVisibility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

@ModuleInfo(
        id = "targetpearl",
        displayName = "TargetPearl",
        aliases = {"FollowPearl"},
        category = ModuleCategory.COMBAT,
        subcategory = ModuleSubcategory.ATTACK,
        description = "module.targetpearl.description")
public final class TargetPearl extends Module {
    private static final String ACTION_TRIGGER = "target_pearl";
    private static final int ROTATION_PRIORITY = 55;
    private static final Predicate<ItemStack> PEARL = stack -> stack != null && stack.is(Items.ENDER_PEARL);

    private final Minecraft mc = Minecraft.getInstance();
    private final EnumValue<Activation> activation =
            enumSetting("targetPearlActivation", "activation", Activation.AUTO, Activation.values());
    private final EnumValue<TargetMode> targetMode =
            enumSetting("targetPearlTargetMode", "target_mode", TargetMode.CURRENT, TargetMode.values());
    private final NumberValue<Float> triggerDistance =
            num("targetPearlTriggerDistance", "trigger_distance", 6.0f, 3.0f, 15.0f);
    private final NumberValue<Integer> cooldownTicks =
            num("targetPearlCooldownTicks", "cooldown_ticks", 20, 5, 60);
    private final NumberValue<Integer> simulationTicks =
            num("targetPearlSimulationTicks", "simulation_ticks", 80, 20, 160);

    private final Set<UUID> handled = new HashSet<>();
    private Candidate pending;
    private int pendingSinceTick = -1;
    private int cooldown;

    public TargetPearl() {
        action(ACTION_TRIGGER, "NONE");
    }

    @Override
    public void onDisable() {
        resetPending();
        handled.clear();
        cooldown = 0;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) {
            resetPending();
            handled.clear();
            cooldown = 0;
            return;
        }

        pruneHandled();

        if (cooldown > 0) cooldown--;

        if (pending != null) {
            if (pendingSinceTick >= 0 && player.tickCount - pendingSinceTick > 6) {
                resetPending();
            }
            return;
        }

        if (cooldown > 0 || !isActivationReady()) return;
        if (player.getCooldowns().isOnCooldown(new ItemStack(Items.ENDER_PEARL))) return;
        if (!hasAvailablePearl(player)) return;
        if (hasOwnPearl()) return;

        Candidate candidate = findCandidate(player);
        if (candidate == null) return;

        pending = candidate;
        pendingSinceTick = player.tickCount;
    }

    @EventHandler(priority = 20)
    private void onRotationUpdate(RotationUpdateEvent event) {
        if (event.getType() != RotationUpdateEvent.Type.PRE || !isEnabled() || pending == null) return;

        LocalPlayer player = mc.player;
        if (player == null || pending.pearl().isRemoved()) {
            resetPending();
            return;
        }

        RotationTarget target = new RotationTarget(
                pending.rotation(),
                pending.pearl(),
                java.util.List.of(),
                1,
                4.0f,
                false,
                MovementCorrection.SILENT,
                new RestrictedSingleUseAction(this::throwPendingPearl)
        );
        RotationManager.INSTANCE.setRotationTarget(target, ROTATION_PRIORITY, this);
    }

    private Candidate findCandidate(LocalPlayer player) {
        LivingEntity currentTarget = TargetManager.getTarget(false);
        Rotation currentRotation = new Rotation(player.getYRot(), player.getXRot());
        Candidate best = null;
        float bestAngle = Float.MAX_VALUE;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ThrownEnderpearl pearl) || !pearl.isAlive()) continue;
            if (handled.contains(pearl.getUUID())) continue;

            Entity owner = pearl.getOwner();
            if (!(owner instanceof Player ownerPlayer) || ownerPlayer == player) continue;
            if (!isAllowedOwner(ownerPlayer)) continue;
            if (targetMode.get() == TargetMode.CURRENT && ownerPlayer != currentTarget) continue;

            ProjectileImpactPrediction.Impact impact = ProjectileImpactPrediction.predict(pearl, simulationTicks.get());
            if (impact == null || impact.position() == null) continue;

            Vec3 impactPos = impact.position();
            if (player.position().distanceTo(impactPos) <= triggerDistance.get()) {
                handled.add(pearl.getUUID());
                continue;
            }

            Rotation rotation = calculatePearlRotation(player, impactPos);
            if (rotation == null) continue;

            float angle = currentRotation.angleTo(rotation);
            if (angle < bestAngle) {
                bestAngle = angle;
                best = new Candidate(pearl, impactPos, rotation);
            }
        }

        return best;
    }

    private Rotation calculatePearlRotation(LocalPlayer player, Vec3 impactPos) {
        Vec3 eye = player.getEyePosition();
        AABB targetBox = new AABB(
                impactPos.x - 0.5,
                impactPos.y - 0.5,
                impactPos.z - 0.5,
                impactPos.x + 0.5,
                impactPos.y + 0.5,
                impactPos.z + 0.5
        );
        return SituationalProjectileAngleCalculator.INSTANCE.calculateAngleFor(
                TrajectoryInfo.GENERIC,
                eye,
                ProjectileTarget.constant(impactPos, targetBox)
        );
    }

    private void throwPendingPearl() {
        LocalPlayer player = mc.player;
        Candidate candidate = pending;
        if (!isEnabled() || player == null || mc.gameMode == null || candidate == null) {
            resetPending();
            return;
        }

        if (player.getCooldowns().isOnCooldown(new ItemStack(Items.ENDER_PEARL))) {
            resetPending();
            return;
        }

        boolean used;
        if (player.getOffhandItem().is(Items.ENDER_PEARL)) {
            used = usePearl(player, InteractionHand.OFF_HAND);
        } else if (player.getMainHandItem().is(Items.ENDER_PEARL)) {
            used = usePearl(player, InteractionHand.MAIN_HAND);
        } else {
            if (!InventorySwap.INSTANCE.findHotbar(PEARL).found() && isMovingForInventory(player)) {
                resetPending();
                return;
            }

            InventorySwapRequest request = InventorySwapRequest.builder(PEARL, () -> usePearl(player, InteractionHand.MAIN_HAND))
                    .scope(InventorySearchScope.FULL)
                    .visibility(InventorySwapVisibility.SILENT)
                    .restore(true)
                    .policy(InventorySwapPolicy.NONE)
                    .actionKind(InventoryActionKind.USE_ITEM)
                    .build();
            used = InventorySwap.INSTANCE.execute(request);
        }

        if (used) {
            handled.add(candidate.pearl().getUUID());
            cooldown = cooldownTicks.get();
        }
        resetPending();
    }

    private boolean usePearl(LocalPlayer player, InteractionHand hand) {
        InteractionResult result = InventorySwap.INSTANCE.useItem(hand);
        if (result == null || !result.consumesAction()) return false;
        player.swing(hand, player.getItemInHand(hand).getInteractAnimation(), false);
        return true;
    }

    private boolean hasAvailablePearl(LocalPlayer player) {
        return player.getMainHandItem().is(Items.ENDER_PEARL)
                || player.getOffhandItem().is(Items.ENDER_PEARL)
                || InventorySwap.INSTANCE.findHotbar(PEARL).found()
                || InventorySwap.INSTANCE.findInventory(PEARL).found();
    }

    private boolean hasOwnPearl() {
        LocalPlayer player = mc.player;
        if (player == null) return false;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ThrownEnderpearl pearl && pearl.isAlive() && pearl.getOwner() == player) {
                return true;
            }
        }
        return false;
    }

    private boolean isAllowedOwner(Player owner) {
        CategoryType type = CategoryRules.determine(owner.getGameProfile().name());
        return type != CategoryType.FRIEND && type != CategoryType.STAFF && type != CategoryType.BEDWARS_SELF;
    }

    private boolean isActivationReady() {
        return activation.get() == Activation.AUTO || isActionHeld(ACTION_TRIGGER);
    }

    private boolean isMovingForInventory(LocalPlayer player) {
        if (player.isSprinting()) return true;
        if (player.input == null || player.input.keyPresses == null) return false;
        Input input = player.input.keyPresses;
        return input.forward() || input.backward() || input.left() || input.right();
    }

    private void pruneHandled() {
        if (handled.isEmpty() || mc.level == null) return;
        Set<UUID> live = new HashSet<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ThrownEnderpearl pearl && pearl.isAlive()) {
                live.add(pearl.getUUID());
            }
        }
        handled.retainAll(live);
    }

    private void resetPending() {
        pending = null;
        pendingSinceTick = -1;
        RotationManager.INSTANCE.release(this);
    }

    public enum Activation implements EnumValue.IdProvider {
        AUTO("auto"),
        BUTTON("button");

        private final String id;

        Activation(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    public enum TargetMode implements EnumValue.IdProvider {
        CURRENT("current"),
        ALL("all");

        private final String id;

        TargetMode(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    private record Candidate(ThrownEnderpearl pearl, Vec3 impact, Rotation rotation) {
    }
}
