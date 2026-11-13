/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.hdr;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.frontend.FrontendRenderPipeline;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

final class HdrPipelineVariantsTest {
    @Test
    void sameNameItemCompilationsRetainTheirSourceRegardlessOfCompletionOrder() {
        var plain = RenderPipelines.ITEM_CUTOUT;
        var glint = RenderPipelines.ITEM_CUTOUT_GLINT;
        var special = RenderPipelines.ITEM_CUTOUT_GLINT_SPECIAL;
        assertEquals(plain.getLocation(), glint.getLocation());
        assertEquals(plain.getLocation(), special.getLocation());
        assertNotEquals(plain.getVertexFormatBinding(0), special.getVertexFormatBinding(0));

        var plainCompiled = compiled(plain);
        var glintCompiled = compiled(glint);
        var specialCompiled = compiled(special);
        var plainFuture = new CompletableFuture<CompiledRenderPipeline.Pending>();
        var glintFuture = new CompletableFuture<CompiledRenderPipeline.Pending>();
        var specialFuture = new CompletableFuture<CompiledRenderPipeline.Pending>();
        var trackedPlain = HdrPipelineVariants.trackCompilation(plain, plainFuture);
        var trackedGlint = HdrPipelineVariants.trackCompilation(glint, glintFuture);
        var trackedSpecial = HdrPipelineVariants.trackCompilation(special, specialFuture);

        specialFuture.complete(() -> specialCompiled);
        glintFuture.complete(() -> glintCompiled);
        plainFuture.complete(() -> plainCompiled);
        assertSame(specialCompiled, trackedSpecial.join().finishCompile());
        assertSame(plainCompiled, trackedPlain.join().finishCompile());
        assertSame(glintCompiled, trackedGlint.join().finishCompile());
        assertSame(plain, HdrPipelineVariants.sourceOf(plainCompiled));
        assertSame(glint, HdrPipelineVariants.sourceOf(glintCompiled));
        assertSame(special, HdrPipelineVariants.sourceOf(specialCompiled));

        var recompiled = compiled(plain);
        // Equal record contents must not make a fresh compilation inherit an old association.
        assertEquals(plainCompiled, recompiled);
        assertNull(HdrPipelineVariants.sourceOf(recompiled));
        HdrPipelineVariants.trackCompilation(plain, CompletableFuture.completedFuture(() -> recompiled))
                .join().finishCompile();
        assertSame(plain, HdrPipelineVariants.sourceOf(recompiled));
        assertSame(special, HdrPipelineVariants.sourceOf(specialCompiled));
    }

    @Test
    void itemVariantsPreserveDrawContractsAndHaveDistinctRegistryIds() {
        var ids = new HashSet<>();
        for (var base : List.of(RenderPipelines.ITEM_CUTOUT, RenderPipelines.ITEM_CUTOUT_GLINT,
                RenderPipelines.ITEM_CUTOUT_GLINT_SPECIAL, RenderPipelines.ITEM_TRANSLUCENT,
                RenderPipelines.ITEM_TRANSLUCENT_GLINT, RenderPipelines.ITEM_TRANSLUCENT_GLINT_SPECIAL)) {
            var variant = HdrPipelineVariants.variantFor(base, List.of(SceneColor.FORMAT), true);
            assertNotSame(base, variant);
            assertTrue(ids.add(variant.getLocation()), variant.getLocation().toString());
            assertDrawContract(base, variant);
            assertEquals(SceneColor.FORMAT, variant.getColorTargetStates().getFirst().format());
            var expectedFlags = new HashSet<>(base.getShaderDefines().flags());
            expectedFlags.add(HdrPipelineVariants.LEGACY_COLOR);
            assertEquals(expectedFlags, variant.getShaderDefines().flags());
            assertSame(variant, HdrPipelineVariants.variantFor(base, List.of(SceneColor.FORMAT), true));
            assertSame(variant, HdrPipelineVariants.variantFor(variant, List.of(SceneColor.FORMAT), true));
        }
    }

    @Test
    void oitConvertsOnlyAccumulatedColorAndPreservesCompositePremultiplication() {
        for (var set : List.of(RenderPipelines.OIT_ITEM, RenderPipelines.OIT_ITEM_GLINT,
                RenderPipelines.OIT_ITEM_GLINT_SPECIAL)) {
            for (var data : List.of(set.depthBoundsPipeline(), set.transmittancePipeline())) {
                var formats = data.getColorTargetStates().stream().map(state -> state.format()).toList();
                assertSame(data, HdrPipelineVariants.variantFor(data, formats, false));
            }
            var base = set.accumulatePipeline();
            var variant = HdrPipelineVariants.variantFor(base, List.of(SceneColor.FORMAT), false);
            assertNotSame(base, variant);
            assertDrawContract(base, variant);
            assertTrue(variant.getShaderDefines().flags().contains(HdrPipelineVariants.LEGACY_COLOR));
        }
        var composite = RenderPipelines.OIT_COMPOSITE;
        var variant = HdrPipelineVariants.variantFor(composite, List.of(SceneColor.FORMAT), true);
        assertDrawContract(composite, variant);
        assertEquals(composite.getShaderDefines(), variant.getShaderDefines());
        assertFalse(variant.getShaderDefines().flags().contains(HdrPipelineVariants.LEGACY_COLOR));
    }

    @Test
    void unrelatedTargetsAndSdrDrawsKeepOriginalPipeline() {
        var base = RenderPipelines.ITEM_CUTOUT;
        assertSame(base, HdrPipelineVariants.variantFor(base, List.of(GpuFormat.RGBA8_UNORM), false));
        assertSame(base, HdrPipelineVariants.variantFor(base, List.of(SceneColor.FORMAT), false));
        assertSame(base, HdrPipelineVariants.variantFor(base, List.of(), true));
    }

    @Test
    void worldMaterialsDistinguishPremultipliedLinearManagedAndNumericOutput() {
        var premultiplied = worldPipeline(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA);
        var premultipliedHdr = HdrPipelineVariants.variantFor(premultiplied, List.of(SceneColor.FORMAT), true);
        assertTrue(premultipliedHdr.getShaderDefines().flags().contains(HdrPipelineVariants.PREMULTIPLIED_COLOR));
        assertTrue(premultipliedHdr.getShaderDefines().flags().contains(HdrPipelineVariants.LEGACY_COLOR));
        assertDrawContract(premultiplied, premultipliedHdr);

        var straight = HdrPipelineVariants.variantFor(worldPipeline(BlendFunction.TRANSLUCENT),
                List.of(SceneColor.FORMAT), true);
        assertFalse(straight.getShaderDefines().flags().contains(HdrPipelineVariants.PREMULTIPLIED_COLOR));
        for (String contract : List.of(HdrPipelineVariants.MANAGED_COLOR,
                HdrPipelineVariants.LINEAR_COLOR, HdrPipelineVariants.DATA_OUTPUT)) {
            var source = worldPipeline(BlendFunction.TRANSLUCENT, contract);
            var hdr = HdrPipelineVariants.variantFor(source, List.of(SceneColor.FORMAT), true);
            assertEquals(SceneColor.FORMAT, hdr.getColorTargetStates().getFirst().format());
            assertFalse(hdr.getShaderDefines().flags().contains(HdrPipelineVariants.LEGACY_COLOR));
            assertEquals(!contract.equals(HdrPipelineVariants.DATA_OUTPUT),
                    hdr.getShaderDefines().flags().contains(HdrPipelineVariants.LINEAR_COLOR));
            assertEquals(contract.equals(HdrPipelineVariants.MANAGED_COLOR),
                    hdr.getShaderDefines().flags().contains(HdrPipelineVariants.DISPLAY_OPACITY));
            assertDrawContract(source, hdr);
            assertSame(source, HdrPipelineVariants.variantFor(source, List.of(GpuFormat.RGBA8_UNORM), false));
            assertSame(source, HdrPipelineVariants.variantFor(source, List.of(SceneColor.FORMAT), false));
        }
        var managedPremultiplied = HdrPipelineVariants.variantFor(worldPipeline(
                BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA, HdrPipelineVariants.MANAGED_COLOR),
                List.of(SceneColor.FORMAT), true);
        assertTrue(managedPremultiplied.getShaderDefines().flags().contains(HdrPipelineVariants.PREMULTIPLIED_COLOR));
        assertTrue(managedPremultiplied.getShaderDefines().flags().contains(HdrPipelineVariants.DISPLAY_OPACITY));
        var additive = HdrPipelineVariants.variantFor(worldPipeline(BlendFunction.ADDITIVE,
                HdrPipelineVariants.MANAGED_COLOR), List.of(SceneColor.FORMAT), true);
        assertFalse(additive.getShaderDefines().flags().contains(HdrPipelineVariants.DISPLAY_OPACITY));
    }

    private static RenderPipeline worldPipeline(BlendFunction blend, String... flags) {
        return worldPipeline(GpuFormat.RGBA8_UNORM, blend, flags);
    }

    private static RenderPipeline worldPipeline(GpuFormat format, BlendFunction blend, String... flags) {
        var source = RenderPipelines.ITEM_CUTOUT;
        var defines = ShaderDefines.builder();
        for (String flag : flags) defines.define(flag);
        return new RenderPipeline(Identifier.fromNamespaceAndPath("combatant", "pipeline/world_contract_test"),
                source.getShaders(), defines.build(), source.getBindGroupLayouts(),
                new ColorTargetState[]{new ColorTargetState(java.util.Optional.ofNullable(blend),
                        format, ColorTargetState.WRITE_ALL)},
                source.getDepthStencilState(), source.getPolygonMode(), source.isCull(),
                source.getVertexFormatBindings().toArray(VertexFormat[]::new),
                source.getPrimitiveTopology(), source.pushConstantSize(), source.getSortKey()) { };
    }

    @Test
    void fullscreenActivatesMaterialConversionEvenWhenFormatAlreadyMatches() {
        for (GpuFormat format : List.of(GpuFormat.RGBA8_UNORM, SceneColor.FORMAT)) {
            var base = worldPipeline(format, null, HdrPipelineVariants.MANAGED_COLOR);
            var hdr = HdrFullscreenPipelines.variantFor(base);
            assertNotSame(base, hdr);
            assertDrawContract(base, hdr);
            assertEquals(SceneColor.FORMAT, hdr.getColorTargetStates().getFirst().format());
            assertTrue(hdr.getShaderDefines().flags().contains(HdrPipelineVariants.LINEAR_COLOR));
            assertFalse(hdr.getShaderDefines().flags().contains(HdrPipelineVariants.LEGACY_COLOR));
            assertSame(hdr, HdrFullscreenPipelines.variantFor(hdr));
            assertSame(hdr, HdrFullscreenPipelines.variantFor(base));
            assertSame(hdr, HdrPipelineVariants.variantFor(hdr, List.of(SceneColor.FORMAT), true));
        }
        var nativeHdr = worldPipeline(SceneColor.FORMAT, null, HdrPipelineVariants.LINEAR_COLOR);
        assertSame(nativeHdr, HdrFullscreenPipelines.variantFor(nativeHdr));
    }

    @Test
    void failedCompilationsPreserveFailureSemantics() {
        var base = RenderPipelines.ITEM_CUTOUT;
        assertNull(HdrPipelineVariants.trackCompilation(base,
                CompletableFuture.completedFuture(CompiledRenderPipeline.Pending.NULL)).join().finishCompile());
        var failure = new IllegalStateException("compilation failed");
        var future = HdrPipelineVariants.trackCompilation(base, CompletableFuture.failedFuture(failure));
        assertSame(failure, assertThrows(CompletionException.class, future::join).getCause());
    }

    private static FrontendRenderPipeline compiled(RenderPipeline source) {
        return new FrontendRenderPipeline(source.getLocation().toString(), null,
                source.getVertexFormatBindings(), Object2IntMaps.emptyMap(), List.of(),
                source.getColorTargetStates(), source.wantsDepthTexture(), source.pushConstantSize());
    }

    private static void assertDrawContract(RenderPipeline base, RenderPipeline variant) {
        assertEquals(base.getShaders(), variant.getShaders());
        assertEquals(base.getShaderDefines().values(), variant.getShaderDefines().values());
        assertEquals(base.getVertexFormatBindings(), variant.getVertexFormatBindings());
        assertEquals(base.getBindGroupLayouts(), variant.getBindGroupLayouts());
        assertEquals(base.getDepthStencilState(), variant.getDepthStencilState());
        assertEquals(base.getPrimitiveTopology(), variant.getPrimitiveTopology());
        assertEquals(base.getPolygonMode(), variant.getPolygonMode());
        assertEquals(base.isCull(), variant.isCull());
        assertEquals(base.pushConstantSize(), variant.pushConstantSize());
        assertEquals(base.getSortKey(), variant.getSortKey());
        assertEquals(base.getColorTargetStates().getFirst().blendFunction(),
                variant.getColorTargetStates().getFirst().blendFunction());
        assertEquals(base.getColorTargetStates().getFirst().writeMask(),
                variant.getColorTargetStates().getFirst().writeMask());
    }
}
