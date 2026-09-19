/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.sections;

import combatant.client.compat.xaero.XaeroIntegration;
import combatant.client.compat.xaero.XaeroMinimapIntegration;
import combatant.client.compat.xaero.XaeroWaypointSnapshot;
import combatant.client.config.subsystem.MapUiConfig;
import combatant.client.features.map.location.PlayerLocationService;
import combatant.client.features.relations.CategoryService;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.draw.UiBackdropRequest;
import combatant.client.render.engine.renderer.ui.draw.UiLiquidGlassMaterial;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.map.MapScreenPoint;
import combatant.client.render.map.MapPlayerMarkerRenderer;
import combatant.client.render.map.MapViewport;
import combatant.client.util.text.LegacyTextUtil;
import combatant.client.util.text.TextRenderUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import xaero.common.HudMod;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.common.config.option.MinimapProfiledConfigOptions;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.radar.RadarSession;
import xaero.hud.minimap.radar.category.setting.EntityRadarCategorySettings;
import xaero.hud.minimap.radar.state.RadarList;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapDimensionHelper;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.path.XaeroPath;
import xaero.hud.render.TextureLocations;
import xaero.lib.client.config.ClientConfigManager;
import xaero.lib.common.config.option.ConfigOption;
import xaero.map.MapProcessor;
import xaero.map.WorldMap;
import xaero.map.element.MapElementGraphics;
import xaero.map.icon.XaeroIcon;
import xaero.map.icon.XaeroIconAtlas;
import xaero.map.common.config.option.WorldMapProfiledConfigOptions;
import xaero.map.config.primary.option.WorldMapPrimaryClientConfigOptions;
import xaero.map.mods.SupportMods;
import xaero.map.mods.SupportXaeroMinimap;
import xaero.map.radar.tracker.PlayerTrackerMapElement;
import xaero.map.world.MapDimension;
import xaero.map.world.MapWorld;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Map-content HUD layer: waypoints, radar entities/loot, tracked players and hover cards only. */
final class XaeroMapElements {
    private static final float XAERO_RADAR_ICON_CONTENT_SIZE = 62.0f;
    private static final float XAERO_WORLD_MAP_REFERENCE_SHORT_SIDE = 1080.0f;
    private static final float XAERO_RADAR_ICON_HIT_HALF_SIZE = 16.0f;
    private static final float XAERO_RADAR_DOT_HIT_HALF_SIZE = 6.0f;
    private static final float MARKER_LABEL_GAP = 2.5f;
    private final List<Element> visible = new ArrayList<>();
    private final Map<String, Float> waypointHoverAnims = new LinkedHashMap<>();
    private final MapElementGraphics xaeroGraphics = new MapElementGraphics(new PoseStack());
    private Element hovered;
    private float hoveredTopExtent;
    private boolean tabDown;
    private double playerMapX = Double.NaN;
    private double playerMapZ = Double.NaN;
    private MinimapWorld waypointWorld;
    private double waypointDimensionDivision = 1.0;

    Snapshot collect(MapProcessor processor, MapDimension dimension, double userScale) {
        visible.clear();
        waypointWorld = null;
        waypointDimensionDivision = 1.0;
        updatePlayerMapPosition(processor, dimension);
        collectXaeroWaypoints(processor, dimension, userScale);
        collectCombatantWaypoints(dimension);
        collectRadarEntities(processor, dimension);
        collectTrackedPlayers(processor, dimension);
        visible.sort(Comparator.comparingInt(Element::priority));
        return new Snapshot(List.copyOf(visible), waypointWorld, waypointDimensionDivision);
    }

    void setTabDown(boolean tabDown) {
        this.tabDown = tabDown;
    }

    void clearHover() {
        hovered = null;
    }

    private void updatePlayerMapPosition(MapProcessor processor, MapDimension dimension) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || processor.getWorldDimensionTypeRegistry() == null) {
            playerMapX = playerMapZ = Double.NaN;
            return;
        }
        double divisor = dimension.calculateDimDiv(
                processor.getWorldDimensionTypeRegistry(), minecraft.player.level().dimensionType());
        if (!Double.isFinite(divisor) || divisor == 0.0) divisor = 1.0;
        playerMapX = minecraft.player.getX() / divisor;
        playerMapZ = minecraft.player.getZ() / divisor;
    }

    Element render(Snapshot snapshot, MapViewport viewport, float mouseX, float mouseY) {
        hovered = null;
        hoveredTopExtent = 0.0f;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Element element : snapshot.elements()) {
            MapScreenPoint point = viewport.project(element.worldX(), element.worldZ());
            if (!viewport.screenBounds().contains(point.x(), point.y())) continue;
            double dx = mouseX - point.x();
            double dy = mouseY - point.y();
            double distance = dx * dx + dy * dy;
            double hitRadius = element.kind() == Kind.WAYPOINT
                    ? 29.0
                    : (element.kind() == Kind.ENTITY ? entityHitRadius(element) : 18.0);
            if (element.interactive() && distance <= hitRadius * hitRadius && distance < bestDistance) {
                hovered = element;
                bestDistance = distance;
            }
        }

        // Geometry first. Labels are intentionally emitted in later passes so a tooltip can never
        // end up underneath a subsequently batched entity/item icon.
        updateWaypointHoverAnimations(snapshot);
        java.util.IdentityHashMap<Element, Float> topExtents = new java.util.IdentityHashMap<>();
        for (Element element : snapshot.elements()) {
            MapScreenPoint point = viewport.project(element.worldX(), element.worldZ());
            if (!viewport.screenBounds().contains(point.x(), point.y())) continue;
            float topExtent = switch (element.kind()) {
                case PLAYER -> drawPlayer(point, element, element == hovered);
                case ENTITY -> drawEntity(point, element, element == hovered);
                case WAYPOINT -> drawWaypoint(point, element,
                        waypointHoverAnims.getOrDefault(element.id(), element == hovered ? 1.0f : 0.0f));
            };
            topExtents.put(element, topExtent);
            if (element == hovered) hoveredTopExtent = topExtent;
        }

        Renderer2D.flushBatch();

        // Persistent Xaero names stay above all icon geometry, but below the active hover card.
        for (Element element : snapshot.elements()) {
            boolean advancedPlayerLabel = MapUiConfig.get().advancedPlayerMarkers()
                    && (element.handle() instanceof Player || element.handle() instanceof PlayerTrackerMapElement<?>);
            if (element == hovered) continue;
            if (!advancedPlayerLabel && (element.kind() != Kind.ENTITY || !element.namesVisible())) continue;
            MapScreenPoint point = viewport.project(element.worldX(), element.worldZ());
            if (!viewport.screenBounds().contains(point.x(), point.y())) continue;
            float topExtent = topExtents.getOrDefault(element, 0.0f);
            if (advancedPlayerLabel) {
                UUID id = playerId(element);
                if (id != null) {
                    MapPlayerMarkerRenderer.drawLabel((float) point.x(), (float) point.y(), id,
                            element.plainName(), element.color(), Math.max(25.5f, topExtent * 2.0f), 1.0f, false);
                }
            } else {
                drawLabel(element.name(), (float) point.x(),
                        (float) point.y() - topExtent - MARKER_LABEL_GAP, element.color(), false);
            }
        }

        return hovered;
    }

    private void updateWaypointHoverAnimations(Snapshot snapshot) {
        float dt = AnimationUtility.deltaTime();
        if (!Float.isFinite(dt) || dt < 0.0f) dt = 1.0f / 60.0f;
        dt = Math.min(dt, 1.0f / 20.0f);
        float blend = 1.0f - (float) Math.exp(-13.0f * dt);
        Set<String> alive = new LinkedHashSet<>();
        for (Element element : snapshot.elements()) {
            if (element.kind() != Kind.WAYPOINT) continue;
            alive.add(element.id());
            float target = element == hovered ? 1.0f : 0.0f;
            float current = waypointHoverAnims.getOrDefault(element.id(), target);
            float next = current + (target - current) * blend;
            waypointHoverAnims.put(element.id(), Math.abs(target - next) < 0.001f ? target : next);
        }
        waypointHoverAnims.keySet().retainAll(alive);
    }

    void renderHover(MapViewport viewport) {
        if (hovered == null || viewport == null) return;
        MapScreenPoint point = viewport.project(hovered.worldX(), hovered.worldZ());
        if (!viewport.screenBounds().contains(point.x(), point.y())) return;

        // Everything in the map-content layer (icons, persistent names and the player arrow) is
        // committed before the hover card is emitted. Map chrome is rendered by the surface later.
        Renderer2D.flushBatch();
        if (MapUiConfig.get().advancedPlayerMarkers()
                && (hovered.handle() instanceof Player || hovered.handle() instanceof PlayerTrackerMapElement<?>)) {
            UUID id = playerId(hovered);
            if (id != null) {
                MapPlayerMarkerRenderer.drawLabel((float) point.x(), (float) point.y(), id,
                        hovered.plainName(), hovered.color(), Math.max(27.0f, hoveredTopExtent * 2.0f), 1.0f, true);
            }
            return;
        }
        float panelBottomY = (float) point.y() - hoveredTopExtent - MARKER_LABEL_GAP;
        drawLabel(hoverLabel(hovered), (float) point.x(), panelBottomY, hovered.color(), true);
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
                        false,
                        false,
                        5.0f,
                        1.0f,
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
            double worldX = waypoint.x() * dimensionScale;
            double worldZ = waypoint.z() * dimensionScale;
            if (containsWaypointAt(worldX, worldZ, waypoint.name(), waypoint.symbol())) continue;
            int color = waypoint.color() == XaeroWaypointSnapshot.Color.GOLD ? 0xFFFFB82E : 0xFFFF4D57;
            visible.add(new Element(
                    Kind.WAYPOINT,
                    waypoint.id(),
                    worldX,
                    worldZ,
                    styled(Component.literal(waypoint.name())),
                    waypoint.symbol(),
                    color,
                    false,
                    true,
                    false,
                    false,
                    5.0f,
                    1.0f,
                    110,
                    waypoint
            ));
        }
    }

    private boolean containsWaypointAt(double worldX, double worldZ, String name, String symbol) {
        String expectedName = LegacyTextUtil.stripLegacy(name == null ? "" : name);
        String expectedSymbol = symbol == null ? "" : symbol;
        for (Element element : visible) {
            if (element.kind() != Kind.WAYPOINT) continue;
            if (Math.abs(element.worldX() - worldX) > 0.5 || Math.abs(element.worldZ() - worldZ) > 0.5) continue;
            boolean sameName = expectedName.isBlank() || expectedName.equalsIgnoreCase(element.plainName());
            boolean sameSymbol = expectedSymbol.isBlank() || expectedSymbol.equalsIgnoreCase(element.symbol());
            if (sameName && sameSymbol) return true;
        }
        return false;
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
            if (!Boolean.TRUE.equals(list.getEffective(EntityRadarCategorySettings.DISPLAYED))) continue;
            int iconMode = ((Double) list.getEffective(EntityRadarCategorySettings.ICONS)).intValue();
            int nameMode = ((Double) list.getEffective(EntityRadarCategorySettings.NAMES)).intValue();
            boolean iconRequested = iconMode == 2 || (iconMode == 1 && tabDown);
            boolean namesVisible = nameMode == 2 || (nameMode == 1 && tabDown)
                    || Boolean.TRUE.equals(list.getEffective(EntityRadarCategorySettings.ALWAYS_NAMETAGS));
            float dotSize = ((Double) list.getEffective(EntityRadarCategorySettings.DOT_SIZE)).floatValue();
            float iconScale = ((Double) list.getEffective(EntityRadarCategorySettings.ICON_SCALE)).floatValue();
            int listColor = 0xFF000000 | radar.getColorHelper().getFallbackColor(list).getHex();
            for (Entity entity : list.getEntities()) {
                if (entity == null || entity == minecraft.player || entity.isRemoved()) continue;
                if (entity instanceof Player player && MapUiConfig.get().advancedPlayerMarkers()) {
                    var unified = PlayerLocationService.get().snapshot().best(player.getUUID());
                    if (unified != null && unified.hasPosition()) continue;
                    visible.add(new Element(
                            Kind.ENTITY,
                            "entity:" + player.getUUID(),
                            player.getX() / divisor,
                            player.getZ() / divisor,
                            styled(player.getDisplayName()),
                            "",
                            CategoryService.getColor(player),
                            false,
                            true,
                            false,
                            false,
                            Math.max(1.0f, dotSize),
                            iconScale,
                            190,
                            player
                    ));
                    continue;
                }
                visible.add(new Element(
                        Kind.ENTITY,
                        "entity:" + entity.getUUID(),
                        entity.getX() / divisor,
                        entity.getZ() / divisor,
                        styled(entity.getDisplayName()),
                        "",
                        listColor,
                        false,
                        true,
                        namesVisible,
                        iconRequested,
                        Math.max(1.0f, dotSize),
                        iconScale,
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
            if (MapUiConfig.get().advancedPlayerMarkers()) {
                var unified = PlayerLocationService.get().snapshot().best(playerId);
                if (unified != null && unified.hasPosition()
                        && dimension.getDimId().identifier().toString().equals(unified.worldIdentity().dimensionKey())) continue;
            }
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
                    CategoryService.getColor(info == null ? name.getString() : info.getProfile().name()),
                    false,
                    true,
                    false,
                    false,
                    5.0f,
                    1.0f,
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

    @SuppressWarnings("unchecked")
    private float drawWaypoint(MapScreenPoint point, Element element, float hoverProgress) {
        ClientConfigManager config = WorldMap.INSTANCE.getConfigs().getClientConfigManager();
        double configuredScale = (Double) config.getEffective(
                (ConfigOption<Double>) WorldMapProfiledConfigOptions.WAYPOINT_SCALE);
        float scale = (float) Math.max(0.90, Math.min(1.45, configuredScale));
        float alpha = element.disabled() ? 0.38f : 1.0f;
        int accent = withAlpha(element.color(), alpha);
        float highlight = Math.max(0.0f, Math.min(1.0f, hoverProgress));

        float badgeWidth = (32.0f + 6.0f * highlight) * scale;
        float badgeHeight = (28.0f + 5.0f * highlight) * scale;
        float radius = Math.min(10.0f * scale, badgeHeight * 0.38f);
        float cx = (float) point.x();
        float anchorY = (float) point.y();
        // The marker still points at the exact map coordinate, but its glass body overlaps the
        // anchor slightly instead of floating a full icon-height above it.
        float badgeBottom = anchorY + 3.5f * scale;
        float x = cx - badgeWidth * 0.5f;
        float y = badgeBottom - badgeHeight;

        Renderer2D renderer = Renderer2D.COLOR;
        float glassAlpha = alpha * (0.80f + 0.12f * highlight);
        float blurAlpha = alpha * (0.70f + 0.12f * highlight);
        withMapGlassSource(() -> {
            UiLiquidGlassMaterial material = UiLiquidGlassMaterial.DEFAULT.withInnerGlow(
                    0.16f + 0.10f * highlight,
                    4.8f + 1.2f * highlight,
                    withAlpha(element.color(), alpha * (0.72f + 0.23f * highlight))
            );
            renderer.withLiquidGlassMaterial(material, () -> renderer.liquidGlassRect(
                    x, y, badgeWidth, badgeHeight, radius,
                    withAlpha(element.color(), 0.10f + 0.08f * highlight),
                    glassAlpha,
                    blurAlpha,
                    Renderer2D.LiquidGlassPreset.BALANCED,
                    0.72f,
                    0.0f
            ));
            renderer.roundedRect(
                    x, y, badgeWidth, badgeHeight, radius,
                    withAlpha(0x0A1017, alpha * (0.18f + 0.07f * highlight))
            );
        });

        String symbol = element.symbol();
        if (symbol != null && !symbol.isBlank()) {
            symbol = symbol.substring(0, Math.min(2, symbol.length())).toUpperCase(java.util.Locale.ROOT);
            float symbolSize = (symbol.length() > 1 ? 11.4f : 13.2f) * scale;
            float symbolWidth = ClickGuiRenderer.textWidth(ClickGuiRenderer.getOnestBold(), symbol, symbolSize);
            ClickGuiRenderer.drawText(
                    ClickGuiRenderer.getOnestBold(),
                    symbol,
                    cx - symbolWidth * 0.5f,
                    y + (badgeHeight - symbolSize) * 0.5f - 0.35f * scale,
                    symbolSize,
                    withAlpha(0xFFF8FBFD, alpha),
                    false
            );
        } else {
            float iconSize = 16.5f * scale;
            renderer.svg(
                    "map-pin",
                    cx - iconSize * 0.5f,
                    y + (badgeHeight - iconSize) * 0.5f,
                    iconSize,
                    iconSize,
                    SvgRenderOptions.overrideColor(accent)
            );
        }

        // A tiny accent point keeps the exact anchor legible without the old tall stem/tail.
        renderer.circle(cx, anchorY + 1.25f * scale, Math.max(1.6f, 1.85f * scale), accent);
        return anchorY - y;
    }

    private float drawPlayer(MapScreenPoint point, Element element, boolean highlighted) {
        if (!(element.handle() instanceof PlayerTrackerMapElement<?> tracked)) return 0.0f;
        if (MapUiConfig.get().advancedPlayerMarkers()) {
            float size = 27.0f;
            String name = element.plainName();
            return MapPlayerMarkerRenderer.drawMarker((float) point.x(), (float) point.y(), tracked.getPlayerId(), name,
                    element.color(), "", size, 1.0f, highlighted);
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getConnection() == null || minecraft.level == null) return 0.0f;
        PlayerInfo info = minecraft.getConnection().getPlayerInfo(tracked.getPlayerId());
        if (info == null || WorldMap.trackedPlayerRenderer.getTrackedPlayerIconManager() == null) return 0.0f;
        Player localPlayer = minecraft.level.getPlayerByUUID(tracked.getPlayerId());
        XaeroIcon icon = WorldMap.trackedPlayerRenderer.getTrackedPlayerIconManager()
                .getIcon(xaeroGraphics, localPlayer, info, tracked);
        if (icon == null || icon.getTextureAtlas() == null) return 0.0f;
        XaeroIconAtlas atlas = icon.getTextureAtlas();
        float size = highlighted ? 36.0f : 32.0f;
        float x = Math.round((float) point.x() - size * 0.5f);
        float y = Math.round((float) point.y() - size * 0.5f);
        Renderer2D.COLOR.textureQuad(atlas.getTextureView(), mapIconSampler(),
                x, y, size, size,
                (icon.getOffsetX() + 1.0) / atlas.getWidth(),
                (icon.getOffsetY() + 1.0) / atlas.getWidth(),
                (icon.getOffsetX() + 31.0) / atlas.getWidth(),
                (icon.getOffsetY() + 31.0) / atlas.getWidth(),
                0xFFFFFFFF);
        return size * 0.5f;
    }

    private float drawEntity(MapScreenPoint point, Element element, boolean highlighted) {
        if (element.handle() instanceof Player player && MapUiConfig.get().advancedPlayerMarkers()) {
            float size = 27.0f;
            String name = player.getGameProfile() == null ? element.plainName() : player.getGameProfile().name();
            return MapPlayerMarkerRenderer.drawMarker((float) point.x(), (float) point.y(), player.getUUID(), name,
                    CategoryService.getColor(player), "", size, 1.0f, highlighted);
        }
        float size = drawRadarIcon(point, element, highlighted);
        if (size <= 0.0f) size = drawRadarDot(point, element, highlighted);
        return size * 0.5f;
    }

    private float drawRadarIcon(MapScreenPoint point, Element element, boolean highlighted) {
        if (!element.iconRequested() || !(element.handle() instanceof Entity entity)) return 0.0f;
        XaeroMinimapIntegration.RadarIconTexture icon =
                XaeroMinimapIntegration.radarIcon(entity, element.iconScale());
        if (icon == null) return 0.0f;

        float size = XAERO_RADAR_ICON_CONTENT_SIZE
                * Math.max(1.0f, xaeroScreenSizeBasedScale() * element.iconScale());
        float x = (float) point.x() - size * 0.5f;
        float y = (float) point.y() - size * 0.5f;
        Renderer2D.COLOR.textureQuad(icon.textureView(), radarIconSampler(),
                x, y, size, size,
                icon.u0(), icon.v0(), icon.u1(), icon.v1(),
                0xFFFFFFFF);
        return size;
    }

    private float drawRadarDot(MapScreenPoint point, Element element, boolean highlighted) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return 0.0f;
        AbstractTexture texture = minecraft.getTextureManager().getTexture(TextureLocations.GUI_TEXTURES);
        if (texture == null || texture.getTextureView() == null) return 0.0f;

        ClientConfigManager minimapConfig = HudMod.INSTANCE.getHudConfigs().getClientConfigManager();
        int dotsStyle = (Integer) minimapConfig.getEffective(MinimapProfiledConfigOptions.RADAR_DOTS_STYLE);
        boolean smoothDots = (Boolean) minimapConfig.getEffective(MinimapProfiledConfigOptions.RADAR_SMOOTH_DOTS);
        int dotSize = Math.max(1, Math.min(4, (int) element.dotSize()));
        float screenScale = xaeroScreenSizeBasedScale();

        int sourceX;
        int sourceY;
        int sourceSize;
        float origin;
        float renderScale = screenScale;
        if (dotsStyle == 1) {
            sourceX = smoothDots ? 1 : 9;
            sourceY = smoothDots ? 88 : 77;
            sourceSize = 8;
            origin = -3.5f;
            renderScale *= 1.0f + 0.5f * (dotSize - 1);
        } else {
            sourceX = 0;
            switch (dotSize) {
                case 1 -> {
                    sourceY = 108;
                    sourceSize = 9;
                    origin = -4.5f;
                }
                case 3 -> {
                    sourceY = 128;
                    sourceSize = 15;
                    origin = -7.5f;
                }
                case 4 -> {
                    sourceY = 160;
                    sourceSize = 21;
                    origin = -10.5f;
                }
                default -> {
                    sourceY = 117;
                    sourceSize = 11;
                    origin = -5.5f;
                }
            }
        }

        float size = sourceSize * renderScale;
        float x = (float) point.x() + origin * renderScale;
        float y = (float) point.y() + origin * renderScale;
        GpuSampler sampler = smoothDots ? radarIconSampler() : mapIconSampler();
        Renderer2D.COLOR.textureQuad(texture.getTextureView(), sampler,
                x, y, size, size,
                sourceX / 256.0, (sourceY + sourceSize) / 256.0,
                (sourceX + sourceSize) / 256.0, sourceY / 256.0,
                element.color());
        return size;
    }

    private static double entityHitRadius(Element element) {
        if (element.handle() instanceof Player && MapUiConfig.get().advancedPlayerMarkers()) {
            return 18.0;
        }
        if (element.iconRequested()) {
            return XAERO_RADAR_ICON_HIT_HALF_SIZE
                    * Math.max(0.0f, element.iconScale()) * xaeroScreenSizeBasedScale();
        }
        int dotSize = Math.max(1, (int) element.dotSize());
        double dotScale = 1.0 + 0.5 * (dotSize - 1);
        return XAERO_RADAR_DOT_HIT_HALF_SIZE * dotScale * xaeroScreenSizeBasedScale();
    }

    private static UUID playerId(Element element) {
        if (element == null) return null;
        if (element.handle() instanceof Player player) return player.getUUID();
        if (element.handle() instanceof PlayerTrackerMapElement<?> tracked) return tracked.getPlayerId();
        return null;
    }

    private static float xaeroScreenSizeBasedScale() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) return 1.0f;
        int shortSide = Math.min(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());
        return shortSide <= XAERO_WORLD_MAP_REFERENCE_SHORT_SIDE
                ? 1.0f
                : shortSide / XAERO_WORLD_MAP_REFERENCE_SHORT_SIDE;
    }

    private static GpuSampler radarIconSampler() {
        return RenderSystem.getSamplerCache().getSampler(
                AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR, FilterMode.LINEAR, false);
    }

    private static GpuSampler mapIconSampler() {
        return RenderSystem.getSamplerCache().getSampler(
                AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                FilterMode.NEAREST, FilterMode.NEAREST, false);
    }

    private Component hoverLabel(Element element) {
        Component name = element.name() == null ? Component.empty() : element.name().copy();
        if (!Double.isFinite(playerMapX) || !Double.isFinite(playerMapZ)) return name;
        double distance = Math.hypot(element.worldX() - playerMapX, element.worldZ() - playerMapZ);
        String formatted = distance >= 1000.0
                ? String.format(java.util.Locale.ROOT, " · %.1f km", distance / 1000.0)
                : " · " + Math.round(distance) + " m";
        return name.copy().append(Component.literal(formatted).withStyle(ChatFormatting.GRAY));
    }

    private static int withAlpha(int argb, float alpha) {
        return (Math.max(0, Math.min(255, Math.round(alpha * 255.0f))) << 24) | (argb & 0x00FFFFFF);
    }

    private static void drawLabel(Component value, float centerX, float panelBottomY, int accent, boolean hover) {
        if (value == null || value.getString().isBlank()) return;
        float size = hover ? 13.5f : 11.25f;
        float padX = hover ? 8.0f : 5.5f;
        float padY = hover ? 4.0f : 3.0f;
        float radius = hover ? 7.0f : 5.5f;
        List<TextRenderUtil.Part> parts = TextRenderUtil.flattenStyled(styled(value), 0xFFF5F8FC);
        float textWidth = styledWidth(parts, size);
        float boxWidth = textWidth + padX * 2.0f;
        float boxHeight = size + padY * 2.0f;
        float boxX = centerX - boxWidth * 0.5f;
        float boxY = panelBottomY - boxHeight;
        float textX = boxX + padX;
        float textY = boxY + padY - 0.25f;

        float glassAlpha = hover ? 0.96f : 0.67f;
        float blurAlpha = hover ? 0.86f : 0.56f;
        withMapGlassSource(() -> {
            UiLiquidGlassMaterial material = UiLiquidGlassMaterial.DEFAULT.withInnerGlow(
                    hover ? 0.19f : 0.095f,
                    hover ? 5.0f : 4.0f,
                    withAlpha(accent, hover ? 0.82f : 0.48f)
            );
            Renderer2D.COLOR.withLiquidGlassMaterial(material, () -> Renderer2D.COLOR.liquidGlassRect(
                    boxX, boxY, boxWidth, boxHeight, radius,
                    withAlpha(accent, hover ? 0.56f : 0.055f),
                    glassAlpha,
                    blurAlpha,
                    Renderer2D.LiquidGlassPreset.BALANCED,
                    hover ? 0.78f : 0.68f,
                    0.0f
            ));
            Renderer2D.COLOR.roundedRect(
                    boxX, boxY, boxWidth, boxHeight, radius,
                    hover ? withAlpha(accent, 0.24f) : 0x24080D14
            );
        });
        drawStyled(parts, textX, textY, size);
    }

    private static void withMapGlassSource(Runnable draw) {
        Renderer2D.COLOR.withLiquidGlassSceneSource(UiBackdropRequest.SceneSource.UI_UNDERLAY, draw);
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
                   boolean namesVisible,
                   boolean iconRequested,
                   float dotSize,
                   float iconScale,
                   int priority,
                   Object handle) {
        String plainName() {
            return name == null ? "" : LegacyTextUtil.stripLegacy(name.getString());
        }
    }

}
