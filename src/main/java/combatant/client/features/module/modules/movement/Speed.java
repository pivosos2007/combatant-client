/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.events.impl.PlayerMoveEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.mixins.accessors.ServerboundMovePlayerPacketAccessor;
import combatant.client.util.player.MovementUtil;

//todo Description
@ModuleInfo(
        id = "speed",
        displayName = "Speed",
        category = ModuleCategory.MOVEMENT
)
public final class Speed extends Module {

    public final NumberValue<Float> legitMultiplier = num("legit_multiplier", 1.20f, 1.15f, 1.50f);
    public final NumberValue<Float> jumpAcceleration = num("jump_acceleration", 1.30f, 1.00f, 1.60f);
    public final NumberValue<Float> entityBoostStrength = num("entity_boost_strength", 0.20f, 0.05f, 0.40f);
    private final EnumValue<Mode> mode = enumMode("mode", Mode.NCP);
    private boolean lastOnGround = true;
    private int airTicks;

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return;

        LocalPlayer player = mc.player;
        updateAirTicks(player);

        switch (mode.get()) {
            case NCP -> handleNcp(player);
            case VULCAN_286 -> handleVulcan286(player);
            case VULCAN_288 -> handleVulcan288(player);
            case VULCAN_GROUND_286 -> handleVulcanGround286(player);
            case MATRIX_7 -> handleMatrix7(player);
        }
    }

    @EventHandler
    private void onMove(PlayerMoveEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        if (event.getType() != MoverType.SELF) return;
        if (mode.get() != Mode.NCP) return;

        LocalPlayer player = mc.player;
        if (!canSpeed(player) || !MovementUtil.isMoving() || player.onGround()) return;

        double speedLevel = getSpeedAmplifier(player);
        double airMin = 0.2 + 0.199999999 * speedLevel;
        double useSpeed = Math.max(horizontalSpeed(player.getDeltaMovement()), airMin);
        event.setMovement(withStrafe(event.getMovement(), player, useSpeed, 0.7));
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!(event.getPacket() instanceof ServerboundMovePlayerPacket packet)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        LocalPlayer player = mc.player;
        if (!canSpeed(player)) return;

        switch (mode.get()) {
            case VULCAN_288 -> {
                if (player.getDeltaMovement().y < 0.0) {
                    ((ServerboundMovePlayerPacketAccessor) packet).combatant$setOnGround(true);
                }
            }
            case VULCAN_GROUND_286 -> {
                if (collidesBottomVertical(player) && !mc.options.keyJump.isDown() && MovementUtil.isMoving()) {
                    ((ServerboundMovePlayerPacketAccessor) packet).combatant$setY(packet.getY(player.getY()) + 0.005);
                }
            }
            default -> {
            }
        }
    }

    public boolean shouldBlockOmniSprintBoost() {
        return isEnabled();
    }

    public boolean enabled(String key) {
        return false;
    }

    private void handleNcp(LocalPlayer player) {
        if (!canSpeed(player)) return;

        if (player.onGround()) {
            if (!MovementUtil.isMoving()) return;

            double groundMin = 0.281 + 0.199999999 * getSpeedAmplifier(player);
            autoJump(player);
            Vec3 velocity = withStrafe(player.getDeltaMovement(), player, groundMin, 1.0);
            player.setDeltaMovement(velocity.x, 0.4, velocity.z);
            return;
        }

        if (airTicks == 5) {
            Vec3 velocity = player.getDeltaMovement();
            player.setDeltaMovement(velocity.x, velocity.y - 0.1523351824467155, velocity.z);
        }

        if (player.hurtTime >= 5 && player.getDeltaMovement().y >= 0.0) {
            Vec3 velocity = player.getDeltaMovement();
            player.setDeltaMovement(velocity.x, velocity.y - 0.1, velocity.z);
        }
    }

    private void handleVulcan286(LocalPlayer player) {
        if (!canSpeed(player)) return;

        if (player.onGround()) {
            if (!MovementUtil.isMoving()) return;
            autoJump(player);
            return;
        }

        double speedLevel = getSpeedAmplifier(player);
        boolean goingSideways = player.input != null && player.input.getMoveVector().x != 0.0f;

        if (airTicks == 1) {
            double speed = goingSideways ? 0.3345 : 0.3355 * (1.0 + speedLevel * 0.3819);
            player.setDeltaMovement(withStrafe(player.getDeltaMovement(), player, speed, 1.0));
        } else if (airTicks == 2 && player.isSprinting()) {
            double speed = goingSideways ? 0.3235 : 0.3284 * (1.0 + speedLevel * 0.355);
            player.setDeltaMovement(withStrafe(player.getDeltaMovement(), player, speed, 1.0));
        } else if (airTicks == 4) {
            Vec3 velocity = player.getDeltaMovement();
            player.setDeltaMovement(velocity.x, -0.376, velocity.z);
        } else if (airTicks == 6) {
            double horizontal = horizontalSpeed(player.getDeltaMovement());
            if (horizontal > 0.298) {
                player.setDeltaMovement(withStrafe(player.getDeltaMovement(), player, 0.298, 1.0));
            }
        }
    }

    private void handleVulcan288(LocalPlayer player) {
        if (!canSpeed(player)) return;

        boolean hasSpeed = getSpeedAmplifier(player) > 0.0;

        if (player.onGround()) {
            if (!MovementUtil.isMoving()) return;
            autoJump(player);
            player.setDeltaMovement(withStrafe(player.getDeltaMovement(), player, hasSpeed ? 0.771 : 0.5, 1.0));
            return;
        }

        if (airTicks == 1) {
            player.setDeltaMovement(withStrafe(player.getDeltaMovement(), player, hasSpeed ? 0.605 : 0.31, 1.0));
        } else if (airTicks == 2) {
            Vec3 velocity = withStrafe(player.getDeltaMovement(), player, hasSpeed ? 0.57 : 0.29, 1.0);
            player.setDeltaMovement(velocity.x, hasSpeed ? -0.5 : -0.37, velocity.z);
        } else if (airTicks == 3) {
            player.setDeltaMovement(withStrafe(player.getDeltaMovement(), player, hasSpeed ? 0.595 : 0.27, 1.0));
        } else if (airTicks == 4) {
            player.setDeltaMovement(withStrafe(player.getDeltaMovement(), player, hasSpeed ? 0.595 : 0.28, 1.0));
        }

        if (hasSpeed && player.fallDistance > 0.0f) {
            Vec3 velocity = player.getDeltaMovement();
            player.setDeltaMovement(velocity.x * 1.055, velocity.y, velocity.z * 1.055);
        }
    }

    private void handleVulcanGround286(LocalPlayer player) {
        if (!canSpeed(player)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;
        if (!MovementUtil.isMoving() || mc.options.keyJump.isDown()) return;
        if (!collidesBottomVertical(player)) return;

        boolean hasSpeed = getSpeedAmplifier(player) > 0.0;
        boolean movingSideways = player.input != null && player.input.getMoveVector().x != 0.0f;
        double strafe = hasSpeed ? 0.59 : movingSideways ? 0.41 : 0.42;

        Vec3 velocity = withStrafe(player.getDeltaMovement(), player, strafe, 1.0);
        player.setDeltaMovement(velocity.x, 0.005, velocity.z);
    }

    private void handleMatrix7(LocalPlayer player) {
        if (!canSpeed(player) || !MovementUtil.isMoving()) return;

        double baseSpeed = Math.max(horizontalSpeed(player.getDeltaMovement()), 0.28);
        if (player.onGround()) {
            Vec3 velocity = withStrafe(player.getDeltaMovement(), player, baseSpeed, 1.0);
            player.setDeltaMovement(velocity.x, 0.419652, velocity.z);
            return;
        }

        if (player.getDeltaMovement().x * player.getDeltaMovement().x + player.getDeltaMovement().z * player.getDeltaMovement().z < 0.04) {
            player.setDeltaMovement(withStrafe(player.getDeltaMovement(), player, baseSpeed, 1.0));
        }
    }

    private void autoJump(LocalPlayer player) {
        if (player == null || player.getDeltaMovement().y > 0.01) return;
        player.jumpFromGround();
    }

    private boolean canSpeed(LocalPlayer player) {
        return player != null
                && !player.isShiftKeyDown()
                && !player.isInWater()
                && !player.isInLava()
                && !player.onClimbable()
                && !player.isPassenger()
                && !player.isFallFlying();
    }

    private void updateAirTicks(LocalPlayer player) {
        boolean onGround = player.onGround();
        if (onGround) {
            airTicks = 0;
        } else if (lastOnGround) {
            airTicks = 1;
        } else {
            airTicks++;
        }

        lastOnGround = onGround;
    }

    private double getSpeedAmplifier(LocalPlayer player) {
        if (player == null) return 0.0;
        var effect = player.getEffect(MobEffects.SPEED);
        return effect == null ? 0.0 : effect.getAmplifier();
    }

    private double horizontalSpeed(Vec3 velocity) {
        return velocity == null ? 0.0 : Math.hypot(velocity.x, velocity.z);
    }

    private Vec3 withStrafe(Vec3 currentVelocity, LocalPlayer player, double speed, double strength) {
        if (player == null || player.input == null) return currentVelocity;

        Vec2 input = player.input.getMoveVector();
        Vec3 movementInput = new Vec3(input.x, 0.0, input.y);
        return MovementUtil.withStrafe(currentVelocity, movementInput, player.getYRot(), speed, strength);
    }

    private boolean collidesBottomVertical(LocalPlayer player) {
        if (player == null || player.level() == null) return false;

        for (var shape : player.level().getBlockCollisions(player, player.getBoundingBox().move(0.0, -0.005, 0.0))) {
            if (!shape.isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private void resetState() {
        lastOnGround = true;
        airTicks = 0;
    }

    public enum Mode {
        NCP,
        VULCAN_286,
        VULCAN_288,
        VULCAN_GROUND_286,
        MATRIX_7
    }
}
