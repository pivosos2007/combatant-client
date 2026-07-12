/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.Modules;

//todo Description
@ModuleInfo(
        id = "aspectratio",
        displayName = "AspectRatio",
        category = ModuleCategory.VISUALS
)
public final class AspectRatio extends Module {
    private static final float EPSILON = 1.0e-4f;

    private final NumberValue<Float> aspect =
            num(
                    "aspectMultiplier",
                    "aspect_multiplier",
                    1.0f,
                    0.2f,
                    3.0f
            );

    public float getMultiplier() {
        if (!isEnabled()) return 1.0f;
        return Mth.clamp(aspect.get(), 0.2f, 3.0f);
    }

    public static float currentMultiplier() {
        AspectRatio module = Modules.get(AspectRatio.class);
        if (module == null || !module.isEnabled()) return 1.0f;
        return module.getMultiplier();
    }

    public static boolean isProjectionModified() {
        return Math.abs(currentMultiplier() - 1.0f) > EPSILON;
    }

    public static void applyToProjection(Matrix4f projection) {
        applyToProjection(projection, currentMultiplier());
    }

    public static void applyToProjection(Matrix4f projection, float multiplier) {
        if (projection == null) return;
        float mul = Mth.clamp(multiplier, 0.2f, 3.0f);
        if (Math.abs(mul - 1.0f) <= EPSILON) return;

        projection.m00(projection.m00() * mul);
    }
}
