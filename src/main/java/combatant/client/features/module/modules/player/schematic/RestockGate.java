package combatant.client.features.module.modules.player.schematic;

/**
 * A container click while the player moves, sprints or sneaks is what Grim's MultiActionsC flags, and the check reads
 * the last input packet the server got. Restocking from the main inventory therefore waits until the player has been
 * still for a couple of ticks, so the packet saying so has arrived before the click goes out.
 */
final class RestockGate {
    /** Consecutive still ticks before a click: the input packet of the first one is on the server by the second. */
    static final int STILL_TICKS = 2;

    private int still;

    /** One call per tick with whether the player moves, sprints or sneaks right now. */
    void observe(boolean active) {
        still = active ? 0 : Math.min(STILL_TICKS, still + 1);
    }

    boolean ready() {
        return still >= STILL_TICKS;
    }

    void reset() {
        still = 0;
    }
}
