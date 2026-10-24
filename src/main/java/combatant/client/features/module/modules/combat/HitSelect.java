/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.module.modules.combat;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Filters manual attacks: a click is swallowed while the hit would be wasted (target still in damage immunity, no
 * sprint knockback, no critical), and force-allowed once the maximum delay is reached so the combo never stalls.
 */
@ModuleInfo(
        id = "hitselect",
        displayName = "HitSelect",
        aliases = {"HitSelector", "ComboSelect"},
        category = ModuleCategory.COMBAT,
        subcategory = ModuleSubcategory.LEGIT,
        description = "module.hitselect.description")
public final class HitSelect extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<Mode> mode = enumSetting("hitSelectMode", "mode", Mode.TARGET_READY, Mode.values());
    private final NumberValue<Integer> maxTargetHurtTime = num("hitSelectMaxTargetHurtTime", "max_target_hurt_time", 2, 0, 10);
    private final NumberValue<Integer> maxDelay = num("hitSelectMaxDelay", "max_delay", 4, 1, 20);
    private final NumberValue<Integer> chance = num("hitSelectChance", "chance", 100, 0, 100);
    private final BooleanValue playersOnly = bool("hitSelectPlayersOnly", "players_only", true);

    private int blockedTicks;
    private int lastBlockTick = Integer.MIN_VALUE;
    private boolean bypassRolled;
    private boolean bypass;

    @Override
    public void onDisable() {
        blockedTicks = 0;
        lastBlockTick = Integer.MIN_VALUE;
        bypassRolled = false;
        bypass = false;
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.player == null) return;
        // The delay budget only accumulates across consecutive blocked ticks.
        if (mc.player.tickCount - lastBlockTick > 1) {
            blockedTicks = 0;
            bypassRolled = false;
        } else if (lastBlockTick == mc.player.tickCount || lastBlockTick == mc.player.tickCount - 1) {
            blockedTicks++;
        }
    }

    /** Called from the manual attack entry point; true swallows the click. */
    public static boolean blocksManualAttack() {
        HitSelect module = Modules.get(HitSelect.class);
        return module != null && module.isEnabled() && module.shouldBlock();
    }

    private boolean shouldBlock() {
        LocalPlayer player = mc.player;
        if (player == null || mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.ENTITY) return false;
        Entity entity = ((EntityHitResult) mc.hitResult).getEntity();
        if (!(entity instanceof LivingEntity target) || !target.isAlive()) return false;
        if (playersOnly.get() && !(target instanceof Player)) return false;

        if (!wouldBeWasted(player, target)) {
            blockedTicks = 0;
            bypassRolled = false;
            return false;
        }

        if (!bypassRolled) {
            bypassRolled = true;
            int value = chance.get();
            bypass = value < 100 && ThreadLocalRandom.current().nextInt(100) >= value;
        }
        if (bypass || blockedTicks >= maxDelay.get()) return false;

        lastBlockTick = player.tickCount;
        return true;
    }

    private boolean wouldBeWasted(LocalPlayer player, LivingEntity target) {
        return switch (mode.get()) {
            case TARGET_READY -> target.hurtTime > maxTargetHurtTime.get();
            case SPRINT_KB -> !player.isSprinting() || target.hurtTime > maxTargetHurtTime.get();
            case CRITICAL -> !isCritical(player) || target.hurtTime > maxTargetHurtTime.get();
        };
    }

    private static boolean isCritical(LocalPlayer player) {
        if (player.onGround() || player.onClimbable() || player.isInWater() || player.isPassenger()) {
            // Not airborne: crits impossible, but never stall inside water/ladders/vehicles.
            return player.isInWater() || player.onClimbable() || player.isPassenger();
        }
        return player.fallDistance > 0.0F;
    }

    public enum Mode {
        TARGET_READY, SPRINT_KB, CRITICAL
    }
}
