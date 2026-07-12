/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.events.impl;

import lombok.Getter;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import combatant.client.events.Event;

public class LightmapModifyEvent extends Event {
    private static final float EPSILON = 1.0e-4f;

    private final LightmapRenderState state;
    private final float originalBrightness;
    @Getter
    private boolean modified;

    public LightmapModifyEvent(LightmapRenderState state) {
        this.state = state;
        this.originalBrightness = state != null ? state.brightness : 0.0f;
    }

    public LightmapRenderState state() {
        return state;
    }

    public float originalBrightness() {
        return originalBrightness;
    }

    public void raiseMinimumLight(float minLight) {
        if (state == null) return;

        float strength = Mth.clamp(minLight, 0.0f, 1.0f);
        if (strength <= EPSILON) return;

        state.needsUpdate = true;

        // Do not emulate FullBright by forcing brightness/night vision to 1.0.
        // In the 1.21+ UBO lightmap path that behaves like global exposure and blows out the scene.
        float exposureFloor = 0.08f + 0.18f * strength;
        state.brightness = Math.max(state.brightness, exposureFloor);
        state.nightVisionEffectIntensity = Math.max(state.nightVisionEffectIntensity, 0.10f * strength);

        // Lift the generated lightmap itself, but keep vanilla color response instead of whitening it.
        state.skyFactor = Math.max(state.skyFactor, 0.55f + 0.35f * strength);
        state.blockFactor = Math.max(state.blockFactor, 1.4f + 0.25f * strength);

        // FullBright must not be attenuated by darkness/boss-world darkening.
        float attenuation = 1.0f - strength;
        state.darknessEffectScale *= attenuation;
        state.bossOverlayWorldDarkening *= attenuation;

        float colorFloor = 0.10f + 0.28f * strength;
        state.blockLightTint = raiseColorFloor(state.blockLightTint, colorFloor);
        state.skyLightColor = raiseColorFloor(state.skyLightColor, colorFloor);
        state.ambientColor = raiseColorFloor(state.ambientColor, colorFloor);
        state.nightVisionColor = raiseColorFloor(state.nightVisionColor, colorFloor);

        modified = true;
    }

    private static Vector3fc raiseColorFloor(Vector3fc color, float floor) {
        float min = Mth.clamp(floor, 0.0f, 1.0f);
        if (color == null) {
            return new Vector3f(min, min, min);
        }
        return new Vector3f(
                Math.max(color.x(), min),
                Math.max(color.y(), min),
                Math.max(color.z(), min)
        );
    }
}
