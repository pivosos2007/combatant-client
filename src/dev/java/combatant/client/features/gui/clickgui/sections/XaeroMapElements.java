/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.sections;

import combatant.client.compat.xaero.XaeroIntegration;
import combatant.client.compat.xaero.XaeroWaypointSnapshot;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.MatteHudStyle;
import combatant.client.render.map.MapScreenPoint;
import combatant.client.render.map.MapViewport;
import combatant.client.util.text.LegacyTextUtil;
import combatant.client.util.text.TextRenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import xaero.common.HudMod;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.common.config.option.MinimapProfiledConfigOptions;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.radar.RadarSession;
import xaero.hud.minimap.radar.state.RadarList;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapDimensionHelper;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.path.XaeroPath;
import xaero.lib.client.config.ClientConfigManager;
import xaero.lib.common.config.option.ConfigOption;
import xaero.map.MapProcessor;
import xaero.map.WorldMap;
import xaero.map.common.config.option.WorldMapProfiledConfigOptions;
import xaero.map.config.primary.option.WorldMapPrimaryClientConfigOptions;
import xaero.map.mods.SupportMods;
import xaero.map.mods.SupportXaeroMinimap;
import xaero.map.radar.tracker.PlayerTrackerMapElement;
import xaero.map.world.MapDimension;
import xaero.map.world.MapWorld;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

final class XaeroMapElements {
    private final List<Element> visible = new ArrayList<>();
    private Element hovered;
    private MinimapWorld waypointWorld;
    private double waypointDimensionDivision = 1.0;

    Snapshot collect(MapProcessor processor, MapDimension dimension, double userScale) {
        visible.clear();
        waypointWorld = null;
        waypointDimensionDivision = 1.0;
        collectXaeroWaypoints(processor, dimension, userScale);
        collectCombatantWaypoints(dimension);
        collectRadarEntities(processor, dimension);
        collectTrackedPlayers(processor, dimension);
        visible.sort(Comparator.comparingInt(Element::priority));
        return new Snapshot(List.copyOf(visible), waypointWorld, waypointDimensionDivision);
    }

    Element render(Snapshot snapshot, MapViewport viewport, float mouseX, float mouseY) {
        hovered = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Element element : snapshot.elements()) {
            MapScreenPoint point = viewport.project(element.worldX(), element.worldZ());
            if (!viewport.screenBounds().contains(point.x(), point.y())) continue;
            double dx = mouseX - point.x();
            double dy = mouseY - point.y();
            double distance = dx * dx + dy * dy;
            double hitRadius = element.kind() == Kind.WAYPOINT ? 14.0 : (element.kind() == Kind.ENTITY ? 12.0 : 16.0);
            if (element.interactive() && distance <= hitRadius * hitRadius && distance < bestDistance) {
                hovered = element;
                bestDistance = distance;
            }
        }

        for (Element element : snapshot.elements()) {
            MapScreenPoint point = viewport.project(element.worldX(), element.worldZ());
            if (!viewport.screenBounds().contains(point.x(), point.y())) continue;
            boolean highlighted = element == hovered;
            if (element.kind() == Kind.PLAYER) {
                drawPlayer(point, element, highlighted);
            } else if (element.kind() == Kind.ENTITY) {
                drawEntity(point, element, highlighted);
            } else {
                drawWaypoint(point, element, highlighted);
            }
        }
        return hovered;
    }

    Element hovered() {
        return hovered;
    }

    MinimapWorld waypointWorld() {
        return waypointWorld;
    }

    @SuppressWarnings("unchecked")
    private void collectXaeroWaypoints(MapProcessor processor, MapDimension dimension, double userScale) {
        if (!SupportMods.minimap()) return;
        ClientConfigManager worldConfig = WorldMap.INSTANCE.getConfigs().getClientConfigManager();
        if (!(Boolean) worldConfig.getEffective((ConfigOption<Boolean>) WorldMapProfiledConfigOptions.WAYPOINTS)
                || !(Boolean) worldConfig.getEffective((ConfigOption<Boolean>) WorldMapProfiledConfigOptions.RENDER_WAYPOINTS)) {
            return;
        }

        Object currentSession = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (!(currentSession instanceof MinimapSession session)) return;
        waypointWorld = resolveWaypointWorld(session, processor.getMapWorld(), dimension);
        if (waypointWorld == null) return;

        boolean allSets = (Boolean) HudMod.INSTANCE.getHudConfigs().getClientConfigManager()
                .getEffective((ConfigOption<Boolean>) MinimapProfiledConfigOptions.WAYPOINTS_ALL_SETS);
        boolean deathpoints = (Boolean) HudMod.INSTANCE.getHudConfigs().getClientConfigManager()
                .getEffective((ConfigOption<Boolean>) MinimapProfiledConfigOptions.DEATHPOINTS);
        boolean showDisabled = (Boolean) worldConfig.getPrimaryConfigManager().getEffective(
                (ConfigOption<Boolean>) WorldMapPrimaryClientConfigOptions.DISPLAY_DISABLED_WAYPOINTS);
        double minLocalZoom = (Double) worldConfig.getEffective(
                (ConfigOption<Double>) WorldMapProfiledConfigOptions.MIN_ZOOM_LOCAL_WAYPOINTS);
        double dimDiv = dimensionDivision(session, processor, dimension, waypointWorld);
        waypointDimensionDivision = dimDiv;
        SupportXaeroMinimap minimap = SupportMods.xaeroMinimap;

        Iterable<WaypointSet> sets;
        if (allSets) {
            sets = waypointWorld.getIterableWaypointSets();
        } else {
            WaypointSet currentSet = waypointWorld.getCurrentWaypointSet();
            sets = currentSet == null ? List.of() : List.of(currentSet);
        }
        for (WaypointSet set : sets) {
            if (set == null) continue;
            for (xaero.common.minimap.waypoints.Waypoint source : set.getWaypoints()) {
                if (!showDisabled && source.isDisabled()) continue;
                if ((source.getWaypointType() == 1 || source.getWaypointType() == 2) && !deathpoints) continue;
                if (!source.isGlobal() && userScale < minLocalZoom) continue;
                xaero.map.mods.gui.Waypoint waypoint = minimap.convertWaypoint(source, true, set.getName(), dimDiv);
                int rgb = waypoint.getColor() & 0x00FFFFFF;
                visible.add(new Element(
                        Kind.WAYPOINT,
                        "xaero:" + System.identityHashCode(source),
                        waypoint.getRenderX(),
                        waypoint.getRenderZ(),
                        styled(Component.literal(waypoint.getName())),
                        waypoint.getSymbol(),
                        0xFF000000 | rgb,
                        waypoint.isDisabled(),
                        true,
                        100,
                        waypoint
                ));
            }
        }
    }

    private void collectCombatantWaypoints(MapDimension dimension) {
        if (!SupportMods.minimap()) return;
        for (XaeroWaypointSnapshot waypoint : XaeroIntegration.snapshots(XaeroIntegration.RenderTarget.WORLD_MAP)) {
            double dimensionScale = waypoint.coordinateSpace() == XaeroWaypointSnapshot.CoordinateSpace.OVERWORLD
                    && dimension.getDimId().equals(Level.NETHER) ? 0.125 : 1.0;
            int color = waypoint.color() == XaeroWaypointSnapshot.Color.GOLD ? 0xFFFFB82E : 0xFFFF4D57;
            visible.add(new Element(
                    Kind.WAYPOINT,
                    waypoint.id(),
                    waypoint.x() * dimensionScale,
                    waypoint.z() * dimensionScale,
                    styled(Component.literal(waypoint.name())),
                    waypoint.symbol(),
                    color,
                    false,
                    false,
                    110,
                    waypoint
            ));
        }
    }

    @SuppressWarnings("unchecked")
    private void collectRadarEntities(MapProcessor processor, MapDimension dimension) {
        if (!SupportMods.minimap()) return;
        ClientConfigManager config = WorldMap.INSTANCE.getConfigs().getClientConfigManager();
        if (!(Boolean) config.getEffective((ConfigOption<Boolean>) WorldMapProfiledConfigOptions.MINIMAP_RADAR)) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || minecraft.player == null) return;
        if (!dimension.getDimId().equals(minecraft.level.dimension())) return;
        Object currentSession = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (!(currentSession instanceof MinimapSession session)) return;
        RadarSession radar = session.getRadarSession();
        if (radar == null) return;
        Entity camera = minecraft.getCameraEntity();
        radar.update(minecraft.level, camera != null ? camera : minecraft.player, minecraft.player);
        double divisor = processor.getWorldDimensionTypeRegistry() == null
                ? 1.0
                : dimension.calculateDimDiv(
                processor.getWorldDimensionTypeRegistry(), minecraft.player.level().dimensionType());
        if (!Double.isFinite(divisor) || divisor == 0.0) divisor = 1.0;
        for (RadarList list : radar.getState().getRadarLists()) {
            if (list == null) continue;
            for (Entity entity : list.getEntities()) {
                if (entity == null || entity == minecraft.player || entity.isRemoved()) continue;
                int color = entity instanceof Player ? 0xFF55D6BE
                        : entity instanceof Monster ? 0xFFFF6575
                        : entity instanceof ItemEntity ? 0xFFFFC65C
                        : entity instanceof LivingEntity ? 0xFF8FE388
                        : 0xFFB8C1CC;
                visible.add(new Element(
                        Kind.ENTITY,
                        "entity:" + entity.getUUID(),
                        entity.getX() / divisor,
                        entity.getZ() / divisor,
                        styled(entity.getDisplayName()),
                        "",
                        color,
                        false,
                        true,
                        entity instanceof Player ? 185 : 150,
                        entity
                ));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void collectTrackedPlayers(MapProcessor processor, MapDimension dimension) {
        ClientConfigManager config = WorldMap.INSTANCE.getConfigs().getClientConfigManager();
        if (!(Boolean) config.getEffective(
                (ConfigOption<Boolean>) WorldMapProfiledConfigOptions.DISPLAY_TRACKED_PLAYERS)) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) return;
        WorldMap.trackedPlayerRenderer.update(minecraft);
        double mapDimScale = dimension.calculateDimScale(processor.getWorldDimensionTypeRegistry());
        for (PlayerTrackerMapElement<?> player : WorldMap.trackedPlayerRenderer.getCollector().getElements()) {
            if (!minecraft.level.dimension().equals(player.getDimension())
                    && !dimension.getDimId().equals(player.getDimension())) continue;
            double scale = minecraft.level.dimension().equals(player.getDimension()) ? 1.0 : mapDimScale;
            UUID playerId = player.getPlayerId();
            PlayerInfo info = minecraft.getConnection() == null ? null : minecraft.getConnection().getPlayerInfo(playerId);
            Component name = info == null
                    ? Component.literal(playerId.toString())
                    : (info.getTabListDisplayName() != null
                    ? info.getTabListDisplayName()
                    : Component.literal(info.getProfile().name()));
            name = styled(name);
            visible.removeIf(element -> element.handle() instanceof Player local && local.getUUID().equals(playerId));
            visible.add(new Element(
                    Kind.PLAYER,
                    "player:" + playerId,
                    player.getX() * scale,
                    player.getZ() * scale,
                    name,
                    "",
                    0xFF55D6BE,
                    false,
                    true,
                    200,
                    player
            ));
        }
    }

    @SuppressWarnings("unchecked")
    private static MinimapWorld resolveWaypointWorld(MinimapSession session,
                                                     MapWorld mapWorld,
                                                     MapDimension dimension) {
        XaeroPath root = session.getWorldState().getAutoRootContainerPath();
        if (root == null) return null;
        String dimensionNode = session.getDimensionHelper().getDimensionDirectoryName(dimension.getDimId());
        String worldNode = mapWorld.isMultiplayer() ? dimension.getCurrentMultiworld() : "waypoints";
        if (worldNode == null || worldNode.isBlank()) worldNode = "waypoints";
        MinimapWorld mapped = session.getWorldManager().getWorld(root.resolve(dimensionNode).resolve(worldNode));
        boolean onlyCurrentMap = (Boolean) WorldMap.INSTANCE.getConfigs().getClientConfigManager()
                .getPrimaryConfigManager().getEffective(
                        (ConfigOption<Boolean>) WorldMapPrimaryClientConfigOptions.ONLY_CURRENT_MAP_WAYPOINTS);
        return onlyCurrentMap ? mapped : session.getWorldManager().getCurrentWorld();
    }

    private static double dimensionDivision(MinimapSession session,
                                            MapProcessor processor,
                                            MapDimension mapDimension,
                                            MinimapWorld waypointWorld) {
        if (waypointWorld.getContainer() == null || processor.getWorldDimensionTypeRegistry() == null) return 1.0;
        MinimapDimensionHelper helper = session.getDimensionHelper();
        ResourceKey<Level> waypointDimensionId = helper.getDimensionKeyForDirectoryName(
                waypointWorld.getContainer().getPath().getLastNode());
        MapDimension waypointMapDimension = processor.getMapWorld().getDimension(waypointDimensionId);
        DimensionType waypointType = MapDimension.getDimensionType(
                waypointMapDimension, waypointDimensionId, processor.getWorldDimensionTypeRegistry());
        DimensionType mapType = MapDimension.getDimensionType(
                mapDimension, mapDimension.getDimId(), processor.getWorldDimensionTypeRegistry());
        double waypointScale = waypointType == null ? 1.0 : waypointType.coordinateScale();
        double mapScale = mapType == null ? 1.0 : mapType.coordinateScale();
        return mapScale / waypointScale;
    }

    private static void drawWaypoint(MapScreenPoint point, Element element, boolean highlighted) {
        Renderer2D renderer = Renderer2D.COLOR;
        TextRenderer medium = ClickGuiRenderer.getOnestMedium();
        TextRenderer bold = ClickGuiRenderer.getOnestBold();
        float iconSize = highlighted ? 18.0f : 16.0f;
        float iconX = (float) point.x() - iconSize * 0.5f;
        float iconY = (float) point.y() - iconSize;
        int color = element.disabled() ? 0xFF7B8491 : element.color();

        MatteHudStyle.drawCompactPlate(renderer, iconX - 2.5f, iconY - 2.5f,
                iconSize + 5.0f, iconSize + 5.0f, 5.0f, highlighted ? 1.0f : 0.88f);
        renderer.svg("map-pin", iconX, iconY, iconSize, iconSize,
                SvgRenderOptions.overrideColor(color));
        if (element.symbol() != null && !element.symbol().isBlank()) {
            String symbol = element.symbol().substring(0, Math.min(2, element.symbol().length()));
            float symbolSize = symbol.length() > 1 ? 5.2f : 6.2f;
            float symbolWidth = ClickGuiRenderer.textWidth(bold, symbol, symbolSize);
            ClickGuiRenderer.drawText(bold, symbol,
                    (float) point.x() - symbolWidth * 0.5f,
                    iconY + 4.1f,
                    symbolSize,
                    0xFFF7FAFC,
                    false);
        }
        if (highlighted) drawLabel(element.name(), (float) point.x(), iconY - 5.0f, color);
    }

    private static void drawPlayer(MapScreenPoint point, Element element, boolean highlighted) {
        Renderer2D renderer = Renderer2D.COLOR;
        float size = highlighted ? 18.0f : 16.0f;
        float x = (float) point.x() - size * 0.5f;
        float y = (float) point.y() - size * 0.5f;
        MatteHudStyle.drawCompactPlate(renderer, x - 2.5f, y - 2.5f, size + 5.0f, size + 5.0f,
                6.0f, highlighted ? 1.0f : 0.9f);
        renderer.svg("users-round", x, y, size, size, SvgRenderOptions.overrideColor(element.color()));
        if (highlighted) drawLabel(element.name(), (float) point.x(), y - 5.0f, element.color());
    }

    private static void drawEntity(MapScreenPoint point, Element element, boolean highlighted) {
        Renderer2D renderer = Renderer2D.COLOR;
        float size = highlighted ? 12.5f : 10.5f;
        float x = (float) point.x() - size * 0.5f;
        float y = (float) point.y() - size * 0.5f;
        String icon = element.handle() instanceof Player ? "users-round"
                : element.handle() instanceof Monster ? "crosshair"
                : element.handle() instanceof ItemEntity ? "package"
                : "radar";
        if (highlighted) {
            MatteHudStyle.drawCompactPlate(renderer, x - 2.0f, y - 2.0f, size + 4.0f, size + 4.0f,
                    5.0f, 0.92f);
        }
        renderer.svg(icon, x, y, size, size, SvgRenderOptions.overrideColor(element.color()));
        if (highlighted) drawLabel(element.name(), (float) point.x(), y - 4.0f, element.color());
    }

    private static void drawLabel(Component value, float centerX, float bottomY, int accent) {
        if (value == null || value.getString().isBlank()) return;
        float size = 10.0f;
        List<TextRenderUtil.Part> parts = TextRenderUtil.flattenStyled(styled(value), 0xFFF5F8FC);
        float textWidth = styledWidth(parts, size);
        float x = centerX - textWidth * 0.5f;
        float y = bottomY - size;
        MatteHudStyle.drawTelemetryPlate(Renderer2D.COLOR, x - 4.0f, y - 2.0f,
                textWidth + 8.0f, size + 4.0f, 4.0f, 1.0f, accent);
        drawStyled(parts, x, y, size);
    }

    private static float styledWidth(List<TextRenderUtil.Part> parts, float size) {
        float width = 0.0f;
        for (TextRenderUtil.Part part : parts) {
            TextRenderer font = part.bold() ? ClickGuiRenderer.getOnestBold() : ClickGuiRenderer.getOnestMedium();
            width += ClickGuiRenderer.textWidth(font, part.text(), size);
        }
        return width;
    }

    private static void drawStyled(List<TextRenderUtil.Part> parts, float x, float y, float size) {
        float cursor = x;
        for (TextRenderUtil.Part part : parts) {
            if (part.text() == null || part.text().isEmpty()) continue;
            TextRenderer font = part.bold() ? ClickGuiRenderer.getOnestBold() : ClickGuiRenderer.getOnestMedium();
            float width = ClickGuiRenderer.textWidth(font, part.text(), size);
            ClickGuiRenderer.drawText(font, part.text(), cursor, y, size, part.color(), false);
            if (part.underline()) {
                Renderer2D.COLOR.quad(cursor, y + size + 0.3f, width, 0.75f, part.color());
            }
            if (part.strikethrough()) {
                Renderer2D.COLOR.quad(cursor, y + size * 0.55f, width, 0.75f, part.color());
            }
            cursor += width;
        }
    }

    private static Component styled(Component component) {
        return LegacyTextUtil.convertLegacyCodesRobust(component == null ? Component.empty() : component);
    }

    record Snapshot(List<Element> elements, MinimapWorld waypointWorld, double waypointDimensionDivision) {
        static Snapshot empty() {
            return new Snapshot(List.of(), null, 1.0);
        }
    }

    enum Kind {
        WAYPOINT,
        PLAYER,
        ENTITY
    }

    record Element(Kind kind,
                   String id,
                   double worldX,
                   double worldZ,
                   Component name,
                   String symbol,
                   int color,
                   boolean disabled,
                   boolean interactive,
                   int priority,
                   Object handle) {
        String plainName() {
            return name == null ? "" : LegacyTextUtil.stripLegacy(name.getString());
        }
    }

}
