/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.helpers;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import combatant.client.mixininterface.IGuiGraphics;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.util.player.PlayerSkinResolver;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;

public enum PlayerHeadRenderer {
    ;

    private static final float SOFTNESS = 1.0f;
    private static final float INSET_FACTOR = 0.06f;

    /**
     * Session-local player skin cache. Entries intentionally have no short TTL: a UUID -> Identifier
     * pair is tiny, and keeping it for the lifetime of the current server connection prevents HUD
     * heads from collapsing to a default skin when the entity/PlayerInfo temporarily disappears
     * (dimension changes, world swaps inside the same connection, locator snapshots, etc.).
     *
     * The cache is still bounded as a safety valve and is cleared explicitly on server disconnect.
     */
    private static final int SESSION_SKIN_CACHE_LIMIT = 1024;
    private static final Map<UUID, Identifier> SESSION_SKIN_CACHE =
            new LinkedHashMap<>(128, 0.75f, true);
    private static final Map<String, UUID> SESSION_NAME_INDEX = new LinkedHashMap<>();

    // 64x64 skin UVs
    private static final float FACE_U1 = 8f / 64f;
    private static final float FACE_V1 = 8f / 64f;
    private static final float FACE_U2 = 16f / 64f;
    private static final float FACE_V2 = 16f / 64f;

    private static final float HAT_U1 = 40f / 64f;
    private static final float HAT_V1 = 8f / 64f;
    private static final float HAT_U2 = 48f / 64f;
    private static final float HAT_V2 = 16f / 64f;

    /* ============================================================
       ROUNDED
       ============================================================ */

    public static void drawRounded(
            GuiGraphicsExtractor ctx,
            float x, float y, float size,
            float radius,
            AbstractClientPlayer player,
            RenderColor color,
            boolean secondLayer,
            RenderColor outlineColor,
            float outlineThickness,
            boolean unscaled
    ) {
        Identifier skin = resolveCachedSkin(player);
        drawRounded(ctx, x, y, size, radius, skin, color, secondLayer, outlineColor, outlineThickness, unscaled);
    }

    public static void drawRounded(
            GuiGraphicsExtractor ctx,
            float x, float y, float size,
            float radius,
            Identifier skin,
            RenderColor color,
            boolean secondLayer,
            RenderColor outlineColor,
            float outlineThickness,
            boolean unscaled
    ) {
        if (skin == null || color == null || color.a <= 0 || size <= 0f) return;

        if (!unscaled) {
            drawRoundedInternal(x, y, size, radius, skin, color, secondLayer, outlineColor, outlineThickness);
            return;
        }
        inProjection(ctx, true,
                () -> drawRoundedInternal(x, y, size, radius, skin, color, secondLayer, outlineColor, outlineThickness));
    }

    private static void drawRoundedInternal(float x,
                                            float y,
                                            float size,
                                            float radius,
                                            Identifier skin,
                                            RenderColor color,
                                            boolean secondLayer,
                                            RenderColor outlineColor,
                                            float outlineThickness) {
        if (outlineColor != null && outlineThickness > 0f) {
            Renderer2D.COLOR.roundedRectStroke(
                    x, y, size, size,
                    radius, SOFTNESS,
                    outlineThickness,
                    outlineColor.argb()
            );
        }

        float inset = size * INSET_FACTOR;
        float innerX = x + inset;
        float innerY = y + inset;
        float innerS = size - inset * 2f;
        float innerRadius = Math.max(0.5f, radius * 0.6f);

        int argb = color.argb();

        Renderer2D.TEXTURE.roundedTexRect(
                innerX, innerY, innerS, innerS,
                innerRadius, SOFTNESS,
                FACE_U1, FACE_V1, FACE_U2, FACE_V2,
                argb, skin
        );

        if (secondLayer) {
            Renderer2D.TEXTURE.roundedTexRect(
                    innerX, innerY, innerS, innerS,
                    innerRadius, SOFTNESS,
                    HAT_U1, HAT_V1, HAT_U2, HAT_V2,
                    argb, skin
            );
        }
    }

    /* ============================================================
       RECT (texQuad)
       ============================================================ */

    public static void drawRect(
            GuiGraphicsExtractor ctx,
            float x, float y, float size,
            AbstractClientPlayer player,
            RenderColor color,
            boolean secondLayer,
            RenderColor outlineColor,
            float outlineThickness,
            boolean unscaled
    ) {
        Identifier skin = resolveCachedSkin(player);
        drawRect(ctx, x, y, size, skin, color, secondLayer, outlineColor, outlineThickness, unscaled);
    }

    public static void drawRect(
            GuiGraphicsExtractor ctx,
            float x, float y, float size,
            Identifier skin,
            RenderColor color,
            boolean secondLayer,
            RenderColor outlineColor,
            float outlineThickness,
            boolean unscaled
    ) {
        if (skin == null || color == null || color.a <= 0 || size <= 0f) return;

        if (!unscaled) {
            drawRectInternal(x, y, size, skin, color, secondLayer, outlineColor, outlineThickness);
            return;
        }
        inProjection(ctx, true,
                () -> drawRectInternal(x, y, size, skin, color, secondLayer, outlineColor, outlineThickness));
    }

    private static void drawRectInternal(float x,
                                         float y,
                                         float size,
                                         Identifier skin,
                                         RenderColor color,
                                         boolean secondLayer,
                                         RenderColor outlineColor,
                                         float outlineThickness) {
        if (outlineColor != null && outlineThickness > 0f) {
            Renderer2D.COLOR.roundedRectStroke(
                    x, y, size, size,
                    0f, SOFTNESS,
                    outlineThickness,
                    outlineColor.argb()
            );
        }

        float inset = size * INSET_FACTOR;
        float innerX = x + inset;
        float innerY = y + inset;
        float innerS = size - inset * 2f;

        int argb = color.argb();

        Renderer2D tex = Renderer2D.TEXTURE;
        tex.begin();
        try {
            tex.texQuad(innerX, innerY, innerS, innerS, FACE_U1, FACE_V1, FACE_U2, FACE_V2, argb);
            if (secondLayer) {
                tex.texQuad(innerX, innerY, innerS, innerS, HAT_U1, HAT_V1, HAT_U2, HAT_V2, argb);
            }
        } finally {
            tex.end();
        }
        tex.render(skin);
    }


    /* ============================================================
       SESSION SKIN CACHE
       ============================================================ */

    public static Identifier resolveCachedSkin(AbstractClientPlayer player) {
        if (player == null) return null;
        String name = player.getGameProfile() != null ? player.getGameProfile().name() : null;
        return resolveCachedSkin(player.getUUID(), name, PlayerSkinResolver.resolvePlayerSkin(player));
    }

    /**
     * Returns the best skin known for this UUID and remembers useful candidates for the current
     * server session. Runtime skins always replace older values; a later default/fallback texture
     * never overwrites an already known runtime skin.
     */
    public static Identifier resolveCachedSkin(UUID playerId, Identifier candidate) {
        return resolveCachedSkin(playerId, null, candidate);
    }

    public static Identifier resolveCachedSkin(UUID playerId, String playerName, Identifier candidate) {
        synchronized (SESSION_SKIN_CACHE) {
            UUID indexedId = playerId;
            String normalizedName = normalizePlayerName(playerName);
            if (indexedId == null && normalizedName != null) {
                indexedId = SESSION_NAME_INDEX.get(normalizedName);
            }

            Identifier normalized = PlayerSkinResolver.normalizeSkinId(candidate);
            if (indexedId == null) return normalized;

            Identifier cached = SESSION_SKIN_CACHE.get(indexedId);
            if (normalized != null && shouldReplaceCachedSkin(cached, normalized)) {
                SESSION_SKIN_CACHE.put(indexedId, normalized);
                cached = normalized;
                trimSessionCache();
            }
            if (normalizedName != null) {
                SESSION_NAME_INDEX.put(normalizedName, indexedId);
            }

            return cached != null ? cached : normalized;
        }
    }

    public static Identifier getCachedSkin(UUID playerId) {
        if (playerId == null) return null;
        synchronized (SESSION_SKIN_CACHE) {
            return SESSION_SKIN_CACHE.get(playerId);
        }
    }

    public static Identifier getCachedSkin(String playerName) {
        String normalizedName = normalizePlayerName(playerName);
        if (normalizedName == null) return null;
        synchronized (SESSION_SKIN_CACHE) {
            UUID id = SESSION_NAME_INDEX.get(normalizedName);
            return id != null ? SESSION_SKIN_CACHE.get(id) : null;
        }
    }

    public static void clearSessionCache() {
        synchronized (SESSION_SKIN_CACHE) {
            SESSION_SKIN_CACHE.clear();
            SESSION_NAME_INDEX.clear();
        }
    }

    private static boolean shouldReplaceCachedSkin(Identifier cached, Identifier candidate) {
        if (candidate == null) return false;
        if (cached == null || cached.equals(candidate)) return true;

        boolean cachedRuntime = isRuntimeSkin(cached);
        boolean candidateRuntime = isRuntimeSkin(candidate);
        if (candidateRuntime) return true;
        return !cachedRuntime;
    }

    private static String normalizePlayerName(String name) {
        if (name == null || name.isBlank()) return null;
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isRuntimeSkin(Identifier id) {
        if (id == null) return false;
        String path = id.getPath();
        return path.startsWith("skins/") || path.startsWith("skin/");
    }

    private static void trimSessionCache() {
        while (SESSION_SKIN_CACHE.size() > SESSION_SKIN_CACHE_LIMIT) {
            var iterator = SESSION_SKIN_CACHE.entrySet().iterator();
            if (!iterator.hasNext()) return;
            UUID evicted = iterator.next().getKey();
            iterator.remove();
            SESSION_NAME_INDEX.entrySet().removeIf(entry -> evicted.equals(entry.getValue()));
        }
    }

    /* ============================================================
       PROJECTION
       ============================================================ */

    private static void inProjection(GuiGraphicsExtractor ctx, boolean unscaled, Runnable draw) {
        if (!unscaled) {
            draw.run();
            return;
        }

        if (ctx instanceof IGuiGraphics accessor) {
            accessor.combatant$runUnscaled(draw);
            return;
        }

        ViewportContext.beginUnscaled(ctx);
        try {
            draw.run();
        } finally {
            ViewportContext.end(ctx);
        }
    }
}
