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
import combatant.client.render.map.MapScreenPoint;
import combatant.client.render.map.MapViewport;
import combatant.client.util.text.LegacyTextUtil;
import combatant.client.util.text.TextRenderUtil;
import combatant.client.util.logging.DebugLog;
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
import xaero.hud.minimap.Minimap;
import xaero.hud.minimap.common.config.option.MinimapProfiledConfigOptions;
import xaero.hud.minimap.element.render.MinimapElementGraphics;
import xaero.hud.minimap.element.render.MinimapElementRendererHandler;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.radar.RadarSession;
import xaero.hud.minimap.radar.category.setting.EntityRadarCategorySettings;
import xaero.hud.minimap.radar.icon.RadarIconManager;
import xaero.hud.minimap.radar.render.element.RadarRenderer;
import xaero.hud.minimap.radar.state.RadarList;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapDimensionHelper;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.path.XaeroPath;
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
import java.util.List;
import java.util.UUID;
import java.lang.reflect.Field;

final class XaeroMapElements {
    private final List<Element> visible = new ArrayList<>();
    private final MapElementGraphics xaeroGraphics = new MapElementGraphics(new PoseStack());
    private Element hovered;
    private boolean tabDown;
    private double playerMapX = Double.NaN;
    private double playerMapZ = Double.NaN;
    private MinimapWorld waypointWorld;
    private double waypointDimensionDivision = 1.0;
    private MinimapSession radarIconSession;
    private RadarIconAccess radarIconAccess;

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
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Element element : snapshot.elements()) {
            MapScreenPoint point = viewport.project(element.worldX(), element.worldZ());
            if (!viewport.screenBounds().contains(point.x(), point.y())) continue;
            double dx = mouseX - point.x();
            double dy = mouseY - point.y();
            double distance = dx * dx + dy * dy;
            double hitRadius = element.kind() == Kind.WAYPOINT ? 15.0 : (element.kind() == Kind.ENTITY ? 14.0 : 16.0);
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
                    false,
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
        resolveRadarIcons(session);
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
    private void drawWaypoint(MapScreenPoint point, Element element, boolean highlighted) {
        ClientConfigManager config = WorldMap.INSTANCE.getConfigs().getClientConfigManager();
        double configuredScale = (Double) config.getEffective(
                (ConfigOption<Double>) WorldMapProfiledConfigOptions.WAYPOINT_SCALE);
        float scale = (float) Math.max(0.85, Math.min(1.8, configuredScale));
        float size = 22.0f * scale + (highlighted ? 2.0f : 0.0f);
        float x = (float) point.x() - size * 0.5f;
        float y = (float) point.y() - size * 0.88f;
        float alpha = element.disabled() ? 0.35f : 1.0f;
        int color = withAlpha(element.color(), alpha);

        Renderer2D renderer = Renderer2D.COLOR;
        renderer.svg("waypoint-map", x, y, size, size, SvgRenderOptions.overrideColor(color));

        if (element.symbol() != null && !element.symbol().isBlank()) {
            String symbol = element.symbol().substring(0, Math.min(2, element.symbol().length()));
            float symbolSize = symbol.length() > 1 ? 6.5f * scale : 7.5f * scale;
            float symbolWidth = ClickGuiRenderer.textWidth(ClickGuiRenderer.getOnestBold(), symbol, symbolSize);
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestBold(), symbol,
                    (float) point.x() - symbolWidth * 0.5f,
                    (float) point.y() - size * 0.56f,
                    symbolSize, withAlpha(0xFFF7FAFC, alpha), false);
        }
        if (highlighted) drawLabel(hoverLabel(element), (float) point.x(), y - 4.0f, element.color());
    }

    private void drawPlayer(MapScreenPoint point, Element element, boolean highlighted) {
        if (!(element.handle() instanceof PlayerTrackerMapElement<?> tracked)) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getConnection() == null || minecraft.level == null) return;
        PlayerInfo info = minecraft.getConnection().getPlayerInfo(tracked.getPlayerId());
        if (info == null || WorldMap.trackedPlayerRenderer.getTrackedPlayerIconManager() == null) return;
        Player localPlayer = minecraft.level.getPlayerByUUID(tracked.getPlayerId());
        XaeroIcon icon = WorldMap.trackedPlayerRenderer.getTrackedPlayerIconManager()
                .getIcon(xaeroGraphics, localPlayer, info, tracked);
        if (icon == null || icon.getTextureAtlas() == null) return;
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
        if (highlighted) drawLabel(hoverLabel(element), (float) point.x(), y - 5.0f, element.color());
    }

    private void drawEntity(MapScreenPoint point, Element element, boolean highlighted) {
        float size = drawRadarIcon(point, element, highlighted);
        if (size <= 0.0f) size = drawRadarDot(point, element, highlighted);
        if (highlighted || element.namesVisible()) {
            drawLabel(highlighted ? hoverLabel(element) : element.name(),
                    (float) point.x(), (float) point.y() - size * 0.5f - 5.0f, element.color());
        }
    }

    private float drawRadarIcon(MapScreenPoint point, Element element, boolean highlighted) {
        if (!element.iconRequested() || !(element.handle() instanceof Entity entity)) return 0.0f;
        RadarIconAccess access = radarIconAccess;
        Minecraft minecraft = Minecraft.getInstance();
        if (access == null || minecraft == null || minecraft.gameRenderer == null
                || minecraft.gameRenderer.mainRenderTarget() == null) return 0.0f;
        try {
            access.manager().allowPrerender();
            xaero.common.icon.XaeroIcon icon = access.manager().get(
                    entity, element.iconScale(), false, false,
                    access.graphics(), minecraft.gameRenderer.mainRenderTarget());
            if (icon == null || icon == RadarIconManager.DOT || icon == RadarIconManager.FAILED
                    || icon.getTextureAtlas() == null) return 0.0f;
            xaero.common.icon.XaeroIconAtlas atlas = icon.getTextureAtlas();
            float configured = Math.max(0.5f, Math.min(4.0f, element.iconScale()));
            float size = Math.round(Math.max(28.0f, Math.min(44.0f, 26.0f * configured)));
            if (highlighted) size += 3.0f;
            float x = Math.round((float) point.x() - size * 0.5f);
            float y = Math.round((float) point.y() - size * 0.5f);
            Renderer2D.COLOR.textureQuad(atlas.getTextureView(), mapIconSampler(),
                    x, y, size, size,
                    (icon.getOffsetX() + 1.0) / atlas.getWidth(),
                    (icon.getOffsetY() + 63.0) / atlas.getWidth(),
                    (icon.getOffsetX() + 63.0) / atlas.getWidth(),
                    (icon.getOffsetY() + 1.0) / atlas.getWidth(),
                    0xFFFFFFFF);
            return size;
        } catch (RuntimeException error) {
            DebugLog.warnOnce("clickgui-map-radar-icon-render",
                    "Xaero radar icon rendering failed; using its dot presentation", error);
            return 0.0f;
        }
    }

    private float drawRadarDot(MapScreenPoint point, Element element, boolean highlighted) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return 0.0f;
        AbstractTexture texture = minecraft.getTextureManager().getTexture(WorldMap.guiTextures);
        if (texture == null || texture.getTextureView() == null || texture.getSampler() == null) return 0.0f;
        float size = Math.round(Math.max(9.0f, element.dotSize() * 1.75f)
                + (highlighted ? 2.0f : 0.0f));
        float x = Math.round((float) point.x() - size * 0.5f);
        float y = Math.round((float) point.y() - size * 0.5f);
        Renderer2D.COLOR.textureQuad(texture.getTextureView(), mapIconSampler(),
                x, y, size, size,
                0.0, 69.0 / 256.0, 5.0 / 256.0, 74.0 / 256.0, element.color());
        return size;
    }

    private static GpuSampler mapIconSampler() {
        return RenderSystem.getSamplerCache().getSampler(
                AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                FilterMode.NEAREST, FilterMode.NEAREST, false);
    }

    private void resolveRadarIcons(MinimapSession session) {
        if (radarIconSession == session) return;
        radarIconSession = session;
        radarIconAccess = null;
        try {
            Field minimapField = MinimapSession.class.getDeclaredField("minimap");
            minimapField.setAccessible(true);
            Minimap minimap = (Minimap) minimapField.get(session);
            if (minimap == null || minimap.getOverMapRendererHandler() == null) return;
            MinimapElementRendererHandler handler = minimap.getOverMapRendererHandler();
            Field renderersField = MinimapElementRendererHandler.class.getDeclaredField("renderers");
            renderersField.setAccessible(true);
            Object renderersValue = renderersField.get(handler);
            if (!(renderersValue instanceof List<?> renderers)) return;
            Field managerField = RadarRenderer.class.getDeclaredField("radarIconManager");
            managerField.setAccessible(true);
            for (Object renderer : renderers) {
                if (!(renderer instanceof RadarRenderer)) continue;
                RadarIconManager manager = (RadarIconManager) managerField.get(renderer);
                if (manager != null) radarIconAccess = new RadarIconAccess(manager, handler.getGuiGraphics());
                return;
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            DebugLog.warnOnce("clickgui-map-radar-icons",
                    "Xaero radar icon manager is unavailable; using its dot presentation", error);
        }
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

    private static void drawLabel(Component value, float centerX, float bottomY, int accent) {
        if (value == null || value.getString().isBlank()) return;
        float uiScale = 1.0f;
        float size = 11.0f;
        List<TextRenderUtil.Part> parts = TextRenderUtil.flattenStyled(styled(value), 0xFFF5F8FC);
        float textWidth = styledWidth(parts, size);
        float x = centerX - textWidth * 0.5f;
        float y = bottomY - size;
        Renderer2D.COLOR.roundedRect(x - 5.0f * uiScale, y - 3.0f * uiScale,
                textWidth + 10.0f * uiScale, size + 6.0f * uiScale, 5.0f * uiScale, 0xB0000000);
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

    private record RadarIconAccess(RadarIconManager manager, MinimapElementGraphics graphics) {
    }
}
