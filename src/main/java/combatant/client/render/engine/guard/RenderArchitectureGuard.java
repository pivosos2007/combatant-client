/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.guard;

import combatant.client.render.engine.core.CombatantRenderSystem;

/** Gates legacy render paths behind explicit system properties and records their use in RHI stats. */
public enum RenderArchitectureGuard {
    ;
    public static final boolean DEBUG = Boolean.getBoolean("combatant.render.debug");
    public static final boolean STRICT = !Boolean.getBoolean("combatant.render.allowLegacyPaths");
    public static final boolean ALLOW_LEGACY_MESH_RENDERER = Boolean.getBoolean("combatant.render.allowLegacyMeshRenderer");
    public static final boolean ALLOW_LEGACY_FULLSCREEN_MESH = Boolean.getBoolean("combatant.render.allowLegacyFullscreenMesh");
    public static final boolean ALLOW_IMMEDIATE_UPLOAD = Boolean.getBoolean("combatant.rhi.allowImmediateFallback")
            || Boolean.getBoolean("combatant.render.allowImmediateUpload");

    public static void requireAllowed(LegacyRenderPath path, String owner) {
        boolean allowed = switch (path) {
            case IMMEDIATE_MESH_UPLOAD -> ALLOW_IMMEDIATE_UPLOAD;
            case LEGACY_FULLSCREEN_MESH -> ALLOW_LEGACY_FULLSCREEN_MESH;
            case LEGACY_MESH_RENDERER -> ALLOW_LEGACY_MESH_RENDERER;
            default -> !STRICT;
        };

        record(path);
        if (allowed) return;

        throw new IllegalStateException("Legacy render path is locked: " + path.description()
                + " owner=" + owner
                + ". Use CombatantRHI/SodiumGlBackend path instead. "
                + "Temporary override: -D" + overrideProperty(path) + "=true");
    }

    public static void record(LegacyRenderPath path) {
        try {
            CombatantRenderSystem.rhi().stats().legacyPath(path);
        } catch (Throwable ignored) {
            // Guard must be safe during bootstrap/static initialization.
        }
    }

    private static String overrideProperty(LegacyRenderPath path) {
        return switch (path) {
            case IMMEDIATE_MESH_UPLOAD -> "combatant.rhi.allowImmediateFallback";
            case LEGACY_FULLSCREEN_MESH -> "combatant.render.allowLegacyFullscreenMesh";
            case LEGACY_MESH_RENDERER -> "combatant.render.allowLegacyMeshRenderer";
            default -> "combatant.render.allowLegacyPaths";
        };
    }
}
