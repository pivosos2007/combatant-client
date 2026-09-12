/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.compat.xaero;

import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.MatteHudStyle;
import combatant.client.render.helpers.ScreenProjection;
import combatant.client.runtime.RuntimeGate;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.text.LegacyTextUtil;
import combatant.client.util.text.TextRenderUtil;
import net.minecraft.network.chat.Component;
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

    private static final float BASE_ICON_SIZE = 19.0f;
    private static final float BASE_NAME_SIZE = 13.0f;
    private static final float BASE_DISTANCE_SIZE = 10.5f;
    private static final float PLATE_PAD_X = 5.0f;
    private static final float PLATE_PAD_Y = 4.0f;
    private static final float ICON_TEXT_GAP = 4.0f;
    private static final float LINE_GAP = 1.0f;
    private static final float ANCHOR_GAP = 7.0f;
    private static final float SCREEN_MARGIN = 6.0f;

    private static List<FrameEntry> frameEntries = List.of();
    private static boolean failed;
    private static long retryAfterNanos;

    public static boolean ownsXaeroWorldWaypoints() {
        if (failed || !RuntimeGate.canRunHud() || System.nanoTime() < retryAfterNanos) return false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) return false;
        try {
            if (BuiltInHudModules.MINIMAP.getCurrentSession() == null) return false;
            ClientConfigManager config = HudMod.INSTANCE.getHudConfigs().getClientConfigManager();
            return effectiveBoolean(config, MinimapProfiledConfigOptions.WAYPOINTS_IN_WORLD);
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

    public static void renderBackground(Renderer2D renderer, TextRenderer fallback, float tickDelta) {
        if (renderer == null || fallback == null || !ownsXaeroWorldWaypoints()) {
            frameEntries = List.of();
            return;
        }
        try {
            frameEntries = captureFrame(fallback, tickDelta);
            for (FrameEntry entry : frameEntries) {
                MatteHudStyle.drawCompactPlate(
                        renderer,
                        entry.plateX(),
                        entry.plateY(),
                        entry.plateWidth(),
                        entry.plateHeight(),
                        Math.min(5.0f, entry.plateHeight() * 0.28f),
                        entry.opacity()
                );
                renderer.svg(
                        "map-pin",
                        entry.iconX(),
                        entry.iconY(),
                        entry.iconSize(),
                        entry.iconSize(),
                        SvgRenderOptions.overrideColor(withAlpha(entry.accent(), Math.round(255.0f * entry.opacity())))
                );
            }
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
        boolean ownDecorationBatch = false;
        try {
            TextRenderer nameFont = Fonts.renderer("OnestMedium", FontInfo.Type.Regular, fallback);
            TextRenderer nameBold = Fonts.renderer("OnestBold", FontInfo.Type.Regular, nameFont);
            TextRenderer distanceFont = Fonts.renderer("OnestMedium", FontInfo.Type.Regular, nameFont);
            boolean hasDecorations = frameEntries.stream()
                    .flatMap(entry -> entry.nameParts().stream())
                    .anyMatch(part -> part.underline() || part.strikethrough());
            if (hasDecorations && !Renderer2D.isBatching()) {
                Renderer2D.COLOR.begin();
                ownDecorationBatch = true;
            }
            for (FrameEntry entry : frameEntries) {
                if (!entry.nameParts().isEmpty()) {
                    renderStyledName(nameFont, nameBold, entry.nameParts(), entry.textX(), entry.nameY(),
                            entry.nameSize(), entry.opacity());
                }
            }

            float distanceSize = frameEntries.getFirst().distanceSize();
            distanceFont.begin(distanceSize / 18.0f, false, false);
            try {
                for (FrameEntry entry : frameEntries) {
                    if (!entry.distance().isEmpty()) {
                        distanceFont.render(
                                entry.distance(),
                                entry.textX(),
                                entry.distanceY(),
                                new RenderColor(withAlpha(0xFFB6BEC9, Math.round(235.0f * entry.opacity()))),
                                false
                        );
                    }
                }
            } finally {
                distanceFont.end();
            }
        } catch (RuntimeException error) {
            recover(error);
        } catch (LinkageError error) {
            disable(error);
        } finally {
            if (ownDecorationBatch) {
                Renderer2D.COLOR.render();
            }
            frameEntries = List.of();
        }
    }

    public static void clearFrame() {
        frameEntries = List.of();
    }

    private static List<FrameEntry> captureFrame(TextRenderer fallback, float tickDelta) {
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

        TextRenderer nameFont = Fonts.renderer("OnestMedium", FontInfo.Type.Regular, fallback);
        TextRenderer distanceFont = Fonts.renderer("OnestMedium", FontInfo.Type.Regular, nameFont);
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

        List<FrameEntry> entries = new ArrayList<>(waypoints.size());
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
            String distanceText = distanceMode != 0 && !shortDistanceSuppressed
                    ? formatDistance(distance, kmThreshold, precision)
                    : "";

            float nameWidth = nameParts.isEmpty() ? 0.0f : measureStyledWidth(nameFont, nameParts, nameSize);
            float distanceWidth = distanceText.isEmpty() ? 0.0f : measureWidth(distanceFont, distanceText, distanceSize);
            float nameHeight = nameParts.isEmpty() ? 0.0f : measureHeight(nameFont, nameSize);
            float distanceHeight = distanceText.isEmpty() ? 0.0f : measureHeight(distanceFont, distanceSize);
            float textWidth = Math.max(nameWidth, distanceWidth);
            boolean hasText = textWidth > 0.0f;
            float textHeight = nameHeight + distanceHeight + (!nameParts.isEmpty() && !distanceText.isEmpty() ? LINE_GAP : 0.0f);
            float plateWidth = PLATE_PAD_X * 2.0f + iconSize + (hasText ? ICON_TEXT_GAP + textWidth : 0.0f);
            float contentHeight = Math.max(iconSize, textHeight);
            float plateHeight = PLATE_PAD_Y * 2.0f + contentHeight;
            float plateX = (float) projected.x - plateWidth * 0.5f;
            float plateY = (float) projected.y - plateHeight - ANCHOR_GAP;
            float iconX = plateX + PLATE_PAD_X;
            float iconY = plateY + (plateHeight - iconSize) * 0.5f;
            float textX = iconX + iconSize + ICON_TEXT_GAP;
            float textTop = plateY + (plateHeight - textHeight) * 0.5f;
            float nameY = textTop;
            float distanceY = nameParts.isEmpty() ? textTop : textTop + nameHeight + LINE_GAP;
            int accent = 0xFF000000 | waypoint.getWaypointColor().getHex();

            entries.add(new FrameEntry(
                    plateX, plateY, plateWidth, plateHeight,
                    iconX, iconY, iconSize,
                    textX, nameY, distanceY,
                    nameSize, distanceSize,
                    nameParts, distanceText, accent, opacity
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

    private static float measureStyledWidth(TextRenderer fallback, List<TextRenderUtil.Part> parts, float size) {
        TextRenderer bold = Fonts.renderer("OnestBold", FontInfo.Type.Regular, fallback);
        float width = 0.0f;
        for (TextRenderUtil.Part part : parts) {
            width += measureWidth(part.bold() ? bold : fallback, part.text(), size);
        }
        return width;
    }

    private static void renderStyledName(TextRenderer regular,
                                         TextRenderer bold,
                                         List<TextRenderUtil.Part> parts,
                                         float x,
                                         float y,
                                         float size,
                                         float opacity) {
        float cursor = x;
        for (TextRenderUtil.Part part : parts) {
            TextRenderer font = part.bold() ? bold : regular;
            font.begin(size / 18.0f, false, false);
            float width;
            try {
                font.render(part.text(), cursor, y,
                        new RenderColor(withAlpha(part.color(), Math.round(255.0f * opacity))), false);
                width = (float) font.getWidth(part.text(), false);
            } finally {
                font.end();
            }
            if (part.underline()) {
                Renderer2D.COLOR.quad(cursor, y + size + 0.3f, width, 0.75f,
                        withAlpha(part.color(), Math.round(255.0f * opacity)));
            }
            if (part.strikethrough()) {
                Renderer2D.COLOR.quad(cursor, y + size * 0.55f, width, 0.75f,
                        withAlpha(part.color(), Math.round(255.0f * opacity)));
            }
            cursor += width;
        }
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
        return clamp(xaeroScale / 2.0f, 0.85f, 2.0f);
    }

    private static float measureWidth(TextRenderer renderer, String text, float size) {
        renderer.begin(size / 18.0f, true, false);
        try {
            return (float) renderer.getWidth(text, false);
        } finally {
            renderer.end();
        }
    }

    private static float measureHeight(TextRenderer renderer, float size) {
        renderer.begin(size / 18.0f, true, false);
        try {
            return (float) renderer.getHeight(false);
        } finally {
            renderer.end();
        }
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

    private static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | ((Math.max(0, Math.min(255, alpha)) & 0xFF) << 24);
    }

    private static float clamp01(float value) {
        return clamp(value, 0.0f, 1.0f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private record FrameEntry(
            float plateX,
            float plateY,
            float plateWidth,
            float plateHeight,
            float iconX,
            float iconY,
            float iconSize,
            float textX,
            float nameY,
            float distanceY,
            float nameSize,
            float distanceSize,
            List<TextRenderUtil.Part> nameParts,
            String distance,
            int accent,
            float opacity
    ) {
    }
}
