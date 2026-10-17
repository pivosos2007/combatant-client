/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.compat.xaero;

import combatant.client.util.logging.DebugLog;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.Minimap;
import xaero.hud.minimap.element.render.MinimapElementGraphics;
import xaero.hud.minimap.element.render.MinimapElementRendererHandler;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.radar.icon.RadarIconManager;
import xaero.hud.minimap.radar.render.element.RadarRenderer;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Converts central Combatant waypoint snapshots into Xaero minimap objects. */
public enum XaeroMinimapIntegration {
    ;

    private static final Map<String, Waypoint> CACHE = new HashMap<>();
    private static MinimapSession radarIconSession;
    private static RadarIconAccess radarIconAccess;

    public static List<Waypoint> waypoints(XaeroIntegration.RenderTarget target) {
        List<XaeroWaypointSnapshot> snapshots = XaeroIntegration.snapshots(target);
        if (snapshots.isEmpty()) {
            CACHE.clear();
            return List.of();
        }

        boolean nether = isNether();
        List<Waypoint> result = new ArrayList<>(snapshots.size());
        Set<String> active = new HashSet<>();
        for (XaeroWaypointSnapshot snapshot : snapshots) {
            String cacheKey = target.name() + ':' + snapshot.id();
            active.add(cacheKey);
            double scale = snapshot.coordinateSpace() == XaeroWaypointSnapshot.CoordinateSpace.OVERWORLD && nether
                    ? 0.125
                    : 1.0;
            int x = (int) Math.round(snapshot.x() * scale);
            int z = (int) Math.round(snapshot.z() * scale);
            Waypoint waypoint = CACHE.get(cacheKey);
            if (waypoint == null) {
                waypoint = new Waypoint(
                        x,
                        snapshot.y(),
                        z,
                        snapshot.name(),
                        snapshot.symbol(),
                        color(snapshot.color()),
                        WaypointPurpose.NORMAL
                );
                CACHE.put(cacheKey, waypoint);
            }
            waypoint.setX(x);
            waypoint.setY(snapshot.y());
            waypoint.setZ(z);
            waypoint.setWaypointColor(color(snapshot.color()));
            waypoint.setTemporary(snapshot.temporary());
            waypoint.setYIncluded(snapshot.yIncluded());
            waypoint.setDisabled(false);
            result.add(waypoint);
        }
        CACHE.keySet().removeIf(key -> key.startsWith(target.name() + ':') && !active.contains(key));
        return List.copyOf(result);
    }


    /**
     * Resolves the same Xaero radar-atlas icon used by the minimap/world-map radar.
     * The returned texture belongs to Xaero; Combatant does not copy or own the atlas.
     */
    public static RadarIconTexture radarIcon(Entity entity, float iconScale) {
        if (entity == null) return null;
        MinimapSession session = currentSession();
        if (session == null) return null;
        RadarIconAccess access = resolveRadarIconAccess(session);
        if (access == null) return null;
        try {
            access.manager().allowPrerender();
            xaero.common.icon.XaeroIcon icon = access.manager().get(
                    entity, iconScale, false, false, access.graphics(), null);
            if (icon == null || icon == RadarIconManager.DOT || icon == RadarIconManager.FAILED
                    || icon.getTextureAtlas() == null) {
                return null;
            }
            xaero.common.icon.XaeroIconAtlas atlas = icon.getTextureAtlas();
            GpuTextureView textureView = atlas.getTextureView();
            if (textureView == null) return null;
            double atlasWidth = atlas.getWidth();
            if (atlasWidth <= 0.0) return null;
            return new RadarIconTexture(
                    textureView,
                    (icon.getOffsetX() + 1.0) / atlasWidth,
                    (icon.getOffsetY() + 63.0) / atlasWidth,
                    (icon.getOffsetX() + 63.0) / atlasWidth,
                    (icon.getOffsetY() + 1.0) / atlasWidth
            );
        } catch (RuntimeException error) {
            DebugLog.warnOnce(
                    "xaero-radar-icon-runtime",
                    "Xaero radar icon rendering is unavailable for Combatant UI",
                    error
            );
            return null;
        }
    }

    private static MinimapSession currentSession() {
        Object session = BuiltInHudModules.MINIMAP.getCurrentSession();
        return session instanceof MinimapSession minimapSession ? minimapSession : null;
    }

    private static RadarIconAccess resolveRadarIconAccess(MinimapSession session) {
        if (radarIconSession == session && radarIconAccess != null) return radarIconAccess;
        radarIconSession = session;
        radarIconAccess = null;
        try {
            Field minimapField = MinimapSession.class.getDeclaredField("minimap");
            minimapField.setAccessible(true);
            Minimap minimap = (Minimap) minimapField.get(session);
            if (minimap == null || minimap.getOverMapRendererHandler() == null) return null;

            MinimapElementRendererHandler handler = minimap.getOverMapRendererHandler();
            Field renderersField = MinimapElementRendererHandler.class.getDeclaredField("renderers");
            renderersField.setAccessible(true);
            Object renderersValue = renderersField.get(handler);
            if (!(renderersValue instanceof List<?> renderers)) return null;

            Field managerField = RadarRenderer.class.getDeclaredField("radarIconManager");
            managerField.setAccessible(true);
            for (Object renderer : renderers) {
                if (!(renderer instanceof RadarRenderer)) continue;
                RadarIconManager manager = (RadarIconManager) managerField.get(renderer);
                if (manager != null) {
                    radarIconAccess = new RadarIconAccess(manager, handler.getGuiGraphics());
                }
                break;
            }
            return radarIconAccess;
        } catch (ReflectiveOperationException | RuntimeException error) {
            DebugLog.warnOnce(
                    "xaero-radar-icon-access",
                    "Xaero radar icon manager is unavailable for Combatant UI",
                    error
            );
            return null;
        }
    }

    public record RadarIconTexture(
            GpuTextureView textureView,
            double u0,
            double v0,
            double u1,
            double v1
    ) {
    }

    private record RadarIconAccess(RadarIconManager manager, MinimapElementGraphics graphics) {
    }

    static WaypointColor color(XaeroWaypointSnapshot.Color color) {
        return color == XaeroWaypointSnapshot.Color.GOLD ? WaypointColor.GOLD : WaypointColor.RED;
    }

    private static boolean isNether() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.level != null && mc.level.dimension() == Level.NETHER;
    }
}
