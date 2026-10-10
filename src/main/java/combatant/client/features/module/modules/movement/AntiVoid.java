/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.modules.movement.antivoid.VoidGuard;

/**
 * Catches a fall into the void. LAUNCH pushes you back up when you drop into the last blocks above
 * the world floor; SETBACK puts you back on the last solid ground you stood on. Servers with movement
 * checks may rubber-band either one, so it is a client-side safety net, not a bypass.
 */
@ModuleInfo(
        id = "antivoid",
        displayName = "AntiVoid",
        aliases = {"VoidSave", "AntiFall"},
        category = ModuleCategory.MOVEMENT,
        subcategory = ModuleSubcategory.BASIC,
        description = "module.antivoid.description")
public final class AntiVoid extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<Mode> mode = enumSetting("antiVoidMode", "mode", Mode.LAUNCH, Mode.values());
    private final NumberValue<Integer> triggerDistance =
            num("antiVoidTriggerDistance", "trigger_distance", 4, 1, 20);
    private final NumberValue<Float> launchStrength = visibleWhen(
            num("antiVoidLaunchStrength", "launch_strength", 0.8f, 0.2f, 2.0f),
            () -> mode.get() == Mode.LAUNCH);
    private final BooleanValue onlyOverVoid = bool("antiVoidOnlyOverVoid", "only_over_void", true);
    private final BooleanValue ignoreElytra = bool("antiVoidIgnoreElytra", "ignore_elytra", true);
    private final NumberValue<Integer> cooldownTicks =
            num("antiVoidCooldownTicks", "cooldown_ticks", 20, 5, 100);

    private Vec3 lastSafe;
    private int cooldown;

    @Override
    public void onEnable() {
        lastSafe = null;
        cooldown = 0;
    }

    @Override
    public void onDisable() {
        lastSafe = null;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            lastSafe = null;
            cooldown = 0;
            return;
        }
        if (cooldown > 0) cooldown--;

        if (player.onGround()) {
            lastSafe = player.position();
            return;
        }
        if (player.getAbilities().flying || player.isSpectator() || player.isPassenger()) return;

        int minY = level.getMinY();
        double feetY = player.getY();
        int distance = triggerDistance.get();
        if (!VoidGuard.inTriggerBand(feetY, minY, distance)) return;

        Vec3 motion = player.getDeltaMovement();
        boolean overVoid = !onlyOverVoid.get() || isOverVoid(level, player, minY);
        boolean ignoredFlight = ignoreElytra.get() && player.isFallFlying();
        if (!VoidGuard.shouldAct(feetY, motion.y, minY, distance, ignoredFlight, overVoid, onlyOverVoid.get(), cooldown)) {
            return;
        }

        rescue(player, motion);
        cooldown = cooldownTicks.get();
    }

    private void rescue(LocalPlayer player, Vec3 motion) {
        if (mode.get() == Mode.SETBACK && lastSafe != null) {
            player.setPos(lastSafe.x, lastSafe.y, lastSafe.z);
            player.setDeltaMovement(Vec3.ZERO);
        } else {
            player.setDeltaMovement(motion.x, launchStrength.get(), motion.z);
        }
        player.fallDistance = 0.0;
    }

    /** True when a ray from the feet straight down to below the world floor hits no collider. */
    private static boolean isOverVoid(ClientLevel level, LocalPlayer player, int minY) {
        Vec3 from = player.position();
        Vec3 to = new Vec3(from.x, minY - 1.0, from.z);
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                .getType() == HitResult.Type.MISS;
    }

    public enum Mode {
        LAUNCH,
        SETBACK
    }
}
