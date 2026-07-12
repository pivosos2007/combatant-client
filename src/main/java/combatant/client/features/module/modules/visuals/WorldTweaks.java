/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import combatant.client.config.values.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import combatant.client.config.values.*;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.WorldPhase;
import combatant.client.mixins.accessors.WorldAccessor;
import combatant.client.render.engine.RenderState;

import java.time.LocalTime;
import java.util.LinkedHashMap;

//todo Description
@ModuleInfo(id = "worldtweaks", displayName = "WorldTweaks", category = ModuleCategory.VISUALS)
public class WorldTweaks extends Module {

    private static final long DAY_TICKS = 24000L;
    private static final float RAIN_THRESHOLD = 0.2f;
    private static final float THUNDER_THRESHOLD = 0.9f;

    private static final String SETTING_FOG_CONTROL = "fog_control";
    private static final String SETTING_FOG_MODIFY = "fog_modify";
    private static final String SETTING_FOG_START = "fog_start";
    private static final String SETTING_FOG_END = "fog_end";
    private static final String SETTING_FOG_COLOR = "fog_color";
    private static final String SETTING_LIQUID_FOG = "liquid_fog";
    private static final String SETTING_ATMOSPHERIC_FOG = "atmospheric_fog";
    private static final String SETTING_DISTANCE_BLOCKS = "distance_blocks";
    private static final String SETTING_WEATHER_DISTANCE_BLOCKS = "weather_distance_blocks";

    private static final String SETTING_WEATHER_CONTROL = "weather_control";
    private static final String SETTING_WEATHER_MODE = "weather_mode";
    private static final String SETTING_SKY = "sky";
    private static final String SETTING_SKY_COLOR = "sky_color";
    private static final String SETTING_SKY_GRADIENT_COLOR = "sky_gradient_color";

    private static final String SETTING_CHANGE_TIME = "change_time";
    private static final String SETTING_TIME_HOURS = "time_hours";
    private static final String SETTING_SYNC_SYSTEM_TIME = "sync_system_time";
    private static final String SETTING_ITEM_PHYSICS = "item_physics";
    private static volatile long serverTimeOfDay;
    private final BooleanValue fogControlEnabled =
            bool("worldTweaksFogControl", SETTING_FOG_CONTROL, true);
    private final BooleanValue fogModifyEnabled =
            visibleWhen(bool("worldTweaksFogModify", SETTING_FOG_MODIFY, false), fogControlEnabled::get);
    private final NumberValue<Integer> fogStart =
            visibleWhen(num("worldTweaksFogStart", SETTING_FOG_START, 0, 0, 256),
                    () -> fogControlEnabled.get() && fogModifyEnabled.get());
    private final NumberValue<Integer> fogEnd =
            visibleWhen(num("worldTweaksFogEnd", SETTING_FOG_END, 64, 10, 256),
                    () -> fogControlEnabled.get() && fogModifyEnabled.get());
    private final RGBColorValue fogColor =
            visibleWhen(colorNoAlpha("worldTweaksFogColor", SETTING_FOG_COLOR, "#A900FF"),
                    () -> fogControlEnabled.get() && fogModifyEnabled.get());
    private final BooleanMapValue liquidToggles = visibleWhen(group(
            "noFogLiquids",
            SETTING_LIQUID_FOG,
            new LinkedHashMap<>() {{
                put("Water", true);
                put("Lava", true);
                put("Powder snow", true);
            }}
    ), fogControlEnabled::get);
    private final BooleanMapValue atmosphericToggles = visibleWhen(group(
            "noFogAtmospheric",
            SETTING_ATMOSPHERIC_FOG,
            new LinkedHashMap<>() {{
                put("Overworld", true);
                put("Nether", true);
                put("End", true);
                put("Distance fog", true);
                put("Weather fog", true);
            }}
    ), fogControlEnabled::get);
    private final NumberValue<Integer> distanceBlocks =
            visibleWhen(num("noFogDistanceBlocks", SETTING_DISTANCE_BLOCKS, 1024, 32, 8192),
                    () -> fogControlEnabled.get() && atmosphericToggles.get("Distance fog"));
    private final NumberValue<Integer> weatherDistanceBlocks =
            visibleWhen(num("noFogWeatherDistanceBlocks", SETTING_WEATHER_DISTANCE_BLOCKS, 512, 32, 8192),
                    () -> fogControlEnabled.get() && atmosphericToggles.get("Weather fog"));
    private final BooleanValue weatherControlEnabled =
            bool("worldTweaksWeatherControl", SETTING_WEATHER_CONTROL, false);
    private final ModeValue weatherMode =
            visibleWhen(modeSetting("weatherMode", SETTING_WEATHER_MODE, "CLEAR", "CLEAR", "RAIN", "THUNDER"),
                    weatherControlEnabled::get);
    private final BooleanMapValue skyToggles = group(
            "worldTweaksSky",
            SETTING_SKY,
            new LinkedHashMap<>() {{
                put("Sky color", false);
                put("Sky gradient", false);
            }}
    );
    private final RGBColorValue skyColor =
            visibleWhen(colorNoAlpha("worldTweaksSkyColor", SETTING_SKY_COLOR, "#78A7FF"),
                    () -> skyToggles.get("Sky color"));
    private final RGBColorValue skyGradientColor =
            visibleWhen(colorNoAlpha("worldTweaksSkyGradientColor", SETTING_SKY_GRADIENT_COLOR, "#FF8A5B"),
                    () -> skyToggles.get("Sky gradient"));
    private final BooleanValue changeTime =
            bool("worldTweaksChangeTime", SETTING_CHANGE_TIME, false);
    private final BooleanValue syncSystemTime =
            visibleWhen(bool("timeControlSyncSystemTime", SETTING_SYNC_SYSTEM_TIME, false), changeTime::get);
    private final NumberValue<Integer> timeHours =
            visibleWhen(num("worldTweaksTimeHours", SETTING_TIME_HOURS, 21, 0, 23),
                    () -> changeTime.get() && !syncSystemTime.get());
    private final BooleanValue itemPhysicsEnabled =
            bool("worldTweaksItemPhysics", SETTING_ITEM_PHYSICS, true);
    private boolean wasOverridingTime;

    private static int getHourTicks(int hour) {
        return Math.floorMod(hour - 6, 24) * 1000;
    }

    public static float getWindTimeSeconds() {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.level != null) {
            long time = client.level.getGameTime() % 24000L;
            return (time + RenderState.tickDelta) / 20.0f;
        }
        long nowMs = System.currentTimeMillis();
        return (nowMs % 100000L) / 1000.0f;
    }

    public static long getServerTimeOfDay() {
        return serverTimeOfDay;
    }

    public static void setServerTimeOfDay(long value) {
        serverTimeOfDay = value;
    }

    public static boolean isServerRaining(Level world) {
        return canHaveWeather(world) && getRawRainGradient(world, 1.0f) > RAIN_THRESHOLD;
    }

    public static boolean isServerThundering(Level world) {
        return canHaveWeather(world) && getRawThunderGradient(world, 1.0f) > THUNDER_THRESHOLD;
    }

    public static boolean hasServerRainAt(Level world, BlockPos pos) {
        if (world == null || pos == null) return false;
        if (!isServerRaining(world)) return false;
        if (!world.canSeeSky(pos)) return false;
        if (world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos).getY() > pos.getY()) return false;
        Biome biome = world.getBiome(pos).value();
        return biome.getPrecipitationAt(pos, world.getSeaLevel()) == Biome.Precipitation.RAIN;
    }

    private static boolean canHaveWeather(Level world) {
        return world != null && world.dimensionType().hasSkyLight() && !world.dimensionType().hasCeiling();
    }

    private static float getRawRainGradient(Level world, float tickDelta) {
        if (!(world instanceof WorldAccessor accessor)) return 0.0f;
        return Mth.lerp(
                tickDelta,
                accessor.combatant$getLastRainGradientRaw(),
                accessor.combatant$getRainGradientRaw()
        );
    }

    private static float getRawThunderGradient(Level world, float tickDelta) {
        if (!(world instanceof WorldAccessor accessor)) return 0.0f;
        float thunder = Mth.lerp(
                tickDelta,
                accessor.combatant$getLastThunderGradientRaw(),
                accessor.combatant$getThunderGradientRaw()
        );
        return thunder * getRawRainGradient(world, tickDelta);
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.BEFORE_TRANSLUCENT;
    }

    @EventHandler(priority = 1000)
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.getPacket() instanceof ClientboundSetTimePacket packet)) return;
        setServerTimeOfDay(packet.gameTime());
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        boolean overriding = shouldOverrideTime();
        if (overriding || wasOverridingTime) {
            invalidateEnvironmentAttributes();
        }
        wasOverridingTime = overriding;
    }

    @Override
    public void onDisable() {
        wasOverridingTime = false;
        invalidateEnvironmentAttributes();
    }

    public boolean shouldOverrideTime() {
        return isEnabled() && changeTime.get();
    }

    public boolean isItemPhysicsEnabled() {
        return isEnabled() && itemPhysicsEnabled.get();
    }

    public int getTimeOfDayTicks() {
        if (syncSystemTime.get()) {
            return getSystemTimeTicks();
        }
        return getHourTicks(Mth.clamp(timeHours.get(), 0, 23));
    }

    public long getRenderClockTicks() {
        long dayBase = Math.floorDiv(serverTimeOfDay, DAY_TICKS) * DAY_TICKS;
        return dayBase + getTimeOfDayTicks();
    }

    private static void invalidateEnvironmentAttributes() {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.level != null) {
            client.level.environmentAttributes().invalidateTickCache();
        }
    }

    private int getSystemTimeTicks() {
        LocalTime now = LocalTime.now();
        double seconds = now.toNanoOfDay() / 1_000_000_000.0;
        double ticks = (seconds / 86400.0) * 24000.0;
        long mcTicks = Math.round(ticks) + 18000L;
        return (int) (mcTicks % 24000L);
    }

    public boolean isWeatherControlEnabled() {
        return isEnabled() && weatherControlEnabled.get();
    }

    public WeatherMode getWeatherMode() {
        if (!isWeatherControlEnabled()) return WeatherMode.CLEAR;
        return switch (weatherMode.get().toUpperCase()) {
            case "RAIN" -> WeatherMode.RAIN;
            case "THUNDER" -> WeatherMode.THUNDER;
            default -> WeatherMode.CLEAR;
        };
    }

    public void applySkyOverrides(SkyRenderState sky) {
        if (sky == null || !isEnabled()) return;

        if (skyToggles.get("Sky color")) {
            sky.skyColor = ARGB.color(ARGB.alpha(sky.skyColor), skyColor.getArgb());
        }

        if (skyToggles.get("Sky gradient")) {
            sky.sunriseAndSunsetColor = ARGB.color(ARGB.alpha(sky.sunriseAndSunsetColor), skyGradientColor.getArgb());
        }
    }

    public boolean isFogControlEnabled() {
        return isEnabled() && fogControlEnabled.get();
    }

    public boolean isFogModifyEnabled() {
        return isFogControlEnabled() && fogModifyEnabled.get();
    }

    public int getFogStartBlocks() {
        return fogStart.get();
    }

    public int getFogEndBlocks() {
        return fogEnd.get();
    }

    public int getFogColorArgb() {
        return fogColor.getArgb();
    }

    public void applyFogOverride(FogData data) {
        if (data == null) return;
        float start = Math.max(0f, fogStart.get());
        float end = Math.max(start + 1f, fogEnd.get());

        data.environmentalStart = start;
        data.environmentalEnd = end;
        data.renderDistanceStart = start;
        data.renderDistanceEnd = end;
        data.skyEnd = end;
        data.cloudEnd = end;
    }

    public boolean disableWaterFog() {
        return isFogControlEnabled() && liquidToggles.get("Water");
    }

    public boolean disableLavaFog() {
        return isFogControlEnabled() && liquidToggles.get("Lava");
    }

    public boolean disablePowderSnowFog() {
        return isFogControlEnabled() && liquidToggles.get("Powder snow");
    }

    public boolean disableOverworldFog() {
        return isFogControlEnabled() && atmosphericToggles.get("Overworld");
    }

    public boolean disableNetherFog() {
        return isFogControlEnabled() && atmosphericToggles.get("Nether");
    }

    public boolean disableEndFog() {
        return isFogControlEnabled() && atmosphericToggles.get("End");
    }

    public boolean disableDistanceFog() {
        return isFogControlEnabled() && atmosphericToggles.get("Distance fog");
    }

    public boolean disableWeatherFog() {
        return isFogControlEnabled() && atmosphericToggles.get("Weather fog");
    }

    public int getDistanceBlocks() {
        return distanceBlocks.get();
    }

    public int getWeatherDistanceBlocks() {
        return weatherDistanceBlocks.get();
    }

    public enum WeatherMode {
        CLEAR,
        RAIN,
        THUNDER
    }

}
