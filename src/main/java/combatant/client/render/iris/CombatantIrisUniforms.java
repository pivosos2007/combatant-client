/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.iris;

import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector3f;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.FullBright;
import combatant.client.features.module.modules.visuals.WorldTweaks;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.light.LightmapState;
import combatant.client.util.logging.DebugLog;

public enum CombatantIrisUniforms {
    ;
    private static boolean loggedRegistration;

    public static void add(UniformHolder uniforms) {
        if (!loggedRegistration) {
            loggedRegistration = true;
            DebugLog.renderThread("[IrisPatch] registering Combatant Iris uniforms");
        }
        uniforms
                .uniform1b(UniformUpdateFrequency.PER_FRAME, "combatantFullbrightEnabled", CombatantIrisUniforms::fullbrightEnabled)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "combatantFullbrightMinLight", CombatantIrisUniforms::fullbrightMinLight)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "combatantAmbientLight", LightmapState::getAmbient)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "combatantBaseAmbientLight", LightmapState::getBaseAmbient)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "combatantOverrideAmbientLight", CombatantIrisUniforms::overrideAmbient)
                .uniform1b(UniformUpdateFrequency.PER_FRAME, "combatantWorldTweaksFogControl", CombatantIrisUniforms::fogControlEnabled)
                .uniform1b(UniformUpdateFrequency.PER_FRAME, "combatantWorldTweaksFogModify", CombatantIrisUniforms::fogModifyEnabled)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "combatantWorldTweaksFogStart", CombatantIrisUniforms::fogStart)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "combatantWorldTweaksFogEnd", CombatantIrisUniforms::fogEnd)
                .uniform3f(UniformUpdateFrequency.PER_FRAME, "combatantWorldTweaksFogColor", CombatantIrisUniforms::fogColor)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "combatantWorldTweaksFogDisableMask", CombatantIrisUniforms::fogDisableMask)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "combatantEyeInWater", CombatantIrisUniforms::eyeInWater)
                .uniform1b(UniformUpdateFrequency.PER_FRAME, "combatantWorldTweaksTimeOverride", CombatantIrisUniforms::timeOverride)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "combatantWorldTweaksTimeTicks", CombatantIrisUniforms::timeTicks)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "combatantWorldTweaksWeatherMode", CombatantIrisUniforms::weatherMode)
                .uniform1b(UniformUpdateFrequency.PER_FRAME, "combatantSuppressShaderpackMotionBlur", IrisCompatibilityGuards::suppressShaderpackMotionBlur);
    }

    private static boolean fullbrightEnabled() {
        FullBright fullBright = Modules.get(FullBright.class);
        return fullBright != null && fullBright.isEnabled();
    }

    private static float fullbrightMinLight() {
        FullBright fullBright = Modules.get(FullBright.class);
        return fullBright != null ? fullBright.getMinLight() / 15.0f : 0.0f;
    }

    private static float overrideAmbient() {
        return Math.max(0.0f, LightmapState.getOverrideAmbient());
    }

    private static WorldTweaks worldTweaks() {
        return Modules.get(WorldTweaks.class);
    }

    private static boolean fogControlEnabled() {
        WorldTweaks module = worldTweaks();
        return module != null && module.isFogControlEnabled();
    }

    private static boolean fogModifyEnabled() {
        WorldTweaks module = worldTweaks();
        return module != null && module.isFogModifyEnabled();
    }

    private static float fogStart() {
        WorldTweaks module = worldTweaks();
        return module != null ? module.getFogStartBlocks() : 0.0f;
    }

    private static float fogEnd() {
        WorldTweaks module = worldTweaks();
        return module != null ? module.getFogEndBlocks() : 0.0f;
    }

    private static Vector3f fogColor() {
        WorldTweaks module = worldTweaks();
        int argb = module != null ? module.getFogColorArgb() : 0xFFFFFFFF;
        return new Vector3f(
                ((argb >> 16) & 0xFF) / 255.0f,
                ((argb >> 8) & 0xFF) / 255.0f,
                (argb & 0xFF) / 255.0f
        );
    }

    private static int fogDisableMask() {
        WorldTweaks module = worldTweaks();
        if (module == null || !module.isFogControlEnabled()) {
            return 0;
        }
        int mask = 0;
        if (module.disableWaterFog()) mask |= 1;
        if (module.disableLavaFog()) mask |= 1 << 1;
        if (module.disablePowderSnowFog()) mask |= 1 << 2;
        if (module.disableOverworldFog()) mask |= 1 << 3;
        if (module.disableNetherFog()) mask |= 1 << 4;
        if (module.disableEndFog()) mask |= 1 << 5;
        if (module.disableDistanceFog()) mask |= 1 << 6;
        if (module.disableWeatherFog()) mask |= 1 << 7;
        return mask;
    }

    private static int eyeInWater() {
        FogType submersion = RenderState.cameraSubmersion;
        if (submersion == FogType.WATER) return 1;
        if (submersion == FogType.LAVA) return 2;
        if (submersion == FogType.POWDER_SNOW) return 3;
        return 0;
    }

    private static boolean timeOverride() {
        WorldTweaks module = worldTweaks();
        return module != null && module.shouldOverrideTime();
    }

    private static int timeTicks() {
        WorldTweaks module = worldTweaks();
        if (module != null && module.shouldOverrideTime()) {
            return module.getTimeOfDayTicks();
        }
        Minecraft client = Minecraft.getInstance();
        return client != null && client.level != null ? (int) (client.level.getLevelData().getGameTime() % 24000L) : 0;
    }

    private static int weatherMode() {
        WorldTweaks module = worldTweaks();
        if (module != null && module.isWeatherControlEnabled()) {
            return switch (module.getWeatherMode()) {
                case RAIN -> 1;
                case THUNDER -> 2;
                default -> 0;
            };
        }
        Minecraft client = Minecraft.getInstance();
        Level world = client != null ? client.level : null;
        if (world == null) return 0;
        if (WorldTweaks.isServerThundering(world)) return 2;
        return WorldTweaks.isServerRaining(world) ? 1 : 0;
    }
}
