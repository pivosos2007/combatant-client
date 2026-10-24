/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation;

import combatant.client.events.EventHandler;
import combatant.client.events.impl.EventPostSync;
import combatant.client.events.impl.MovementInputEvent;
import combatant.client.events.impl.RotationUpdateEvent;
import combatant.client.events.impl.WorldChangeEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleManager;
import combatant.client.runtime.RuntimeShutdownContext;
import combatant.client.runtime.RuntimeShutdownParticipant;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Client-side driver of the shared automation runtime. Owns the {@link ActionCoordinator} and the
 * {@link ActionScheduler} and binds them to the client tick:
 *
 * <ul>
 *   <li>{@code RotationUpdateEvent PRE}: apply manual mouse override, tick tasks.</li>
 *   <li>{@code RotationUpdateEvent POST}: run {@link ExecutionPhase#AFTER_ROTATION} work.</li>
 *   <li>{@code MovementInputEvent}: player keys revoke yielding leases, then leased keys are applied.</li>
 *   <li>{@code EventPostSync}: run {@link ExecutionPhase#AFTER_MOVEMENT_PACKET} work.</li>
 * </ul>
 *
 * Every task is cancelled on world change, disconnect, a new player entity, panic and shutdown;
 * a module's tasks are cancelled when it is disabled.
 */
public final class AutomationRuntime {

    public static final AutomationRuntime INSTANCE = new AutomationRuntime();

    /** Mouse travel (raw turn units) that counts as the player looking around. */
    private static final double MANUAL_TURN_THRESHOLD = 2.0;

    private final ActionCoordinator coordinator = new ActionCoordinator();
    private final ActionScheduler scheduler = new ActionScheduler(coordinator);
    private LocalPlayer lastPlayer;
    private long tick;
    private double pendingMouseTurn;

    private AutomationRuntime() {
        coordinator.setProbe(new LegacyResourceProbe());
        scheduler.setOwnerAlive(owner -> !(owner instanceof Module module) || module.isEnabled());
        scheduler.setErrorSink(e -> DebugLog.error("Automation callback failed", e));
    }

    /** Hooks module disable and runtime shutdown. Call once during client init. */
    public void install() {
        ModuleManager.addListener((name, enabled) -> {
            if (!enabled) cancelModuleTasks(name);
        });
    }

    public RuntimeShutdownParticipant shutdownParticipant() {
        return new RuntimeShutdownParticipant() {
            @Override
            public String id() {
                return "automation";
            }

            @Override
            public void shutdown(RuntimeShutdownContext context) {
                cancelAll("runtime shutdown");
            }
        };
    }

    public AutomationTask submit(AutomationTask task) {
        return scheduler.submit(task);
    }

    public void cancelOwner(Object owner, String reason) {
        scheduler.cancelOwner(owner, reason);
    }

    public boolean hasTasks(Object owner) {
        return scheduler.hasTasks(owner);
    }

    public ActionCoordinator coordinator() {
        return coordinator;
    }

    public ActionScheduler scheduler() {
        return scheduler;
    }

    public void cancelAll(String reason) {
        scheduler.cancelAll(reason);
        coordinator.revokeAll(ActionLease.RevokeReason.SHUTDOWN);
        pendingMouseTurn = 0.0;
    }

    /** Physical mouse motion from {@code MouseHandler.turnPlayer}; synthetic turns never reach here. */
    public void onManualMouseTurn(double dx, double dy) {
        pendingMouseTurn += Math.abs(dx) + Math.abs(dy);
    }

    private void cancelModuleTasks(String moduleName) {
        for (AutomationTask task : scheduler.tasks()) {
            if (task.owner() instanceof Module module && module.name().equals(moduleName)) {
                scheduler.cancel(task, "module disabled");
            }
        }
    }

    @EventHandler(priority = 1000)
    public void onRotationUpdate(RotationUpdateEvent event) {
        if (event.getType() == RotationUpdateEvent.Type.POST) {
            scheduler.runDeferred(ExecutionPhase.AFTER_ROTATION);
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        if (player != lastPlayer) {
            if (lastPlayer != null) cancelAll("player respawned");
            lastPlayer = player;
        }
        if (pendingMouseTurn >= MANUAL_TURN_THRESHOLD) coordinator.revokeForManualInput(ActionResource.ROTATION);
        pendingMouseTurn = 0.0;
        scheduler.tick(++tick);
    }

    /** Runs first: the event still carries the keys the player is physically holding. */
    @EventHandler(priority = 100_000)
    public void onPlayerMovementInput(MovementInputEvent event) {
        if (event.isForward() || event.isBackward() || event.isLeft() || event.isRight()) {
            coordinator.revokeForManualInput(ActionResource.MOVEMENT);
        }
        if (event.isJump()) coordinator.revokeForManualInput(ActionResource.JUMP);
        if (event.isSneak()) coordinator.revokeForManualInput(ActionResource.SNEAK);
    }

    /** Applies leased keys before ordinary modules, so safety filters such as SafeWalk still see them. */
    @EventHandler(priority = 1000)
    public void onApplyMovementInput(MovementInputEvent event) {
        MovementIntent intent = scheduler.movement();
        if (intent.hasMovement(coordinator)) {
            event.setForward(intent.forward());
            event.setBackward(intent.backward());
            event.setLeft(intent.left());
            event.setRight(intent.right());
        }
        if (intent.hasJump(coordinator)) event.setJump(intent.jump());
        if (intent.hasSneak(coordinator)) event.setSneak(intent.sneak());
        if (intent.hasSprint(coordinator)) event.setSprint(intent.sprint());
    }

    @EventHandler
    public void onPostSync(EventPostSync event) {
        scheduler.runDeferred(ExecutionPhase.AFTER_MOVEMENT_PACKET);
    }

    @EventHandler(priority = 1000)
    public void onWorldChange(WorldChangeEvent event) {
        cancelAll(event.reason() == WorldChangeEvent.Reason.DISCONNECT ? "disconnect" : "world change");
        lastPlayer = null;
    }
}
