/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.util.combat.AttackUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.MovementInputEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.events.impl.PlayerJumpEvent;
import combatant.client.events.impl.SprintControlEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.movement.Flight;
import combatant.client.features.module.modules.movement.Timer;
import combatant.client.mixins.accessors.ServerboundMovePlayerPacketAccessor;
import combatant.client.util.click.ClickScheduler;
import combatant.client.util.target.TargetManager;

//todo Description
@ModuleInfo(
        id = "criticals",
        displayName = "Criticals",
        category = ModuleCategory.COMBAT
)
public final class Criticals extends Module {

    private static final float VANILLA_JUMP_MOTION = 0.42f;
    private static final int JUMP_CRIT_LEAD_TICKS = 4;
    private static final int VULCAN_297_JUMP_RECOVERY_TICKS = 8;
    private static final int VULCAN_297_LANDING_RECOVERY_TICKS = 2;
    private final Minecraft mc = Minecraft.getInstance();
    private final EnumValue<Mode> mode =
            enumMode(
                    "mode",
                    Mode.JUMP,
                    Mode.values()
            );
    private final EnumValue<SelectionMode> selectionMode =
            enumCommon(
                    "selection_mode",
                    "selection_mode",
                    CommonSettingSchemas.COMBAT_CRITICALS,
                    SelectionMode.SMART,
                    SelectionMode.values()
            );
    private final BooleanValue whenSprinting =
            boolCommon(
                    "when_sprinting",
                    "when_sprinting",
                    CommonSettingSchemas.COMBAT_WHEN_SPRINTING,
                    false
            );
    private final EnumValue<StopSprintingMode> stopSprintingMode =
            visibleWhen(enumCommon(
                    "stop_sprinting_mode",
                    "stop_sprinting_mode",
                    CommonSettingSchemas.COMBAT_STOP_SPRINTING,
                    StopSprintingMode.LEGIT,
                    StopSprintingMode.values()
            ), whenSprinting::get);
    private final NumberValue<Float> jumpHeight =
            visibleWhen(num(
                    "jump_height",
                    "jump_height",
                    0.42f,
                    0.1f,
                    0.42f
            ), () -> mode.get() == Mode.JUMP);
    private final NumberValue<Float> jumpRange =
            visibleWhen(numCommon(
                    "jump_range",
                    "range",
                    CommonSettingSchemas.COMBAT_RANGE,
                    4.0f,
                    1.0f,
                    6.0f
            ), () -> mode.get() == Mode.JUMP);
    private final BooleanValue optimizeForCooldown =
            visibleWhen(bool(
                    "optimize_for_cooldown",
                    "optimize_for_cooldown",
                    true
            ), () -> mode.get() == Mode.JUMP);
    private final BooleanValue canBeSeen =
            visibleWhen(bool(
                    "can_be_seen",
                    "can_be_seen",
                    true
            ), () -> mode.get() == Mode.JUMP);
    private final EnumValue<PacketMode> packetMode =
            visibleWhen(enumMode(
                    "packet_mode",
                    PacketMode.NO_CHEAT_PLUS,
                    PacketMode.values()
            ), () -> mode.get() == Mode.PACKET);
    private final EnumValue<PacketType> packetType =
            visibleWhen(enumMode(
                    "packet_type",
                    PacketType.FULL,
                    PacketType.values()
            ), () -> mode.get() == Mode.PACKET);
    private final NumberValue<Float> timerSpeed =
            visibleWhen(num(
                    "timer_speed",
                    "timer_speed",
                    0.8f,
                    0.1f,
                    1.0f
            ), () -> mode.get() == Mode.TIMER);
    private final NumberValue<Float> timerRange =
            visibleWhen(num(
                    "timer_range",
                    "timer_range",
                    4.0f,
                    0.0f,
                    10.0f
            ), () -> mode.get() == Mode.TIMER);
    private Mode killAuraModeOverride;
    private SelectionMode killAuraSelectionOverride;
    private boolean adjustNextJump;
    private int lastJumpInputAge = -1;
    private int lastPreparedAttackAge = -1;
    private int lastPreparedTargetId = Integer.MIN_VALUE;
    private int vulcan297JumpRecoveryTicks;
    private int vulcan297LandingRecoveryTicks;
    private boolean vulcan297WasAirborne;

    @Override
    public void onEnable() {
        adjustNextJump = false;
        lastJumpInputAge = -1;
        resetVulcan297State();
        resetPreparedAttack();
        Timer.clearExternalTickTimer();
    }

    @Override
    public void onDisable() {
        adjustNextJump = false;
        lastJumpInputAge = -1;
        resetVulcan297State();
        resetPreparedAttack();
        Timer.clearExternalTickTimer();
    }

    public void setKillAuraOverrides(Mode modeOverride, SelectionMode selectionOverride) {
        killAuraModeOverride = modeOverride;
        killAuraSelectionOverride = selectionOverride;
    }

    public void clearKillAuraOverrides() {
        killAuraModeOverride = null;
        killAuraSelectionOverride = null;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        if (getMode() == Mode.TIMER) {
            Entity target = getTargetInRange(timerRange.get());
            if (target != null && wouldDoCriticalHit(shouldAttemptCritWhileSprinting())) {
                Timer.setExternalTickTimer(timerSpeed.get());
            } else {
                Timer.clearExternalTickTimer();
            }
        } else {
            Timer.clearExternalTickTimer();
        }

        updateVulcan297State(currentPlayer());
    }

    @EventHandler
    private void onMovementInput(MovementInputEvent event) {
        if (!isEnabled() || getMode() != Mode.JUMP) return;

        LocalPlayer player = currentPlayer();
        if (player == null) return;
        Entity target = getTargetInRange();

        if (target != null && shouldAutoJumpCrit(target) && player.onGround()) {
            event.setJump(true);
            adjustNextJump = true;
        }

        if (event.isJump()) {
            lastJumpInputAge = player.tickCount;
        }
    }

    @EventHandler
    private void onPlayerJump(PlayerJumpEvent event) {
        if (isEnabled() && getMode() == Mode.VULCAN_297) {
            vulcan297JumpRecoveryTicks = VULCAN_297_JUMP_RECOVERY_TICKS;
            vulcan297LandingRecoveryTicks = VULCAN_297_LANDING_RECOVERY_TICKS;
            vulcan297WasAirborne = true;
            return;
        }

        if (!isEnabled() || getMode() != Mode.JUMP) return;

        if (adjustNextJump && Math.abs(event.getMotion() - VANILLA_JUMP_MOTION) < 1.0E-4f) {
            event.setMotion(jumpHeight.get());
        }
        adjustNextJump = false;
    }

    @EventHandler
    private void onSprintControl(SprintControlEvent event) {
        if (!shouldStopSprintingNow()) return;

        StopSprintingMode stopMode = stopSprintingMode.get();
        if (stopMode == StopSprintingMode.LEGIT) {
            if (event.getSource() == SprintControlEvent.Source.MOVEMENT_TICK
                    || event.getSource() == SprintControlEvent.Source.INPUT) {
                event.setSprint(false);
            }
        } else if (stopMode == StopSprintingMode.ON_NETWORK) {
            if (event.getSource() == SprintControlEvent.Source.NETWORK
                    || event.getSource() == SprintControlEvent.Source.INPUT) {
                event.setSprint(false);
            }
        }
    }

    public boolean isCriticalHit(Entity target) {
        return isCriticalHitInternal(target, false);
    }

    private boolean isCriticalHitInternal(Entity target, boolean ignoreEnabledState) {
        if (!ignoreEnabledState && !isEnabled()) return true;
        boolean ignoreSprint = shouldAttemptCritWhileSprinting();
        return switch (getSelectionMode()) {
            case IGNORE -> true;
            case SMART -> switch (getMode()) {
                case PACKET, NO_GROUND, VULCAN_297 -> canDoCriticalHit(true, ignoreSprint);
                case JUMP, TIMER -> !shouldWaitForCrit(target, true);
                case OFF -> true;
            };
            case ALWAYS -> switch (getMode()) {
                case PACKET, NO_GROUND, VULCAN_297 -> canDoCriticalHit(true, ignoreSprint);
                case JUMP, TIMER -> wouldDoCriticalHit(ignoreSprint);
                case OFF -> true;
            };
        };
    }

    public boolean shouldStopSprinting(ClickScheduler clicker, Entity target) {
        if (!shouldStopSprintingBase()) return false;
        if (getSelectionMode() == SelectionMode.IGNORE) return false;

        LocalPlayer player = currentPlayer();
        if (player == null) return false;
        if (target == null) return false;
        if (!canAttemptCriticalAttack(target)) return false;
        if (usesNaturalCriticalState() && player.onGround()) return false;

        return clicker != null && clicker.willClickAt(1);
    }

    public boolean shouldStopSprintingOnAttack(ClickScheduler clicker, Entity target) {
        return shouldStopSprinting(clicker, target)
                && stopSprintingMode.get() == StopSprintingMode.ON_ATTACK;
    }

    public boolean shouldAttack(Entity target) {
        return shouldAttack(null, target);
    }

    public boolean shouldAttack(ClickScheduler clicker, Entity target) {
        return shouldAttackInternal(clicker, target, false);
    }

    private boolean shouldAttackInternal(ClickScheduler clicker,
                                         Entity target,
                                         boolean ignoreEnabledState) {
        if (target == null) return false;
        LocalPlayer player = currentPlayer();
        if (player == null) return false;

        if (getMode() == Mode.JUMP && getSelectionMode() != SelectionMode.IGNORE && canUseJumpCriticalState(target)) {
            if (player.onGround()) return false;
            if (player.getDeltaMovement().y > 0.0) return false;
            if (clicker != null && !clicker.willClickAt(2) && player.fallDistance <= 0.0f) return false;
        }

        return isCriticalHitInternal(target, ignoreEnabledState);
    }

    /**
     * Runs attack gate with temporary KillAura-specific selection override.
     * Keeps Criticals internals (mode checks, jump state checks, click lookahead) intact.
     */
    public boolean shouldAttackForKillAura(ClickScheduler clicker,
                                           Entity target,
                                           SelectionMode selectionOverride,
                                           boolean ignoreSprintAfterReset) {
        if (target == null) return false;
        LocalPlayer player = currentPlayer();
        if (player == null) return false;

        SelectionMode selection = selectionOverride != null ? selectionOverride : selectionMode.get();
        if (selection == SelectionMode.IGNORE) {
            return true;
        }

        boolean ignoreSprint = ignoreSprintAfterReset || shouldAttemptCritWhileSprinting();
        if (canUseKillAuraJumpCriticalState(target)) {
            if (selection == SelectionMode.ALWAYS) {
                return isNaturalCriticalReady(ignoreSprint);
            }
            if (selection == SelectionMode.SMART
                    && player.onGround()
                    && hasJumpIntent(player)
                    && shouldForceJumpCrit(target)) {
                return false;
            }
            if (shouldHoldKillAuraForNaturalCrit(player, ignoreSprint)) {
                return isNaturalCriticalReady(ignoreSprint);
            }
        }

        return switch (selection) {
            case SMART -> !shouldWaitForCrit(target, true);
            case ALWAYS -> wouldDoCriticalHit(ignoreSprint);
            case IGNORE -> true;
        };
    }

    public boolean shouldControlTargetStrafeJump(Entity target) {
        if (!isEnabled() || target == null) {
            return false;
        }
        if (getSelectionMode() == SelectionMode.IGNORE) {
            return false;
        }
        return canUseJumpCriticalState(target);
    }

    public void beforeAttack(Minecraft mc, LocalPlayer player, Entity target) {
        if (mc == null || player == null || target == null || !isEnabled()) return;
        if (isAttackAlreadyPrepared(player, target)) return;
        markAttackPrepared(player, target);

        if (stopSprintingMode.get() == StopSprintingMode.ON_ATTACK
                && whenSprinting.get()
                && getSelectionMode() != SelectionMode.IGNORE
                && canAttemptCriticalAttack(target)) {
            AttackUtil.sendStopSprinting(mc, player);
        }

        if (getMode() == Mode.PACKET && canDoCriticalHit(true, shouldAttemptCritWhileSprinting())) {
            performPacketCritical(mc, player, target);
        } else if (getMode() == Mode.VULCAN_297
                && canDoCriticalHit(true, shouldAttemptCritWhileSprinting())
                && canPerformVulcan297Critical(player)) {
            performVulcan297Critical(mc, player);
        }
    }

    public boolean shouldStopSprinting() {
        return shouldStopSprintingBase();
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!isEnabled()) return;

        if (getMode() == Mode.NO_GROUND && event.getPacket() instanceof ServerboundMovePlayerPacket move) {
            ((ServerboundMovePlayerPacketAccessor) move).combatant$setOnGround(false);
        }
    }

    private boolean shouldStopSprintingBase() {
        if (!isEnabled() || !whenSprinting.get()) return false;
        if (stopSprintingMode.get() == StopSprintingMode.NONE) return false;
        if (getSelectionMode() == SelectionMode.IGNORE) return false;
        return canAttemptCriticalAttack(getTargetInRange());
    }

    private boolean shouldStopSprintingNow() {
        if (!shouldStopSprintingBase()) return false;

        Entity target = getTargetInRange();
        if (target == null) return false;

        KillAura killAura = Modules.get(KillAura.class);
        if (killAura != null && killAura.isEnabled()) {
            return shouldStopSprinting(killAura.getClickScheduler(), target);
        }

        return false;
    }

    public boolean allowsCriticalHit(boolean ignoreOnGround) {
        LocalPlayer player = currentPlayer();
        if (player == null) return false;

        Flight flight = Modules.get(Flight.class);
        if (flight != null && flight.isEnabled()) return false;
        if (player.isFallFlying()) return false;
        if (player.isInWater() || player.isUnderWater() || player.isInLava()) return false;
        if (player.isPassenger() || player.getVehicle() instanceof Boat) return false;
        if (player.onClimbable() || player.isNoGravity()) return false;
        if (player.getAbilities().flying) return false;
        if (player.isUsingItem()) return false;
        if (player.hasEffect(MobEffects.BLINDNESS)
                || player.hasEffect(MobEffects.LEVITATION)
                || player.hasEffect(MobEffects.SLOW_FALLING)) return false;
        if (!ignoreOnGround && player.onGround()) return false;
        AABB box = player.getBoundingBox();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minX = (int) Math.floor(box.minX);
        int maxX = (int) Math.floor(box.maxX);
        int minY = (int) Math.floor(box.minY);
        int maxY = (int) Math.floor(box.maxY);
        int minZ = (int) Math.floor(box.minZ);
        int maxZ = (int) Math.floor(box.maxZ);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    BlockState state = player.level().getBlockState(pos);
                    if (state.is(Blocks.COBWEB)) return false;
                    FluidState fluid = player.level().getFluidState(pos);
                    if (fluid.is(FluidTags.WATER) || fluid.is(FluidTags.LAVA)) return false;
                }
            }
        }

        return true;
    }

    public boolean canDoCriticalHit(boolean ignoreOnGround, boolean ignoreSprint) {
        LocalPlayer player = currentPlayer();
        if (player == null) return false;

        return allowsCriticalHit(ignoreOnGround)
                && player.getAttackStrengthScale(0.5f) > 0.9f
                && (!player.isSprinting() || ignoreSprint);
    }

    public boolean wouldDoCriticalHit(boolean ignoreSprint) {
        LocalPlayer player = currentPlayer();
        if (player == null) return false;

        return isNaturalCriticalReady(ignoreSprint);
    }

    private boolean isNaturalCriticalReady(boolean ignoreSprint) {
        LocalPlayer player = currentPlayer();
        if (player == null) return false;

        return canDoCriticalHit(false, ignoreSprint)
                && player.fallDistance > 0.0f
                && player.getDeltaMovement().y <= 0.0;
    }

    private boolean shouldHoldKillAuraForNaturalCrit(LocalPlayer player, boolean ignoreSprint) {
        if (player == null || player.onGround()) return false;
        return ignoreSprint || !player.isSprinting();
    }

    private boolean hasJumpIntent(LocalPlayer player) {
        if (player == null) return false;
        if (mc.options != null && mc.options.keyJump.isDown()) return true;
        return lastJumpInputAge >= 0 && player.tickCount - lastJumpInputAge <= 1;
    }

    public boolean shouldWaitForCrit(Entity target, boolean ignoreState) {
        LocalPlayer player = currentPlayer();
        if (player == null || target == null) return false;
        if (!ignoreState && (!isEnabled() || getMode() != Mode.JUMP)) return false;
        if (player.isFallFlying()) return false;
        if (!allowsCriticalHit(false) || player.getDeltaMovement().y < -0.08) return false;
        if (player.fallDistance > 0.0f) return false;
        if (player.distanceToSqr(target) > jumpRange.get() * jumpRange.get()) return false;

        return player.getDeltaMovement().y > 0.0
                && player.getAttackStrengthScale(0.5f) > 0.9f;
    }

    private boolean shouldWaitForJump() {
        LocalPlayer player = currentPlayer();
        if (player == null || !allowsCriticalHit(true) || getMode() != Mode.JUMP) return false;
        return player.getAttackStrengthScale(0.5f) < 0.9f;
    }

    private Entity getTargetInRange() {
        return getTargetInRange(jumpRange.get());
    }

    private Entity getTargetInRange(float rangeValue) {
        LocalPlayer player = currentPlayer();
        Entity target = getPreferredTarget();
        if (player == null || target == null || !target.isAlive()) return null;
        if (player.distanceToSqr(target) > rangeValue * rangeValue) return null;
        if (canBeSeen.get() && !player.hasLineOfSight(target)) return null;
        return target;
    }

    private LocalPlayer currentPlayer() {
        return mc != null ? mc.player : null;
    }

    private boolean usesNaturalCriticalState() {
        return getMode() == Mode.JUMP || getMode() == Mode.TIMER;
    }

    private boolean shouldForceJumpCrit(Entity target) {
        LocalPlayer player = currentPlayer();
        if (player == null || target == null) return false;
        if (getMode() != Mode.JUMP) return false;
        if (!allowsCriticalHit(true)) return false;
        if (player.distanceToSqr(target) > jumpRange.get() * jumpRange.get()) return false;
        if (canBeSeen.get() && !player.hasLineOfSight(target)) return false;
        return !optimizeForCooldown.get() || !shouldWaitForJump();
    }

    private boolean shouldAttemptCritWhileSprinting() {
        return isEnabled() && whenSprinting.get() && stopSprintingMode.get() == StopSprintingMode.NONE;
    }

    private boolean canAttemptCriticalAttack(Entity target) {
        if (target == null) return false;
        boolean ignoreSprint = shouldAttemptCritWhileSprinting();
        return switch (getMode()) {
            case PACKET, NO_GROUND, VULCAN_297 -> canDoCriticalHit(true, ignoreSprint);
            case JUMP, TIMER -> getTargetInRange() != null;
            case OFF -> false;
        };
    }

    private boolean canUseJumpCriticalState(Entity target) {
        LocalPlayer player = currentPlayer();
        if (player == null || target == null) return false;
        if (getMode() != Mode.JUMP) return false;
        if (!allowsCriticalHit(true)) return false;
        if (player.distanceToSqr(target) > jumpRange.get() * jumpRange.get()) return false;
        return !canBeSeen.get() || player.hasLineOfSight(target);
    }

    private boolean canUseKillAuraJumpCriticalState(Entity target) {
        LocalPlayer player = currentPlayer();
        if (player == null || target == null) return false;
        if (!allowsCriticalHit(true)) return false;
        if (player.distanceToSqr(target) > jumpRange.get() * jumpRange.get()) return false;
        return !canBeSeen.get() || player.hasLineOfSight(target);
    }

    private boolean shouldAutoJumpCrit(Entity target) {
        if (!shouldForceJumpCrit(target)) return false;

        KillAura killAura = Modules.get(KillAura.class);
        if (killAura == null || !killAura.isEnabled()) return true;
        return killAura.willAttackInTicks(JUMP_CRIT_LEAD_TICKS);
    }

    private Mode getMode() {
        return killAuraModeOverride != null ? killAuraModeOverride : mode.get();
    }

    private SelectionMode getSelectionMode() {
        return killAuraSelectionOverride != null ? killAuraSelectionOverride : selectionMode.get();
    }

    private Entity getPreferredTarget() {
        KillAura killAura = Modules.get(KillAura.class);
        if (killAura != null && killAura.isEnabled() && killAura.getCurrentTarget() != null) {
            return killAura.getCurrentTarget();
        }
        return TargetManager.getTarget();
    }

    private void performPacketCritical(Minecraft mc, LocalPlayer player, Entity target) {
        if (mc.getConnection() == null) return;

        switch (packetMode.get()) {
            case VANILLA -> {
                sendCriticalPacket(player, 0.2, false);
                sendCriticalPacket(player, 0.01, false);
            }
            case NO_CHEAT_PLUS -> {
                sendCriticalPacket(player, 0.11, false);
                sendCriticalPacket(player, 0.1100013579, false);
                sendCriticalPacket(player, 0.0000013579, false);
            }
            case FALLING -> {
                sendCriticalPacket(player, 0.0625, false);
                sendCriticalPacket(player, 0.0625013579, false);
                sendCriticalPacket(player, 0.0000013579, false);
            }
            case LOW -> {
                sendCriticalPacket(player, 1.0E-9, false);
                sendCriticalPacket(player, 0.0, false);
            }
            case DOWN -> sendCriticalPacket(player, -1.0E-9, false);
            case GRIM -> {
                if (!player.onGround()) {
                    sendCriticalPacket(player, -1.0E-6, false);
                }
            }
            case BLOCKSMC -> {
                if (player.tickCount % 4 == 0) {
                    sendCriticalPacket(player, 0.0011, true);
                    sendCriticalPacket(player, 0.0, false);
                }
            }
        }
    }

    private void performVulcan297Critical(Minecraft mc, LocalPlayer player) {
        if (mc.getConnection() == null) return;

        sendCriticalPacket(player, 0.021, false, PacketType.FULL);
        sendCriticalPacket(player, 0.011, false, PacketType.FULL);
    }

    private void updateVulcan297State(LocalPlayer player) {
        if (getMode() != Mode.VULCAN_297) {
            resetVulcan297State();
            return;
        }

        if (vulcan297JumpRecoveryTicks > 0) {
            vulcan297JumpRecoveryTicks--;
        }

        if (player == null) {
            resetVulcan297State();
            return;
        }

        if (!player.onGround()) {
            vulcan297WasAirborne = true;
            vulcan297LandingRecoveryTicks = VULCAN_297_LANDING_RECOVERY_TICKS;
            return;
        }

        if (vulcan297WasAirborne && vulcan297LandingRecoveryTicks > 0) {
            vulcan297LandingRecoveryTicks--;
            return;
        }

        vulcan297WasAirborne = false;
        vulcan297LandingRecoveryTicks = 0;
    }

    private boolean canPerformVulcan297Critical(LocalPlayer player) {
        if (player == null || !player.onGround()) return false;
        if (vulcan297JumpRecoveryTicks > 0) return false;
        return vulcan297LandingRecoveryTicks <= 0;
    }

    private void sendCriticalPacket(LocalPlayer player, double yOffset, boolean onGround) {
        sendCriticalPacket(player, yOffset, onGround, packetType.get());
    }

    private void sendCriticalPacket(LocalPlayer player, double yOffset, boolean onGround, PacketType type) {
        if (mc == null || mc.getConnection() == null || player == null) return;

        Packet<?> packet = switch (type) {
            case POSITION -> new ServerboundMovePlayerPacket.Pos(
                    player.getX(),
                    player.getY() + yOffset,
                    player.getZ(),
                    onGround,
                    player.horizontalCollision
            );
            case FULL -> new ServerboundMovePlayerPacket.PosRot(
                    player.getX(),
                    player.getY() + yOffset,
                    player.getZ(),
                    player.getYRot(),
                    player.getXRot(),
                    onGround,
                    player.horizontalCollision
            );
        };

        mc.getConnection().send(packet);
    }

    private boolean isAttackAlreadyPrepared(LocalPlayer player, Entity target) {
        return player.tickCount == lastPreparedAttackAge && target.getId() == lastPreparedTargetId;
    }

    private void markAttackPrepared(LocalPlayer player, Entity target) {
        lastPreparedAttackAge = player.tickCount;
        lastPreparedTargetId = target.getId();
    }

    private void resetPreparedAttack() {
        lastPreparedAttackAge = -1;
        lastPreparedTargetId = Integer.MIN_VALUE;
    }

    private void resetVulcan297State() {
        vulcan297JumpRecoveryTicks = 0;
        vulcan297LandingRecoveryTicks = 0;
        vulcan297WasAirborne = false;
    }

    public enum Mode {
        OFF,
        JUMP,
        PACKET,
        NO_GROUND,
        TIMER,
        VULCAN_297
    }

    public enum SelectionMode {
        SMART,
        IGNORE,
        ALWAYS
    }

    public enum StopSprintingMode {
        NONE,
        LEGIT,
        ON_NETWORK,
        ON_ATTACK
    }

    public enum PacketMode {
        VANILLA,
        NO_CHEAT_PLUS,
        FALLING,
        LOW,
        DOWN,
        GRIM,
        BLOCKSMC
    }

    public enum PacketType {
        POSITION,
        FULL
    }

}
