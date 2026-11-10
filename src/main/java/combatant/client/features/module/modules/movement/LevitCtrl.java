/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PacketEvent;
import combatant.client.events.impl.PlayerEffectGravityEvent;
import combatant.client.events.impl.PlayerEffectMoveEvent;
import combatant.client.events.impl.PlayerEffectVelocityEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(
        id = "levitctrl",
        displayName = "LevitCtrl",
        aliases = {"LevitationControl", "SlowFallControl"},
        category = ModuleCategory.MOVEMENT,
        subcategory = ModuleSubcategory.RAGE,
        description = "module.levitctrl.description")
/** Applies configurable movement control to levitation and slow-falling physics. */
public final class LevitCtrl extends Module {
    private static final double AIR_FRICTION = 0.9100000262260437;
    private static final double MAX_AIR_ACCELERATION = 0.019;
    private static final double MAX_EFFECT_ASSIST = 0.006;
    private static final double MAX_LEVITATION_ADJUSTMENT = 0.004;

    private final Minecraft mc = Minecraft.getInstance();
    private final EnumValue<LevitationMode> levitation =
            enumMode("levitctrl_levitation", LevitationMode.CONTROL, LevitationMode.values());
    private final EnumValue<SlowFallingMode> slowFalling =
            enumMode("levitctrl_slow_falling", SlowFallingMode.CONTROL, SlowFallingMode.values());
    private final BooleanValue noInertia = bool("levitctrl_no_inertia", "no_inertia", false);
    private final EffectMotionPlanner planner = new EffectMotionPlanner();

    private LocalPlayer lastPlayer;
    private PlayerEffectMoveEvent.EffectType lastEffect;
    private int correctionUntilTick;
    private Vec3 previousVelocity;

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    @Override
    public void onTick() {
        LocalPlayer player = mc.player;
        if (player == null || player != lastPlayer || mc.level == null
                || (!player.hasEffect(MobEffects.LEVITATION) && !player.hasEffect(MobEffects.SLOW_FALLING))) {
            reset();
        }
    }

    @EventHandler
    private void onCorrection(PacketEvent.Receive event) {
        if (!event.isOriginal() || !(event.getPacket() instanceof ClientboundPlayerPositionPacket)) return;
        planner.noteCorrection();
        if (mc.player != null) correctionUntilTick = mc.player.tickCount + 8;
    }

    @EventHandler
    private void onEffectGravity(PlayerEffectGravityEvent event) {
        LocalPlayer player = event.getPlayer();
        if (canControl(player) && !player.hasEffect(MobEffects.LEVITATION)
                && slowFalling.get() == SlowFallingMode.OFF) {
            event.setGravity(event.getNormalGravity());
        }
    }

    @EventHandler(priority = -500)
    private void onEffectMove(PlayerEffectMoveEvent event) {
        LocalPlayer player = event.getPlayer();
        if (!canControl(player)) return;
        var effect = event.getEffectType();
        if (effect == PlayerEffectMoveEvent.EffectType.SLOW_FALLING
                && slowFalling.get() == SlowFallingMode.OFF) return;
        updateEffect(player, effect);
        Vec3 vanilla = event.getMovement();
        if (!finite(vanilla) || player.tickCount < correctionUntilTick) return;

        if (effect == PlayerEffectMoveEvent.EffectType.SLOW_FALLING
                || levitation.get() == LevitationMode.CONTROL) return;

        Vec2 input = player.input == null ? Vec2.ZERO : player.input.getMoveVector();
        boolean obstructed = player.horizontalCollision || player.verticalCollision
                || player.isInWater() || player.isInLava() || player.onClimbable();
        var activeEffect = player.getEffect(MobEffects.LEVITATION);
        int amplifier = activeEffect == null ? 0 : activeEffect.getAmplifier();
        EffectMotionPlanner.Motion motion = planner.calculate(player.tickCount,
                EffectMotionPlanner.Effect.LEVITATION, amplifier, player.getY(),
                vanilla.x, vanilla.y, vanilla.z, input.x, input.y, player.getYRot(),
                mc.options.keyJump.isDown(), mc.options.keyShift.isDown(), noInertia.get(),
                levitation.get() == LevitationMode.BUFFERED_IGNORE, player.onGround(), obstructed);
        Vec3 adjusted = new Vec3(motion.x(), motion.y(), motion.z());
        if (finite(adjusted) && !adjusted.equals(vanilla)) event.setMovement(adjusted);
    }

    @EventHandler(priority = -500)
    private void onEffectVelocity(PlayerEffectVelocityEvent event) {
        LocalPlayer player = event.getPlayer();
        if (!canControl(player)) return;
        var effect = event.getEffectType();
        updateEffect(player, effect);
        if (effect == PlayerEffectMoveEvent.EffectType.LEVITATION
                && levitation.get() == LevitationMode.CONTROL) {
            if (player.tickCount >= correctionUntilTick && finite(event.getVelocity())) {
                event.setVelocity(levitationControl(player, event.getVelocity()));
            }
            previousVelocity = event.getVelocity();
        } else if (effect == PlayerEffectMoveEvent.EffectType.SLOW_FALLING) {
            Vec3 vanilla = event.getVelocity();
            if (!finite(vanilla)) return;
            if (slowFalling.get() == SlowFallingMode.OFF) {
                previousVelocity = vanilla;
                return;
            }
            Vec3 adjusted = horizontalControl(player, vanilla);
            if (slowFalling.get() == SlowFallingMode.CONTROL) {
                adjusted = fallingControl(player, adjusted);
            }
            if (finite(adjusted)) event.setVelocity(adjusted);
            previousVelocity = adjusted;
        }
        if (player.onGround() || player.verticalCollision) planner.clearAltitude();
    }

    private Vec3 horizontalControl(LocalPlayer player, Vec3 vanilla) {
        Vec2 input = player.input == null ? Vec2.ZERO : player.input.getMoveVector();
        double inputLength = Math.min(1.0, Math.hypot(input.x, input.y));
        if (inputLength < 0.001) {
            if (noInertia.get() && !player.horizontalCollision && !player.onGround()
                    && !player.isInWater() && !player.isInLava()) {
                return new Vec3(vanilla.x * 0.65, vanilla.y, vanilla.z * 0.65);
            }
            return vanilla;
        }
        if (player.horizontalCollision || player.onGround() || player.isInWater() || player.isInLava()
                || player.onClimbable() || previousVelocity == null) return vanilla;

        double yaw = Math.toRadians(player.getYRot());
        double forward = input.y / inputLength;
        double strafe = input.x / inputLength;
        double dx = (strafe * Math.cos(yaw) - forward * Math.sin(yaw)) * MAX_EFFECT_ASSIST;
        double dz = (forward * Math.cos(yaw) + strafe * Math.sin(yaw)) * MAX_EFFECT_ASSIST;
        double baseX = previousVelocity.x * AIR_FRICTION;
        double baseZ = previousVelocity.z * AIR_FRICTION;
        double vanillaAccel = Math.hypot(vanilla.x - baseX, vanilla.z - baseZ);
        double allowance = Math.max(0.0, MAX_AIR_ACCELERATION - vanillaAccel);
        if (allowance <= 1.0E-5) return vanilla;
        double magnitude = Math.hypot(dx, dz);
        double scale = Math.min(1.0, allowance / magnitude);
        return new Vec3(vanilla.x + dx * scale, vanilla.y, vanilla.z + dz * scale);
    }

    private Vec3 levitationControl(LocalPlayer player, Vec3 vanilla) {
        if (player.verticalCollision || player.onGround()) return vanilla;
        boolean up = mc.options.keyJump.isDown();
        boolean down = mc.options.keyShift.isDown();
        if (up == down) return vanilla;
        double adjustment = up ? MAX_LEVITATION_ADJUSTMENT : -MAX_LEVITATION_ADJUSTMENT;
        var effect = player.getEffect(MobEffects.LEVITATION);
        if (effect == null) return vanilla;
        double naturalLimit = 0.05 * (effect.getAmplifier() + 1);
        double lower = Math.max(0.0, Math.min(naturalLimit * 0.25, vanilla.y));
        double y = Mth.clamp(vanilla.y + adjustment, lower, naturalLimit * 1.05);
        return new Vec3(vanilla.x, y, vanilla.z);
    }

    private Vec3 fallingControl(LocalPlayer player, Vec3 vanilla) {
        if (player.verticalCollision || player.onGround() || vanilla.y >= 0.0) return vanilla;
        boolean up = mc.options.keyJump.isDown();
        boolean down = mc.options.keyShift.isDown();
        if (up == down) return vanilla;
        double requested = up ? 0.004 : -0.004;
        double y = Math.min(0.0, vanilla.y + requested);
        return new Vec3(vanilla.x, y, vanilla.z);
    }

    private void updateEffect(LocalPlayer player, PlayerEffectMoveEvent.EffectType effect) {
        if (player == lastPlayer && effect == lastEffect) return;
        lastPlayer = player;
        lastEffect = effect;
        planner.reset();
        previousVelocity = null;
    }

    private boolean canControl(LocalPlayer player) {
        return player != null && player == mc.player && mc.level != null
                && !player.isPassenger() && !player.isFallFlying() && !player.isSpectator();
    }

    private static boolean finite(Vec3 velocity) {
        return velocity != null && Double.isFinite(velocity.x)
                && Double.isFinite(velocity.y) && Double.isFinite(velocity.z);
    }

    private void reset() {
        lastPlayer = null;
        lastEffect = null;
        correctionUntilTick = 0;
        planner.reset();
        previousVelocity = null;
    }

    public enum LevitationMode {
        CONTROL,
        BUFFERED_IGNORE
    }

    public enum SlowFallingMode {
        OFF,
        STABILIZE,
        CONTROL
    }
}
