/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player.inventory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Items;
import combatant.client.features.module.Modules;
import combatant.client.mixininterface.IAttachedFireworkRocket;
import combatant.client.features.module.modules.combat.KillAura;

/**
 * Shared firework-use path for elytra modules.
 * <p>
 * Firework use is routed through {@link InventorySwap#execute}: the rocket slot is selected before the use packet
 * and the slot restore waits for the start of the next client tick, so no held-slot change ever follows the use in
 * the same Grim packet window (PacketOrderE) or after the movement packet (Post). A use the window does not allow
 * right now returns {@code false}; the caller retries on a later tick. Internal selected-slot/swing packets are
 * marked as inventory controller activity and do not poison AttackPressing/KillAura cooldown timing.
 */
public final class FireworkUseController {
    public static final FireworkUseController INSTANCE = new FireworkUseController();

    public static final int DEFAULT_MANUAL_COOLDOWN_TICKS = 20;
    private static final long MILLIS_PER_TICK = 50L;

    private final Minecraft mc = Minecraft.getInstance();
    private long lastUseMs;
    private int lastUseTick = Integer.MIN_VALUE;
    private int rocketIdAtLastUse = -1;
    private boolean lastLaunchConfirmed;
    private int lastObservedRocketId = -1;
    private int lastObservedRocketAge;
    private int lastObservedMinimumLifetime;
    private int lastObservedTick = Integer.MIN_VALUE;
    private int prematureEndUntilTick = Integer.MIN_VALUE;
    private int sampledTick = Integer.MIN_VALUE;
    private BoostState sampledState;
    private net.minecraft.world.level.Level trackedLevel;

    /** Client-side fuse estimate. The randomized server fuse is not synchronized. */
    public record BoostState(boolean active, int guaranteedTicks, int expectedTicks,
                             boolean recentlyEndedEarly, boolean awaitingSpawn) {
    }


    private FireworkUseController() {
    }

    public boolean use(float yaw,
                       float pitch,
                       boolean allowInventory,
                       boolean restore,
                       boolean silent,
                       int cooldownTicks,
                       boolean syncCombat) {
        return use(yaw, pitch, allowInventory, restore, silent, cooldownTicks, syncCombat, false);
    }

    /**
     * A critically stalled climb can request another boost before the previous rocket expires.
     * The short packet cooldown still prevents multiple uses in the same movement window.
     */
    public boolean use(float yaw,
                       float pitch,
                       boolean allowInventory,
                       boolean restore,
                       boolean silent,
                       int cooldownTicks,
                       boolean syncCombat,
                       boolean urgentReboost) {
        LocalPlayer player = mc.player;
        MultiPlayerGameMode manager = mc.gameMode;
        if (player == null || manager == null || mc.level == null) return false;
        if (player.inventoryMenu == null) return false;
        if (player.isUsingItem()) return false;
        if (!canPassCooldown(cooldownTicks)) return false;
        BoostState state = getBoostState(player);
        // Never block an urgent rescue on the mere presence of an almost-spent rocket.
        if (!urgentReboost && (state.awaitingSpawn() || (state.active() && state.guaranteedTicks() > 3))) {
            return false;
        }

        boolean offhandReady = player.getOffhandItem().is(Items.FIREWORK_ROCKET);
        boolean mainHandReady = player.getMainHandItem().is(Items.FIREWORK_ROCKET);
        // A rocket already held in either hand does not mutate inventory and must not be
        // stalled by KillAura's attack preparation window.
        if (syncCombat && !offhandReady && !mainHandReady && isKillAuraAttackWindowBusy()) return false;

        boolean used;
        if (offhandReady) {
            used = useWithTemporaryRotation(player, yaw, pitch, InteractionHand.OFF_HAND);
        } else if (mainHandReady) {
            used = useWithTemporaryRotation(player, yaw, pitch, InteractionHand.MAIN_HAND);
        } else {
            used = useMainHandRocket(player, yaw, pitch, allowInventory, restore, silent);
        }

        if (used) {
            lastUseMs = System.currentTimeMillis();
            rocketIdAtLastUse = lastObservedRocketId;
            lastLaunchConfirmed = false;
            lastUseTick = player.tickCount;
            sampledTick = Integer.MIN_VALUE;
        }
        return used;
    }

    public boolean canPassCooldown(int cooldownTicks) {
        int safeTicks = Math.max(0, cooldownTicks);
        LocalPlayer player = mc.player;
        if (player != null && mc.level != trackedLevel) resetForWorld();
        if (player != null && lastUseTick != Integer.MIN_VALUE) {
            if (player.tickCount < lastUseTick) {
                resetForWorld();
            } else {
                return player.tickCount - lastUseTick >= safeTicks;
            }
        }
        return System.currentTimeMillis() - lastUseMs >= safeTicks * MILLIS_PER_TICK;
    }

    public boolean isCooling(int cooldownTicks) {
        return !canPassCooldown(cooldownTicks);
    }

    private boolean useMainHandRocket(LocalPlayer player,
                                      float yaw,
                                      float pitch,
                                      boolean allowInventory,
                                      boolean restore,
                                      boolean silent) {
        InventorySearchScope scope = allowInventory ? InventorySearchScope.FULL : InventorySearchScope.HOTBAR;
        boolean[] accepted = {false};

        InventorySwapRequest request = InventorySwapRequest
                .builder(stack -> stack.is(Items.FIREWORK_ROCKET), () -> {
                    accepted[0] = useWithTemporaryRotation(player, yaw, pitch, InteractionHand.MAIN_HAND);
                })
                .scope(scope)
                .visibility(silent ? InventorySwapVisibility.SILENT : InventorySwapVisibility.NORMAL)
                .restore(restore)
                .actionKind(InventoryActionKind.USE_ITEM)
                .build();

        return InventorySwap.INSTANCE.execute(request) && accepted[0];
    }

    private boolean useWithTemporaryRotation(LocalPlayer player, float yaw, float pitch, InteractionHand hand) {
        float oldYaw = player.getYRot();
        float oldPitch = player.getXRot();
        player.setYRot(yaw);
        player.setXRot(pitch);

        try {
            InteractionResult result = InventorySwap.INSTANCE.useItem(hand);
            if (!result.consumesAction()) {
                return false;
            }
            InventorySwap.INSTANCE.swingHand(hand);
            return true;
        } finally {
            player.setYRot(oldYaw);
            player.setXRot(oldPitch);
        }
    }

    /**
     * The server chooses the actual rocket lifetime as
     * 10 * (flightDuration + 1) + random(0..5) + random(0..6) ticks.
     * Only the item is synchronized, so the guaranteed remainder is based on
     * the shortest possible fuse, not on a guessed exact lifetime.
     */
    public BoostState getBoostState(LocalPlayer player) {
        if (player == null || mc.level == null) return new BoostState(false, 0, 0, false, false);
        if (mc.level != trackedLevel || (lastUseTick != Integer.MIN_VALUE && player.tickCount < lastUseTick)) {
            resetForWorld();
        }
        if (sampledTick == player.tickCount && sampledState != null) return sampledState;

        FireworkRocketEntity best = null;
        int bestGuarantee = Integer.MIN_VALUE;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof FireworkRocketEntity rocket)
                    || !rocket.isAlive()
                    || (rocket.getOwner() != player && !(rocket instanceof IAttachedFireworkRocket attached
                    && attached.combatant$isAttachedTo(player)))) continue;
            int minimum = minimumLifetime(rocket);
            // Entity spawn/metadata arrives after the server has already ticked the fuse.
            int guaranteed = minimum - rocket.tickCount - 2;
            if (best == null || guaranteed > bestGuarantee) {
                best = rocket;
                bestGuarantee = guaranteed;
            }
        }

        if (best != null) {
            if (lastUseTick != Integer.MIN_VALUE && best.getId() != rocketIdAtLastUse) {
                lastLaunchConfirmed = true;
            }
            lastObservedRocketId = best.getId();
            lastObservedRocketAge = best.tickCount;
            lastObservedMinimumLifetime = minimumLifetime(best);
            lastObservedTick = player.tickCount;
            prematureEndUntilTick = Integer.MIN_VALUE;
        } else if (lastObservedRocketId != -1) {
            // Entity removal is authoritative even when the rocket's randomized
            // fuse has not elapsed. Collisions can end a boost almost immediately.
            if (lastObservedRocketAge + Math.max(0, player.tickCount - lastObservedTick)
                    < lastObservedMinimumLifetime - 1) {
                prematureEndUntilTick = player.tickCount + 10;
            }
            lastObservedRocketId = -1;
        }

        int elapsed = lastUseTick == Integer.MIN_VALUE ? Integer.MAX_VALUE : player.tickCount - lastUseTick;
        boolean noSpawnConfirmation = elapsed >= 0 && !lastLaunchConfirmed;
        boolean awaitingSpawn = noSpawnConfirmation && elapsed < 5;
        boolean unconfirmedLaunch = noSpawnConfirmation && elapsed >= 5 && elapsed <= 10;
        sampledState = new BoostState(
                best != null,
                best == null ? 0 : Math.max(0, bestGuarantee),
                best == null ? 0 : Math.max(0, bestGuarantee + 5),
                (prematureEndUntilTick != Integer.MIN_VALUE && player.tickCount <= prematureEndUntilTick)
                        || unconfirmedLaunch,
                awaitingSpawn
        );
        sampledTick = player.tickCount;
        return sampledState;
    }

    private static int minimumLifetime(FireworkRocketEntity rocket) {
        Fireworks fireworks = rocket.getItem().get(DataComponents.FIREWORKS);
        int flight = fireworks == null ? 0 : fireworks.flightDuration();
        return 10 * (1 + Math.max(0, flight));
    }

    private void resetForWorld() {
        trackedLevel = mc.level;
        lastUseMs = 0;
        lastUseTick = Integer.MIN_VALUE;
        rocketIdAtLastUse = -1;
        lastLaunchConfirmed = false;
        lastObservedRocketId = -1;
        lastObservedRocketAge = 0;
        lastObservedMinimumLifetime = 0;
        lastObservedTick = Integer.MIN_VALUE;
        prematureEndUntilTick = Integer.MIN_VALUE;
        sampledTick = Integer.MIN_VALUE;
        sampledState = null;
    }

    private boolean isKillAuraAttackWindowBusy() {
        KillAura killAura = Modules.get(KillAura.class);
        if (killAura == null || !killAura.isEnabled() || killAura.getCurrentTarget() == null) {
            return false;
        }

        LocalPlayer player = mc.player;
        var target = killAura.getCurrentTarget();
        if (player == null || target == null || !target.isAlive()) return false;
        double reach = killAura.getEffectiveRange(target);
        var eye = player.getEyePosition();
        var box = target.getBoundingBox();
        double x = net.minecraft.util.Mth.clamp(eye.x, box.minX, box.maxX);
        double y = net.minecraft.util.Mth.clamp(eye.y, box.minY, box.maxY);
        double z = net.minecraft.util.Mth.clamp(eye.z, box.minZ, box.maxZ);
        if (eye.distanceToSqr(x, y, z) > reach * reach) return false;
        return killAura.willAttackInTicks(0) || killAura.willAttackInTicks(1);
    }
}
