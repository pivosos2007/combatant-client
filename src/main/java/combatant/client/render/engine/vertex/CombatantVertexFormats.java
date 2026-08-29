/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on, adapted from, or implemented
 * with reference to Meteor Client
 * (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 *
 * Licensed under the GNU General Public License v3.0.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.render.engine.vertex;

import com.mojang.blaze3d.vertex.VertexFormat;

public enum CombatantVertexFormats {
    ;
    public static final VertexFormat POS2 = VertexFormat.builder(0)
            .addAttribute("Position", CombatantVertexFormatElements.POS2)
            .build();

    public static final VertexFormat POS3_TEXTURE_COLOR_PARAMS2 = VertexFormat.builder(0)
            .addAttribute("Position", CombatantVertexFormatElements.POS3)
            .addAttribute("UV0", CombatantVertexFormatElements.TEXTURE)
            .addAttribute("Color", CombatantVertexFormatElements.COLOR)
            .addAttribute("Params", CombatantVertexFormatElements.PARAMS)
            .addAttribute("Params2", CombatantVertexFormatElements.PARAMS2)
            .build();

    public static final VertexFormat POS2_COLOR = VertexFormat.builder(0)
            .addAttribute("Position", CombatantVertexFormatElements.POS2)
            .addAttribute("Color", CombatantVertexFormatElements.COLOR)
            .build();

    /**
     * World-space wide line vertex.
     * Position is the current endpoint, Color is endpoint color, Line packs the opposite endpoint in xyz
     * and signed screen-space line width in w. The vertex shader expands the endpoint in clip space.
     */
    public static final VertexFormat POS3_COLOR_LINE = VertexFormat.builder(0)
            .addAttribute("Position", CombatantVertexFormatElements.POS3)
            .addAttribute("Color", CombatantVertexFormatElements.COLOR)
            .addAttribute("Line", CombatantVertexFormatElements.PARAMS)
            .build();

    public static final VertexFormat POS2_TEXTURE_COLOR = VertexFormat.builder(0)
            .addAttribute("Position", CombatantVertexFormatElements.POS2)
            .addAttribute("UV0", CombatantVertexFormatElements.TEXTURE)
            .addAttribute("Color", CombatantVertexFormatElements.COLOR)
            .build();

    public static final VertexFormat POS2_COLOR_RECT_PARAMS = VertexFormat.builder(0)
            .addAttribute("Position", CombatantVertexFormatElements.POS2)
            .addAttribute("Color", CombatantVertexFormatElements.COLOR)
            .addAttribute("Rect", CombatantVertexFormatElements.RECT)
            .addAttribute("Params", CombatantVertexFormatElements.PARAMS)
            .build();

    public static final VertexFormat POS2_COLOR_RECT_PARAMS2 = VertexFormat.builder(0)
            .addAttribute("Position", CombatantVertexFormatElements.POS2)
            .addAttribute("Color", CombatantVertexFormatElements.COLOR)
            .addAttribute("Rect", CombatantVertexFormatElements.RECT)
            .addAttribute("Params", CombatantVertexFormatElements.PARAMS)
            .addAttribute("Params2", CombatantVertexFormatElements.PARAMS2)
            .build();

    public static final VertexFormat POS2_LOCAL_COLOR_RECT_PARAMS = VertexFormat.builder(0)
            .addAttribute("Position", CombatantVertexFormatElements.POS2)
            .addAttribute("Local", CombatantVertexFormatElements.LOCAL)
            .addAttribute("Color", CombatantVertexFormatElements.COLOR)
            .addAttribute("Rect", CombatantVertexFormatElements.RECT)
            .addAttribute("Params", CombatantVertexFormatElements.PARAMS)
            .build();

    public static final VertexFormat POS2_LOCAL_COLOR_RECT_PARAMS2 = VertexFormat.builder(0)
            .addAttribute("Position", CombatantVertexFormatElements.POS2)
            .addAttribute("Local", CombatantVertexFormatElements.LOCAL)
            .addAttribute("Color", CombatantVertexFormatElements.COLOR)
            .addAttribute("Rect", CombatantVertexFormatElements.RECT)
            .addAttribute("Params", CombatantVertexFormatElements.PARAMS)
            .addAttribute("Params2", CombatantVertexFormatElements.PARAMS2)
            .build();

    /** Shared data-driven UI geometry family: shape header plus two shape-specific payloads. */
    public static final VertexFormat POS2_LOCAL_COLOR_RECT_PARAMS3 = VertexFormat.builder(0)
            .addAttribute("Position", CombatantVertexFormatElements.POS2)
            .addAttribute("Local", CombatantVertexFormatElements.LOCAL)
            .addAttribute("Color", CombatantVertexFormatElements.COLOR)
            .addAttribute("Rect", CombatantVertexFormatElements.RECT)
            .addAttribute("Params", CombatantVertexFormatElements.PARAMS)
            .addAttribute("Params2", CombatantVertexFormatElements.PARAMS2)
            .addAttribute("Params3", CombatantVertexFormatElements.PARAMS3)
            .build();

    public static final VertexFormat POS2_TEXTURE_COLOR_RECT_PARAMS = VertexFormat.builder(0)
            .addAttribute("Position", CombatantVertexFormatElements.POS2)
            .addAttribute("UV0", CombatantVertexFormatElements.TEXTURE)
            .addAttribute("Color", CombatantVertexFormatElements.COLOR)
            .addAttribute("Rect", CombatantVertexFormatElements.RECT)
            .addAttribute("Params", CombatantVertexFormatElements.PARAMS)
            .build();

    public static final VertexFormat POS2_TEXTURE_LOCAL_COLOR_RECT_PARAMS = VertexFormat.builder(0)
            .addAttribute("Position", CombatantVertexFormatElements.POS2)
            .addAttribute("UV0", CombatantVertexFormatElements.TEXTURE)
            .addAttribute("Local", CombatantVertexFormatElements.LOCAL)
            .addAttribute("Color", CombatantVertexFormatElements.COLOR)
            .addAttribute("Rect", CombatantVertexFormatElements.RECT)
            .addAttribute("Params", CombatantVertexFormatElements.PARAMS)
            .build();

    public static final VertexFormat POS2_TEXTURE_LOCAL_COLOR_RECT_PARAMS2 = VertexFormat.builder(0)
            .addAttribute("Position", CombatantVertexFormatElements.POS2)
            .addAttribute("UV0", CombatantVertexFormatElements.TEXTURE)
            .addAttribute("Local", CombatantVertexFormatElements.LOCAL)
            .addAttribute("Color", CombatantVertexFormatElements.COLOR)
            .addAttribute("Rect", CombatantVertexFormatElements.RECT)
            .addAttribute("Params", CombatantVertexFormatElements.PARAMS)
            .addAttribute("Params2", CombatantVertexFormatElements.PARAMS2)
            .build();

    /** Liquid-glass optical payload plus an optional eight-point primitive mask. */
    public static final VertexFormat POS2_TEXTURE_LOCAL_COLOR_RECT_PARAMS6 = VertexFormat.builder(0)
            .addAttribute("Position", CombatantVertexFormatElements.POS2)
            .addAttribute("UV0", CombatantVertexFormatElements.TEXTURE)
            .addAttribute("Local", CombatantVertexFormatElements.LOCAL)
            .addAttribute("Color", CombatantVertexFormatElements.COLOR)
            .addAttribute("Rect", CombatantVertexFormatElements.RECT)
            .addAttribute("Params", CombatantVertexFormatElements.PARAMS)
            .addAttribute("Params2", CombatantVertexFormatElements.PARAMS2)
            .addAttribute("Params3", CombatantVertexFormatElements.PARAMS3)
            .addAttribute("Params4", CombatantVertexFormatElements.PARAMS4)
            .addAttribute("Params5", CombatantVertexFormatElements.PARAMS5)
            .addAttribute("Params6", CombatantVertexFormatElements.PARAMS6)
            .build();

    public static final VertexFormat POS2_TEXTURE_COLOR_RECT_PARAMS2 = VertexFormat.builder(0)
            .addAttribute("Position", CombatantVertexFormatElements.POS2)
            .addAttribute("UV0", CombatantVertexFormatElements.TEXTURE)
            .addAttribute("Color", CombatantVertexFormatElements.COLOR)
            .addAttribute("Rect", CombatantVertexFormatElements.RECT)
            .addAttribute("Params", CombatantVertexFormatElements.PARAMS)
            .addAttribute("Params2", CombatantVertexFormatElements.PARAMS2)
            .build();

    public static final VertexFormat POS2_LOCAL_COLOR_RECT_PARAMS5 = VertexFormat.builder(0)
            .addAttribute("Position", CombatantVertexFormatElements.POS2)
            .addAttribute("Local", CombatantVertexFormatElements.LOCAL)
            .addAttribute("Color", CombatantVertexFormatElements.COLOR)
            .addAttribute("Rect", CombatantVertexFormatElements.RECT)
            .addAttribute("Params", CombatantVertexFormatElements.PARAMS)
            .addAttribute("Params2", CombatantVertexFormatElements.PARAMS2)
            .addAttribute("Params3", CombatantVertexFormatElements.PARAMS3)
            .addAttribute("Params4", CombatantVertexFormatElements.PARAMS4)
            .addAttribute("Params5", CombatantVertexFormatElements.PARAMS5)
            .build();

    /**
     * Rigged textured 3D vertex. Attribute order is mirrored by rig_textured.vert.
     * 12 + 8 + 12 + 4 + 4 + 4 + 16 + 4 = 64 bytes.
     */
    public static final VertexFormat RIG_POSITION_TEXTURE_NORMAL_COLOR_BONES_DEFORM = VertexFormat.builder(0)
            .addAttribute("Position", CombatantVertexFormatElements.POS3)
            .addAttribute("UV0", CombatantVertexFormatElements.TEXTURE)
            .addAttribute("Normal", CombatantVertexFormatElements.NORMAL)
            .addAttribute("Color", CombatantVertexFormatElements.COLOR)
            .addAttribute("BoneIndices", CombatantVertexFormatElements.BONE_INDICES)
            .addAttribute("BoneWeights", CombatantVertexFormatElements.BONE_WEIGHTS)
            .addAttribute("DeformCoord", CombatantVertexFormatElements.DEFORM_COORD)
            .addAttribute("DeformMeta", CombatantVertexFormatElements.DEFORM_META)
            .build();

}
