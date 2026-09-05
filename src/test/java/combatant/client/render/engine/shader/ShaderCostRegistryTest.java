/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.shader;

import com.mojang.blaze3d.shaders.ShaderType;
import combatant.client.render.engine.rhi.RhiStats;
import combatant.client.render.engine.rhi.RhiStatsSnapshot;
import combatant.client.render.engine.rhi.pipeline.RenderPipelineSpec;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderCostRegistryTest {
    @Test
    void estimatesSourceAndWeightsActualPipelineBinds() {
        ShaderCostRegistry.clear();
        Identifier vertexId = Identifier.fromNamespaceAndPath("combatant", "test.vert");
        Identifier fragmentId = Identifier.fromNamespaceAndPath("combatant", "test.frag");
        ShaderCostRegistry.analyze(vertexId, ShaderType.VERTEX,
                "void main(){ float x = 1.0 + 2.0 * 3.0; }");
        ShaderCostRegistry.analyze(fragmentId, ShaderType.FRAGMENT,
                "void main(){ if(true){ vec4 c = texture(tex, uv); c.rgb = pow(c.rgb, vec3(2.0)); } }");

        RenderPipelineSpec spec = RenderPipelineSpec.builder("combatant:test")
                .vertexShader(vertexId.toString())
                .fragmentShader(fragmentId.toString())
                .build();
        Object pipelineA = new Object();
        Object pipelineB = new Object();
        RhiStats stats = new RhiStats();
        stats.setDetailedPipelineStats(true);
        stats.beginFrame(7L);
        stats.pipelineUse(pipelineA, spec, 2, 1, true);
        stats.pipelineUse(pipelineA, spec, 2, 1, false);
        stats.pipelineUse(pipelineB, spec, 1, 0, true);

        RhiStatsSnapshot snapshot = stats.snapshot(true);
        assertEquals(2, snapshot.pipelineBinds());
        assertEquals(1, snapshot.pipelineBindSkips());
        assertEquals(1, snapshot.pipelineSwitches());
        assertEquals(2, snapshot.uniquePipelines());
        assertEquals(5, snapshot.uniformBinds());
        assertEquals(2, snapshot.samplerBinds());
        assertTrue(snapshot.estimatedShaderAluOps() > 0);
        assertEquals(3, snapshot.estimatedShaderTextureOps());
        assertEquals(3, snapshot.estimatedShaderTranscendentalOps());
        assertEquals(3, snapshot.estimatedShaderBranchOps());
        assertEquals(2, snapshot.pipelineBreakdown().size());
        assertEquals(3, snapshot.pipelineBreakdown().stream().mapToLong(entry -> entry.draws()).sum());
        assertEquals(2, snapshot.pipelineBreakdown().stream().mapToLong(entry -> entry.binds()).sum());
    }

    @Test
    void beginFrameResetsBindingAndShaderCounters() {
        RhiStats stats = new RhiStats();
        stats.beginFrame(1L);
        stats.pipelineBind(new Object(), null, 4, 3);
        stats.beginFrame(2L);

        RhiStatsSnapshot snapshot = stats.snapshot();
        assertEquals(0, snapshot.pipelineBinds());
        assertEquals(0, snapshot.pipelineBindSkips());
        assertEquals(0, snapshot.uniquePipelines());
        assertEquals(0, snapshot.uniformBinds());
        assertEquals(0, snapshot.samplerBinds());
        assertEquals(0, snapshot.estimatedShaderAluOps());
    }
}
