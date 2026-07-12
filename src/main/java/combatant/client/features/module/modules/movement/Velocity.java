/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on LiquidBounce
 * (https://github.com/CCBlueX/LiquidBounce).
 * Copyright (c) 2015-2026 CCBlueX.
 *
 * LiquidBounce portions are licensed under GPLv3-or-later.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.features.module.modules.movement;

import combatant.client.events.impl.*;
import combatant.client.util.screen.ClientScreen;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.Events;
import combatant.client.events.impl.*;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.mixins.accessors.ClientboundExplodePacketAccessor;
import combatant.client.mixins.accessors.ClientboundSetEntityMotionPacketAccessor;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.network.BlinkManager;
import combatant.client.util.network.TransferOrigin;
import combatant.client.util.player.MovementUtil;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Velocity modes ported from LiquidBounce's ModuleVelocity.
 */
//todo Description
@ModuleInfo(
        id = "velocity",
        displayName = "Velocity",
        category = ModuleCategory.MOVEMENT
)
public final class Velocity extends Module {

    private static final double VULCAN_297_SAFE_HORIZONTAL_RATIO = 0.25;
    private static final double VULCAN_297_TINY_HORIZONTAL = 0.01;
    private static final double VULCAN_297_JUMP_LIKE_VERTICAL = 0.41999998688697815;
    private static final double VULCAN_297_PROTECTED_VERTICAL = 0.6;
    private static final int VULCAN_297_WIND_BURST_PENDING_TICKS = 8;
    private static final int VULCAN_297_WIND_BURST_PROTECT_TICKS = 12;
    private static final String VULCAN_EXEMPT_LIQUID = "liquid";
    private static final String VULCAN_EXEMPT_FROZEN = "frozen";
    private static final String VULCAN_EXEMPT_FALL_DAMAGE = "fall_damage";
    private static final String VULCAN_EXEMPT_WEB = "web";
    private static final String VULCAN_EXEMPT_VOID = "void";
    private static final String VULCAN_EXEMPT_SLOW_FALLING = "slow_falling";
    private static final String VULCAN_EXEMPT_TRAPDOOR = "trapdoor";
    private static final String VULCAN_EXEMPT_SOUL_SAND = "soul_sand";
    private static final String VULCAN_EXEMPT_SLIME = "slime";
    private static final String VULCAN_EXEMPT_FLIGHT = "flight";
    private static final String VULCAN_EXEMPT_VEHICLE = "vehicle";
    private static final String VULCAN_EXEMPT_ELYTRA = "elytra";
    private static final String VULCAN_EXEMPT_FENCE = "fence";
    private static final String VULCAN_EXEMPT_WALL = "wall";
    private static final String VULCAN_EXEMPT_SWEET_BERRIES = "sweet_berries";
    private static final String VULCAN_EXEMPT_NETHERITE_ARMOR = "netherite_armor";
    private final Minecraft mc = Minecraft.getInstance();
    private final EnumValue<Mode> mode =
            enumCommon("velocityMode", "velocity_mode", CommonSettingSchemas.ANTICHEAT_MODE, Mode.MODIFY, Mode.values());
    private final NumberValue<Integer> delay = visibleWhen(num("velocityDelay", "delay", 0, 0, 40), this::supportsIncomingVelocity);
    private final NumberValue<Integer> pauseOnFlag = num("velocityPauseOnFlag", "pause_on_flag", 0, 0, 20);
    private final NumberValue<Float> modifyHorizontal = visibleWhen(num("velocityModifyHorizontal", "modify_horizontal", 0.0f, -1.0f, 1.0f), () -> mode.get() == Mode.MODIFY);
    private final NumberValue<Float> modifyVertical = visibleWhen(num("velocityModifyVertical", "modify_vertical", 0.0f, -1.0f, 1.0f), () -> mode.get() == Mode.MODIFY);
    private final NumberValue<Float> modifyMotionHorizontal = visibleWhen(num("velocityModifyMotionHorizontal", "modify_motion_horizontal", 0.0f, 0.0f, 1.0f), () -> mode.get() == Mode.MODIFY);
    private final NumberValue<Float> modifyMotionVertical = visibleWhen(num("velocityModifyMotionVertical", "modify_motion_vertical", 0.0f, 0.0f, 1.0f), () -> mode.get() == Mode.MODIFY);
    private final NumberValue<Integer> modifyChance = visibleWhen(num("velocityModifyChance", "modify_chance", 100, 0, 100), () -> mode.get() == Mode.MODIFY);
    private final EnumValue<Filter> modifyFilter = visibleWhen(enumSetting("velocityModifyFilter", "modify_filter", Filter.ALWAYS, Filter.values()), () -> mode.get() == Mode.MODIFY);
    private final BooleanValue modifyOnlyMove = visibleWhen(bool("velocityModifyOnlyMove", "modify_only_move", false), () -> mode.get() == Mode.MODIFY);
    private final NumberValue<Integer> modifyTransactionBuffer = visibleWhen(num("velocityModifyTransactionBuffer", "modify_transaction_buffer", 0, 0, 3), () -> mode.get() == Mode.MODIFY);
    private final NumberValue<Integer> reversalDelay = visibleWhen(num("velocityReversalDelay", "reversal_delay", 2, 1, 5), () -> mode.get() == Mode.REVERSAL);
    private final NumberValue<Float> reversalXModifier = visibleWhen(num("velocityReversalXModifier", "reversal_x_modifier", 0.5f, 0.0f, 1.0f), () -> mode.get() == Mode.REVERSAL);
    private final NumberValue<Float> reversalZModifier = visibleWhen(num("velocityReversalZModifier", "reversal_z_modifier", 0.5f, 0.0f, 1.0f), () -> mode.get() == Mode.REVERSAL);
    private final BooleanValue reversalOnlyMoving = visibleWhen(bool("velocityReversalOnlyMoving", "reversal_only_moving", false), () -> mode.get() == Mode.REVERSAL);
    private final NumberValue<Integer> strafeDelay = visibleWhen(num("velocityStrafeDelay", "strafe_delay", 2, 0, 10), () -> mode.get() == Mode.STRAFE);
    private final NumberValue<Float> strafeStrength = visibleWhen(num("velocityStrafeStrength", "strafe_strength", 1.0f, 0.1f, 2.0f), () -> mode.get() == Mode.STRAFE);
    private final BooleanValue strafeUntilGround = visibleWhen(bool("velocityStrafeUntilGround", "strafe_until_ground", false), () -> mode.get() == Mode.STRAFE);
    private final NumberValue<Float> jumpResetChance = visibleWhen(num("velocityJumpResetChance", "jump_reset_chance", 100.0f, 0.0f, 100.0f), () -> mode.get() == Mode.JUMP_RESET);
    private final BooleanValue jumpResetByHits = visibleWhen(bool("velocityJumpResetByHits", "jump_reset_by_hits", false), () -> mode.get() == Mode.JUMP_RESET);
    private final NumberValue<Integer> jumpResetHits = visibleWhen(num("velocityJumpResetHits", "jump_reset_hits", 2, 0, 10), () -> mode.get() == Mode.JUMP_RESET);
    private final BooleanValue jumpResetByDelay = visibleWhen(bool("velocityJumpResetByDelay", "jump_reset_by_delay", true), () -> mode.get() == Mode.JUMP_RESET);
    private final NumberValue<Integer> jumpResetTicks = visibleWhen(num("velocityJumpResetTicks", "jump_reset_ticks", 2, 0, 20), () -> mode.get() == Mode.JUMP_RESET);
    private final NumberValue<Integer> lagTime = visibleWhen(num("velocityLagTime", "lag_time", 5, 1, 20), () -> mode.get() == Mode.LAG);
    private final BooleanValue lagJumpReset = visibleWhen(bool("velocityLagJumpReset", "lag_jump_reset", false), () -> mode.get() == Mode.LAG);
    private final NumberValue<Float> dexlandHReduce = visibleWhen(num("velocityDexlandHReduce", "dexland_h_reduce", 0.3f, 0.0f, 1.0f), () -> mode.get() == Mode.DEXLAND);
    private final NumberValue<Integer> dexlandAttacksToWork = visibleWhen(num("velocityDexlandAttacksToWork", "dexland_attacks_to_work", 4, 1, 10), () -> mode.get() == Mode.DEXLAND);
    private final BooleanValue grim2344AlternativeBypass = visibleWhen(bool("velocityGrim2344AlternativeBypass", "grim2344_alternative_bypass", true), () -> mode.get() == Mode.GRIM_2344);
    private final NumberValue<Float> aacReduce = visibleWhen(num("velocityAACReduce", "aac_reduce", 0.62f, 0.0f, 1.0f), () -> mode.get() == Mode.AAC_442);
    private final BooleanValue intaveReduceOnAttack = visibleWhen(bool("velocityIntaveReduceOnAttack", "intave_reduce_on_attack", true), () -> mode.get() == Mode.INTAVE);
    private final NumberValue<Float> intaveReduceFactor = visibleWhen(num("velocityIntaveReduceFactor", "intave_reduce_factor", 0.6f, 0.6f, 1.0f), () -> mode.get() == Mode.INTAVE);
    private final NumberValue<Integer> intaveHurtTimeMin = visibleWhen(num("velocityIntaveHurtTimeMin", "intave_hurt_time_min", 5, 1, 10), () -> mode.get() == Mode.INTAVE);
    private final NumberValue<Integer> intaveHurtTimeMax = visibleWhen(num("velocityIntaveHurtTimeMax", "intave_hurt_time_max", 7, 1, 10), () -> mode.get() == Mode.INTAVE);
    private final NumberValue<Integer> intaveLastAttackTime = visibleWhen(num("velocityIntaveLastAttackTime", "intave_last_attack_time", 2000, 1, 10000), () -> mode.get() == Mode.INTAVE);
    private final BooleanValue intaveJumpReset = visibleWhen(bool("velocityIntaveJumpReset", "intave_jump_reset", true), () -> mode.get() == Mode.INTAVE);
    private final NumberValue<Float> intaveJumpChance = visibleWhen(num("velocityIntaveJumpChance", "intave_jump_chance", 50.0f, 0.0f, 100.0f), () -> mode.get() == Mode.INTAVE);
    private final BooleanValue intaveRandomize = visibleWhen(bool("velocityIntaveRandomize", "intave_randomize", false), () -> mode.get() == Mode.INTAVE);
    private final NumberValue<Integer> intaveRandomDelayTicks = visibleWhen(num("velocityIntaveRandomDelayTicks", "intave_random_delay_ticks", 5, 0, 10), () -> mode.get() == Mode.INTAVE);
    private final NumberValue<Float> vulcan297ExemptRatio = visibleWhen(num("velocityVulcan297ExemptRatio", "vulcan297_exempt_ratio", 0.0f, 0.0f, 1.0f), this::isVulcan297Mode);
    private final BooleanMapValue vulcan297Exempts = visibleWhen(group("velocityVulcan297Exempts", "vulcan297_exempts", vulcan297ExemptDefaults()), this::isVulcan297Mode);
    private final Queue<DelayedPacket> delayedPackets = new ArrayDeque<>();
    private int pause;
    private int transactionBuffer;
    private boolean reversalHandlingVelocity;
    private int reversalVelocityTicks;
    private int strafeTicks = -1;
    private boolean strafeApplyUntilGround;
    private int jumpLimitUntilJump;
    private boolean jumpResetFallDamage;
    private boolean lagShouldLag;
    private int lagTicks;
    private int lagShouldJumpTicks;
    private boolean hypixelAbsorbedVelocity;
    private long dexlandLastAttackTime;
    private int dexlandCount;
    private boolean grim2371CancelNextVelocity;
    private boolean grim2371Delay;
    private boolean grim2371NeedClick;
    private boolean grim2371WaitForPing;
    private boolean grim2371WaitForUpdate;
    private boolean grim2371ShouldSkip;
    private BlockHitResult grim2371HitResult;
    private int grim2371FreezeTicks;
    private boolean grim2344CanCancel;
    private long intaveLastAttack;
    private boolean intaveFallDamage;
    private int intaveCurrentDelay;
    private int intaveDelayCounter;
    private int vulcan297WindBurstPendingTicks;
    private int vulcan297WindBurstProtectTicks;

    private static Map<String, Boolean> vulcan297ExemptDefaults() {
        Map<String, Boolean> defaults = new LinkedHashMap<>();
        defaults.put(VULCAN_EXEMPT_LIQUID, true);
        defaults.put(VULCAN_EXEMPT_FROZEN, true);
        defaults.put(VULCAN_EXEMPT_FALL_DAMAGE, true);
        defaults.put(VULCAN_EXEMPT_WEB, true);
        defaults.put(VULCAN_EXEMPT_VOID, true);
        defaults.put(VULCAN_EXEMPT_SLOW_FALLING, true);
        defaults.put(VULCAN_EXEMPT_TRAPDOOR, true);
        defaults.put(VULCAN_EXEMPT_SOUL_SAND, true);
        defaults.put(VULCAN_EXEMPT_SLIME, true);
        defaults.put(VULCAN_EXEMPT_FLIGHT, true);
        defaults.put(VULCAN_EXEMPT_VEHICLE, true);
        defaults.put(VULCAN_EXEMPT_ELYTRA, true);
        defaults.put(VULCAN_EXEMPT_FENCE, true);
        defaults.put(VULCAN_EXEMPT_WALL, true);
        defaults.put(VULCAN_EXEMPT_SWEET_BERRIES, true);
        defaults.put(VULCAN_EXEMPT_NETHERITE_ARMOR, true);
        return defaults;
    }

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        BlinkManager.INSTANCE.flush(TransferOrigin.INCOMING);
        resetState();
    }

    @EventHandler(priority = 2)
    private void onPacket(PacketEvent event) {
        if (!isEnabled() || player() == null) return;

        Packet<?> packet = event.getPacket();
        if (event.isOriginal()) {
            if (packet instanceof ClientboundPlayerPositionPacket) {
                pause = pauseOnFlag.get();
                return;
            }
            if (pause > 0) return;
            if (event.getOrigin() == TransferOrigin.INCOMING
                    && supportsIncomingVelocity()
                    && isLocalPlayerVelocity(packet)
                    && delay.get() > 0) {
                event.cancel();
                delayedPackets.add(new DelayedPacket(packet, delay.get()));
                return;
            }
        }

        switch (mode.get()) {
            case MODIFY -> handleModify(event, packet);
            case REVERSAL -> handleReversal(event, packet);
            case STRAFE -> handleStrafePacket(event, packet);
            case JUMP_RESET -> handleJumpResetPacket(packet);
            case LAG -> handleLagPacket(packet);
            case HYPIXEL -> handleHypixel(event, packet);
            case BLOCKSMC -> handleBlocksMc(event, packet);
            case GRIM_2371 -> handleGrim2371Packet(event, packet);
            case GRIM_2344 -> handleGrim2344(event, packet);
            case INTAVE -> handleIntavePacket(packet);
            case VULCAN_297 -> handleVulcan297(event, packet);
            default -> {
            }
        }

    }

    @EventHandler
    private void onBlinkPacket(BlinkPacketEvent event) {
        if (!isEnabled() || player() == null) return;

        if (mode.get() == Mode.LAG
                && lagShouldLag
                && event.getOrigin() == TransferOrigin.INCOMING
                && !(event.getPacket() instanceof ClientboundPingPacket)) {
            event.setAction(BlinkManager.Action.QUEUE);
            return;
        }

        if (mode.get() == Mode.GRIM_2371
                && !grim2371WaitForUpdate
                && grim2371Delay
                && event.getOrigin() == TransferOrigin.INCOMING) {
            event.setAction(BlinkManager.Action.QUEUE);
        }
    }

    @EventHandler
    private void onTick(GameTickEvent event) {
        if (!isEnabled()) return;

        if (pause > 0) pause--;
        processDelayedPackets();

        LocalPlayer player = player();
        if (player == null) return;

        switch (mode.get()) {
            case REVERSAL -> tickReversal(player);
            case STRAFE -> tickStrafe(player);
            case LAG -> tickLag(player);
            case HYPIXEL -> {
                if (player.onGround()) hypixelAbsorbedVelocity = false;
            }
            case HYLEX -> tickHylex(player);
            case GRIM_2371 -> tickGrim2371(player);
            case AAC_442 -> {
                if (player.hurtTime > 0 && !player.onGround()) {
                    multiplyHorizontal(player, aacReduce.get());
                }
            }
            default -> {
            }
        }

        if (vulcan297WindBurstProtectTicks > 0) {
            vulcan297WindBurstProtectTicks--;
        }
        if (vulcan297WindBurstPendingTicks > 0) {
            vulcan297WindBurstPendingTicks--;
        }
    }

    @EventHandler
    private void onMovementInput(MovementInputEvent event) {
        if (!isEnabled() || player() == null) return;

        if (mode.get() == Mode.JUMP_RESET) {
            handleJumpResetInput(event, player());
        } else if (mode.get() == Mode.LAG) {
            if (lagJumpReset.get() && lagShouldJumpTicks > 0 && player().onGround() && player().isSprinting()) {
                event.setJump(true);
                lagShouldJumpTicks = 0;
            }
        } else if (mode.get() == Mode.INTAVE) {
            handleIntaveJumpReset(event, player());
        }
    }

    @EventHandler
    private void onMove(PlayerMoveEvent event) {
        if (!isEnabled() || mode.get() != Mode.STRAFE || !strafeApplyUntilGround || player() == null) return;

        if (player().onGround()) {
            strafeApplyUntilGround = false;
            return;
        }

        event.setMovement(withStrafe(event.getMovement(), strafeStrength.get()));
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (!isEnabled() || player() == null) return;

        if (mode.get() == Mode.DEXLAND) {
            long now = System.currentTimeMillis();
            if (player().hurtTime > 0
                    && ++dexlandCount % dexlandAttacksToWork.get() == 0
                    && now - dexlandLastAttackTime <= 8000L) {
                multiplyHorizontal(player(), dexlandHReduce.get());
            }
            dexlandLastAttackTime = now;
        } else if (mode.get() == Mode.HYLEX) {
            handleHylexAttack(player());
        } else if (mode.get() == Mode.INTAVE && intaveReduceOnAttack.get()) {
            long now = System.currentTimeMillis();
            int min = Math.min(intaveHurtTimeMin.get(), intaveHurtTimeMax.get());
            int max = Math.max(intaveHurtTimeMin.get(), intaveHurtTimeMax.get());
            if (player().hurtTime >= min && player().hurtTime <= max && now - intaveLastAttack <= intaveLastAttackTime.get()) {
                multiplyHorizontal(player(), intaveReduceFactor.get());
            }
            intaveLastAttack = now;
        } else if (mode.get() == Mode.VULCAN_297 && isVulcan297WindBurstMaceAttack(event, player())) {
            armVulcan297MaceVelocityWindow();
        }
    }

    private void handleModify(PacketEvent event, Packet<?> packet) {
        if (packet instanceof ClientboundSetEntityMotionPacket velocity && velocity.id() == player().getId()) {
            if (!shouldModifyVelocity()) return;

            if (modifyHorizontal.get() == 0.0f && modifyVertical.get() == 0.0f) {
                event.cancel();
                return;
            }

            Vec3 packetVelocity = velocity.movement();
            Vec3 currentVelocity = player().getDeltaMovement();
            double x = modifyHorizontal.get() != 0.0f
                    ? packetVelocity.x * modifyHorizontal.get()
                    : currentVelocity.x * modifyMotionHorizontal.get();
            double y = modifyVertical.get() != 0.0f
                    ? packetVelocity.y * modifyVertical.get()
                    : currentVelocity.y * modifyMotionVertical.get();
            double z = modifyHorizontal.get() != 0.0f
                    ? packetVelocity.z * modifyHorizontal.get()
                    : currentVelocity.z * modifyMotionHorizontal.get();
            ((ClientboundSetEntityMotionPacketAccessor) (Object) velocity).combatant$setVelocity(new Vec3(x, y, z));
            transactionBuffer += modifyTransactionBuffer.get();
            return;
        }

        if (packet instanceof ClientboundExplodePacket explosion && explosion.playerKnockback().isPresent()) {
            if (!shouldModifyVelocity()) return;

            Vec3 knockback = explosion.playerKnockback().get();
            ((ClientboundExplodePacketAccessor) (Object) explosion).combatant$setPlayerKnockback(Optional.of(new Vec3(
                    knockback.x * modifyHorizontal.get(),
                    knockback.y * modifyVertical.get(),
                    knockback.z * modifyHorizontal.get()
            )));
            transactionBuffer += modifyTransactionBuffer.get();
            return;
        }

        if (packet instanceof ServerboundPongPacket && transactionBuffer > 0) {
            event.cancel();
            transactionBuffer--;
        }
    }

    private boolean shouldModifyVelocity() {
        if (modifyChance.get() != 100 && ThreadLocalRandom.current().nextInt(100) > modifyChance.get()) return false;
        if (modifyOnlyMove.get() && !isMoving()) return false;

        return switch (modifyFilter.get()) {
            case ALWAYS -> true;
            case ON_GROUND -> player().onGround();
            case IN_AIR -> !player().onGround();
        };
    }

    private void handleReversal(PacketEvent event, Packet<?> packet) {
        if (!isLocalPlayerVelocity(packet)) return;
        if (reversalOnlyMoving.get() && !isMoving()) return;

        reversalVelocityTicks = 0;
        reversalHandlingVelocity = true;
    }

    private void tickReversal(LocalPlayer player) {
        if (!reversalHandlingVelocity) return;

        Vec3 velocity = player.getDeltaMovement();
        if (velocity.lengthSqr() == 0.0) {
            reversalHandlingVelocity = false;
            reversalVelocityTicks = 0;
            return;
        }

        if (reversalVelocityTicks++ >= reversalDelay.get()) {
            player.setDeltaMovement(
                    velocity.x * -reversalXModifier.get(),
                    velocity.y,
                    velocity.z * -reversalZModifier.get()
            );
            reversalHandlingVelocity = false;
            reversalVelocityTicks = 0;
        }
    }

    private void handleStrafePacket(PacketEvent event, Packet<?> packet) {
        if (!isLocalPlayerVelocity(packet)) return;

        strafeTicks = strafeDelay.get();
        if (strafeTicks <= 0) {
            applyStrafe(player());
        }
    }

    private void tickStrafe(LocalPlayer player) {
        if (strafeTicks < 0) return;

        if (strafeTicks-- <= 0) {
            applyStrafe(player);
            strafeTicks = -1;
        }
    }

    private void applyStrafe(LocalPlayer player) {
        if (player == null) return;

        player.setDeltaMovement(withStrafe(player.getDeltaMovement(), strafeStrength.get()));
        if (strafeUntilGround.get()) {
            strafeApplyUntilGround = true;
        }
    }

    private Vec3 withStrafe(Vec3 currentVelocity, double strength) {
        return MovementUtil.withStrafe(
                currentVelocity,
                movementInput(),
                movementYaw(),
                horizontalSpeed(currentVelocity) * strength,
                1.0
        );
    }

    private void handleJumpResetPacket(Packet<?> packet) {
        if (packet instanceof ClientboundSetEntityMotionPacket(int id, Vec3 v) && id == player().getId()) {
            jumpResetFallDamage = v.x == 0.0 && v.z == 0.0 && v.y < 0.0;
        }
    }

    private void handleJumpResetInput(MovementInputEvent event, LocalPlayer player) {
        if (player.hurtTime != 9
                || !player.onGround()
                || !player.isSprinting()
                || jumpResetFallDamage
                || !jumpResetCooldownOver()
                || !rollChance(jumpResetChance.get())) {
            updateJumpResetLimit(player);
            return;
        }

        event.setJump(true);
        jumpLimitUntilJump = 0;
    }

    private boolean jumpResetCooldownOver() {
        if (jumpResetByHits.get()) return jumpLimitUntilJump >= jumpResetHits.get();
        if (jumpResetByDelay.get()) return jumpLimitUntilJump >= jumpResetTicks.get();
        return true;
    }

    private void updateJumpResetLimit(LocalPlayer player) {
        if (jumpResetByHits.get()) {
            if (player.hurtTime == 9) jumpLimitUntilJump++;
        } else {
            jumpLimitUntilJump++;
        }
    }

    private void handleLagPacket(Packet<?> packet) {
        if (packet instanceof ClientboundSetEntityMotionPacket velocity && velocity.id() == player().getId()) {
            lagShouldLag = true;
            lagTicks = lagTime.get();
        }
    }

    private void tickLag(LocalPlayer player) {
        if (lagShouldLag && --lagTicks <= 0) {
            lagShouldLag = false;
            lagTicks = 0;
            BlinkManager.INSTANCE.flush(TransferOrigin.INCOMING);
            lagShouldJumpTicks = 2;
        }
        if (lagShouldJumpTicks > 0) {
            lagShouldJumpTicks--;
        }
    }

    private void handleHypixel(PacketEvent event, Packet<?> packet) {
        if (!(packet instanceof ClientboundSetEntityMotionPacket velocity) || velocity.id() != player().getId()) return;

        if (!player().onGround() && !hypixelAbsorbedVelocity) {
            event.cancel();
            hypixelAbsorbedVelocity = true;
            return;
        }

        Vec3 current = player().getDeltaMovement();
        Vec3 packetVelocity = velocity.movement();
        ((ClientboundSetEntityMotionPacketAccessor) (Object) velocity).combatant$setVelocity(new Vec3(current.x, packetVelocity.y, current.z));
    }

    private void handleBlocksMc(PacketEvent event, Packet<?> packet) {
        if (!(packet instanceof ClientboundSetEntityMotionPacket velocity) || velocity.id() != player().getId()) return;

        event.cancel();
        sendSneaking(true);
        sendSneaking(false);
    }

    private void handleHylexAttack(LocalPlayer player) {
        if (!isMoving() || !player.isSprinting()) return;

        switch (player.hurtTime) {
            case 9 -> multiplyHorizontal(player, 0.8);
            case 8 -> multiplyHorizontal(player, 0.11);
            case 7 -> multiplyHorizontal(player, 0.4);
            case 4 -> multiplyHorizontal(player, 0.37);
            default -> {
            }
        }
    }

    private void tickHylex(LocalPlayer player) {
        if (player.hurtTime > 5 && player.onGround()) {
            player.jumpFromGround();
        }
    }

    private void handleGrim2371Packet(PacketEvent event, Packet<?> packet) {
        if (packet instanceof ServerboundInteractPacket || packet instanceof ServerboundUseItemOnPacket) {
            grim2371ShouldSkip = true;
        } else if (packet instanceof ServerboundMovePlayerPacket move && move.hasPosition() && grim2371WaitForUpdate) {
            event.cancel();
            return;
        } else if (packet instanceof ServerboundPongPacket && grim2371WaitForPing) {
            grim2371WaitForUpdate = false;
            grim2371WaitForPing = false;
        }

        if (packet instanceof ClientboundBlockUpdatePacket block
                && grim2371WaitForUpdate
                && block.getPos().equals(player().blockPosition())) {
            grim2371WaitForPing = true;
            grim2371NeedClick = false;
        }

        if (event.isCancelled() || grim2371WaitForUpdate || grim2371Delay) return;

        if (isLocalPlayerDamage(packet)) {
            grim2371CancelNextVelocity = true;
        } else if (grim2371CancelNextVelocity && isLocalPlayerVelocity(packet)) {
            event.cancel();
            grim2371Delay = true;
            grim2371CancelNextVelocity = false;
            grim2371NeedClick = true;
        }
    }

    private void tickGrim2371(LocalPlayer player) {
        if (grim2371NeedClick && !grim2371ShouldSkip && !player.isUsingItem()) {
            grim2371HitResult = traceDownToPlayerBlock(player);
        }

        if (grim2371HitResult != null) {
            grim2371Delay = false;
            BlinkManager.INSTANCE.flush(TransferOrigin.INCOMING);

            if (mc.gameMode != null) {
                InteractionResult result = mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, grim2371HitResult);
                if (result.consumesAction()) {
                    player.swing(InteractionHand.MAIN_HAND);
                }
            }

            sendGrim2371Rotation(player);
            grim2371FreezeTicks = 0;
            grim2371WaitForUpdate = true;
            grim2371HitResult = null;
            grim2371NeedClick = false;
        }

        if (grim2371WaitForUpdate && ++grim2371FreezeTicks > 20) {
            grim2371WaitForUpdate = false;
            grim2371WaitForPing = false;
            grim2371NeedClick = false;
        }

        grim2371ShouldSkip = false;
    }

    private void handleGrim2344(PacketEvent event, Packet<?> packet) {
        if (isLocalPlayerDamage(packet)) {
            grim2344CanCancel = true;
        }

        if (isLocalPlayerVelocity(packet) && grim2344CanCancel) {
            event.cancel();
            int repeats = grim2344AlternativeBypass.get() ? 4 : 1;
            for (int i = 0; i < repeats; i++) {
                sendFullMovePacket(player());
            }
            sendStopDestroyBlock(player());
            grim2344CanCancel = false;
        }
    }

    private void handleIntavePacket(Packet<?> packet) {
        if (packet instanceof ClientboundSetEntityMotionPacket(int id, Vec3 v) && id == player().getId()) {
            intaveFallDamage = v.x == 0.0 && v.z == 0.0 && v.y < 0.0;
        }
    }

    private void handleIntaveJumpReset(MovementInputEvent event, LocalPlayer player) {
        if (!intaveJumpReset.get()) return;

        boolean shouldJump = rollChance(intaveJumpChance.get()) && player.hurtTime > 5 && !intaveFallDamage;
        boolean canJump = player.onGround() && !(ClientScreen.current() instanceof InventoryScreen);

        if (intaveRandomize.get()) {
            intaveDelayCounter++;
            if (intaveDelayCounter >= intaveCurrentDelay) {
                if (shouldJump && canJump) event.setJump(true);
                intaveDelayCounter = 0;
                intaveCurrentDelay = ThreadLocalRandom.current().nextInt(intaveRandomDelayTicks.get() + 1);
            }
        } else if (shouldJump && canJump) {
            event.setJump(true);
        }
    }

    private void handleVulcan297(PacketEvent event, Packet<?> packet) {
        LocalPlayer player = player();
        if (player == null) return;

        if (event.getOrigin() == TransferOrigin.OUTGOING
                && packet instanceof ServerboundSwingPacket swing
                && isVulcan297MaceSwing(swing, player)) {
            armVulcan297MaceVelocityWindow();
            return;
        }

        if (packet instanceof ClientboundSetEntityMotionPacket velocity && velocity.id() == player.getId()) {
            if (vulcan297WindBurstProtectTicks > 0) {
                return;
            }

            Vec3 modified = getVulcan297Velocity(player, velocity.movement());
            if (isZeroVelocity(modified)) {
                event.cancel();
                return;
            }

            ((ClientboundSetEntityMotionPacketAccessor) (Object) velocity).combatant$setVelocity(modified);
            return;
        }

        if (packet instanceof ClientboundExplodePacket explosion && explosion.playerKnockback().isPresent()) {
            if (vulcan297WindBurstPendingTicks > 0) {
                vulcan297WindBurstPendingTicks = 0;
                vulcan297WindBurstProtectTicks = VULCAN_297_WIND_BURST_PROTECT_TICKS;
            }
        }
    }

    private void armVulcan297MaceVelocityWindow() {
        vulcan297WindBurstPendingTicks = Math.max(vulcan297WindBurstPendingTicks, VULCAN_297_WIND_BURST_PENDING_TICKS);
        vulcan297WindBurstProtectTicks = Math.max(vulcan297WindBurstProtectTicks, VULCAN_297_WIND_BURST_PROTECT_TICKS);
    }

    private boolean isVulcan297MaceSwing(ServerboundSwingPacket packet, LocalPlayer player) {
        return packet.getHand() == InteractionHand.MAIN_HAND && player.getMainHandItem().is(Items.MACE);
    }

    private boolean isVulcan297WindBurstMaceAttack(AttackEntityEvent event, LocalPlayer player) {
        if (event.getPlayer() != player || player.isFallFlying() || player.fallDistance < 1.5f) {
            return false;
        }

        ItemStack stack = player.getMainHandItem();
        return stack.is(Items.MACE) && getEnchantmentLevel(Enchantments.WIND_BURST, stack) > 0;
    }

    private int getEnchantmentLevel(ResourceKey<Enchantment> key, ItemStack stack) {
        Holder<Enchantment> entry = getEnchantmentEntry(key);
        return entry == null ? 0 : EnchantmentHelper.getItemEnchantmentLevel(entry, stack);
    }

    private Holder<Enchantment> getEnchantmentEntry(ResourceKey<Enchantment> key) {
        if (mc.level == null) return null;
        Registry<Enchantment> registry = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Enchantment enchantment = registry.getValue(key);
        return enchantment != null ? registry.wrapAsHolder(enchantment) : null;
    }

    private Vec3 getVulcan297Velocity(LocalPlayer player, Vec3 velocity) {
        double horizontal = horizontalSpeed(velocity);
        boolean typeAExempt = isVulcan297TypeAExempt(player);
        boolean typeBExempt = isVulcan297TypeBExempt(player);

        if (!typeAExempt && shouldPreserveVulcan297EntityImpulse(velocity)) {
            return velocity;
        }

        double exemptRatio = vulcan297ExemptRatio.get();
        double horizontalFactor = typeBExempt ? exemptRatio : VULCAN_297_SAFE_HORIZONTAL_RATIO;
        double verticalFactor = typeAExempt ? exemptRatio : velocity.y > 0.0 ? 1.0 : 0.0;

        if (!typeBExempt && horizontal < VULCAN_297_TINY_HORIZONTAL && velocity.y <= VULCAN_297_JUMP_LIKE_VERTICAL) {
            horizontalFactor = 0.0;
        }

        return applyVulcan297Budget(velocity, horizontalFactor, verticalFactor);
    }

    private Vec3 applyVulcan297Budget(Vec3 velocity, double horizontalFactor, double verticalFactor) {
        return new Vec3(
                velocity.x * horizontalFactor,
                velocity.y * verticalFactor,
                velocity.z * horizontalFactor
        );
    }

    private boolean shouldPreserveVulcan297EntityImpulse(Vec3 velocity) {
        double horizontal = horizontalSpeed(velocity);

        if (velocity.y >= VULCAN_297_PROTECTED_VERTICAL) {
            return true;
        }

        return velocity.y > VULCAN_297_JUMP_LIKE_VERTICAL && horizontal >= VULCAN_297_SAFE_HORIZONTAL_RATIO;
    }

    private boolean isVulcan297TypeAExempt(LocalPlayer player) {
        return isVulcan297SharedExempt(player)
                || getVulcan297BlockExempt(player);
    }

    private boolean isVulcan297TypeBExempt(LocalPlayer player) {
        return isVulcan297SharedExempt(player)
                || getVulcan297BlockExempt(player)
                || vulcan297Exempts.get(VULCAN_EXEMPT_NETHERITE_ARMOR) && hasVulcan297NetheriteArmor(player);
    }

    private boolean isVulcan297SharedExempt(LocalPlayer player) {
        if (vulcan297Exempts.get(VULCAN_EXEMPT_VEHICLE) && player.isPassenger()) return true;
        if (vulcan297Exempts.get(VULCAN_EXEMPT_ELYTRA) && player.isFallFlying()) return true;
        if (vulcan297Exempts.get(VULCAN_EXEMPT_LIQUID) && (player.isInWater()
                || player.isUnderWater()
                || player.isInLava()
                || player.isSwimming())) {
            return true;
        }
        if (vulcan297Exempts.get(VULCAN_EXEMPT_FROZEN) && player.isFullyFrozen()) return true;
        if (vulcan297Exempts.get(VULCAN_EXEMPT_FLIGHT) && player.getAbilities().flying) return true;
        if (vulcan297Exempts.get(VULCAN_EXEMPT_FALL_DAMAGE) && player.fallDistance > 3.0f) return true;
        if (vulcan297Exempts.get(VULCAN_EXEMPT_VOID) && isBelowWorld(player)) return true;
        return vulcan297Exempts.get(VULCAN_EXEMPT_SLOW_FALLING) && player.hasEffect(MobEffects.SLOW_FALLING);
    }

    private boolean isBelowWorld(LocalPlayer player) {
        return mc.level != null && player.getY() < mc.level.getMinY();
    }

    private boolean getVulcan297BlockExempt(LocalPlayer player) {
        if (mc.level == null) {
            return false;
        }

        BlockPos feet = player.blockPosition();
        return isVulcan297ExemptBlock(mc.level.getBlockState(feet).getBlock())
                || isVulcan297ExemptBlock(mc.level.getBlockState(feet.below()).getBlock());
    }

    private boolean isVulcan297ExemptBlock(Block block) {
        return vulcan297Exempts.get(VULCAN_EXEMPT_WEB) && (block == Blocks.COBWEB || block == Blocks.POWDER_SNOW)
                || vulcan297Exempts.get(VULCAN_EXEMPT_SOUL_SAND) && block == Blocks.SOUL_SAND
                || vulcan297Exempts.get(VULCAN_EXEMPT_SLIME) && block == Blocks.SLIME_BLOCK
                || vulcan297Exempts.get(VULCAN_EXEMPT_SWEET_BERRIES) && block == Blocks.SWEET_BERRY_BUSH
                || vulcan297Exempts.get(VULCAN_EXEMPT_TRAPDOOR) && block instanceof TrapDoorBlock
                || vulcan297Exempts.get(VULCAN_EXEMPT_FENCE) && block instanceof FenceBlock
                || vulcan297Exempts.get(VULCAN_EXEMPT_WALL) && block instanceof WallBlock;
    }

    private boolean hasVulcan297NetheriteArmor(LocalPlayer player) {
        return isNetheriteArmor(player.getItemBySlot(EquipmentSlot.HEAD))
                || isNetheriteArmor(player.getItemBySlot(EquipmentSlot.CHEST))
                || isNetheriteArmor(player.getItemBySlot(EquipmentSlot.LEGS))
                || isNetheriteArmor(player.getItemBySlot(EquipmentSlot.FEET));
    }

    private boolean isNetheriteArmor(ItemStack stack) {
        return stack.is(Items.NETHERITE_HELMET)
                || stack.is(Items.NETHERITE_CHESTPLATE)
                || stack.is(Items.NETHERITE_LEGGINGS)
                || stack.is(Items.NETHERITE_BOOTS);
    }

    private boolean isZeroVelocity(Vec3 velocity) {
        return velocity.x == 0.0 && velocity.y == 0.0 && velocity.z == 0.0;
    }

    private void processDelayedPackets() {
        int size = delayedPackets.size();
        for (int i = 0; i < size; i++) {
            DelayedPacket delayed = delayedPackets.poll();
            if (delayed == null) continue;

            delayed.ticks--;
            if (delayed.ticks > 0) {
                delayedPackets.add(delayed);
                continue;
            }

            PacketEvent.Receive packetEvent = new PacketEvent.Receive(delayed.packet, false);
            Events.BUS.post(packetEvent);
            if (!packetEvent.isCancelled()) {
                handleIncomingPacket(delayed.packet);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void handleIncomingPacket(Packet<?> packet) {
        Connection connection = mc.getConnection() != null ? mc.getConnection().getConnection() : null;
        PacketListener listener = connection != null ? connection.getPacketListener() : null;
        if (listener != null) {
            ((Packet) packet).handle(listener);
        }
    }

    private boolean supportsIncomingVelocity() {
        return mode.get() != Mode.DEXLAND
                && mode.get() != Mode.HYLEX
                && mode.get() != Mode.AAC_442
                && mode.get() != Mode.INTAVE
                && mode.get() != Mode.VULCAN_297;
    }

    private boolean isVulcan297Mode() {
        return mode.get() == Mode.VULCAN_297;
    }

    private boolean isLocalPlayerVelocity(Packet<?> packet) {
        LocalPlayer player = player();
        if (player == null) return false;
        if (packet instanceof ClientboundSetEntityMotionPacket velocity) {
            return velocity.id() == player.getId();
        }
        return packet instanceof ClientboundExplodePacket explosion && explosion.playerKnockback().isPresent();
    }

    private boolean isLocalPlayerDamage(Packet<?> packet) {
        return packet instanceof ClientboundDamageEventPacket damage && player() != null && damage.entityId() == player().getId();
    }

    private LocalPlayer player() {
        return mc.player;
    }

    private boolean isMoving() {
        return MovementUtil.isMoving();
    }

    private Vec3 movementInput() {
        if (player() == null || player().input == null) return Vec3.ZERO;
        var input = player().input.getMoveVector();
        return new Vec3(input.x, 0.0, input.y);
    }

    private float movementYaw() {
        Rotation rotation = RotationManager.INSTANCE.getMovementRotation();
        return rotation != null ? rotation.yaw() : player().getYRot();
    }

    private double horizontalSpeed(Vec3 velocity) {
        return Math.hypot(velocity.x, velocity.z);
    }

    private void multiplyHorizontal(LocalPlayer player, double factor) {
        Vec3 velocity = player.getDeltaMovement();
        player.setDeltaMovement(velocity.x * factor, velocity.y, velocity.z * factor);
    }

    private boolean rollChance(float chance) {
        return chance >= 100.0f || ThreadLocalRandom.current().nextDouble(100.0) < chance;
    }

    private void sendSneaking(boolean sneaking) {
        if (mc.getConnection() == null) return;
        Input input = player() != null && player().input != null
                ? player().input.keyPresses
                : new Input(false, false, false, false, false, false, false);
        mc.getConnection().send(new ServerboundPlayerInputPacket(new Input(
                input.forward(),
                input.backward(),
                input.left(),
                input.right(),
                input.jump(),
                sneaking,
                input.sprint()
        )));
    }

    private BlockHitResult traceDownToPlayerBlock(LocalPlayer player) {
        Rotation server = RotationManager.INSTANCE.getServerRotation();
        float yaw = server != null ? server.yaw() : player.getYRot();
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(Vec3.directionFromRotation(90.0f, yaw).scale(4.5));
        HitResult hit = player.level().clip(new ClipContext(
                start,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
        ));
        if (!(hit instanceof BlockHitResult blockHit) || blockHit.getType() == HitResult.Type.MISS) return null;

        BlockPos playerBlock = player.blockPosition();
        return blockHit.getBlockPos().relative(blockHit.getDirection()).equals(playerBlock) ? blockHit : null;
    }

    private void sendGrim2371Rotation(LocalPlayer player) {
        if (mc.getConnection() == null) return;
        Rotation server = RotationManager.INSTANCE.getServerRotation();
        if (server == null || Mth.abs(server.pitch() - 90.0f) > 0.001f) {
            mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(
                    player.getYRot(),
                    90.0f,
                    player.onGround(),
                    player.horizontalCollision
            ));
        } else {
            mc.getConnection().send(new ServerboundMovePlayerPacket.StatusOnly(
                    player.onGround(),
                    player.horizontalCollision
            ));
        }
    }

    private void sendFullMovePacket(LocalPlayer player) {
        if (mc.getConnection() == null || player == null) return;
        mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYRot(),
                player.getXRot(),
                player.onGround(),
                player.horizontalCollision
        ));
    }

    private void sendStopDestroyBlock(LocalPlayer player) {
        if (mc.getConnection() == null || player == null) return;
        mc.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                player.blockPosition(),
                Direction.DOWN
        ));
    }

    private void resetState() {
        delayedPackets.clear();
        pause = 0;
        transactionBuffer = 0;
        reversalHandlingVelocity = false;
        reversalVelocityTicks = 0;
        strafeTicks = -1;
        strafeApplyUntilGround = false;
        jumpLimitUntilJump = 0;
        jumpResetFallDamage = false;
        lagShouldLag = false;
        lagTicks = 0;
        lagShouldJumpTicks = 0;
        hypixelAbsorbedVelocity = false;
        dexlandLastAttackTime = 0L;
        dexlandCount = 0;
        grim2371CancelNextVelocity = false;
        grim2371Delay = false;
        grim2371NeedClick = false;
        grim2371WaitForPing = false;
        grim2371WaitForUpdate = false;
        grim2371ShouldSkip = false;
        grim2371HitResult = null;
        grim2371FreezeTicks = 0;
        grim2344CanCancel = false;
        intaveLastAttack = 0L;
        intaveFallDamage = false;
        intaveCurrentDelay = 0;
        intaveDelayCounter = 0;
        vulcan297WindBurstPendingTicks = 0;
        vulcan297WindBurstProtectTicks = 0;
    }

    @Getter
    @RequiredArgsConstructor
    public enum Mode implements EnumValue.IdProvider {
        MODIFY("modify"),
        REVERSAL("reversal"),
        STRAFE("strafe"),
        JUMP_RESET("jump_reset"),
        LAG("lag"),
        HYPIXEL("hypixel"),
        DEXLAND("dexland"),
        HYLEX("hylex"),
        BLOCKSMC("blocksmc"),
        GRIM_2371("grim_2371"),
        GRIM_2344("grim_2344_117"),
        AAC_442("aac_4_4_2"),
        INTAVE("intave"),
        VULCAN_297("vulcan_297");

        private final String id;
    }

    private enum Filter {
        ALWAYS,
        ON_GROUND,
        IN_AIR
    }

    private static final class DelayedPacket {
        final Packet<?> packet;
        int ticks;

        DelayedPacket(Packet<?> packet, int ticks) {
            this.packet = packet;
            this.ticks = ticks;
        }
    }
}
