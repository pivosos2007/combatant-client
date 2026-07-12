/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.guard;

/**
 * Old rendering paths that are no longer allowed as production behavior after the RHI migration.
 */
public enum LegacyRenderPath {
    IMMEDIATE_MESH_UPLOAD("MeshBuilder immediate GPU upload"),
    LEGACY_FULLSCREEN_MESH("FullScreenRenderer.mesh compatibility path"),
    LEGACY_MESH_RENDERER("MeshRenderer legacy facade"),
    DIRECT_GL_OUTSIDE_BACKEND("direct GL call outside SodiumGlBackend boundary"),
    DIRECT_SODIUM_OUTSIDE_BRIDGE("direct Sodium call outside SodiumRenderBridge/backend boundary"),
    PIPELINE_IDENTITY_POLICY("pipeline policy inferred from RenderPipeline object identity"),
    LOCAL_FRAMEBUFFER_POOL("subsystem-local framebuffer/texture pool");

    private final String description;

    LegacyRenderPath(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
