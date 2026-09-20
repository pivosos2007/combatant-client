/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.map;

import combatant.client.config.subsystem.MapUiConfig;
import combatant.client.features.map.location.PlayerLocationService;
import combatant.client.features.map.location.PlayerLocationSnapshot;
import combatant.client.features.map.location.PlayerLocationSource;
import combatant.client.features.relations.CategoryService;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.BuiltinFontCatalog;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.engine.text.TextSizing;
import combatant.client.render.helpers.PlayerHeadRenderer;
import combatant.client.render.helpers.ScreenProjection;
import combatant.client.runtime.RuntimeGate;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * World-space HUD for exact remote player locations which are outside the client's entity tracking range.
 * Loaded player entities intentionally invalidate their remote marker to prevent duplicate identity HUD.
 */
public enum PlayerLocationHudOverlay {
    ;

    private static final float HEAD_SIZE = 24.0f;
    private static final float BORDER = 1.25f;
    private static final float HOVER_PAD = 4.0f;
    private static final float CARD_GAP = 5.0f;
    private static final float CARD_RIGHT_PAD = 14.0f;
    private static final float TITLE_SIZE = 12.75f;
    private static final float META_SIZE = 10.0f;
    private static final float SCREEN_MARGIN = 8.0f;

    private static List<Layout> frameEntries = List.of();

    public static boolean hasHudWork() {
        MapUiConfig config = MapUiConfig.get();
        if (!config.hudEnabled() || !config.hudPlayerMarkers() || !RuntimeGate.canRunHud()) return false;
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null
                && minecraft.player != null
                && minecraft.level != null
                && !PlayerLocationService.get().snapshot().bestByPlayer().isEmpty();
    }

    public static void renderBackground(Renderer2D renderer, TextRenderer fallback, float tickDelta) {
        if (renderer == null || fallback == null || !hasHudWork()) {
            frameEntries = List.of();
            return;
        }

        frameEntries = layout(captureCandidates(tickDelta), fallback);
        if (frameEntries.isEmpty()) return;

        // The background and relation border must be submitted before the immediate textured head pass.
        for (Layout entry : frameEntries) {
            if (entry.hovered()) {
                renderer.quadGradientLinear(
                        entry.cardX(), entry.cardY(), entry.cardWidth(), entry.cardHeight(),
                        0xD80B1016, 0x100B1016, 0.0f
                );
                renderer.quad(
                        entry.cardX(), entry.cardY(), 1.5f, entry.cardHeight(),
                        withAlpha(entry.accentArgb(), 224)
                );
            }
            drawQuadBorder(renderer, entry.headX(), entry.headY(), HEAD_SIZE, BORDER, entry.accentArgb());
        }
        Renderer2D.flushBatch();

        for (Layout entry : frameEntries) {
            PlayerHeadRenderer.drawRect(
                    null,
                    entry.headX(), entry.headY(), HEAD_SIZE,
                    entry.skin(),
                    new RenderColor(255, 255, 255, 255),
                    true,
                    null,
                    0.0f,
                    false
            );
        }
    }

    public static void renderForeground(TextRenderer fallback) {
        if (fallback == null || frameEntries.isEmpty()) {
            frameEntries = List.of();
            return;
        }

        TextRenderer title = BuiltinFontCatalog.ONEST_BOLD.renderer(fallback);
        TextRenderer meta = BuiltinFontCatalog.ONEST_BOLD.renderer(title);
        try {
            for (Layout entry : frameEntries) {
                if (!entry.hovered()) continue;

                title.begin(TextSizing.scaleForSize(TITLE_SIZE), false, false);
                try {
                    title.render(
                            entry.playerName(),
                            entry.textX(), entry.titleY(),
                            new RenderColor(0xFFF5F8FC),
                            false
                    );
                } finally {
                    title.end();
                }

                meta.begin(TextSizing.scaleForSize(META_SIZE), false, false);
                try {
                    meta.render(
                            entry.distanceText(),
                            entry.textX(), entry.metaY(),
                            new RenderColor(withAlpha(entry.accentArgb(), 220)),
                            false
                    );
                } finally {
                    meta.end();
                }
            }
        } finally {
            frameEntries = List.of();
        }
    }

    public static void clearFrame() {
        frameEntries = List.of();
    }

    private static List<Candidate> captureCandidates(float tickDelta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) return List.of();

        String currentDimension = minecraft.level.dimension().identifier().toString();
        Vec3 playerPosition = minecraft.player.getPosition(tickDelta);
        UUID localPlayerId = minecraft.player.getUUID();
        double maxDistance = MapUiConfig.get().hudPlayerMaxDistance();
        ViewportContext viewport = ViewportContext.current();
        double viewportWidth = viewport != null ? viewport.width() : minecraft.getWindow().getWidth();
        double viewportHeight = viewport != null ? viewport.height() : minecraft.getWindow().getHeight();

        List<Candidate> out = new ArrayList<>();
        for (Map.Entry<UUID, PlayerLocationSnapshot> source
                : PlayerLocationService.get().snapshot().bestByPlayer().entrySet()) {
            UUID playerId = source.getKey();
            PlayerLocationSnapshot location = source.getValue();
            if (playerId == null || location == null || playerId.equals(localPlayerId)) continue;
            if (!location.exact() || location.source() == PlayerLocationSource.LOCAL_ENTITY_EXACT) continue;
            if (!currentDimension.equals(location.worldIdentity().dimensionKey())) continue;

            // This is the important MapLink-style invalidation contract: once Minecraft itself has
            // a tracked entity for the player, the remote marker is redundant and must disappear.
            if (minecraft.level.getPlayerByUUID(playerId) != null) continue;

            double anchorY = Double.isFinite(location.y()) ? location.y() + 1.62 : playerPosition.y + 1.0;
            Vec3 anchor = new Vec3(location.x(), anchorY, location.z());
            double distance = playerPosition.distanceTo(new Vec3(
                    location.x(), Double.isFinite(location.y()) ? location.y() : playerPosition.y, location.z()));
            if (!Double.isFinite(distance) || distance > maxDistance) continue;

            Vec3 projected = ScreenProjection.worldToScreen(anchor, tickDelta);
            if (projected == null) continue;
            if (projected.x < -SCREEN_MARGIN || projected.x > viewportWidth + SCREEN_MARGIN
                    || projected.y < -SCREEN_MARGIN || projected.y > viewportHeight + SCREEN_MARGIN) {
                continue;
            }

            String name = MapPlayerMarkerRenderer.resolveDisplayName(playerId, location.playerName());
            if (name == null || name.isBlank()) name = location.playerName();
            if (name == null || name.isBlank()) name = playerId.toString();
            int accent = CategoryService.getColor(name);
            Identifier skin = MapPlayerMarkerRenderer.resolveSkin(playerId, name);
            if (skin == null) continue;

            out.add(new Candidate(
                    playerId,
                    name,
                    accent,
                    skin,
                    (float) projected.x,
                    (float) projected.y,
                    distance
            ));
        }

        // Distant heads first, near heads last. This matches natural depth priority for overlaps.
        out.sort(Comparator.comparingDouble(Candidate::distance).reversed());
        return out;
    }

    private static List<Layout> layout(List<Candidate> candidates, TextRenderer fallback) {
        if (candidates == null || candidates.isEmpty()) return List.of();

        ViewportContext viewport = ViewportContext.current();
        Minecraft minecraft = Minecraft.getInstance();
        float viewportWidth = viewport != null
                ? viewport.width()
                : minecraft != null ? minecraft.getWindow().getWidth() : 0.0f;
        float viewportHeight = viewport != null
                ? viewport.height()
                : minecraft != null ? minecraft.getWindow().getHeight() : 0.0f;
        float hoverX = viewportWidth * 0.5f;
        float hoverY = viewportHeight * 0.5f;

        Candidate hovered = null;
        double bestHoverDistance = Double.POSITIVE_INFINITY;
        float halfHit = HEAD_SIZE * 0.5f + HOVER_PAD;
        for (Candidate candidate : candidates) {
            float dx = hoverX - candidate.anchorX();
            float dy = hoverY - candidate.anchorY();
            if (Math.abs(dx) > halfHit || Math.abs(dy) > halfHit) continue;
            double hitDistance = dx * dx + dy * dy;
            if (hitDistance < bestHoverDistance) {
                hovered = candidate;
                bestHoverDistance = hitDistance;
            }
        }

        TextRenderer title = BuiltinFontCatalog.ONEST_BOLD.renderer(fallback);
        TextRenderer meta = BuiltinFontCatalog.ONEST_BOLD.renderer(title);
        List<Layout> layouts = new ArrayList<>(candidates.size());
        for (Candidate candidate : candidates) {
            boolean isHovered = candidate == hovered;
            float headX = candidate.anchorX() - HEAD_SIZE * 0.5f;
            float headY = candidate.anchorY() - HEAD_SIZE * 0.5f;
            String distanceText = formatDistance(candidate.distance());

            float cardX = headX - 2.0f;
            float cardY = headY - 2.0f;
            float cardHeight = HEAD_SIZE + 4.0f;
            float textX = headX + HEAD_SIZE + CARD_GAP;
            float titleHeight = measureHeight(title, TITLE_SIZE);
            float metaHeight = measureHeight(meta, META_SIZE);
            float textGap = 1.0f;
            float textBlockHeight = titleHeight + textGap + metaHeight;
            float textTop = cardY + (cardHeight - textBlockHeight) * 0.5f;
            float titleY = textTop;
            float metaY = textTop + titleHeight + textGap;
            float cardWidth = HEAD_SIZE + 4.0f;
            if (isHovered) {
                float titleWidth = measure(title, candidate.playerName(), TITLE_SIZE);
                float metaWidth = measure(meta, distanceText, META_SIZE);
                cardWidth = HEAD_SIZE + CARD_GAP + Math.max(titleWidth, metaWidth) + CARD_RIGHT_PAD + 2.0f;
            }

            layouts.add(new Layout(
                    candidate.playerId(),
                    candidate.playerName(),
                    candidate.accentArgb(),
                    candidate.skin(),
                    candidate.distance(),
                    distanceText,
                    candidate.anchorX(), candidate.anchorY(),
                    headX, headY,
                    cardX, cardY, cardWidth, cardHeight,
                    textX, titleY, metaY,
                    isHovered
            ));
        }

        // Keep the active card and head over overlapping passive heads.
        if (hovered != null) {
            layouts.sort(Comparator.comparing(Layout::hovered));
        }
        return List.copyOf(layouts);
    }

    private static float measure(TextRenderer renderer, String text, float size) {
        if (renderer == null || text == null || text.isEmpty()) return 0.0f;
        renderer.begin(TextSizing.scaleForSize(size), true, false);
        try {
            return (float) renderer.getWidth(text, false);
        } finally {
            renderer.end();
        }
    }

    private static float measureHeight(TextRenderer renderer, float size) {
        if (renderer == null) return 0.0f;
        renderer.begin(TextSizing.scaleForSize(size), true, false);
        try {
            return (float) renderer.getHeight(false);
        } finally {
            renderer.end();
        }
    }

    private static void drawQuadBorder(Renderer2D renderer,
                                       float x,
                                       float y,
                                       float size,
                                       float thickness,
                                       int argb) {
        renderer.quad(x, y, size, thickness, argb);
        renderer.quad(x, y + size - thickness, size, thickness, argb);
        renderer.quad(x, y + thickness, thickness, size - thickness * 2.0f, argb);
        renderer.quad(x + size - thickness, y + thickness, thickness, size - thickness * 2.0f, argb);
    }

    private static String formatDistance(double distance) {
        if (distance >= 1000.0) {
            return String.format(java.util.Locale.ROOT, "%.1f km", distance / 1000.0);
        }
        return Math.round(distance) + " m";
    }

    private static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | ((Math.max(0, Math.min(255, alpha)) & 0xFF) << 24);
    }

    private record Candidate(
            UUID playerId,
            String playerName,
            int accentArgb,
            Identifier skin,
            float anchorX,
            float anchorY,
            double distance
    ) {}

    private record Layout(
            UUID playerId,
            String playerName,
            int accentArgb,
            Identifier skin,
            double distance,
            String distanceText,
            float anchorX,
            float anchorY,
            float headX,
            float headY,
            float cardX,
            float cardY,
            float cardWidth,
            float cardHeight,
            float textX,
            float titleY,
            float metaY,
            boolean hovered
    ) {}
}
