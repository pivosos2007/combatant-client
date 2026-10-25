/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.hud;

import combatant.client.render.engine.renderer.ui.draw.UiGlassVisual;
import combatant.client.events.impl.RenderPrewarmCollectEvent;
import combatant.client.render.engine.world.environment.BiomeClimateSampler;
import combatant.client.render.engine.world.environment.BiomeClimateSample;
import combatant.client.render.engine.world.environment.PrecipitationKind;
import combatant.client.runtime.CombatantBuild;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;

/**
 * Camera-local, purely visual climate state for UI materials.
 *
 * Precipitation uses the effective Level weather, including enabled WorldTweaks
 * overrides, and vanilla per-position exposure checks at the player's eyes.
 * Leaving water is a separate, short-lived deposition event.
 * No packets, simulated player flags, or world-render rain state are altered.
 * Captured only on the render/client thread, never inside a fragment draw.
 */
public final class HudClimateSurface {
    public record Sample(float rain, float snow, float waterExit, float frost, float humidity,
                         float condensation, float heat) {
        public static final Sample DRY = new Sample(0f, 0f, 0f, 0f, 0f, 0f, 0f);
        public boolean active() { return rain + snow + waterExit + frost + condensation + heat > 0.012f; }
    }

    private static final UiGlassVisual CLIMATE_VISUAL = UiGlassVisual.shader("ui_glass_climate.frag", 11.0f);
    private static final ThreadLocal<Boolean> ENABLED = ThreadLocal.withInitial(() -> true);
    private static final ThreadLocal<Integer> HUD_SCOPES = ThreadLocal.withInitial(() -> 0);
    private static ClientLevel previousLevel;
    private static long previousMillis = -1L;
    private static boolean previousInWater;
    private static float rain, snow, water, waterReservoir, frost, humidity, condensation, heat;
    private static float surfaceTemperature;
    private static Sample last = Sample.DRY;

    private HudClimateSurface() { }

    /** Per-widget opt-out; scopes capture while constructing deferred draw commands. */
    public static void withEnabled(boolean enabled, Runnable draw) {
        if (!CombatantBuild.isDevelopmentBuild()) {
            draw.run();
            return;
        }
        boolean previous = ENABLED.get();
        ENABLED.set(previous && enabled);
        HUD_SCOPES.set(HUD_SCOPES.get() + 1);
        try { draw.run(); } finally {
            ENABLED.set(previous);
            HUD_SCOPES.set(Math.max(0, HUD_SCOPES.get() - 1));
        }
    }

    public static Sample sample() {
        if (!CombatantBuild.isDevelopmentBuild()) return Sample.DRY;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) {
            previousLevel = null;
            previousMillis = -1L;
            previousInWater = false;
            rain = snow = water = waterReservoir = frost = humidity = condensation = heat = 0f;
            surfaceTemperature = 0f;
            return last = Sample.DRY;
        }
        ClientLevel level = mc.level;
        long now = Util.getMillis();
        if (previousLevel != level) {
            previousLevel = level;
            previousMillis = now;
            previousInWater = mc.player.isInWater() || mc.player.isUnderWater();
            rain = snow = water = waterReservoir = frost = humidity = condensation = heat = 0f;
            BlockPos initialPos = BlockPos.containing(mc.player.getEyePosition());
            surfaceTemperature = BiomeClimateSampler.sampleAt(level, initialPos.getX(),
                    initialPos.getY(), initialPos.getZ()).effectiveTemperature();
        }
        // Sample the world at most 20 times per second regardless of HUD count.
        if (now - previousMillis < 50L) return last;
        float dt = Math.min(Math.max(now - previousMillis, 0L) / 1000f, 0.25f);
        previousMillis = now;

        // A local exposure check must follow the player/camera, not a global
        // weather boolean: standing under a roof must stop NEW rain deposition.
        BlockPos pos = BlockPos.containing(mc.player.getEyePosition());
        boolean submerged = mc.player.isInWater() || mc.player.isUnderWater();
        if (previousInWater && !submerged) waterReservoir = 1.0f;
        previousInWater = submerged;

        // Level.getRainLevel and isRainingAt include the active WorldTweaks
        // visual weather mode. isRainingAt also checks precipitation type,
        // sky exposure, and the blocking heightmap at the player's eyes.
        float levelRain = clamp01(level.getRainLevel(1.0f));
        boolean rainExposed = !submerged && level.isRainingAt(pos);
        BiomeClimateSample biome = BiomeClimateSampler.sampleAt(level, pos.getX(), pos.getY(), pos.getZ());
        // Snow shares the same spatial exposure test as rain, using the
        // effective WorldTweaks-aware rain level and the biome's SNOW type.
        boolean snowExposed = !submerged && level.isRaining() && biome.valid()
                && biome.precipitation() == PrecipitationKind.SNOW
                && level.canSeeSky(pos)
                && level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, pos).getY() <= pos.getY();
        float airTemperature = biome.valid() ? biome.effectiveTemperature() : 0.50f;
        float targetFrost = biome.valid()
                ? clamp01((0.22f - airTemperature) * 1.65f)
                : 0f;
        // Biome downfall is a humidity proxy, not an actual relative humidity measurement.
        float targetHumidity = biome.valid() ? biome.humidityBaseline() : 0f;
        rain = smooth(rain, rainExposed ? levelRain : 0f, dt, rainExposed ? 2.3f : 7.0f);
        snow = smooth(snow, snowExposed ? levelRain : 0f, dt, snowExposed ? 3.5f : 7f);
        water = smooth(water, waterReservoir, dt, waterReservoir > water ? 0.65f : 4.0f);
        waterReservoir = smooth(waterReservoir, 0f, dt, 4.7f);
        frost = smooth(frost, targetFrost, dt, targetFrost > frost ? 13f : 8f);
        humidity = smooth(humidity, targetHumidity, dt, 3f);
        // A slowly responding glass surface can stay cooler than suddenly
        // warming humid air. Downfall is a proxy, not measured dew-point data.
        surfaceTemperature = smooth(surfaceTemperature, airTemperature, dt, 11f);
        float targetCondensation = biome.valid()
                ? clamp01((airTemperature - surfaceTemperature - 0.045f) *
                        Math.max(0f, targetHumidity - 0.35f) * 2.4f) : 0f;
        condensation = smooth(condensation, targetCondensation, dt,
                targetCondensation > condensation ? 3f : 8f);
        heat = smooth(heat, biome.valid() ? clamp01((airTemperature - 0.82f) * 1.9f) : 0f,
                dt, 5f);
        return last = new Sample(rain, snow, water, frost, humidity, condensation, heat);
    }

    /** Climate for an ordinary physical surface, not a glass-only effect. */
    public static boolean materialHudScopeActive() {
        return CombatantBuild.isDevelopmentBuild() && HUD_SCOPES.get() > 0 && ENABLED.get();
    }

    public static combatant.client.render.engine.renderer.ui.draw.UiMaterialClimateSpec materialClimate() {
        if (!materialHudScopeActive())
            return combatant.client.render.engine.renderer.ui.draw.UiMaterialClimateSpec.DRY;
        Sample s = sample();
        return new combatant.client.render.engine.renderer.ui.draw.UiMaterialClimateSpec(
                s.rain(), s.waterExit(), s.frost(), s.snow(), s.condensation());
    }

    /** Optional climate coat for opaque, matte, cellular and metallic HUD materials. */
    public static void drawMaterialClimate(combatant.client.render.engine.renderer.Renderer2D renderer,
                                           combatant.client.render.engine.renderer.ui.draw.UiPrimitive primitive,
                                           float opacity) {
        if (renderer == null || primitive == null || opacity <= 0f) return;
        var values = materialClimate();
        if (!values.active()) return;
        renderer.climatePrimitive(primitive,
                combatant.client.render.engine.renderer.ui.draw.UiPaint.solid(
                        (Math.round(Math.min(1f, opacity) * 255f) << 24) | 0x00FFFFFF), values);
    }

    public static void declarePrewarm(RenderPrewarmCollectEvent event) {
        if (CombatantBuild.isDevelopmentBuild() && event != null) event.glassVisual(CLIMATE_VISUAL);
    }

    /** A single custom glass pass: rain beads, exit-water streaks and frost edging. */
    public static UiGlassVisual glassVisual(boolean enabled) {
        if (!CombatantBuild.isDevelopmentBuild() || !enabled || !ENABLED.get()) return UiGlassVisual.DEFAULT;
        Sample s = sample();
        if (!s.active()) return UiGlassVisual.DEFAULT;
        return CLIMATE_VISUAL.withVector(0, s.rain(), s.waterExit(), s.frost(), s.snow())
                .withVector(1, s.humidity(), s.condensation(), s.heat(), 0.0f);
    }

    private static float smooth(float current, float target, float dt, float tau) {
        return current + (target - current) * (1f - (float) Math.exp(-dt / tau));
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
}
