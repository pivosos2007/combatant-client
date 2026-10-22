/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PlayerMoveEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Notifier;
import combatant.client.features.module.modules.movement.rules.MovementRules;
import combatant.client.util.anticheat.AntiCheatPreset;
import combatant.client.util.player.MovementUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/**
 * Boosts horizontal speed for a few ticks and lets it fade back to normal. BOOST starts when you jump while walking;
 * DAMAGE starts when you take damage. Both change the horizontal motion beyond what vanilla physics allows, so the
 * Grim preset turns the module off.
 */
@ModuleInfo(
        id = "longjump",
        displayName = "LongJump",
        aliases = {"LJ"},
        category = ModuleCategory.MOVEMENT,
        subcategory = ModuleSubcategory.RAGE,
        description = "module.longjump.description")
public final class LongJump extends Module {

    /** Horizontal speed of a sprint jump; a boost from a standing start is scaled from this. */
    private static final double MIN_LAUNCH_SPEED = 0.28D;
    private static final double JUMP_LAUNCH_VELOCITY = 0.2D;

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<AntiCheatPreset> anticheat = enumMode("anticheat_mode", AntiCheatPreset.VANILLA);
    private final EnumValue<Mode> mode = enumMode("mode", Mode.BOOST);
    private final NumberValue<Double> boost = num("boost", 1.8D, 1.0D, 4.0D);
    private final NumberValue<Integer> duration = num("duration", 10, 2, 30);
    private final NumberValue<Double> falloff = num("falloff", 1.5D, 0.5D, 4.0D);

    private int boostTick = -1;
    private double launchSpeed;
    private int lastHurtTime;

    @Override
    public void onEnable() {
        stopBoost();
        lastHurtTime = 0;
        if (anticheat.get() == AntiCheatPreset.GRIM) Notifier.info(I18n.get("notification.longjump.grim"));
    }

    @Override
    public void onDisable() {
        stopBoost();
    }

    @Override
    public void onTick() {
        LocalPlayer player = mc.player;
        if (player == null) return;
        int hurt = player.hurtTime;
        boolean newlyHurt = hurt > lastHurtTime;
        lastHurtTime = hurt;
        if (newlyHurt && active() && mode.get() == Mode.DAMAGE && MovementUtil.isMoving()) {
            startBoost(horizontalSpeed(player.getDeltaMovement()));
        }
    }

    @EventHandler
    private void onMove(PlayerMoveEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || event.getType() != MoverType.SELF) return;
        if (!active() || player.isInWater() || player.isInLava() || player.onClimbable()
                || player.isPassenger() || player.isFallFlying()) {
            stopBoost();
            return;
        }

        Vec3 movement = event.getMovement();
        if (mode.get() == Mode.BOOST && boostTick < 0 && player.onGround()
                && movement.y > JUMP_LAUNCH_VELOCITY && MovementUtil.isMoving()) {
            startBoost(horizontalSpeed(movement));
        }
        if (boostTick < 0) return;

        if (mode.get() == Mode.BOOST && boostTick > 0 && player.onGround()) {
            stopBoost();
            return;
        }
        if (boostTick >= duration.get() || !MovementUtil.isMoving()) {
            stopBoost();
            return;
        }

        double speed = MovementRules.boostSpeed(launchSpeed, MIN_LAUNCH_SPEED, boostTick, duration.get(),
                boost.get(), falloff.get());
        double[] direction = MovementUtil.forward(1.0D);
        double length = Math.hypot(direction[0], direction[1]);
        if (length < 1.0E-6) return;
        event.setMovement(new Vec3(direction[0] / length * speed, movement.y, direction[1] / length * speed));
        boostTick++;
    }

    private boolean active() {
        return anticheat.get() != AntiCheatPreset.GRIM;
    }

    private void startBoost(double speed) {
        launchSpeed = speed;
        boostTick = 0;
    }

    private void stopBoost() {
        boostTick = -1;
        launchSpeed = 0.0D;
    }

    private static double horizontalSpeed(Vec3 v) {
        return Math.hypot(v.x, v.z);
    }

    public enum Mode {
        BOOST,
        DAMAGE
    }
}
