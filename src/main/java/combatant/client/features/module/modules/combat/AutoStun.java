/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.module.modules.combat;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.RotationUpdateEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.mixins.accessors.MultiPlayerGameModeAccessor;
import combatant.client.mixins.accessors.PlayerInventoryAccessor;
import combatant.client.util.combat.AttackUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Breaks shields: when the player under the crosshair raises a shield, the hotbar axe is selected, the target is
 * hit (with a swing) in the pre-rotation phase, and the previous slot is restored afterwards.
 */
@ModuleInfo(
        id = "autostun",
        displayName = "AutoStun",
        aliases = {"ShieldBreaker", "AutoShieldBreak"},
        category = ModuleCategory.COMBAT,
        subcategory = ModuleSubcategory.ATTACK,
        description = "module.autostun.description")
public final class AutoStun extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> cooldown = num("autoStunCooldown", "cooldown", 10, 0, 40);
    private final NumberValue<Integer> swapDelay = num("autoStunSwapDelay", "swap_delay", 0, 0, 5);
    private final BooleanValue swapBack = bool("autoStunSwapBack", "swap_back", true);
    private final NumberValue<Integer> swapBackDelay = num("autoStunSwapBackDelay", "swap_back_delay", 1, 0, 10);
    private final BooleanValue skipWhenUsing = bool("autoStunSkipWhenUsing", "skip_when_using", true);

    private int restoreSlot = -1;
    private int restoreAtTick;
    private int pendingSlot = -1;
    private int pendingAtTick;
    private int lastStunTick = Integer.MIN_VALUE;

    @Override
    public void onDisable() {
        LocalPlayer player = mc.player;
        if (player != null && restoreSlot >= 0) select(player, restoreSlot);
        restoreSlot = -1;
        pendingSlot = -1;
        lastStunTick = Integer.MIN_VALUE;
    }

    @EventHandler
    private void onRotationUpdate(RotationUpdateEvent event) {
        if (!isEnabled() || event.getType() != RotationUpdateEvent.Type.PRE) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) {
            restoreSlot = -1;
            pendingSlot = -1;
            return;
        }

        int tick = player.tickCount;
        if (restoreSlot >= 0 && tick >= restoreAtTick) {
            select(player, restoreSlot);
            restoreSlot = -1;
        }

        Player target = shieldingTarget(player);
        if (pendingSlot >= 0) {
            if (target == null) {
                // Target lowered the shield before the delayed swap finished: undo.
                if (restoreSlot >= 0) {
                    select(player, restoreSlot);
                    restoreSlot = -1;
                }
                pendingSlot = -1;
                return;
            }
            if (tick < pendingAtTick) return;
            strike(player, target);
            return;
        }
        if (target == null || restoreSlot >= 0) return;
        if (tick - lastStunTick < cooldown.get()) return;
        if (skipWhenUsing.get() && player.isUsingItem()) return;

        PlayerInventoryAccessor inv = (PlayerInventoryAccessor) player.getInventory();
        int current = inv.combatant$getSelectedSlot();
        if (player.getInventory().getItem(current).is(ItemTags.AXES)) {
            strike(player, target);
            return;
        }
        int axe = findAxe(player);
        if (axe < 0) return;

        restoreSlot = current;
        restoreAtTick = Integer.MAX_VALUE;
        select(player, axe);
        if (swapDelay.get() > 0) {
            pendingSlot = axe;
            pendingAtTick = tick + swapDelay.get();
            return;
        }
        strike(player, target);
    }

    private void strike(LocalPlayer player, Player target) {
        pendingSlot = -1;
        if (AttackUtil.attackCurrentItem(mc, target, false)) {
            lastStunTick = player.tickCount;
        }
        if (restoreSlot >= 0) {
            if (swapBack.get()) {
                restoreAtTick = player.tickCount + swapBackDelay.get();
                if (swapBackDelay.get() == 0) {
                    select(player, restoreSlot);
                    restoreSlot = -1;
                }
            } else {
                restoreSlot = -1;
            }
        }
    }

    private Player shieldingTarget(LocalPlayer player) {
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.ENTITY) return null;
        if (!(((EntityHitResult) mc.hitResult).getEntity() instanceof Player target)) return null;
        if (target == player || !target.isAlive() || !target.isBlocking()) return null;
        return player.distanceTo(target) <= player.entityInteractionRange() + 0.5 ? target : null;
    }

    private static int findAxe(LocalPlayer player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i).is(ItemTags.AXES)) return i;
        }
        return -1;
    }

    private void select(LocalPlayer player, int slot) {
        ((PlayerInventoryAccessor) player.getInventory()).combatant$setSelectedSlot(slot);
        if (mc.gameMode instanceof MultiPlayerGameModeAccessor accessor) {
            accessor.combatant$syncSelectedSlot();
        }
    }
}
