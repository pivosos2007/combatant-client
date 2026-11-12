/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.hdr;

import com.mojang.renderpearl.api.GpuFormat;
import combatant.client.render.engine.framegraph.FrameGraphResourceKey;

/** Scene-referred linear RGB with sRGB/Rec.709 primaries; alpha is linear coverage. */
public final class SceneColor {
    public static final GpuFormat FORMAT = GpuFormat.RGBA16_FLOAT;
    public static final FrameGraphResourceKey KEY = FrameGraphResourceKey.external("world.scene_color");

    private SceneColor() { }

    public static float srgbToLinear(float value) {
        return value <= 0.04045f ? value / 12.92f
                : (float) Math.pow((value + 0.055f) / 1.055f, 2.4);
    }

    public static float linearToSrgb(float value) {
        return value <= 0.0031308f ? value * 12.92f
                : 1.055f * (float) Math.pow(value, 1.0 / 2.4) - 0.055f;
    }
}
