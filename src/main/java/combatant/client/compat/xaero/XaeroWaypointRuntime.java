/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.compat.xaero;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.common.HudMod;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.minimap.world.MinimapWorldManager;
import xaero.hud.path.XaeroPath;

import java.util.ArrayList;
import java.util.List;

final class XaeroWaypointRuntime {
    private XaeroWaypointRuntime() {
    }

    static XaeroWaypointStore.RuntimeAddResult addFromCurrentDimension(int x,
                                                                       int y,
                                                                       int z,
                                                                       String name,
                                                                       boolean dualDefault) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            return fail("No client world is loaded.");
        }

        MinimapSession session = currentSession();
        if (session == null) {
            return fail("Xaero minimap session is not ready.");
        }

        List<XaeroWaypointStore.Marker> added = new ArrayList<>(2);
        ResourceKey<Level> currentDim = mc.level.dimension();
        boolean overworld = currentDim == Level.OVERWORLD;
        boolean nether = currentDim == Level.NETHER;

        if (dualDefault && overworld) {
            addToWorld(session, Level.OVERWORLD, x, y, z, name, "overworld", added);
            addToWorld(session, Level.NETHER, XaeroWaypointStore.netherCoord(x), y, XaeroWaypointStore.netherCoord(z), name, "nether", added);
            return new XaeroWaypointStore.RuntimeAddResult(true,
                    "Added dual Xaero marker: " + XaeroWaypointStore.format(x, y, z)
                            + " / Nether " + XaeroWaypointStore.format(XaeroWaypointStore.netherCoord(x), y, XaeroWaypointStore.netherCoord(z)) + ".",
                    List.copyOf(added));
        }
        if (dualDefault && nether) {
            addToWorld(session, Level.NETHER, x, y, z, name, "nether", added);
            addToWorld(session, Level.OVERWORLD, x * 8, y, z * 8, name, "overworld", added);
            return new XaeroWaypointStore.RuntimeAddResult(true,
                    "Added dual Xaero marker: Nether " + XaeroWaypointStore.format(x, y, z)
                            + " / Overworld " + XaeroWaypointStore.format(x * 8, y, z * 8) + ".",
                    List.copyOf(added));
        }

        addToWorld(session, currentDim, x, y, z, name, XaeroWaypointStore.dimensionId(currentDim.identifier()), added);
        return new XaeroWaypointStore.RuntimeAddResult(true,
                "Added Xaero marker: " + XaeroWaypointStore.format(x, y, z) + ".",
                List.copyOf(added));
    }

    static boolean remove(XaeroWaypointStore.Marker marker) {
        if (!(marker.waypointSetHandle() instanceof WaypointSet set)
                || !(marker.waypointHandle() instanceof Waypoint waypoint)) {
            return true;
        }
        set.remove(waypoint);
        markChanged();
        return true;
    }

    static boolean exists(XaeroWaypointStore.Marker marker) {
        if (!(marker.waypointSetHandle() instanceof WaypointSet set)
                || !(marker.waypointHandle() instanceof Waypoint waypoint)) {
            return false;
        }
        for (Waypoint existing : set.getWaypoints()) {
            if (existing == waypoint) {
                return true;
            }
        }
        return false;
    }

    private static void addToWorld(MinimapSession session,
                                   ResourceKey<Level> dimension,
                                   int x,
                                   int y,
                                   int z,
                                   String name,
                                   String kind,
                                   List<XaeroWaypointStore.Marker> out) {
        MinimapWorld world = resolveWorld(session, dimension);
        if (world == null) {
            throw new IllegalStateException("Could not resolve Xaero world for " + dimension.identifier());
        }

        WaypointSet set = currentSet(world);
        Waypoint waypoint = new Waypoint(
                x,
                y,
                z,
                XaeroWaypointStore.displayName(name, kind),
                XaeroWaypointStore.DEFAULT_SYMBOL,
                WaypointColor.GOLD,
                WaypointPurpose.NORMAL,
                true,
                true
        );
        waypoint.setTemporary(true);
        waypoint.setYIncluded(true);
        waypoint.setDisabled(false);
        set.add(waypoint);
        out.add(XaeroWaypointStore.createMarker(
                XaeroWaypointStore.dimensionId(dimension.identifier()),
                x,
                y,
                z,
                waypoint.getName(),
                waypoint.getSymbol(),
                set.getName(),
                kind,
                set,
                waypoint
        ));
        markChanged();
    }

    private static WaypointSet currentSet(MinimapWorld world) {
        WaypointSet set = world.getCurrentWaypointSet();
        if (set != null) {
            return set;
        }
        String setId = world.getCurrentWaypointSetId();
        if (setId == null || setId.isBlank()) {
            setId = XaeroWaypointStore.SET;
            world.setCurrentWaypointSetId(setId);
        }
        set = world.getWaypointSet(setId);
        if (set == null) {
            world.addWaypointSet(setId);
            set = world.getWaypointSet(setId);
        }
        if (set == null) {
            throw new IllegalStateException("Could not resolve Xaero waypoint set.");
        }
        return set;
    }

    private static MinimapWorld resolveWorld(MinimapSession session, ResourceKey<Level> dimension) {
        Minecraft mc = Minecraft.getInstance();
        MinimapWorldManager manager = session.getWorldManager();
        if (mc != null && mc.level != null && mc.level.dimension() == dimension) {
            return manager.getCurrentWorld();
        }

        XaeroPath rootPath = session.getWorldState().getCurrentRootContainerPath();
        XaeroPath currentWorldPath = session.getWorldState().getCurrentWorldPath();
        if (rootPath == null || currentWorldPath == null) {
            return manager.getCurrentWorld();
        }

        String dimensionNode = session.getDimensionHelper().getDimensionDirectoryName(dimension);
        if (dimensionNode == null || dimensionNode.isBlank()) {
            return null;
        }

        String worldNode = null;
        try {
            boolean worldMap = HudMod.INSTANCE != null
                    && HudMod.INSTANCE.getSupportMods() != null
                    && HudMod.INSTANCE.getSupportMods().worldmap();
            worldNode = session.getWorldStateUpdater().getPotentialWorldNode(dimension, worldMap);
        } catch (Throwable ignored) {
            // Fallback below keeps the marker in the same multiworld node as the current dimension.
        }
        if (worldNode == null || worldNode.isBlank()) {
            worldNode = currentWorldPath.getLastNode();
        }

        XaeroPath worldPath = rootPath.resolve(dimensionNode).resolve(worldNode);
        MinimapWorld world = manager.getWorld(worldPath);
        if (world != null) {
            world.setDimId(dimension);
        }
        return world;
    }

    private static MinimapSession currentSession() {
        Object session = BuiltInHudModules.MINIMAP.getCurrentSession();
        return session instanceof MinimapSession minimapSession ? minimapSession : null;
    }

    private static void markChanged() {
        MinimapSession session = currentSession();
        if (session != null && session.getWaypointSession() != null) {
            session.getWaypointSession().setSetChangedTime(System.currentTimeMillis());
        }
        requestWorldMapRefresh();
    }

    private static void requestWorldMapRefresh() {
        try {
            Class<?> supportMods = Class.forName("xaero.map.mods.SupportMods");
            Object support = supportMods.getField("xaeroMinimap").get(null);
            if (support != null) {
                support.getClass().getMethod("requestWaypointsRefresh").invoke(support);
            }
        } catch (Throwable ignored) {
            // World Map is optional; minimap hover/list deletion works through the real Xaero set without it.
        }
    }

    private static XaeroWaypointStore.RuntimeAddResult fail(String message) {
        return new XaeroWaypointStore.RuntimeAddResult(false, message, List.of());
    }
}
