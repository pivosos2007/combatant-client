/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.compat.baritone;

import combatant.client.automation.navigation.AbstractNavigationHandle;
import combatant.client.automation.navigation.FailedNavigationHandle;
import combatant.client.automation.navigation.NavigationFailure;
import combatant.client.automation.navigation.NavigationHandle;
import combatant.client.automation.navigation.NavigationProvider;
import combatant.client.automation.navigation.NavigationRequest;
import combatant.client.automation.navigation.PlayerSample;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;

/**
 * Navigation through Baritone's public API, when the Baritone mod is installed. Baritone is not a build
 * dependency: nothing from {@code baritone.*} is named at compile time, and its classes are looked up by
 * reflection only after the mod check says the mod is loaded. Without the mod this provider is simply
 * unavailable.
 *
 * <p>Baritone moves the player itself, so its handles report {@code drivesPlayer()}: the navigate action keeps
 * its movement leases (nothing else fights Baritone) and cancels the handle, which cancels Baritone, when the
 * player takes over.</p>
 */
public final class BaritoneNavigationProvider implements NavigationProvider {

    public static final String ID = "baritone";

    private static final String[] MOD_IDS = {"baritone", "baritone-meteor", "baritone_meteor"};

    private final BooleanSupplier modLoaded;
    private Api api;
    private boolean bindingFailed;

    public BaritoneNavigationProvider() {
        this(BaritoneNavigationProvider::fabricHasBaritone);
    }

    /** For tests: {@code modLoaded} replaces the Fabric mod lookup. */
    public BaritoneNavigationProvider(BooleanSupplier modLoaded) {
        this.modLoaded = modLoaded;
    }

    private static boolean fabricHasBaritone() {
        try {
            FabricLoader loader = FabricLoader.getInstance();
            for (String id : MOD_IDS) if (loader.isModLoaded(id)) return true;
        } catch (Throwable ignored) {
            // No Fabric loader (headless run): no Baritone.
        }
        return false;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean isAvailable() {
        return bind() != null;
    }

    /** Resolves Baritone's API once, and only when the mod check passes. */
    private synchronized Api bind() {
        if (api != null) return api;
        if (bindingFailed || !modLoaded.getAsBoolean()) return null;
        try {
            api = new Api();
        } catch (ReflectiveOperationException | LinkageError e) {
            bindingFailed = true;
        }
        return api;
    }

    @Override
    public NavigationHandle navigate(NavigationRequest request) {
        Api bound = bind();
        if (bound == null) return new FailedNavigationHandle(request, NavigationFailure.UNAVAILABLE, "Baritone is not loaded");
        return new BaritoneHandle(request, bound);
    }

    /** Reflective view of the parts of Baritone's API used here. */
    private static final class Api {
        private final Method getProvider;
        private final Method getPrimaryBaritone;
        private final Method getCustomGoalProcess;
        private final Method getPathingBehavior;
        private final Method setGoalAndPath;
        private final Method isActive;
        private final Method cancelEverything;
        private final Constructor<?> goalBlock;
        private final Constructor<?> goalNear;

        Api() throws ReflectiveOperationException {
            ClassLoader loader = BaritoneNavigationProvider.class.getClassLoader();
            Class<?> baritoneApi = Class.forName("baritone.api.BaritoneAPI", true, loader);
            Class<?> provider = Class.forName("baritone.api.IBaritoneProvider", true, loader);
            Class<?> baritone = Class.forName("baritone.api.IBaritone", true, loader);
            Class<?> goal = Class.forName("baritone.api.pathing.goals.Goal", true, loader);
            Class<?> goalProcess = Class.forName("baritone.api.process.ICustomGoalProcess", true, loader);
            Class<?> pathing = Class.forName("baritone.api.behavior.IPathingBehavior", true, loader);
            getProvider = baritoneApi.getMethod("getProvider");
            getPrimaryBaritone = provider.getMethod("getPrimaryBaritone");
            getCustomGoalProcess = baritone.getMethod("getCustomGoalProcess");
            getPathingBehavior = baritone.getMethod("getPathingBehavior");
            setGoalAndPath = goalProcess.getMethod("setGoalAndPath", goal);
            isActive = goalProcess.getMethod("isActive");
            cancelEverything = pathing.getMethod("cancelEverything");
            goalBlock = Class.forName("baritone.api.pathing.goals.GoalBlock", true, loader)
                    .getConstructor(int.class, int.class, int.class);
            goalNear = Class.forName("baritone.api.pathing.goals.GoalNear", true, loader)
                    .getConstructor(BlockPos.class, int.class);
        }

        private Object baritone() throws ReflectiveOperationException {
            return getPrimaryBaritone.invoke(getProvider.invoke(null));
        }

        Object goalFor(NavigationRequest request) throws ReflectiveOperationException {
            return switch (request.kind()) {
                case BLOCK -> goalBlock.newInstance(request.x(), request.y(), request.z());
                case NEAR -> goalNear.newInstance(new BlockPos(request.x(), request.y(), request.z()),
                        Math.max(1, (int) Math.ceil(request.range())));
                // Baritone measures from the block to the feet; the eyes sit about 1.6 higher.
                case REACH -> goalNear.newInstance(new BlockPos(request.x(), request.y(), request.z()),
                        Math.max(1, (int) Math.floor(request.range()) - 1));
            };
        }

        void start(NavigationRequest request) throws ReflectiveOperationException {
            setGoalAndPath.invoke(getCustomGoalProcess.invoke(baritone()), goalFor(request));
        }

        boolean active() throws ReflectiveOperationException {
            return (Boolean) isActive.invoke(getCustomGoalProcess.invoke(baritone()));
        }

        void cancel() throws ReflectiveOperationException {
            cancelEverything.invoke(getPathingBehavior.invoke(baritone()));
        }
    }

    private static final class BaritoneHandle extends AbstractNavigationHandle {
        /** Ticks Baritone may report inactive right after the goal was set before that counts as the end. */
        private static final int START_GRACE_TICKS = 5;

        private final Api api;
        private boolean started;
        private int ticksSinceStart;
        private double remaining = Double.NaN;

        BaritoneHandle(NavigationRequest request, Api api) {
            super(request);
            this.api = api;
        }

        @Override
        protected void step(PlayerSample p) {
            remaining = Math.sqrt(request.distanceSqr(p.x, p.y, p.z));
            if (request.satisfiedBy(p.x, p.y, p.z, p.eyeHeight)) {
                succeed();
                return;
            }
            try {
                if (!started) {
                    api.start(request);
                    started = true;
                    return;
                }
                ticksSinceStart++;
                if (!api.active() && ticksSinceStart > START_GRACE_TICKS) {
                    fail(NavigationFailure.NO_PATH, "Baritone stopped before reaching the goal");
                }
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                fail(NavigationFailure.ERROR, "Baritone: " + cause);
            } catch (ReflectiveOperationException e) {
                fail(NavigationFailure.ERROR, "Baritone API: " + e);
            }
        }

        @Override
        protected void onFinished(combatant.client.automation.navigation.NavigationResult result) {
            if (!started) return;
            try {
                api.cancel();
            } catch (ReflectiveOperationException ignored) {
                // Baritone vanished mid-run; nothing left to stop.
            }
        }

        @Override
        public boolean drivesPlayer() {
            return true;
        }

        @Override
        public double remainingDistance() {
            return remaining;
        }

        @Override
        public int expandedNodes() {
            return -1;
        }
    }
}
