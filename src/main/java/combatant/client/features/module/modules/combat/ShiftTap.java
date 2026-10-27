/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.automation.Action;
import combatant.client.automation.ActionPriority;
import combatant.client.automation.Actions;
import combatant.client.automation.AutomationRuntime;
import combatant.client.automation.AutomationTask;
import combatant.client.automation.actions.SprintTapAction;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.AttackEntityEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.util.combat.TapRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

@ModuleInfo(
        id = "shifttap",
        displayName = "ShiftTap",
        aliases = {"WTap", "SprintReset", "STap"},
        category = ModuleCategory.COMBAT,
        subcategory = ModuleSubcategory.LEGIT,
        description = "module.shifttap.description")
public final class ShiftTap extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<Mode> mode = enumSetting("shiftTapMode", "mode", Mode.W_TAP, Mode.values());
    private final NumberValue<Integer> minTicks = num("shiftTapMinTicks", "min_ticks", 1, 1, 6);
    private final NumberValue<Integer> maxTicks = num("shiftTapMaxTicks", "max_ticks", 2, 1, 6);
    private final NumberValue<Integer> startDelay = num("shiftTapStartDelay", "start_delay", 0, 0, 3);
    private final NumberValue<Integer> cooldown = num("shiftTapCooldown", "cooldown", 6, 0, 40);
    private final NumberValue<Integer> chance = num("shiftTapChance", "chance", 100, 0, 100);
    private final BooleanValue playersOnly = bool("shiftTapPlayersOnly", "players_only", false);

    private AutomationTask active;
    private long lastTapTick = Long.MIN_VALUE;

    @Override
    public void onDisable() {
        active = null;
        lastTapTick = Long.MIN_VALUE;
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        if (player == null || event.getPlayer() != player) return;
        if (!(event.getTarget() instanceof LivingEntity target) || !target.isAlive()) return;
        if (playersOnly.get() && !(target instanceof Player)) return;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        boolean tapActive = active != null && !active.isDone();
        if (!TapRules.shouldTap(player.isSprinting(), tapActive, player.tickCount, lastTapTick,
                cooldown.get(), chance.get(), random.nextDouble())) {
            return;
        }

        int ticks = TapRules.pickTicks(minTicks.get(), maxTicks.get(), random.nextDouble());
        Action tap = mode.get() == Mode.SNEAK_TAP ? SprintTapAction.sneak(ticks) : SprintTapAction.forwardRelease(ticks);
        int delay = startDelay.get();
        Action root = delay > 0 ? Actions.sequence("ShiftTap", Actions.waitTicks(delay), tap) : tap;

        active = AutomationTask.builder(this, "shifttap", root)
                .priority(ActionPriority.COMBAT)
                .yieldToManualInput(Collections.emptySet())
                .build();
        lastTapTick = player.tickCount;
        AutomationRuntime.INSTANCE.submit(active);
    }

    public enum Mode {
        W_TAP, SNEAK_TAP
    }
}
