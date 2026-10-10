/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.storage.SectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Sodium 0.9.2+mc26.2 bridge to the section storage owned by RenderSectionManager. */
@Pseudo
@Mixin(value = RenderSectionManager.class, remap = false)
public interface SodiumRenderSectionManagerAccessor {
    @Accessor("renderSections")
    SectionStorage combatant$getRenderSections();
}
