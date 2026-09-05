/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.shader;

/** Combatant-only texture usage bits layered on top of Mojang GpuTexture usage. */
public final class RhiTextureUsage {
    /**
     * Requests native shader image load/store support while retaining Mojang texture ownership.
     * Chosen outside Mojang's current low usage-bit range; Vulkan lowering is patched explicitly.
     */
    public static final int STORAGE_IMAGE = 1 << 20;

    private RhiTextureUsage() {}
}
