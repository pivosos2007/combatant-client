/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.pvp.client;

import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import combatant.client.config.MainConfig;
import combatant.client.config.values.ItemCooldownRulesValue;
import combatant.client.util.pvp.ItemCooldownSnapshot;
import combatant.client.util.pvp.ItemUseCooldowns;

import java.util.Map;

/**
 * Local-player PvP state facade.
 * <p>
 * Item cooldown storage is not kept here. It delegates to the generic owner-based
 * ItemUseCooldowns engine with SELF_OWNER, so self/opponent/future systems all use
 * the same rule/window/cooldown implementation.
 */
public class CooldownManager {
    private boolean inPvp = false;
    private long lastPvpExitMs = 0L;

    public void enterPvp() {
        inPvp = true;
        lastPvpExitMs = 0L;
    }

    public void exitPvp() {
        inPvp = false;
        lastPvpExitMs = Util.getMillis();
    }

    public boolean isInPvp() {
        return MainConfig.get().isForcePvp() || inPvp;
    }

    public boolean isPvpGraceActive(long graceMs) {
        if (isInPvp()) return true;
        if (lastPvpExitMs <= 0L) return false;
        return Util.getMillis() - lastPvpExitMs <= graceMs;
    }

    /**
     * Legacy prediction hook retained as a no-op. Use prediction must never block interaction anymore.
     */
    public void beginPredictedUse(Item item) {
        // no-op
    }

    /**
     * Legacy confirmation hook retained for old call sites; it now records only configured item-use rules.
     */
    public void commitConfirmedUse(Item item, long startedAtMs) {
        ItemUseCooldowns.recordSelfUse(item);
    }

    public ItemUseCooldowns.RuleUseResult recordRuleUse(Item item, ItemCooldownRulesValue.Rule rule) {
        return ItemUseCooldowns.recordUse(ItemUseCooldowns.SELF_OWNER, item, rule);
    }

    public void startLocalCooldown(Item item, int seconds) {
        ItemUseCooldowns.startSelfCooldown(item, seconds);
    }

    public float getCooldownProgress(Item item) {
        return snapshot(item).cooldownProgress();
    }

    public boolean isCooling(Item item) {
        return ItemUseCooldowns.isSelfCooling(item);
    }

    public ItemCooldownSnapshot snapshot(Item item) {
        return ItemUseCooldowns.selfSnapshot(item);
    }

    public Map<Item, ItemCooldownSnapshot> snapshots() {
        return ItemUseCooldowns.selfSnapshots();
    }

    public void clear() {
        ItemUseCooldowns.clearSelf();
        lastPvpExitMs = 0L;
    }

    public boolean isPredicted(Item item) {
        return false;
    }
}
