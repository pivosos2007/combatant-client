/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import combatant.client.mixins.accessors.RenderLayerInvokerAccessor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public enum CombatantEntityRenderTypes {
    ;

    private static final RenderPipeline ENTITY_TRANSLUCENT_NO_DEPTH_WRITE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/entity_translucent_no_depth_write"))
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withBindGroupLayout(BindGroupLayouts.LIGHTING)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1_SAMPLER2)
            .withVertexShader("core/entity")
            .withFragmentShader("core/entity")
            .withVertexBinding(0, DefaultVertexFormat.ENTITY)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withShaderDefine("ALPHA_CUTOUT", 0.1f)
            .withShaderDefine("PER_FACE_LIGHTING")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .build();

    private static final RenderPipeline ENTITY_TRANSLUCENT_NO_DEPTH_WRITE_NO_CULL = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/entity_translucent_no_depth_write_no_cull"))
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withBindGroupLayout(BindGroupLayouts.LIGHTING)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1_SAMPLER2)
            .withVertexShader("core/entity")
            .withFragmentShader("core/entity")
            .withVertexBinding(0, DefaultVertexFormat.ENTITY)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withShaderDefine("ALPHA_CUTOUT", 0.1f)
            .withShaderDefine("PER_FACE_LIGHTING")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .build();

    private static final Map<Key, RenderType> ENTITY_TRANSLUCENT_CACHE = new ConcurrentHashMap<>();

    public static RenderType entityTranslucentNoDepthWrite(Identifier texture, boolean cull) {
        return ENTITY_TRANSLUCENT_CACHE.computeIfAbsent(new Key(texture, cull), CombatantEntityRenderTypes::createEntityTranslucent);
    }

    public static List<RenderPipeline> irisMappedPipelines() {
        return List.of(ENTITY_TRANSLUCENT_NO_DEPTH_WRITE, ENTITY_TRANSLUCENT_NO_DEPTH_WRITE_NO_CULL);
    }

    private static RenderType createEntityTranslucent(Key key) {
        RenderPipeline pipeline = key.cull
                ? ENTITY_TRANSLUCENT_NO_DEPTH_WRITE
                : ENTITY_TRANSLUCENT_NO_DEPTH_WRITE_NO_CULL;

        RenderSetup setup = RenderSetup.builder(pipeline)
                .withTexture("Sampler0", key.texture)
                .useLightmap()
                .useOverlay()
                .affectsCrumbling()
                .sortOnUpload()
                .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                .createRenderSetup();
        return RenderLayerInvokerAccessor.callOf(
                key.cull ? "combatant_entity_translucent_no_depth_write" : "combatant_entity_translucent_no_depth_write_no_cull",
                setup
        );
    }

    private record Key(Identifier texture, boolean cull) {
    }
}
