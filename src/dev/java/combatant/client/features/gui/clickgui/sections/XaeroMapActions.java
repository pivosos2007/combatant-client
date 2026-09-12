/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.sections;

import combatant.client.util.logging.DebugLog;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.map.MapProcessor;
import xaero.map.gui.ExportScreen;
import xaero.map.gui.MapTileSelection;
import xaero.map.mods.SupportMods;
import xaero.map.mods.gui.Waypoint;
import xaero.map.radar.tracker.PlayerTeleporter;
import xaero.map.radar.tracker.PlayerTrackerMapElement;
import xaero.map.teleport.MapTeleporter;

import java.io.IOException;

final class XaeroMapActions {
    private XaeroMapActions() {
    }

    static boolean available() {
        return SupportMods.minimap() && session() != null;
    }

    static void createWaypoint(MinimapWorld world, int x, int y, int z,
                               String name, String symbol, int colorIndex) {
        MinimapSession session = session();
        WaypointSet set = currentSet(world);
        if (session == null || set == null) return;
        String safeName = name == null || name.isBlank() ? "Waypoint" : name.trim();
        String safeSymbol = symbol == null || symbol.isBlank()
                ? safeName.substring(0, 1).toUpperCase()
                : symbol.substring(0, Math.min(2, symbol.length())).toUpperCase();
        xaero.common.minimap.waypoints.Waypoint waypoint =
                new xaero.common.minimap.waypoints.Waypoint(
                        x, y == Short.MAX_VALUE ? 0 : y, z,
                        safeName, safeSymbol, WaypointColor.fromIndex(Math.floorMod(colorIndex, 16)));
        waypoint.setYIncluded(y != Short.MAX_VALUE);
        set.add(waypoint);
        changed(session, world);
    }

    static void editWaypoint(MinimapWorld world, Waypoint wrapper,
                             String name, String symbol, int colorIndex) {
        MinimapSession session = session();
        if (session == null || wrapper == null
                || !(wrapper.getOriginal() instanceof xaero.common.minimap.waypoints.Waypoint waypoint)) return;
        if (name != null && !name.isBlank()) waypoint.setName(name.trim());
        if (symbol != null && !symbol.isBlank()) {
            waypoint.setSymbol(symbol.substring(0, Math.min(2, symbol.length())).toUpperCase());
        }
        waypoint.setWaypointColor(WaypointColor.fromIndex(Math.floorMod(colorIndex, 16)));
        changed(session, world);
    }

    static void createTemporary(MinimapWorld world, int x, int y, int z, double dimensionScale) {
        MinimapSession session = session();
        if (session == null || world == null) return;
        session.getWaypointSession().getTemporaryHandler().createTemporaryWaypoint(
                world, x, y == Short.MAX_VALUE ? 0 : y, z, y != Short.MAX_VALUE, dimensionScale);
        SupportMods.xaeroMinimap.requestWaypointsRefresh();
    }

    static void toggleDisabled(MinimapWorld world, Waypoint wrapper) {
        if (wrapper == null
                || !(wrapper.getOriginal() instanceof xaero.common.minimap.waypoints.Waypoint waypoint)) return;
        waypoint.setDisabled(!waypoint.isDisabled());
        wrapper.setDisabled(waypoint.isDisabled());
        changed(session(), world);
    }

    static void toggleTemporary(MinimapWorld world, Waypoint wrapper) {
        if (wrapper == null
                || !(wrapper.getOriginal() instanceof xaero.common.minimap.waypoints.Waypoint waypoint)) return;
        waypoint.setTemporary(!waypoint.isTemporary());
        wrapper.setTemporary(waypoint.isTemporary());
        changed(session(), world);
    }

    static void deleteWaypoint(MinimapWorld world, Waypoint wrapper) {
        if (world == null || wrapper == null
                || !(wrapper.getOriginal() instanceof xaero.common.minimap.waypoints.Waypoint waypoint)) return;
        for (WaypointSet set : world.getIterableWaypointSets()) set.remove(waypoint);
        changed(session(), world);
    }

    static void teleportWaypoint(MinimapWorld world, Waypoint wrapper) {
        MinimapSession session = session();
        Screen screen = ClientScreen.current();
        if (session == null || screen == null || world == null || wrapper == null
                || !(wrapper.getOriginal() instanceof xaero.common.minimap.waypoints.Waypoint waypoint)) return;
        session.getWaypointSession().getTeleport().teleportToWaypoint(waypoint, world, screen);
    }

    static void shareWaypoint(MinimapWorld world, Waypoint wrapper) {
        MinimapSession session = session();
        Screen screen = ClientScreen.current();
        if (session == null || screen == null || world == null || wrapper == null
                || !(wrapper.getOriginal() instanceof xaero.common.minimap.waypoints.Waypoint waypoint)) return;
        session.getWaypointSession().getSharing().shareWaypoint(screen, waypoint, world);
    }

    static void shareLocation(MinimapWorld world, int x, int y, int z) {
        MinimapSession session = session();
        Screen screen = ClientScreen.current();
        if (session == null || screen == null || world == null) return;
        xaero.common.minimap.waypoints.Waypoint location =
                new xaero.common.minimap.waypoints.Waypoint(
                        x, y == Short.MAX_VALUE ? 0 : y, z,
                        "Shared Location", "S", WaypointColor.getRandom());
        location.setYIncluded(y != Short.MAX_VALUE);
        session.getWaypointSession().getSharing().shareWaypoint(screen, location, world);
    }

    static void teleportMap(MapProcessor processor, int x, int y, int z,
                            ResourceKey<Level> dimension) {
        Screen screen = ClientScreen.current();
        if (screen == null || processor == null || processor.getMapWorld() == null) return;
        new MapTeleporter().teleport(screen, processor.getMapWorld(), x,
                y == Short.MAX_VALUE ? Short.MAX_VALUE : y + 1, z, dimension);
    }

    static void teleportPlayer(MapProcessor processor, PlayerTrackerMapElement<?> player) {
        Screen screen = ClientScreen.current();
        if (screen == null || processor == null || processor.getMapWorld() == null || player == null) return;
        new PlayerTeleporter().teleportToPlayer(screen, processor.getMapWorld(), player);
    }

    static void export(MapProcessor processor, int startChunkX, int startChunkZ,
                       int endChunkX, int endChunkZ) {
        Screen parent = ClientScreen.current();
        if (parent == null || processor == null) return;
        MapTileSelection selection = new MapTileSelection(startChunkX, startChunkZ);
        selection.setEnd(endChunkX, endChunkZ);
        ClientScreen.show(new ExportScreen(parent, parent, processor, selection));
    }

    private static WaypointSet currentSet(MinimapWorld world) {
        if (world == null) return null;
        WaypointSet set = world.getCurrentWaypointSet();
        if (set != null) return set;
        String id = world.getCurrentWaypointSetId();
        if (id == null || id.isBlank()) id = "gui.xaero_default";
        world.addWaypointSet(id);
        world.setCurrentWaypointSetId(id);
        return world.getCurrentWaypointSet();
    }

    private static MinimapSession session() {
        Object current = BuiltInHudModules.MINIMAP.getCurrentSession();
        return current instanceof MinimapSession session ? session : null;
    }

    private static void changed(MinimapSession session, MinimapWorld world) {
        if (session == null || world == null) return;
        session.getWaypointSession().setSetChangedTime(System.currentTimeMillis());
        try {
            session.getWorldManagerIO().saveWorld(world);
        } catch (IOException error) {
            DebugLog.warnOnce("xaero-map-waypoint-save", "Failed to save a Xaero waypoint change", error);
        }
        SupportMods.xaeroMinimap.requestWaypointsRefresh();
    }
}
