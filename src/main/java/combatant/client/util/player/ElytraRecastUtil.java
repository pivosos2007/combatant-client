/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import combatant.client.events.impl.EventSync;
import combatant.client.events.impl.MovementInputEvent;

import java.util.IdentityHashMap;
import java.util.Map;

public final class ElytraRecastUtil {

    public static final ElytraRecastUtil INSTANCE = new ElytraRecastUtil();

    private static final int NORMAL_COOLDOWN_TICKS = 5;
    private static final int GRIM_COOLDOWN_TICKS = 3;
    private static final double VANILLA_JUMP_HEADROOM = 0.42;

    private final Minecraft client = Minecraft.getInstance();
    private final Map<Object, RecastState> states = new IdentityHashMap<>();

    private ElytraRecastUtil() {
    }

    private static boolean isElytraUsable(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.ELYTRA)) return false;
        if (!stack.isDamageableItem()) return true;
        return (stack.getMaxDamage() - stack.getDamageValue()) > 1;
    }

    public void request(
            Object owner,
            LocalPlayer player,
            Mode mode,
            boolean allowBroken,
            boolean requireJumpHeld,
            boolean autoWalk,
            int delayTicks
    ) {
        if (owner == null) return;

        RecastState state = state(owner);
        state.mode = mode == null ? Mode.NORMAL : mode;
        state.allowBroken = allowBroken;
        state.requireJumpHeld = requireJumpHeld;
        state.autoWalk = autoWalk;

        if (state.phase != Phase.IDLE) {
            return;
        }

        if (!canAttempt(player, state)) {
            reset(owner);
            return;
        }

        if (state.autoWalk) {
            client.options.keyUp.setDown(true);
            state.forcedForward = true;
        }

        state.delayTicks = Math.max(0, delayTicks);
        if (state.delayTicks > 0) {
            state.phase = Phase.WAIT_DELAY;
        } else {
            beginStartPhase(state, player);
        }
    }

    public void tick(Object owner, LocalPlayer player) {
        RecastState state = states.get(owner);
        if (state == null) return;

        if (state.phase == Phase.IDLE) {
            releaseForcedKeys(state);
            return;
        }

        if (state.phase == Phase.COOLDOWN) {
            if (state.cooldownTicks > 0) {
                state.cooldownTicks--;
            }
            if (state.cooldownTicks <= 0 || player == null || player.isFallFlying()) {
                reset(owner);
            }
            return;
        }

        if (!canAttempt(player, state)) {
            if (player != null && player.isFallFlying()) {
                state.phase = Phase.COOLDOWN;
                state.cooldownTicks = state.mode == Mode.GRIM ? GRIM_COOLDOWN_TICKS : NORMAL_COOLDOWN_TICKS;
                return;
            }
            reset(owner);
            return;
        }

        if (state.phase == Phase.WAIT_DELAY) {
            if (state.delayTicks > 0) {
                state.delayTicks--;
            }
            if (state.delayTicks <= 0) {
                beginStartPhase(state, player);
            }
            return;
        }

        if (state.phase == Phase.VANILLA_START_JUMP && state.jumpTicks <= 0) {
            state.phase = Phase.COOLDOWN;
            state.cooldownTicks = state.mode == Mode.GRIM ? GRIM_COOLDOWN_TICKS : NORMAL_COOLDOWN_TICKS;
        }
    }

    public boolean handleMovementInput(Object owner, MovementInputEvent event) {
        RecastState state = states.get(owner);
        if (state == null) return false;

        if (state.phase == Phase.GRIM_RELEASE) {
            event.setJump(false);
            state.phase = Phase.VANILLA_START_JUMP;
            state.jumpTicks = 1;
            return true;
        }

        if (state.phase == Phase.VANILLA_START_JUMP) {
            LocalPlayer player = client.player;
            if (!canAttempt(player, state)) {
                reset(owner);
                return false;
            }

            event.setJump(true);
            if (state.jumpTicks > 0) {
                state.jumpTicks--;
            }
            if (state.jumpTicks <= 0) {
                state.phase = Phase.COOLDOWN;
                state.cooldownTicks = state.mode == Mode.GRIM ? GRIM_COOLDOWN_TICKS : NORMAL_COOLDOWN_TICKS;
            }
            return true;
        }

        return false;
    }

    public boolean handleSync(Object owner, EventSync event) {
        return false;
    }

    public boolean isActive(Object owner) {
        RecastState state = states.get(owner);
        return state != null && state.phase != Phase.IDLE;
    }

    public void reset(Object owner) {
        RecastState state = states.remove(owner);
        if (state != null) {
            releaseForcedKeys(state);
        }
    }

    private RecastState state(Object owner) {
        return states.computeIfAbsent(owner, ignored -> new RecastState());
    }

    private void beginStartPhase(RecastState state, LocalPlayer player) {
        state.jumpTicks = 0;
        if (state.mode == Mode.GRIM || isJumpInputActive(player)) {
            state.phase = Phase.GRIM_RELEASE;
        } else {
            state.phase = Phase.VANILLA_START_JUMP;
            state.jumpTicks = 1;
        }
    }

    private boolean canAttempt(LocalPlayer player, RecastState state) {
        if (player == null || client.options == null) return false;
        if (player.isFallFlying() || player.onGround()) return false;
        if (player.getDeltaMovement().y > -0.01 && player.fallDistance <= 0.0f) return false;
        if (state.requireJumpHeld && !client.options.keyJump.isDown()) return false;
        if (player.getAbilities().flying) return false;
        if (player.isPassenger()) return false;
        if (player.onClimbable()) return false;
        if (player.isInWater() || player.isInLava()) return false;
        if (player.hasEffect(MobEffects.LEVITATION)) return false;
        if (!hasVanillaGlideStartSpace(player)) return false;

        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        return chest.is(Items.ELYTRA) && (isElytraUsable(chest) || state.allowBroken);
    }

    private boolean hasVanillaGlideStartSpace(LocalPlayer player) {
        if (player == null || player.level() == null) return false;

        AABB glidingBox = player.getLocalBoundsForPose(Pose.FALL_FLYING).deflate(1.0E-7);
        if (!player.level().noCollision(player, glidingBox)) {
            return false;
        }

        AABB jumpHeadroomBox = player.getBoundingBox()
                .expandTowards(0.0, VANILLA_JUMP_HEADROOM, 0.0)
                .deflate(1.0E-7);
        return player.level().noCollision(player, jumpHeadroomBox);
    }

    private boolean isJumpInputActive(LocalPlayer player) {
        if (player != null && player.input != null && player.input.keyPresses != null && player.input.keyPresses.jump()) {
            return true;
        }
        return client.options != null && client.options.keyJump.isDown();
    }

    private void releaseForcedKeys(RecastState state) {
        if (!state.forcedForward || client.options == null) return;

        state.forcedForward = false;
        client.options.keyUp.setDown(false);
        KeyMapping.setAll();
    }

    public enum Mode {
        NORMAL("normal"),
        GRIM("grim");

        private final String id;

        Mode(String id) {
            this.id = id;
        }

        public static Mode from(String value) {
            return "grim".equalsIgnoreCase(String.valueOf(value)) ? GRIM : NORMAL;
        }

        public String id() {
            return id;
        }
    }

    private enum Phase {
        IDLE,
        WAIT_DELAY,
        GRIM_RELEASE,
        VANILLA_START_JUMP,
        COOLDOWN
    }

    private static final class RecastState {
        private Mode mode = Mode.NORMAL;
        private boolean allowBroken;
        private boolean requireJumpHeld;
        private boolean autoWalk;
        private boolean forcedForward;
        private int delayTicks;
        private int cooldownTicks;
        private int jumpTicks;
        private Phase phase = Phase.IDLE;
    }
}
