/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.shader;

/** Static, source-level shader complexity estimate. This is not vendor ISA. */
public record ShaderCostEstimate(
        String shaderId,
        String stage,
        int sourceLines,
        int aluOps,
        int transcendentalOps,
        int textureOps,
        int branchOps,
        int loopOps,
        int discardOps,
        int weightedScore
) {
    public static final ShaderCostEstimate NONE = new ShaderCostEstimate(
            "", "unknown", 0, 0, 0, 0, 0, 0, 0, 0
    );
}
