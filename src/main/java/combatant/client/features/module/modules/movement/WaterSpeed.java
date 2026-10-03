/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.TimeUnit;

@ModuleInfo(
        id = "waterspeed",
        displayName = "WaterSpeed",
        aliases = {"AquaSpeed"},
        category = ModuleCategory.MOVEMENT,
        subcategory = ModuleSubcategory.RAGE,
        description = "module.waterspeed.description")
//does not bypass anything serious todo bypasses
public final class WaterSpeed extends Module {

    private static final double NON_SPRINT_HORIZONTAL_MULTIPLIER = 1.156D;
    private static final double SPRINT_HORIZONTAL_MULTIPLIER = 1.025D;
    private static final double JUMP_VERTICAL_BOOST = 0.05D;
    private static final double SNEAK_VERTICAL_BOOST = -0.05D;
    private static final double IDLE_VERTICAL_BOOST = 0.005D;

    private final NumberValue<Float> strength = num("waterSpeedStrength", "strength", 1.0f, 0.0f, 2.0f);
    private final BooleanValue vertical = bool("waterSpeedVertical", "vertical", true);
    private final NumberValue<Integer> armDelayMs = num("waterSpeedArmDelayMs", "arm_delay_ms", 160, 0, 500);

    private final Minecraft mc = Minecraft.getInstance();

    private long armedSinceNanos;
    private int lastStartSprintAge = Integer.MIN_VALUE;

    @Override
    public void onEnable() {
        resetArmTimer();
        lastStartSprintAge = Integer.MIN_VALUE;
    }

    @Override
    public void onDisable() {
        armedSinceNanos = 0L;
        lastStartSprintAge = Integer.MIN_VALUE;
    }

    @Override
    public void onTick() {
        LocalPlayer player = mc.player;
        if (!isEnabled() || player == null || mc.level == null || mc.getConnection() == null) {
            return;
        }

        if (!player.isInWater()) {
            resetArmTimer();
            return;
        }

        if (mc.options.keyJump.isDown() && !isWaterAtBody(player)) {
            resetArmTimer();
            return;
        }

        if (player.tickCount == lastStartSprintAge || !isArmed()) {
            return;
        }

        sendShiftPulse(player);

        double verticalBoost;
        if (mc.options.keyJump.isDown()) {
            verticalBoost = JUMP_VERTICAL_BOOST;
        } else if (mc.options.keyShift.isDown()) {
            verticalBoost = SNEAK_VERTICAL_BOOST;
        } else {
            verticalBoost = player.isSprinting() ? 0.0D : IDLE_VERTICAL_BOOST;
        }
        if (!vertical.get()) verticalBoost = 0.0D;

        double horizontalMultiplier = player.isSprinting()
                ? SPRINT_HORIZONTAL_MULTIPLIER
                : NON_SPRINT_HORIZONTAL_MULTIPLIER;
        horizontalMultiplier = 1.0D + (horizontalMultiplier - 1.0D) * strength.get();

        Vec3 velocity = player.getDeltaMovement();
        player.setDeltaMovement(
                velocity.x * horizontalMultiplier,
                velocity.y + verticalBoost,
                velocity.z * horizontalMultiplier
        );
    }

    @EventHandler
    private void onPacketSendPost(PacketEvent.SendPost event) {
        if (!(event.getPacket() instanceof ServerboundPlayerCommandPacket command)) {
            return;
        }

        if (command.getAction() == ServerboundPlayerCommandPacket.Action.START_SPRINTING && mc.player != null) {
            lastStartSprintAge = mc.player.tickCount;
        }
    }

    private boolean isWaterAtBody(LocalPlayer player) {
        BlockPos pos = BlockPos.containing(player.getX(), player.getY() + 0.2D, player.getZ());
        return mc.level != null && mc.level.getBlockState(pos).is(Blocks.WATER);
    }

    private void sendShiftPulse(LocalPlayer player) {
        Input input = player.input.keyPresses;
        mc.getConnection().send(new ServerboundPlayerInputPacket(withShift(input, true)));
        mc.getConnection().send(new ServerboundPlayerInputPacket(withShift(input, false)));
        if (input.shift()) {
            mc.getConnection().send(new ServerboundPlayerInputPacket(input));
        }
    }

    private Input withShift(Input input, boolean shift) {
        return new Input(
                input.forward(),
                input.backward(),
                input.left(),
                input.right(),
                input.jump(),
                shift,
                input.sprint()
        );
    }

    private boolean isArmed() {
        if (armedSinceNanos == 0L) {
            resetArmTimer();
            return false;
        }
        return System.nanoTime() - armedSinceNanos >= TimeUnit.MILLISECONDS.toNanos(armDelayMs.get());
    }

    private void resetArmTimer() {
        armedSinceNanos = System.nanoTime();
    }
}
