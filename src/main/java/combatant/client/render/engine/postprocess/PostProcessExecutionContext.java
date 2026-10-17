/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.postprocess;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.RhiStorageImage;
import org.jetbrains.annotations.Nullable;

/** Per-pass execution view exposing optional storage-capable graph endpoints to modern effects. */
public record PostProcessExecutionContext(
        PostProcessContext context,
        CombatantRhi rhi,
        GpuTextureView source,
        GpuTextureView destination,
        @Nullable RhiStorageImage sourceStorage,
        @Nullable RhiStorageImage destinationStorage
) {
}
