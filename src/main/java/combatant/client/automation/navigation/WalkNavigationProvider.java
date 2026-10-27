/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.navigation;

import combatant.client.util.schematic.walk.LevelWalkTerrain;
import combatant.client.util.schematic.walk.WalkTerrain;
import net.minecraft.client.Minecraft;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The built-in provider: plans with {@code WalkPathfinder} over a {@code TerrainCache} in per-tick time slices,
 * follows the route with {@code RouteFollower}, and reports what it wants as a {@link NavigationIntent}. It never
 * reads or writes input.
 */
public final class WalkNavigationProvider implements NavigationProvider {

    public static final String ID = "walk";

    private final Supplier<? extends WalkTerrain> terrain;
    private final BooleanSupplier available;

    /** Walks the live client world. */
    public WalkNavigationProvider() {
        this(LevelWalkTerrain::new, () -> {
            Minecraft mc = Minecraft.getInstance();
            return mc.level != null && mc.player != null;
        });
    }

    /**
     * @param terrain   source of the world a handle plans in; called once per navigation (a fresh
     *                  {@code TerrainCache} wraps it)
     * @param available whether navigation can start now
     */
    public WalkNavigationProvider(Supplier<? extends WalkTerrain> terrain, BooleanSupplier available) {
        this.terrain = terrain;
        this.available = available;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean isAvailable() {
        return available.getAsBoolean();
    }

    @Override
    public NavigationHandle navigate(NavigationRequest request) {
        return new WalkNavigationHandle(request, terrain.get());
    }
}
