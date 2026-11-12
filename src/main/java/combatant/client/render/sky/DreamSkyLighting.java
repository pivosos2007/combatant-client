/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.sky;

import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.util.Mth;

/**
 * The sun angle is independent of precipitation; Minecraft's star brightness is
 * attenuated by bad weather and therefore must not be used as the night clock.
 * The smooth horizon threshold follows the same one-day celestial rotation.
 */
public enum DreamSkyLighting {
    ;

    public static float nightFactor(SkyRenderState sky) {
        if (sky == null) return 0f;
        float sunHeight = Mth.cos(sky.sunAngle);
        return Mth.clamp((0.16f - sunHeight) * 2.4f, 0f, 1f);
    }
}
