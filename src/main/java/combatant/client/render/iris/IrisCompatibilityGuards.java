/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.iris;

import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.MotionBlur;
import combatant.client.runtime.RuntimeGate;

public enum IrisCompatibilityGuards {
    ;

    public static boolean suppressShaderpackMotionBlur() {
        if (!RuntimeGate.canRunShaderBridge()) return false;
        MotionBlur module = Modules.get(MotionBlur.class);
        return IrisRuntime.isShaderpackRendererActive()
                && IrisRuntime.supports(IrisCompatibilityFeature.MOTION_BLUR_POLICY)
                && module != null
                && module.isActive();
    }

    public static boolean suppressCombatantTerrainShaderOverrides() {
        if (!RuntimeGate.canRunShaderBridge()) return false;
        return IrisRuntime.isModLoaded();
    }

    public static boolean suppressIrisHandRendering() {
        // Do not cancel Iris' own hand renderer. Iris redirects vanilla hand submit to a no-op
        // while a shaderpack is active, so suppressing HandRenderer removes the visible hand entirely.
        // Chams builds its mask as an extra pass and must not own the visible hand path.
        return false;
    }

    public static boolean deferIrisFinalizationForSecondHandScene() {
        if (!RuntimeGate.canRunShaderBridge()) return false;
        return IrisRuntime.isShaderpackRendererActive();
    }
}
