/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;

//todo Description
@ModuleInfo(
        id = "cameraclip",
        displayName = "CameraClip",
        category = ModuleCategory.VISUALS
)
public final class CameraClip extends Module {

    private final NumberValue<Double> distanceMultiplier =
            num(
                    "cameraClipDistanceMultiplier",
                    "distance_multiplier",
                    1.0,
                    0.1,
                    5.0
            );

    private final NumberValue<Double> backOffset =
            num(
                    "cameraClipBackOffset",
                    "back_offset",
                    0.0,
                    -10.0,
                    10.0
            );

    private final NumberValue<Double> rightOffset =
            num(
                    "cameraClipRightOffset",
                    "right_offset",
                    0.0,
                    -10.0,
                    10.0
            );

    private final NumberValue<Double> upOffset =
            num(
                    "cameraClipUpOffset",
                    "up_offset",
                    0.0,
                    -10.0,
                    10.0
            );

    public double getDistanceMultiplier() {
        return isEnabled() ? distanceMultiplier.get() : 1.0;
    }

    public double getBackOffset() {
        return isEnabled() ? backOffset.get() : 0.0;
    }

    public double getRightOffset() {
        return isEnabled() ? rightOffset.get() : 0.0;
    }

    public double getUpOffset() {
        return isEnabled() ? upOffset.get() : 0.0;
    }

    public float distanceMultiplier() {
        return (float) getDistanceMultiplier();
    }

    public float backOffset() {
        return (float) getBackOffset();
    }

    public float rightOffset() {
        return (float) getRightOffset();
    }

    public float upOffset() {
        return (float) getUpOffset();
    }
}
