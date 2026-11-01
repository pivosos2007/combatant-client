/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.model.gpu;

/**
 * Semantic decode/upload policy for an imported model image.
 *
 * <p>One model texture may legally be referenced by both color and data semantics. Residency is
 * therefore keyed by texture index + usage instead of pretending that an image has one universal
 * color space. The physical format remains backend-neutral RGBA8; this usage controls mip
 * generation and tells compatibility/shaderpack material code how sampled values are interpreted.</p>
 */
public enum ModelGpuTextureUsage {
    /** baseColor/emissive: encoded sRGB color, alpha remains linear coverage. */
    SRGB_COLOR,
    /** occlusion/roughness/metallic and other scalar/data maps. */
    LINEAR_DATA,
    /** tangent-space normal data; mip generation renormalizes the averaged vector. */
    NORMAL_DATA
}
