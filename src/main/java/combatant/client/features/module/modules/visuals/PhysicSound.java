/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import combatant.client.config.values.BooleanValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.util.sound.SoundSystem;

@ModuleInfo(id = "physicsound", displayName = "PhysicSound", category = ModuleCategory.VISUALS,
        subcategory = ModuleSubcategory.COSMETIC, description = "module.physicsound.description")
public final class PhysicSound extends Module {
    private final BooleanValue underwater = bool("physicSoundUnderwater", "underwater", true);
    private final BooleanValue caveEcho = bool("physicSoundCaveEcho", "cave_echo", true);
    private final BooleanValue occlusion = bool("physicSoundOcclusion", "occlusion", true);

    public boolean underwaterEnabled() {
        return underwater.get();
    }

    public boolean caveEchoEnabled() {
        return caveEcho.get();
    }

    public boolean occlusionEnabled() {
        return occlusion.get();
    }

    @Override
    public void onDisable() {
        SoundSystem.get().disableAcoustics();
    }
}
