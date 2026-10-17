/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.sodium;

import net.minecraft.resources.Identifier;
import combatant.client.util.logging.DebugLog;

/**
 * Sodium shader override/workaround registry.
 * <p>
 * Sodium 0.9.2 for Minecraft 26.3 compiles terrain through RenderPearl.
 * Redirect only the shader identifier, retaining Sodium/ShaderManager define handling,
 * #include resolution, OIT permutations and the MDI vertex contract.
 */
public final class SodiumShaderWorkarounds {
    private static final Identifier SODIUM_BLOCK_LAYER_OPAQUE = Identifier.fromNamespaceAndPath("sodium", "blocks/block_layer_opaque");
    private static final Identifier COMBATANT_BLOCK_LAYER_OPAQUE = Identifier.fromNamespaceAndPath("combatant", "sodium/block_layer_opaque");
    private long overrideHits;

    public Identifier overrideShaderIdentifier(Identifier id) {
        if (id == null) return null;
        if (SODIUM_BLOCK_LAYER_OPAQUE.equals(id)) {
            overrideHits++;
            DebugLog.infoOnChange("sodium.shader.override.block_layer_opaque", "combatant",
                    "[SODIUM] Combatant Sodium RenderPipeline shader override applied for %s", id);
            return COMBATANT_BLOCK_LAYER_OPAQUE;
        }
        return id;
    }

}
