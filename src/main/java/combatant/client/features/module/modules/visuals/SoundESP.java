/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.ModeValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.features.module.HudPhase;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.MatteHudStyle;
import combatant.client.render.helpers.ScreenProjection;

import java.util.ArrayList;
import java.util.List;

//todo Description
@ModuleInfo(id = "soundesp", displayName = "SoundESP", category = ModuleCategory.VISUALS)
public class SoundESP extends Module {
    private static final String SETTING_NAME_MODE = "name_mode";
    private static final String SETTING_MAX_DISTANCE = "max_distance";
    private static final String SETTING_LIFETIME_MS = "lifetime_ms";
    private static final String SETTING_COLOR = "color";
    private static final double PAD_X = 4.0;
    private static final double PAD_Y = 1.5;
    private static final double LABEL_Y_OFFSET = 5.0;
    private static final double TEXT_SCALE = 0.82;
    private static final long FADE_IN_MS = 160L;
    private static final long FADE_OUT_MS = 360L;
    private final Minecraft mc = Minecraft.getInstance();
    private final ModeValue nameMode = modeSetting(
            "soundEspNameMode",
            SETTING_NAME_MODE,
            "Technical",
            "Technical",
            "Localized"
    );
    private final NumberValue<Integer> maxDistance =
            num("soundEspMaxDistance", SETTING_MAX_DISTANCE, 64, 4, 256);
    private final NumberValue<Integer> lifetimeMs =
            num("soundEspLifetimeMs", SETTING_LIFETIME_MS, 2000, 200, 10000);
    private final RGBAColorValue color =
            color("soundEspColor", SETTING_COLOR, "#FFFFFFFF");

    private final List<SoundPing> sounds = new ArrayList<>();
    private final Object soundLock = new Object();

    private static float computeAlpha(long ageMs, int lifetimeMs) {
        float in = FADE_IN_MS <= 0L ? 1.0f : clamp01(ageMs / (float) FADE_IN_MS);
        float out = FADE_OUT_MS <= 0L ? 1.0f : clamp01((lifetimeMs - ageMs) / (float) FADE_OUT_MS);
        return easeOutCubic(Math.min(in, out));
    }

    private static float easeOutCubic(float t) {
        float p = clamp01(t);
        float inv = 1.0f - p;
        return 1.0f - inv * inv * inv;
    }

    private static float clamp01(float value) {
        if (value < 0.0f) return 0.0f;
        if (value > 1.0f) return 1.0f;
        return value;
    }

    @Override
    public HudPhase getHudPhase() {
        return HudPhase.FIRST;
    }

    public void handleSound(Identifier id, Vec3 pos) {
        if (!isEnabled() || mc.level == null || mc.player == null) return;
        if (id == null || pos == null) return;
        synchronized (soundLock) {
            sounds.add(new SoundPing(id, pos, System.currentTimeMillis()));
        }
    }

    @Override
    public void onDisable() {
        synchronized (soundLock) {
            sounds.clear();
        }
    }

    @Override
    public void onRenderHudEngine(Renderer2D renderer, TextRenderer textRenderer, GuiGraphicsExtractor ctx, float tickDelta) {
        if (!isEnabled() || mc.level == null || mc.player == null) return;

        long now = System.currentTimeMillis();
        int lifetime = Math.max(1, lifetimeMs.get());
        List<SoundPing> snapshot = new ArrayList<>();
        Vec3 camPos = mc.gameRenderer.mainCamera().position();
        double maxDistSq = (double) maxDistance.get() * maxDistance.get();

        synchronized (soundLock) {
            for (int i = sounds.size() - 1; i >= 0; i--) {
                SoundPing ping = sounds.get(i);
                long age = now - ping.timestampMs();
                if (age > lifetime) {
                    sounds.remove(i);
                    continue;
                }
                if (camPos.distanceToSqr(ping.pos()) > maxDistSq) {
                    continue;
                }
                snapshot.add(ping);
            }
        }
        if (snapshot.isEmpty()) return;

        boolean measureStarted = false;
        if (!textRenderer.isBuilding()) {
            textRenderer.begin(TEXT_SCALE, true, false);
            measureStarted = true;
        }

        List<SoundLabel> labels = new ArrayList<>(snapshot.size());
        for (SoundPing ping : snapshot) {
            Vec3 screen = worldToScreen(ping.pos(), tickDelta);
            if (screen == null) continue;

            String text = resolveName(ping.getId());
            if (text.isBlank()) continue;

            long age = now - ping.timestampMs();
            float alpha = computeAlpha(age, lifetime);
            if (alpha <= 0.001f) continue;

            double textWidth = textRenderer.getWidth(text, false);
            double textHeight = textRenderer.getHeight(false);
            double width = textWidth + PAD_X * 2.0;
            double height = textHeight + PAD_Y * 2.0;
            double x = Math.floor(screen.x - width * 0.5 + 0.5);
            double y = Math.floor(screen.y - height - LABEL_Y_OFFSET + 0.5);
            double textX = x + PAD_X;
            double textY = y + (height - textHeight) * 0.5;
            labels.add(new SoundLabel(text, x, y, width, height, textX, textY, alpha));
        }

        if (measureStarted) {
            textRenderer.end();
        }
        if (labels.isEmpty()) return;

        for (SoundLabel label : labels) {
            MatteHudStyle.drawPlate(renderer, label.x(), label.y(), label.width(), label.height(), 2.0f, label.alpha());
        }

        boolean renderStarted = false;
        if (!textRenderer.isBuilding()) {
            textRenderer.begin(TEXT_SCALE);
            renderStarted = true;
        }
        int baseTextColor = color.getArgb();
        for (SoundLabel label : labels) {
            textRenderer.render(label.text(), label.textX(), label.textY(), new RenderColor(MatteHudStyle.scaleAlpha(baseTextColor, label.alpha())), false);
        }
        if (renderStarted) {
            textRenderer.end();
        }
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor ctx, float tickDelta) {
        // Vanilla DrawContext path is intentionally not used.
    }

    private String resolveName(Identifier id) {
        if (id == null) return "";
        if ("Localized".equals(nameMode.get())) {
            String key = "subtitles." + id.getPath();
            String translated = I18n.get(key);
            if (!translated.equals(key)) return translated;
        }
        return id.toString();
    }

    private Vec3 worldToScreen(Vec3 worldPos, float tickDelta) {
        return ScreenProjection.worldToScreen(worldPos, tickDelta);
    }

    private record SoundPing(Identifier id, Vec3 pos, long timestampMs) {
        public Identifier getId() {
            return id;
        }
    }

    private record SoundLabel(String text, double x, double y, double width, double height,
                              double textX, double textY, float alpha) {
    }
}
