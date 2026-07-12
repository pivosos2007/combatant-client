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

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.MovementInputEvent;

import java.util.*;

public final class PlayerSimulationCache {

    public static final PlayerSimulationCache INSTANCE = new PlayerSimulationCache();

    private final Map<Player, SimulatedPlayerCache> otherPlayerCache = new WeakHashMap<>();
    private final Map<Player, RemotePlayerObservation> remoteObservations = new WeakHashMap<>();
    private SimulatedPlayerCache localPlayerCache;

    private PlayerSimulationCache() {
    }

    public static SimulatedPlayerCache getSimulationForOtherPlayers(Player player) {
        RemotePlayerObservation observation = INSTANCE.remoteObservations.computeIfAbsent(player, RemotePlayerObservation::new);
        observation.update(player);

        return INSTANCE.otherPlayerCache.computeIfAbsent(player,
                p -> new SimulatedPlayerCache(
                        new RemotePlayerSimulation(p, SimulatedInput.guessInput(p), observation)
                ));
    }

    public static SimulatedPlayerCache getSimulationForLocalPlayer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return null;
        }

        SimulatedPlayerCache cached = INSTANCE.localPlayerCache;
        if (cached != null) {
            return cached;
        }

        cached = new SimulatedPlayerCache(
                new LocalPlayerSimulation(
                        mc.player,
                        SimulatedInput.fromPlayer(mc.player)
                )
        );
        INSTANCE.localPlayerCache = cached;
        return cached;
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        otherPlayerCache.clear();
        localPlayerCache = null;
        pruneRemoteObservations();
    }

    @EventHandler
    private void onMovementInput(MovementInputEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            localPlayerCache = null;
            return;
        }

        localPlayerCache = new SimulatedPlayerCache(
                new LocalPlayerSimulation(
                        mc.player,
                        SimulatedInput.fromMovementEvent(mc.player, event)
                )
        );
    }

    private void pruneRemoteObservations() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            remoteObservations.clear();
            return;
        }

        Iterator<Player> iterator = remoteObservations.keySet().iterator();
        while (iterator.hasNext()) {
            Player player = iterator.next();
            if (player == null || player.isRemoved() || player.level() != mc.level) {
                iterator.remove();
            }
        }
    }

    public static final class SimulatedPlayerCache {
        private final PlayerMovementSimulation simulator;
        private final List<SimulatedPlayerSnapshot> snapshots = new ArrayList<>();
        private int simulatedTicks;

        private SimulatedPlayerCache(PlayerMovementSimulation simulator) {
            this.simulator = simulator;
            this.snapshots.add(new SimulatedPlayerSnapshot(simulator));
        }

        public SimulatedPlayerSnapshot getSnapshotAt(int ticks) {
            simulateUntil(ticks);
            return snapshots.get(ticks);
        }

        public boolean isRemoteFlightLike() {
            return simulator.motionKind() == RemotePlayerObservation.MotionKind.SERVER_FLIGHT
                    || simulator.motionKind() == RemotePlayerObservation.MotionKind.SURVIVAL_FLIGHT
                    || simulator.motionKind() == RemotePlayerObservation.MotionKind.HOVER
                    || simulator.motionKind() == RemotePlayerObservation.MotionKind.FROZEN_AIR;
        }

        public boolean isFrozenInAir() {
            return simulator.motionKind() == RemotePlayerObservation.MotionKind.FROZEN_AIR;
        }

        private void simulateUntil(int ticks) {
            while (simulatedTicks < ticks) {
                simulator.tick();
                snapshots.add(new SimulatedPlayerSnapshot(simulator));
                simulatedTicks++;
            }
        }
    }

    public record SimulatedPlayerSnapshot(Vec3 pos, double fallDistance, Vec3 velocity, boolean onGround,
                                          boolean clipLedged) {
        private SimulatedPlayerSnapshot(PlayerMovementSimulation simulator) {
            this(
                    simulator.position(),
                    simulator.simulatedFallDistance(),
                    simulator.simulatedVelocity(),
                    simulator.simulatedOnGround(),
                    simulator.simulatedClipLedged()
            );
        }
    }
}
