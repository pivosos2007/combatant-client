/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals.finder;

import combatant.client.config.values.EnumValue;
import combatant.client.features.module.Module;

/** Shared render mode for the ordinary, batched 3D scanner markers. */
public abstract class ShaderBoxModule extends Module {
    public enum RenderMode { BOX, OUTLINE, FILL }

    protected final EnumValue<RenderMode> renderMode;

    protected ShaderBoxModule(RenderMode defaultMode, RenderMode... modes) {
        renderMode = enumMode("render_mode", defaultMode, modes);
    }
}
