/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.mainmenu;

import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.config.subsystem.VisualConfig;
import combatant.client.features.theme.Theme;
import combatant.client.features.theme.Themes;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.draw.UiHoneycombMaterialSpec;
import java.awt.Color;

final class MainMenuBackdrop {
    static final float MENU_SCALE = 1.22f;
    static final float SQRT_3 = 1.7320508f;
    static final float CELL_RADIUS = 31.5f * MENU_SCALE;
    static final float CELL_GAP = 0f;
    static final float HONEYCOMB_RIM_WIDTH = 0.92f * MENU_SCALE;
    private static final MainMenuHexTrail HEX_TRAIL = new MainMenuHexTrail();
    private static final MainMenuSmoke SMOKE = new MainMenuSmoke();
    private static final VisualConfig OPTIONS = VisualConfig.get();
    private static final AmbientClusterField AMBIENT = new AmbientClusterField();

    private MainMenuBackdrop() {
    }

    static GridLayout layout(float width, float height) {
        float radius = CELL_RADIUS;
        float stepX = SQRT_3 * radius;
        float stepY = 1.5f * radius;
        float originX = width * 0.5f - stepX * 0.5f;
        float originY = height * 0.56f;
        return new GridLayout(radius, stepX, stepY, originX, originY);
    }

    static void render(float width, float height, float opacity, float mouseX, float mouseY,
                       GridLayout grid, Cutout exclusion) {
        renderBase(width, height);
        renderCrystal(width, height, opacity, mouseX, mouseY, grid, exclusion);
        renderSmoke(width, height, opacity, mouseX, mouseY, false);
        renderHoneycomb(width, height, opacity, mouseX, mouseY, grid, exclusion);
    }

    static void renderBase(float width, float height) {
        Themes.Theme theme = Theme.theme();
        int base = theme != null ? theme.windowBg() : 0xFF10141B;
        int dark = HudRenderUtil.mixColor(base, 0xFF05070A, 0.75f) | 0xFF000000;
        int light = HudRenderUtil.mixColor(base, 0xFF080D16, 0.36f) | 0xFF000000;
        Renderer2D.COLOR.quad(0, 0, width, height, dark, dark, light, light);
    }

    static void renderSmoke(float width, float height, float opacity, float mouseX, float mouseY,
                            boolean fluidPointerDown) {
        GridLayout grid = layout(width, height);
        long now = System.nanoTime();
        HEX_TRAIL.advance(width, height, mouseX, mouseY, grid, now,
                !OPTIONS.menuCursorGlowEnabled(), fluidPointerDown);
        if (OPTIONS.menuAmbientGlowEnabled()) {
            AMBIENT.advance(width, height, now, grid);
            if (OPTIONS.menuFluidEnabled()) AMBIENT.emitFluid(now, HEX_TRAIL);
        }
        if (OPTIONS.menuFluidEnabled() && SMOKE.canAttemptCompute()) SMOKE.render(width, height, opacity, HEX_TRAIL, grid);
    }

    static boolean computeSupported() { return SMOKE.computeSupported(); }

    static void release() {
        SMOKE.close();
        HEX_TRAIL.clear();
        AMBIENT.clear();
    }

    static void renderCrystal(float width, float height, float opacity, float mouseX, float mouseY,
                              GridLayout grid, Cutout exclusion) {
        if (grid == null || opacity <= 0.001f || !OPTIONS.menuCrystalBackgroundEnabled()) return;
        Themes.Theme theme = Theme.theme();
        int accent = theme != null ? theme.accent() : 0xFF85B4FF;
        int secondary = theme != null ? theme.accentSoft() : 0xFF6285A0;
        if (OPTIONS.menuCustomColors()) {
            accent = OPTIONS.menuGlowColorValue().getArgb();
            secondary = OPTIONS.menuGridColorValue().getArgb();
        }
        int interiorColor = HudRenderUtil.mixColor(accent, secondary, 0.28f);
        UiHoneycombMaterialSpec material = new UiHoneycombMaterialSpec(
                UiHoneycombMaterialSpec.HEX_PATH.cellRadiusPx(),
                UiHoneycombMaterialSpec.HEX_PATH.lineWidthPx(),
                0f, 0f, 0f, UiHoneycombMaterialSpec.HEX_PATH.motionSpeed(),
                OPTIONS.menuCrystalIntensity(), OPTIONS.menuCrystalGloss());
        Cutout cutout = exclusion != null ? exclusion : Cutout.NONE;
        if (cutout.enabled) {
            Renderer2D.COLOR.honeycombAreaCutout(0, 0, width, height,
                    grid.radius, HONEYCOMB_RIM_WIDTH, opacity,
                    mouseX, mouseY, grid.radius * 2.7f, grid.originX, grid.originY,
                    interiorColor, material,
                    cutout.x, cutout.y, cutout.w, cutout.h, cutout.cut);
        } else {
            Renderer2D.COLOR.honeycombArea(0, 0, width, height,
                    grid.radius, HONEYCOMB_RIM_WIDTH, opacity,
                    mouseX, mouseY, grid.radius * 2.7f, grid.originX, grid.originY,
                    accent, material);
        }
    }

    static void renderHoneycomb(float width, float height, float opacity, float mouseX, float mouseY,
                                GridLayout grid, Cutout exclusion) {
        if (grid == null || opacity <= 0.001f) return;
        Themes.Theme theme = Theme.theme();
        int accent = theme != null ? theme.accent() : 0xFF85B4FF;
        int base = theme != null ? (theme.accentSoft() | 0xFF000000) : 0xFF895A30;
        if (OPTIONS.menuCustomColors()) {
            accent = OPTIONS.menuGlowColorValue().getArgb();
            base = OPTIONS.menuGridColorValue().getArgb();
        }
        Cutout cutout = exclusion != null ? exclusion : Cutout.NONE;
        boolean sharedFluid = OPTIONS.menuFluidEnabled() && OPTIONS.menuCursorGlowEnabled() && SMOKE.computeSupported();
        float glowShare = sharedFluid ? 0.58f : 1f;
        UiHoneycombMaterialSpec material = new UiHoneycombMaterialSpec(
                UiHoneycombMaterialSpec.HEX_PATH.cellRadiusPx(),
                UiHoneycombMaterialSpec.HEX_PATH.lineWidthPx(),
                0.17f * OPTIONS.menuGridIntensity(),
                0.85f * OPTIONS.menuCursorIntensity() * glowShare,
                0.62f * OPTIONS.menuHoneycombBloom() * glowShare,
                UiHoneycombMaterialSpec.HEX_PATH.motionSpeed(),
                OPTIONS.menuCrystalBackgroundEnabled() ? OPTIONS.menuCrystalIntensity() : 0f,
                OPTIONS.menuCrystalGloss());
        if (OPTIONS.menuGridEnabled()) {
            UiHoneycombMaterialSpec baseMaterial = new UiHoneycombMaterialSpec(
                    material.cellRadiusPx(), material.lineWidthPx(), material.lineOpacity(),
                    material.emission(), material.glow(), material.motionSpeed(),
                    0f, 0f);
            if (cutout.enabled) {
                Renderer2D.COLOR.honeycombAreaCutout(0, 0, width, height,
                        grid.radius, HONEYCOMB_RIM_WIDTH, opacity,
                        -100000f, -100000f, 0f, grid.originX, grid.originY,
                        base, baseMaterial,
                        cutout.x, cutout.y, cutout.w, cutout.h, cutout.cut);
            } else {
                Renderer2D.COLOR.honeycombArea(0, 0, width, height,
                        grid.radius, HONEYCOMB_RIM_WIDTH, opacity,
                        -100000f, -100000f, 0f, grid.originX, grid.originY,
                        base, baseMaterial);
            }
        }
        if (OPTIONS.menuAmbientGlowEnabled()) {
            AMBIENT.render(width, height, opacity * OPTIONS.menuAmbientIntensity() * (sharedFluid ? 0.56f : 1f),
                    grid, cutout, accent, coldColor(accent), material);
        }
        if (OPTIONS.menuCursorGlowEnabled()) {
            HEX_TRAIL.render(width, height, opacity, grid,
                    cutout, accent, coldColor(accent), material);
        }
    }

    static int warmColor(int accent) {
        float[] hsv = Color.RGBtoHSB((accent >>> 16) & 255, (accent >>> 8) & 255, accent & 255, null);
        float hue = (hsv[0] + 0.865f) % 1.0f;
        return Color.HSBtoRGB(hue,
                Math.max(0.55f, hsv[1] * 0.90f),
                Math.max(0.80f, hsv[2]));
    }

    static int coldColor(int accent) {
        float[] hsv = Color.RGBtoHSB((accent >>> 16) & 255, (accent >>> 8) & 255, accent & 255, null);
        float hue = (hsv[0] + 0.095f) % 1.0f;
        return Color.HSBtoRGB(hue,
                Math.max(0.42f, hsv[1] * 0.88f),
                Math.max(0.73f, hsv[2]));
    }

    private static final class AmbientClusterField {
        private static final int COUNT = 5;
        private final Cluster[] clusters = new Cluster[COUNT];
        private float width;
        private float height;

        private AmbientClusterField() {
            for (int i = 0; i < COUNT; i++) clusters[i] = new Cluster();
        }

        void advance(float width, float height, long now, GridLayout grid) {
            float time = (float) ((now * 1.0e-9) % 8192.0);
            if (this.width != width || this.height != height) {
                this.width = width;
                this.height = height;
                for (int i = 0; i < COUNT; i++) clusters[i].seed(width, height, i, time);
            }
            for (Cluster cluster : clusters) cluster.update(width, height, time, grid.radius());
        }

        void emitFluid(long now, MainMenuHexTrail trail) {
            float time = (float)((now * 1.0e-9) % 8192.0);
            for (int i = 0; i < COUNT; i++) {
                Cluster cluster = clusters[i];
                float phase = time * 0.55f + cluster.phaseOffset;
                float waveAge = (phase / 6.2831853f - (float)Math.floor(phase / 6.2831853f)) * 3.6f;
                trail.emitAmbient(cluster.x, cluster.y, phase,
                        cluster.strength(time) * 1.45f * OPTIONS.menuAmbientIntensity(), waveAge);
            }
        }

        void render(float width, float height, float opacity, GridLayout grid, Cutout cutout,
                    int hotColor, int coldColor, UiHoneycombMaterialSpec material) {
            float time = (float) ((System.nanoTime() * 1.0e-9) % 8192.0);
            for (int i = 0; i < COUNT; i++) {
                Cluster cluster = clusters[i];
                float strength = cluster.strength(time) * opacity * 0.84f;
                if (strength <= 0.001f) continue;
                Renderer2D.COLOR.honeycombAmbient(width, height,
                        grid.radius, HONEYCOMB_RIM_WIDTH, strength,
                        cluster.x, cluster.y, cluster.radius(grid.radius), cluster.phase(time),
                        grid.originX, grid.originY, hotColor, coldColor, material,
                        cutout.x, cutout.y, cutout.w, cutout.h, cutout.cut);
            }
        }

        void clear() {
            width = 0f;
            height = 0f;
        }

        private static final class Cluster {
            private float x;
            private float y;
            private float driftX;
            private float driftY;
            private float seed;
            private float phaseOffset;

            void seed(float width, float height, int index, float time) {
                float t = index * 1.731f + 2.17f;
                this.seed = t;
                this.phaseOffset = t * 0.73f;
                this.x = width * (0.18f + 0.64f * fract((float)Math.sin(t * 1.37) * 43758.5453f));
                this.y = height * (0.16f + 0.60f * fract((float)Math.sin(t * 1.91 + 3.0) * 15731.743f));
                this.driftX = (float)Math.cos(t * 2.13) * 0.65f;
                this.driftY = (float)Math.sin(t * 1.67) * 0.52f;
                update(width, height, time, 24f);
            }

            void update(float width, float height, float time, float radius) {
                float amp = radius * 0.95f;
                x = clamp(x + driftX * 0.09f + (float)Math.sin(time * 0.17f + seed) * amp * 0.010f,
                        radius * 1.5f, width - radius * 1.5f);
                y = clamp(y + driftY * 0.08f + (float)Math.cos(time * 0.13f + seed * 0.7f) * amp * 0.010f,
                        radius * 1.5f, height - radius * 1.5f);
            }

            float strength(float time) {
                float pulse = 0.5f + 0.5f * (float)Math.sin(time * 0.55f + phaseOffset);
                return 0.15f + pulse * 0.15f;
            }

            float radius(float cellRadius) {
                return cellRadius * 2.6f + cellRadius * 0.9f * fract((float)Math.sin(seed * 3.17f) * 21131.17f);
            }

            float phase(float time) {
                return time * 0.32f + phaseOffset;
            }

            private static float fract(float v) { return v - (float)Math.floor(v); }
            private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
        }
    }

    record GridLayout(float radius, float stepX, float stepY, float originX, float originY) {
    }

    record Cutout(float x, float y, float w, float h, float cut, boolean enabled) {
        private static final Cutout NONE = new Cutout(0f, 0f, 0f, 0f, 0f, false);

        static Cutout chamfered(float x, float y, float w, float h, float cut) {
            if (w <= 0f || h <= 0f) return NONE;
            return new Cutout(x, y, w, h, Math.max(0f, cut), true);
        }
    }
}
