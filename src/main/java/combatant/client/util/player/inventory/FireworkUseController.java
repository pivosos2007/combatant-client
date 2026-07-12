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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Items;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.combat.KillAura;

/**
 * Shared firework-use path for elytra modules.
 * <p>
 * The important part is not the delay itself. Firework use is routed through
 * InventorySwap, so internal selected-slot/swing packets are marked as inventory
 * controller activity and do not poison AttackPressing/KillAura cooldown timing.
 */
public final class FireworkUseController {
    public static final FireworkUseController INSTANCE = new FireworkUseController();

    public static final int DEFAULT_MANUAL_COOLDOWN_TICKS = 20;
    private static final long MILLIS_PER_TICK = 50L;

    private final Minecraft mc = Minecraft.getInstance();
    private long lastUseMs;

    private FireworkUseController() {
    }

    public boolean use(float yaw,
                       float pitch,
                       boolean allowInventory,
                       boolean restore,
                       boolean silent,
                       int cooldownTicks,
                       boolean syncCombat) {
        LocalPlayer player = mc.player;
        MultiPlayerGameMode manager = mc.gameMode;
        if (player == null || manager == null || mc.level == null) return false;
        if (player.inventoryMenu == null) return false;
        if (player.isUsingItem()) return false;
        if (!canPassCooldown(cooldownTicks)) return false;
        if (hasOwnedActiveFirework(player)) return false;
        if (syncCombat && isKillAuraAttackWindowBusy()) return false;

        boolean used;
        if (player.getOffhandItem().is(Items.FIREWORK_ROCKET)) {
            used = useWithTemporaryRotation(player, yaw, pitch, InteractionHand.OFF_HAND);
        } else {
            used = useMainHandRocket(player, yaw, pitch, allowInventory, restore, silent);
        }

        if (used) {
            lastUseMs = System.currentTimeMillis();
        }
        return used;
    }

    public boolean canPassCooldown(int cooldownTicks) {
        int safeTicks = Math.max(0, cooldownTicks);
        long delayMs = safeTicks * MILLIS_PER_TICK;
        return System.currentTimeMillis() - lastUseMs >= delayMs;
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

        return InventorySwap.INSTANCE.command(request) && accepted[0];
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
            InventorySwap.INSTANCE.swingHand(hand, InventorySwap.INSTANCE.defaultPolicy());
            return true;
        } finally {
            player.setYRot(oldYaw);
            player.setXRot(oldPitch);
        }
    }

    private boolean hasOwnedActiveFirework(LocalPlayer player) {
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof FireworkRocketEntity firework
                    && firework.isAlive()
                    && firework.getOwner() == player) {
                return true;
            }
        }
        return false;
    }

    private boolean isKillAuraAttackWindowBusy() {
        KillAura killAura = Modules.get(KillAura.class);
        if (killAura == null || !killAura.isEnabled() || killAura.getCurrentTarget() == null) {
            return false;
        }

        return killAura.willAttackInTicks(0) || killAura.willAttackInTicks(1);
    }
}
