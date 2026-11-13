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
import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.features.gui.hud.gauges.GaugeConnect;
import combatant.client.features.gui.hud.gauges.GaugeRegistry;
import combatant.client.features.module.modules.movement.speed.Vulcan297ImpulseController;
import combatant.client.features.module.modules.combat.TargetPearl;
import combatant.client.util.player.TeleportImpulseTracker;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.MovementInputEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.events.impl.PlayerMoveEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleListInfoProvider;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.movement.speed.BhopInput;
import combatant.client.mixins.accessors.ServerboundMovePlayerPacketAccessor;
import combatant.client.util.player.MovementUtil;

import java.util.Map;

@ModuleInfo(
        id = "speed",
        displayName = "Speed",
        category = ModuleCategory.MOVEMENT, subcategory = ModuleSubcategory.RAGE,
        description = "module.speed.description")
public final class Speed extends Module {

    private final EnumValue<Mode> mode = enumCommon("mode", "mode", CommonSettingSchemas.MODE,
            Mode.VULCAN_297, Mode.values());
    private boolean lastOnGround = true;
    private int airTicks;
    private final Vulcan297ImpulseController vulcanImpulse = new Vulcan297ImpulseController();
    private final BooleanMapValue vulcanSources = visibleWhen(group("speedVulcan297Sources", "vulcan297_sources",
            Map.of("pearl", true, "chorus", true, "command", true, "generic", true)),
            () -> mode.get() == Mode.VULCAN_297);
    private long lastTeleportSequence;
    private LocalPlayer trackedPlayer;
    private net.minecraft.client.multiplayer.ClientLevel trackedLevel;
    private Mode trackedMode;

    @ModuleListInfoProvider
    private String moduleListDetail() {
        return switch (mode.get()) {
            case NCP -> "NCP";
            case VULCAN_297 -> "Vulcan 2.9.7";
            case VULCAN_286 -> "Vulcan 2.86";
            case VULCAN_288 -> "Vulcan 2.88";
            case VULCAN_GROUND_286 -> "Vulcan Ground";
            case MATRIX_7 -> "Matrix 7";
            case BHOP -> "BHop";
        };
    }

    @Override
    public void onEnable() {
        resetState();
        trackedMode = mode.get();
        Minecraft mc = Minecraft.getInstance();
        trackedPlayer = mc.player;
        trackedLevel = mc.level;
        lastTeleportSequence = currentTeleportSequence();
    }

    @Override
    public void onTick() {
        GaugeRegistry.updateAnnotated("speed", this);
    }

    @GaugeConnect(id = "vulcan297_friction", labelKey = "hud.gauges.speed.friction",
            readiness = GaugeRegistry.Readiness.THRESHOLD, hideAfterMillis = 1100)
    private GaugeRegistry.Sample vulcan297FrictionGauge() {
        boolean visible = isEnabled() && mode.get() == Mode.VULCAN_297 && vulcanImpulse.visible();
        float fill = vulcanImpulse.available();
        return new GaugeRegistry.Sample(fill, vulcanImpulse.canBoost(), visible,
                GaugeRegistry.Flow.FILL, "", vulcanImpulse.consumptionThreshold());
    }

    @Override
    public void onDisable() {
        resetState();
        GaugeRegistry.removeOwner("speed");
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) {
            resetState();
            trackedPlayer = null;
            trackedLevel = null;
            return;
        }

        LocalPlayer player = mc.player;
        if (trackedPlayer != player || trackedLevel != mc.level || trackedMode != mode.get()) {
            resetState();
            trackedPlayer = player;
            trackedLevel = mc.level;
            trackedMode = mode.get();
        }
        if (mode.get() == Mode.VULCAN_297) {
            vulcanImpulse.tick(player.tickCount);
            consumeTeleport(player);
        }
        Jesus jesus = Modules.get(Jesus.class);
        if (jesus != null && jesus.isManagingVulcan297(player)) {
            resetState();
            return;
        }
        updateAirTicks(player);

        switch (effectiveMode()) {
            case VULCAN_297 -> { }
            case NCP -> handleNcp(player);
            case VULCAN_286 -> handleVulcan286(player);
            case VULCAN_288 -> handleVulcan288(player);
            case VULCAN_GROUND_286 -> handleVulcanGround286(player);
            case MATRIX_7 -> handleMatrix7(player);
            case BHOP -> {
            }
        }
    }

    /**
     * Bhop hops through the input, not through {@code jumpFromGround}: a 26.2 client's input packet is how GrimAC
     * knows a jump was asked for, and a jump without it is a Simulation flag.
     */
    @EventHandler
    private void onMovementInput(MovementInputEvent event) {
        if (!isEnabled() || effectiveMode() != Mode.BHOP) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc == null ? null : mc.player;
        if (player == null) return;

        BhopInput hop = BhopInput.decide(canSpeed(player), player.onGround(),
                event.isForward(), event.isBackward(), event.isLeft(), event.isRight());
        if (hop.jump()) event.setJump(true);
        if (hop.sprint()) event.setSprint(true);
    }

    @EventHandler
    private void onMove(PlayerMoveEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        if (event.getType() != MoverType.SELF) return;
        if (effectiveMode() == Mode.VULCAN_297) {
            handleVulcan297Move(mc.player, event);
            return;
        }
        if (effectiveMode() != Mode.NCP) return;

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
        switch (effectiveMode()) {
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
            default -> { }
        }
    }

    private long currentTeleportSequence() {
        var latest = TeleportImpulseTracker.INSTANCE.latest();
        return latest == null ? 0L : latest.sequence();
    }

    private void consumeTeleport(LocalPlayer player) {
        var applied = TeleportImpulseTracker.INSTANCE.latest();
        if (applied == null || applied.sequence() <= lastTeleportSequence) return;
        lastTeleportSequence = applied.sequence();
        if (player.tickCount < applied.tick() || player.tickCount - applied.tick() > 3) return;
        Vulcan297ImpulseController.Source source = switch (applied.kind()) {
            case PEARL -> vulcanSources.get("pearl") ? Vulcan297ImpulseController.Source.PEARL : null;
            case CHORUS -> vulcanSources.get("chorus") ? Vulcan297ImpulseController.Source.CHORUS : null;
            case COMMAND -> vulcanSources.get("command") ? Vulcan297ImpulseController.Source.TELEPORT : null;
            case GENERIC -> vulcanSources.get("generic") ? Vulcan297ImpulseController.Source.TELEPORT : null;
            case CORRECTION -> null;
        };
        if (source == null) {
            if (applied.kind() == TeleportImpulseTracker.Kind.CORRECTION) vulcanImpulse.correction();
            return;
        }
        TargetPearl targetPearl = Modules.get(TargetPearl.class);
        Vec3 target = targetPearl != null && targetPearl.isEnabled() && targetPearl.isFrictionGuidanceEnabled()
                ? applied.target() : null;
        vulcanImpulse.arm(source, target);
    }

    private void handleVulcan297Move(LocalPlayer player, PlayerMoveEvent event) {
        Vec3 current = event.getMovement();
        Vec3 targetDirection = vulcanTargetDirection(player);
        if (!canSpeed(player) || (!MovementUtil.isMoving() && targetDirection == null) || player.isAutoSpinAttack()) {
            vulcanImpulse.observeUnmodified(horizontalSpeed(current), player.tickCount);
            return;
        }
        double target = vulcanImpulse.prepare(horizontalSpeed(current), player.tickCount);
        if (target <= 0.0D) return;

        double now = horizontalSpeed(current);
        Vec3 result;
        if (targetDirection != null) {
            Vec3 blended = targetDirection;
            if (now > 0.05D) {
                Vec3 previousDirection = new Vec3(current.x / now, 0.0D, current.z / now);
                blended = previousDirection.scale(0.72D).add(targetDirection.scale(0.28D));
                double length = Math.hypot(blended.x, blended.z);
                if (length > 1.0E-6D) blended = blended.scale(1.0D / length);
            }
            result = new Vec3(blended.x * target, current.y, blended.z * target);
        } else {
            result = withStrafe(current, player, target, 1.0D);
        }
        if (now > 0.05D && targetDirection == null) {
            double alignment = (result.x * current.x + result.z * current.z)
                    / Math.max(1.0E-6D, horizontalSpeed(result) * now);
            if (alignment < 0.75D) {
                result = new Vec3(current.x / now * target, current.y, current.z / now * target);
            }
        }
        event.setMovement(new Vec3(result.x, current.y, result.z));
    }

    private Vec3 vulcanTargetDirection(LocalPlayer player) {
        TargetPearl pearl = Modules.get(TargetPearl.class);
        if (pearl == null || !pearl.isEnabled() || !pearl.isFrictionGuidanceEnabled()
                || !vulcanImpulse.active()) return null;
        Vec3 target = TeleportImpulseTracker.INSTANCE.targetAfterLanding(player.tickCount);
        if (target == null || player.level() == null) return null;
        if (!player.level().hasChunkAt(net.minecraft.core.BlockPos.containing(target))) return null;
        double dx = target.x - player.getX();
        double dz = target.z - player.getZ();
        double length = Math.hypot(dx, dz);
        if (length < 0.8D || length > 24.0D || Math.abs(target.y - player.getY()) > 3.0D) return null;
        Vec3 direction = new Vec3(dx / length, 0.0D, dz / length);
        if (!player.level().noCollision(player, player.getBoundingBox().move(
                direction.x * Math.min(0.7D, length), 0.0D,
                direction.z * Math.min(0.7D, length)))) return null;
        return direction;
    }

    public boolean shouldBlockOmniSprintBoost() {
        return isEnabled();
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
        Jesus jesus = Modules.get(Jesus.class);
        return player != null
                && (jesus == null || !jesus.isManagingVulcan297(player))
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

    private Mode effectiveMode() {
        return mode.get();
    }

    private void resetState() {
        lastOnGround = true;
        airTicks = 0;
        vulcanImpulse.reset();
        lastTeleportSequence = currentTeleportSequence();
    }

    public enum Mode {
        VULCAN_297,
        NCP,
        VULCAN_286,
        VULCAN_288,
        VULCAN_GROUND_286,
        MATRIX_7,
        BHOP
    }
}
