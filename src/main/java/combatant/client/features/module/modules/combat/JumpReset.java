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
import combatant.client.events.EventHandler;
import combatant.client.events.impl.MovementInputEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Presses the real jump key for one tick when the player has just been hit, so the knockback is taken in the air
 * and the vertical momentum is not wasted.
 */
@ModuleInfo(
        id = "jumpreset",
        displayName = "JumpReset",
        aliases = {"JumpOnHit", "KBJump"},
        category = ModuleCategory.COMBAT,
        subcategory = ModuleSubcategory.PROTECT,
        description = "module.jumpreset.description")
public final class JumpReset extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<Trigger> trigger = enumSetting("jumpResetTrigger", "trigger", Trigger.HURT_TIME, Trigger.values());
    private final NumberValue<Integer> chance = num("jumpResetChance", "chance", 100, 0, 100);
    private final NumberValue<Integer> minHurtTime = num("jumpResetMinHurtTime", "min_hurt_time", 6, 1, 10);
    private final NumberValue<Integer> maxHurtTime = num("jumpResetMaxHurtTime", "max_hurt_time", 10, 1, 10);
    private final NumberValue<Integer> cooldown = num("jumpResetCooldown", "cooldown", 10, 0, 40);
    private final BooleanValue onlyWhenSprinting = bool("jumpResetOnlyWhenSprinting", "only_when_sprinting", false);
    private final BooleanValue requireForward = bool("jumpResetRequireForward", "require_forward", false);

    private volatile boolean pendingJump;
    private boolean handledThisHurt;
    private int lastJumpTick = Integer.MIN_VALUE;

    @Override
    public void onDisable() {
        pendingJump = false;
        handledThisHurt = false;
        lastJumpTick = Integer.MIN_VALUE;
    }

    @EventHandler(priority = 100)
    private void onPacket(PacketEvent.Receive event) {
        if (!isEnabled() || trigger.get() != Trigger.PACKET) return;
        LocalPlayer player = mc.player;
        if (player == null) return;
        if (!(event.getPacket() instanceof ClientboundSetEntityMotionPacket packet)) return;
        if (packet.id() != player.getId() || packet.movement().y <= 0.0) return;
        if (canJump(player) && roll()) {
            pendingJump = true;
        }
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        if (player == null) {
            pendingJump = false;
            return;
        }
        if (player.hurtTime <= 0) {
            handledThisHurt = false;
            return;
        }
        if (trigger.get() != Trigger.HURT_TIME || handledThisHurt) return;

        int min = Math.min(minHurtTime.get(), maxHurtTime.get());
        int max = Math.max(minHurtTime.get(), maxHurtTime.get());
        if (player.hurtTime < min || player.hurtTime > max) return;
        if (!player.onGround()) return;
        handledThisHurt = true;
        if (canJump(player) && roll()) {
            pendingJump = true;
        }
    }

    @EventHandler
    private void onMovementInput(MovementInputEvent event) {
        if (!pendingJump) return;
        pendingJump = false;
        LocalPlayer player = mc.player;
        if (!isEnabled() || player == null || !player.onGround()) return;
        event.setJump(true);
        lastJumpTick = player.tickCount;
    }

    private boolean canJump(LocalPlayer player) {
        if (!player.onGround() || player.isInWater() || player.isInLava() || player.onClimbable()
                || player.isPassenger() || player.isSleeping()) {
            return false;
        }
        if (player.tickCount - lastJumpTick < cooldown.get()) return false;
        if (onlyWhenSprinting.get() && !player.isSprinting()) return false;
        return !requireForward.get() || mc.options.keyUp.isDown();
    }

    private boolean roll() {
        int value = chance.get();
        return value >= 100 || ThreadLocalRandom.current().nextInt(100) < value;
    }

    public enum Trigger {
        HURT_TIME, PACKET
    }
}
