/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;

import java.util.LinkedHashMap;

//todo Description
@ModuleInfo(
        id = "fovcontrol",
        displayName = "FovControl",
        category = ModuleCategory.VISUALS
)
public class FovControl extends Module {
    private static final String SETTING_USE_CUSTOM_FOV = "use_custom_fov";
    private static final String SETTING_CUSTOM_FOV = "custom_fov";
    private static final String SETTING_EXPAND_SCALE = "expand_scale";
    private static final String SETTING_SHRINK_SCALE = "shrink_scale";
    private static final String SETTING_DISABLE_FACTORS = "disable_factors";

    private final BooleanValue useCustomFov =
            bool("fovControlUseCustom", SETTING_USE_CUSTOM_FOV, false);
    private final NumberValue<Integer> customFov =
            visibleWhen(num("fovControlCustomFov", SETTING_CUSTOM_FOV, 70, 10, 350), useCustomFov::get);

    private final NumberValue<Float> expandScale =
            num("fovControlExpandScale", SETTING_EXPAND_SCALE, 1.0f, 0.0f, 2.0f);
    private final NumberValue<Float> shrinkScale =
            num("fovControlShrinkScale", SETTING_SHRINK_SCALE, 1.0f, 0.0f, 2.0f);

    private final BooleanMapValue toggles = group(
            "fovControlToggles",
            SETTING_DISABLE_FACTORS,
            new LinkedHashMap<>() {{
                put("Sprint", false);
                put("Speed effects", false);
                put("Slowness effects", false);
                put("Flying", false);
                put("Bow", false);
                put("Spyglass", false);
                put("Fluid", false);
            }}
    );

    public boolean useCustomFov() {
        return isEnabled() && useCustomFov.get();
    }

    public int getCustomFov() {
        return customFov.get();
    }

    public float getExpandScale() {
        return expandScale.get();
    }

    public float getShrinkScale() {
        return shrinkScale.get();
    }

    public boolean disableSprintFov() {
        return isEnabled() && toggles.get("Sprint");
    }

    public boolean disableSpeedFov() {
        return isEnabled() && toggles.get("Speed effects");
    }

    public boolean disableSlownessFov() {
        return isEnabled() && toggles.get("Slowness effects");
    }

    public boolean disableFlyingFov() {
        return isEnabled() && toggles.get("Flying");
    }

    public boolean disableBowFov() {
        return isEnabled() && toggles.get("Bow");
    }

    public boolean disableSpyglassFov() {
        return isEnabled() && toggles.get("Spyglass");
    }

    public boolean disableFluidFov() {
        return isEnabled() && toggles.get("Fluid");
    }
}
