/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.compat.xaero;

import combatant.client.config.subsystem.MapUiConfig;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.marker.WorldMarkerHudRenderer;
import combatant.client.render.helpers.ScreenProjection;
import combatant.client.runtime.RuntimeGate;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.text.LegacyTextUtil;
import combatant.client.util.text.TextRenderUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import xaero.common.HudMod;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.common.config.option.MinimapProfiledConfigOptions;
import xaero.hud.minimap.config.util.MinimapConfigClientUtils;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.lib.client.config.ClientConfigManager;
import xaero.lib.common.config.option.ConfigOption;
import xaero.lib.common.config.option.IndexedConfigOption;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public enum XaeroWaypointHudOverlay {
    ;

    private static final float BASE_ICON_SIZE = 12.0f;
    private static final float BASE_NAME_SIZE = 10.5f;
    private static final float BASE_DISTANCE_SIZE = 8.75f;
    private static final float SCREEN_MARGIN = 6.0f;

    private static List<WorldMarkerHudRenderer.Layout> frameEntries = List.of();
    private static boolean failed;
    private static long retryAfterNanos;

    public static boolean ownsXaeroWorldWaypoints() {
        if (failed || System.nanoTime() < retryAfterNanos || !RuntimeGate.canRunHud()) return false;
        try {
            return overlayEnabled();
        } catch (RuntimeException error) {
            recover(error);
            return false;
        } catch (LinkageError error) {
            disable(error);
            return false;
        }
    }

    public static boolean shouldSuppressNativeXaeroWaypoints() {
        // Once the integration is healthy Combatant owns the visibility policy for world waypoints:
        // when our waypoint HUD is enabled they are replaced by our renderer; when the user disables
        // them they stay hidden instead of silently falling back to Xaero's native HUD. Runtime
        // failures still release ownership so Xaero can act as the safety fallback.
        if (failed || System.nanoTime() < retryAfterNanos || !RuntimeGate.canRunHud()) return false;
        try {
            return xaeroSessionAvailable();
        } catch (RuntimeException error) {
            recover(error);
            return false;
        } catch (LinkageError error) {
            disable(error);
            return false;
        }
    }

    public static boolean hasHudWork() {
        return ownsXaeroWorldWaypoints();
    }

    private static boolean overlayEnabled() {
        MapUiConfig ui = MapUiConfig.get();
        return ui.hudEnabled() && ui.hudWaypointMarkers() && xaeroWorldWaypointsEnabled();
    }

    private static boolean xaeroSessionAvailable() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null
                && minecraft.player != null
                && minecraft.level != null
                && BuiltInHudModules.MINIMAP.getCurrentSession() != null;
    }

    private static boolean xaeroWorldWaypointsEnabled() {
        if (!xaeroSessionAvailable()) return false;
        ClientConfigManager config = HudMod.INSTANCE.getHudConfigs().getClientConfigManager();
        return effectiveBoolean(config, MinimapProfiledConfigOptions.WAYPOINTS_IN_WORLD);
    }

    public static void renderBackground(Renderer2D renderer, TextRenderer fallback, float tickDelta) {
        if (renderer == null || fallback == null || !ownsXaeroWorldWaypoints()) {
            frameEntries = List.of();
            return;
        }
        try {
            frameEntries = WorldMarkerHudRenderer.layout(captureFrame(tickDelta), fallback);
            WorldMarkerHudRenderer.renderBackground(renderer, frameEntries);
        } catch (RuntimeException error) {
            frameEntries = List.of();
            recover(error);
        } catch (LinkageError error) {
            frameEntries = List.of();
            disable(error);
        }
    }

    public static void renderForeground(TextRenderer fallback) {
        if (fallback == null || frameEntries.isEmpty() || failed) {
            frameEntries = List.of();
            return;
        }
        try {
            WorldMarkerHudRenderer.renderForeground(fallback, frameEntries);
        } catch (RuntimeException error) {
            recover(error);
        } catch (LinkageError error) {
            disable(error);
        } finally {
            frameEntries = List.of();
        }
    }

    public static void clearFrame() {
        frameEntries = List.of();
    }

    private static List<WorldMarkerHudRenderer.Marker> captureFrame(float tickDelta) {
        Minecraft minecraft = Minecraft.getInstance();
        MinimapSession session = (MinimapSession) BuiltInHudModules.MINIMAP.getCurrentSession();
        if (minecraft == null || minecraft.player == null || session == null || session.getWaypointSession() == null) {
            return List.of();
        }

        ClientConfigManager config = HudMod.INSTANCE.getHudConfigs().getClientConfigManager();
        List<Waypoint> waypoints = new ArrayList<>();
        session.getWaypointSession().getCollector().collect(waypoints);
        waypoints.addAll(XaeroMinimapIntegration.waypoints(XaeroIntegration.RenderTarget.WORLD_HUD));
        if (waypoints.isEmpty()) return List.of();

        Vec3 playerPosition = minecraft.player.getPosition(tickDelta);
        waypoints.sort(Comparator.comparingDouble((Waypoint waypoint) -> waypoint == null
                ? Double.NEGATIVE_INFINITY
                : waypoint.getDistanceSq(playerPosition.x, playerPosition.y, playerPosition.z)).reversed());

        int maxDistance = effectiveInt(config, MinimapProfiledConfigOptions.WAYPOINT_MAX_DISTANCE);
        double minDistance = effectiveNumber(config, MinimapProfiledConfigOptions.WAYPOINT_MIN_DISTANCE_IN_WORLD);
        boolean dimensionScaleDistance = effectiveBoolean(config, MinimapProfiledConfigOptions.WAYPOINT_MAX_DISTANCE_DIMENSION_SCALE);
        boolean temporaryWaypointsGlobal = effectiveBoolean(config, MinimapProfiledConfigOptions.TEMPORARY_WAYPOINTS_GLOBAL);
        boolean keepNames = effectiveBoolean(config, MinimapProfiledConfigOptions.WAYPOINT_NAME_IN_WORLD);
        int distanceMode = effectiveInt(config, MinimapProfiledConfigOptions.WAYPOINT_DISTANCE_IN_WORLD);
        int horizontalPointingAngle = Mth.clamp(
                effectiveInt(config, MinimapProfiledConfigOptions.WAYPOINT_HORIZONTAL_POINTING_ANGLE), 0, 180);
        int verticalPointingAngle = Mth.clamp(
                effectiveInt(config, MinimapProfiledConfigOptions.WAYPOINT_VERTICAL_POINTING_ANGLE), 0, 180);
        boolean displayShortDistance = effectiveBoolean(config, MinimapProfiledConfigOptions.WAYPOINT_SHORT_DISTANCE_IN_WORLD);
        int kmThreshold = effectiveInt(config, MinimapProfiledConfigOptions.WAYPOINT_CONVERT_DISTANCE_TO_KM_AT);
        int precision = Math.max(0, effectiveInt(config, MinimapProfiledConfigOptions.WAYPOINT_DISTANCE_PRECISION));
        float opacity = clamp01(effectiveInt(config, MinimapProfiledConfigOptions.WAYPOINT_OPACITY_IN_WORLD) / 100.0f);

        float iconFactor = uiScaleFactor(config, MinimapProfiledConfigOptions.WAYPOINT_ICON_SCALE_IN_WORLD, 1.0);
        float nameFactor = uiScaleFactor(config, MinimapProfiledConfigOptions.WAYPOINT_NAME_SCALE_IN_WORLD, 0.5);
        float distanceFactor = uiScaleFactor(config, MinimapProfiledConfigOptions.WAYPOINT_DISTANCE_SCALE_IN_WORLD, 1.0);
        float iconSize = BASE_ICON_SIZE * iconFactor;
        float nameSize = BASE_NAME_SIZE * nameFactor;
        float distanceSize = BASE_DISTANCE_SIZE * distanceFactor;

        ViewportContext viewport = ViewportContext.current();
        double viewportWidth = viewport != null ? viewport.width() : minecraft.getWindow().getWidth();
        double viewportHeight = viewport != null ? viewport.height() : minecraft.getWindow().getHeight();

        double backgroundCoordinateScale = minecraft.level.dimensionType().coordinateScale();
        MinimapWorld currentWorld = session.getWorldManager() == null ? null : session.getWorldManager().getCurrentWorld();
        double waypointDimensionScale = backgroundCoordinateScale;
        if (currentWorld != null && session.getDimensionHelper() != null) {
            waypointDimensionScale = session.getDimensionHelper().getDimCoordinateScale(currentWorld);
        }
        double coordinateDivision = waypointDimensionScale == 0.0
                ? 1.0
                : backgroundCoordinateScale / waypointDimensionScale;

        List<WorldMarkerHudRenderer.Marker> entries = new ArrayList<>(waypoints.size());
        for (Waypoint waypoint : waypoints) {
            if (waypoint == null || waypoint.isDisabled()) continue;

            Vec3 anchor = new Vec3(
                    waypoint.getX(coordinateDivision) + 0.5,
                    waypoint.isYIncluded() ? waypoint.getY() + 1.0 : playerPosition.y + 1.0,
                    waypoint.getZ(coordinateDivision) + 0.5
            );
            double horizontalDistance = Math.hypot(anchor.x - playerPosition.x, anchor.z - playerPosition.z);
            if (minDistance > 0.0 && horizontalDistance < minDistance) continue;
            double configuredRangeDistance = dimensionScaleDistance
                    ? horizontalDistance * backgroundCoordinateScale
                    : horizontalDistance;
            if (outsideConfiguredRange(waypoint, configuredRangeDistance, maxDistance, temporaryWaypointsGlobal)) continue;

            Vec3 projected = ScreenProjection.worldToScreen(anchor, tickDelta);
            if (projected == null) continue;
            if (projected.x < -SCREEN_MARGIN || projected.x > viewportWidth + SCREEN_MARGIN
                    || projected.y < -SCREEN_MARGIN || projected.y > viewportHeight + SCREEN_MARGIN) {
                continue;
            }

            double dx = anchor.x - playerPosition.x;
            double dz = anchor.z - playerPosition.z;
            double dy = waypoint.isYIncluded() ? waypoint.getY() - playerPosition.y : 0.0;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            boolean shortDistanceSuppressed = distance <= 20.0 && !displayShortDistance;
            List<TextRenderUtil.Part> nameParts = keepNames ? safeNameParts(waypoint) : List.of();
            boolean distanceVisible = !shortDistanceSuppressed && shouldShowDistance(
                    minecraft, anchor, distanceMode, horizontalPointingAngle, verticalPointingAngle);
            String distanceText = distanceVisible ? formatDistance(distance, kmThreshold, precision) : "";

            int accent = 0xFF000000 | waypoint.getWaypointColor().getHex();
            entries.add(new WorldMarkerHudRenderer.Marker(
                    (float) projected.x,
                    (float) projected.y,
                    "map-pin",
                    iconSize,
                    nameParts,
                    nameSize,
                    distanceText,
                    distanceSize,
                    accent,
                    opacity
            ));
        }
        return List.copyOf(entries);
    }

    private static boolean outsideConfiguredRange(Waypoint waypoint,
                                                  double distance,
                                                  int maxDistance,
                                                  boolean temporaryWaypointsGlobal) {
        if (maxDistance == 0) return false;
        if (waypoint.isDestination() || waypoint.getPurpose() == WaypointPurpose.DEATH || waypoint.isGlobal()) return false;
        if (waypoint.isTemporary() && temporaryWaypointsGlobal) return false;
        return maxDistance > 0 && distance > maxDistance;
    }

    private static List<TextRenderUtil.Part> safeNameParts(Waypoint waypoint) {
        String name = waypoint.getLocalizedName();
        if (name == null || name.isBlank()) return List.of();
        Component component = LegacyTextUtil.convertLegacyCodesRobust(Component.literal(name.trim()));
        return TextRenderUtil.flattenStyled(component, 0xFFF5F8FC);
    }

    private static String formatDistance(double distance, int kmThreshold, int precision) {
        if (kmThreshold > 0 && distance >= kmThreshold) {
            int decimals = Math.min(3, precision);
            return String.format(Locale.ROOT, "%." + decimals + "f km", distance / 1000.0);
        }
        int decimals = Math.min(2, precision);
        if (decimals == 0) return Math.round(distance) + " m";
        return String.format(Locale.ROOT, "%." + decimals + "f m", distance);
    }

    private static float uiScaleFactor(ClientConfigManager config,
                                       IndexedConfigOption<Integer> option,
                                       double autoMultiplier) {
        float xaeroScale = autoMultiplier == 1.0
                ? MinimapConfigClientUtils.getUIScale(config, option)
                : MinimapConfigClientUtils.getUIScale(config, option, autoMultiplier);
        return clamp(xaeroScale, 1.0f, 4.0f);
    }

    private static boolean shouldShowDistance(Minecraft minecraft,
                                              Vec3 anchor,
                                              int distanceMode,
                                              int horizontalAngle,
                                              int verticalAngle) {
        if (distanceMode <= 0) return false;
        if (distanceMode >= 2) return true;
        if (horizontalAngle <= 0 || verticalAngle <= 0) return false;
        if (minecraft == null || minecraft.gameRenderer == null) return false;
        Camera camera = minecraft.gameRenderer.mainCamera();
        if (camera == null) return false;

        Vec3 cameraPos = camera.position();
        double dx = anchor.x - cameraPos.x;
        double dy = anchor.y - cameraPos.y;
        double dz = anchor.z - cameraPos.z;
        double horizontal = Math.hypot(dx, dz);
        if (horizontal < 1.0E-6 && Math.abs(dy) < 1.0E-6) return true;

        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.max(1.0E-6, horizontal)));
        float yawDelta = Math.abs(Mth.wrapDegrees(targetYaw - camera.yRot()));
        float pitchDelta = Math.abs(Mth.wrapDegrees(targetPitch - camera.xRot()));
        return (horizontalAngle >= 90 || yawDelta <= horizontalAngle)
                && (verticalAngle >= 90 || pitchDelta <= verticalAngle);
    }

    @SuppressWarnings("unchecked")
    private static boolean effectiveBoolean(ClientConfigManager config, ConfigOption<?> option) {
        return (Boolean) config.getEffective((ConfigOption<Boolean>) option);
    }

    @SuppressWarnings("unchecked")
    private static int effectiveInt(ClientConfigManager config, ConfigOption<?> option) {
        return ((Number) config.getEffective((ConfigOption<? extends Number>) option)).intValue();
    }

    @SuppressWarnings("unchecked")
    private static double effectiveNumber(ClientConfigManager config, ConfigOption<?> option) {
        return ((Number) config.getEffective((ConfigOption<? extends Number>) option)).doubleValue();
    }

    private static void recover(RuntimeException error) {
        retryAfterNanos = System.nanoTime() + 1_000_000_000L;
        frameEntries = List.of();
        DebugLog.warnOnce(
                "xaero-waypoint-hud-overlay-runtime",
                "Combatant Xaero waypoint HUD takeover failed temporarily; Xaero rendering will be used while it recovers",
                error
        );
    }

    private static void disable(LinkageError error) {
        failed = true;
        frameEntries = List.of();
        DebugLog.warnOnce(
                "xaero-waypoint-hud-overlay-linkage",
                "Combatant Xaero waypoint HUD takeover is incompatible; restoring Xaero world waypoint rendering",
                error
        );
    }

    private static float clamp01(float value) {
        return clamp(value, 0.0f, 1.0f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

}
