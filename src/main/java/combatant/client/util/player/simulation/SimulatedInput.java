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

package combatant.client.util.player.simulation;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import combatant.client.events.Events;
import combatant.client.events.impl.MovementInputEvent;
import combatant.client.events.impl.PlayerSafeWalkEvent;

final class SimulatedInput {
    private static final double MAX_WALKING_SPEED = 0.121;

    final boolean forward;
    final boolean backward;
    final boolean left;
    final boolean right;
    final boolean jump;
    final boolean sneak;
    final boolean sprint;
    boolean ignoreClippingAtLedge;
    boolean forceSafeWalk;

    float movementForward;
    float movementSideways;

    private SimulatedInput(boolean forward, boolean backward, boolean left, boolean right, boolean jump, boolean sneak, boolean sprint) {
        this.forward = forward;
        this.backward = backward;
        this.left = left;
        this.right = right;
        this.jump = jump;
        this.sneak = sneak;
        this.sprint = sprint;
    }

    static SimulatedInput fromPlayer(Player player) {
        if (player instanceof LocalPlayer clientPlayer && clientPlayer.input != null) {
            SimulatedInput input = new SimulatedInput(
                    clientPlayer.input.keyPresses.forward(),
                    clientPlayer.input.keyPresses.backward(),
                    clientPlayer.input.keyPresses.left(),
                    clientPlayer.input.keyPresses.right(),
                    clientPlayer.input.keyPresses.jump(),
                    clientPlayer.input.keyPresses.shift(),
                    clientPlayer.input.keyPresses.sprint()
            );
            input.forceSafeWalk = shouldForceSafeWalk();
            return input;
        }

        return guessInput(player);
    }

    static SimulatedInput fromMovementEvent(Player player, MovementInputEvent event) {
        SimulatedInput input = new SimulatedInput(
                event.isForward(),
                event.isBackward(),
                event.isLeft(),
                event.isRight(),
                event.isJump(),
                event.isSneak(),
                event.isSprint()
        );
        input.forceSafeWalk = shouldForceSafeWalk();
        input.ignoreClippingAtLedge = false;
        return input;
    }

    static SimulatedInput guessInput(Player player) {
        Vec3 velocity = player.position().subtract(player.xo, player.yo, player.zo);
        double horizontalSpeedSq = velocity.x * velocity.x + velocity.z * velocity.z;

        boolean sprinting = horizontalSpeedSq >= MAX_WALKING_SPEED * MAX_WALKING_SPEED;
        boolean jump = !player.onGround();
        boolean sneak = player.isShiftKeyDown();

        if (horizontalSpeedSq <= 0.05 * 0.05) {
            return new SimulatedInput(false, false, false, false, jump, sneak, sprinting);
        }

        double yawRad = Math.toRadians(player.getYRot());
        double sideways = velocity.x * Math.cos(yawRad) + velocity.z * Math.sin(yawRad);
        double forward = velocity.z * Math.cos(yawRad) - velocity.x * Math.sin(yawRad);

        boolean forwardKey = forward > 0.05;
        boolean backwardKey = forward < -0.05;
        boolean leftKey = sideways > 0.05;
        boolean rightKey = sideways < -0.05;

        SimulatedInput input = new SimulatedInput(forwardKey, backwardKey, leftKey, rightKey, jump, sneak, sprinting);
        if (sprinting) {
            input.update();
        }
        return input;
    }

    private static boolean shouldForceSafeWalk() {
        if (!Events.BUS.hasListeners(PlayerSafeWalkEvent.class)) {
            return false;
        }

        PlayerSafeWalkEvent event = new PlayerSafeWalkEvent();
        Events.BUS.post(event);
        return event.isSafeWalk();
    }

    void update() {
        if (forward != backward) {
            movementForward = forward ? 1.0f : -1.0f;
        } else {
            movementForward = 0.0f;
        }

        if (left == right) {
            movementSideways = 0.0f;
        } else {
            movementSideways = left ? 1.0f : -1.0f;
        }

        if (sneak) {
            movementForward *= 0.3f;
            movementSideways *= 0.3f;
        }
    }
}
