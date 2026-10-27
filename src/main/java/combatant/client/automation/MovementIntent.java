/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation;

/**
 * Movement keys requested by lease holders for the current tick. Actions write it while ticking;
 * the runtime applies each group whose lease is still held to {@code MovementInputEvent} and then
 * clears it, so a holder that stops writing stops steering on the next tick.
 */
public final class MovementIntent {
    private LeaseHolder movementWriter;
    private boolean forward;
    private boolean backward;
    private boolean left;
    private boolean right;
    private LeaseHolder jumpWriter;
    private boolean jump;
    private LeaseHolder sneakWriter;
    private boolean sneak;
    private LeaseHolder sprintWriter;
    private boolean sprint;

    void setMovement(LeaseHolder writer, boolean forward, boolean backward, boolean left, boolean right) {
        this.movementWriter = writer;
        this.forward = forward;
        this.backward = backward;
        this.left = left;
        this.right = right;
    }

    void setJump(LeaseHolder writer, boolean jump) {
        this.jumpWriter = writer;
        this.jump = jump;
    }

    void setSneak(LeaseHolder writer, boolean sneak) {
        this.sneakWriter = writer;
        this.sneak = sneak;
    }

    void setSprint(LeaseHolder writer, boolean sprint) {
        this.sprintWriter = writer;
        this.sprint = sprint;
    }

    /** True when the current MOVEMENT holder wrote keys this tick. */
    public boolean hasMovement(ActionCoordinator coordinator) {
        return movementWriter != null && coordinator.holds(movementWriter, ActionResource.MOVEMENT);
    }

    public boolean hasJump(ActionCoordinator coordinator) {
        return jumpWriter != null && coordinator.holds(jumpWriter, ActionResource.JUMP);
    }

    public boolean hasSneak(ActionCoordinator coordinator) {
        return sneakWriter != null && coordinator.holds(sneakWriter, ActionResource.SNEAK);
    }

    public boolean hasSprint(ActionCoordinator coordinator) {
        return sprintWriter != null && coordinator.holds(sprintWriter, ActionResource.SPRINT);
    }

    public boolean forward() {
        return forward;
    }

    public boolean backward() {
        return backward;
    }

    public boolean left() {
        return left;
    }

    public boolean right() {
        return right;
    }

    public boolean jump() {
        return jump;
    }

    public boolean sneak() {
        return sneak;
    }

    public boolean sprint() {
        return sprint;
    }

    public void clear() {
        movementWriter = null;
        jumpWriter = null;
        sneakWriter = null;
        sprintWriter = null;
        forward = backward = left = right = jump = sneak = sprint = false;
    }
}
