/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.map;

import combatant.client.features.account.SkinManager;
import com.mojang.authlib.GameProfile;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.helpers.PlayerHeadRenderer;
import combatant.client.util.player.PlayerSkinResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** Shared player marker renderer used by local Xaero contacts and unified location overlays. */
public final class MapPlayerMarkerRenderer {
    private MapPlayerMarkerRenderer() {}

    public static float draw(float centerX,
                             float centerY,
                             UUID playerUuid,
                             String playerName,
                             int accentArgb,
                             String sourceGlyph,
                             float size,
                             float alpha) {
        float extent = drawMarker(centerX, centerY, playerUuid, playerName, accentArgb, sourceGlyph, size, alpha, false);
        Renderer2D.flushBatch();
        drawLabel(centerX, centerY, playerUuid, playerName, accentArgb, size, alpha, false);
        return extent;
    }

    /** Emits only marker geometry. Labels are intentionally a separate pass. */
    public static float drawMarker(float centerX,
                                   float centerY,
                                   UUID playerUuid,
                                   String playerName,
                                   int accentArgb,
                                   String sourceGlyph,
                                   float size,
                                   float alpha) {
        return drawMarker(centerX, centerY, playerUuid, playerName, accentArgb, sourceGlyph, size, alpha, false);
    }

    /**
     * Compact production player marker. Source glyphs do not live on the head itself:
     * source/freshness belongs to hover/context details, while the always-visible marker is only identity.
     */
    public static float drawMarker(float centerX,
                                   float centerY,
                                   UUID playerUuid,
                                   String playerName,
                                   int accentArgb,
                                   String sourceGlyph,
                                   float size,
                                   float alpha,
                                   boolean hovered) {
        alpha = clamp01(alpha);
        if (alpha <= 0.001f || size <= 0.0f) return 0.0f;
        String name = resolveDisplayName(playerUuid, playerName);
        Identifier skin = resolveSkin(playerUuid, name);

        float visualSize = size + (hovered ? 2.0f : 0.0f);
        float x = centerX - visualSize * 0.5f;
        float y = centerY - visualSize * 0.5f;
        float radius = Math.max(4.2f, visualSize * 0.24f);

        // Relation is represented as a soft underlay rather than an outline/stroke. This keeps the
        // head readable without turning every player into a bordered HUD card.
        float halo = hovered ? 0.28f : 0.16f;
        Renderer2D.COLOR.roundedRect(
                x - 1.25f, y - 1.25f, visualSize + 2.5f, visualSize + 2.5f,
                radius + 1.25f, withAlpha(accentArgb, halo * alpha));
        Renderer2D.COLOR.roundedRect(
                x, y, visualSize, visualSize, radius,
                withAlpha(0xFF0B1016, (hovered ? 0.46f : 0.34f) * alpha));

        PlayerHeadRenderer.drawRounded(null, x, y, visualSize, radius, skin,
                new RenderColor(255, 255, 255, Math.round(255.0f * alpha)), true,
                null, 0.0f, false);
        return visualSize * 0.5f;
    }

    /** Emits the identity plate above an already rendered marker. */
    public static void drawLabel(float centerX,
                                 float centerY,
                                 UUID playerUuid,
                                 String playerName,
                                 int accentArgb,
                                 float size,
                                 float alpha) {
        drawLabel(centerX, centerY, playerUuid, playerName, "", accentArgb, size, alpha, false);
    }

    public static void drawLabel(float centerX,
                                 float centerY,
                                 UUID playerUuid,
                                 String playerName,
                                 int accentArgb,
                                 float size,
                                 float alpha,
                                 boolean hovered) {
        drawLabel(centerX, centerY, playerUuid, playerName, "", accentArgb, size, alpha, hovered);
    }

    public static void drawLabel(float centerX,
                                 float centerY,
                                 UUID playerUuid,
                                 String playerName,
                                 String statusLabel,
                                 int accentArgb,
                                 float size,
                                 float alpha) {
        drawLabel(centerX, centerY, playerUuid, playerName, statusLabel, accentArgb, size, alpha, false);
    }

    /**
     * Minimal name treatment: neutral matte pill, relation dot and the nickname only. Persistent
     * source/stale text is intentionally omitted; it is available from the marker hover/context UI.
     */
    public static void drawLabel(float centerX,
                                 float centerY,
                                 UUID playerUuid,
                                 String playerName,
                                 String statusLabel,
                                 int accentArgb,
                                 float size,
                                 float alpha,
                                 boolean hovered) {
        alpha = clamp01(alpha);
        if (alpha <= 0.001f || size <= 0.0f) return;
        String name = resolveDisplayName(playerUuid, playerName);
        if (name.isBlank()) return;

        float markerTop = centerY - (size + (hovered ? 2.0f : 0.0f)) * 0.5f;
        float textSize = Math.max(10.0f, Math.min(11.2f, size * 0.375f));
        float textW = ClickGuiRenderer.textWidth(ClickGuiRenderer.getOnestMedium(), name, textSize);
        float dot = 3.0f;
        float gap = 4.0f;
        float padLeft = 5.0f;
        float padRight = 5.5f;
        float labelH = textSize + 4.6f;
        float labelW = padLeft + dot + gap + textW + padRight;
        float labelX = centerX - labelW * 0.5f;
        float labelY = markerTop - labelH - 2.5f;

        Renderer2D.COLOR.roundedRect(labelX, labelY, labelW, labelH, labelH * 0.36f,
                withAlpha(0xFF0B1016, (hovered ? 0.82f : 0.68f) * alpha));
        Renderer2D.COLOR.circle(labelX + padLeft + dot * 0.5f,
                labelY + labelH * 0.5f, dot * 0.5f,
                withAlpha(accentArgb, (hovered ? 1.0f : 0.88f) * alpha));
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), name,
                labelX + padLeft + dot + gap,
                labelY + 1.95f,
                textSize,
                withAlpha(0xFFF4F7FA, alpha), false);
    }

    /** Resolve the visible player identity centrally so every map source gets the same label. */
    public static String resolveDisplayName(UUID id, String fallback) {
        String cleanFallback = fallback == null ? "" : fallback.trim();
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && id != null) {
            if (mc.level != null) {
                net.minecraft.world.entity.player.Player player = mc.level.getPlayerByUUID(id);
                if (player != null) {
                    if (player.getGameProfile() != null && player.getGameProfile().name() != null
                            && !player.getGameProfile().name().isBlank()) {
                        return player.getGameProfile().name();
                    }
                    if (player.getName() != null && !player.getName().getString().isBlank()) {
                        return player.getName().getString();
                    }
                }
            }
            if (mc.getConnection() != null) {
                PlayerInfo info = mc.getConnection().getPlayerInfo(id);
                if (info != null && info.getProfile() != null && info.getProfile().name() != null
                        && !info.getProfile().name().isBlank()) {
                    return info.getProfile().name();
                }
            }
        }
        return cleanFallback;
    }

    public static Identifier resolveSkin(UUID id, String name) {
        Identifier cached = PlayerHeadRenderer.getCachedSkin(id);
        if (cached == null && !name.isBlank()) cached = PlayerHeadRenderer.getCachedSkin(name);
        Minecraft mc = Minecraft.getInstance();
        if (cached == null && mc != null && mc.getConnection() != null && id != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(id);
            if (info != null && info.getProfile() != null) {
                Identifier runtime = PlayerSkinResolver.resolveProfileSkin(info.getProfile());
                cached = PlayerHeadRenderer.resolveCachedSkin(id, info.getProfile().name(), runtime);
            }
        }
        if (cached == null && id != null) {
            Identifier resolved = PlayerSkinResolver.resolveProfileSkin(new GameProfile(id, name.isBlank() ? "Player" : name));
            cached = PlayerHeadRenderer.resolveCachedSkin(id, name, resolved);
        }
        if (cached == null && !name.isBlank()) {
            cached = SkinManager.getSkin(name);
            cached = PlayerHeadRenderer.resolveCachedSkin(id, name, cached);
        }
        return cached;
    }

    private static int withAlpha(int argb, float alpha) {
        return (Math.max(0, Math.min(255, Math.round(clamp01(alpha) * 255.0f))) << 24) | (argb & 0x00FFFFFF);
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
