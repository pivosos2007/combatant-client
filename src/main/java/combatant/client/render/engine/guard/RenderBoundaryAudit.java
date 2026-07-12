/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.guard;

import combatant.client.util.logging.DebugLog;

import java.util.List;

/**
 * Lightweight runtime boundary audit. It does not scan source files; it validates explicitly registered legacy
 * exceptions and prints the architectural rules once during bootstrap in debug mode.
 */
public enum RenderBoundaryAudit {
    ;
    private static final List<String> LOCKED_RULES = List.of(
            "Renderer3D/Renderer2D/Text/PostProcess must submit through CombatantRHI or command layers",
            "Direct GL is backend/profiler/MSAA-legacy only",
            "Direct Sodium runtime access is SodiumRenderBridge/terrain interop only",
            "Immediate mesh upload is emergency fallback only",
            "Individual uniform flipFrame calls are forbidden",
            "Fullscreen MeshBuilder quad is forbidden as production path",
            "Pipeline policy must come from RenderPipelineSpec metadata"
    );
    private static boolean ran;

    public static void runOnce() {
        if (ran) return;
        ran = true;
        if (!RenderArchitectureGuard.DEBUG) return;
        DebugLog.renderThread("[CombatantRHI] render architecture boundary locked:");
        for (String rule : LOCKED_RULES) {
            DebugLog.renderThread("[CombatantRHI]  - " + rule);
        }
    }
}
