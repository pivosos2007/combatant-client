/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.mixininterface.IMsaaTexture;
import combatant.client.render.engine.framegraph.FrameGraphPassContract;
import combatant.client.render.engine.framegraph.FrameGraphPhysicalPlan;
import combatant.client.render.engine.framegraph.FrameGraphPhysicalPlanner;
import combatant.client.render.engine.framegraph.FrameGraphResourceDeclaration;
import combatant.client.render.engine.framegraph.FrameGraphResourceLifetime;
import combatant.client.render.engine.framegraph.FrameGraphResourceUse;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Deferred-specific descriptor lowering into the generic frame-graph physical planner. */
final class DeferredFrameGraphResourcePlanner {
    private DeferredFrameGraphResourcePlanner() {
    }

    static FrameGraphPhysicalPlan plan(List<DeferredPassSpec> passes, DeferredPassContext context) {
        return plan(passes, context, layout(context));
    }

    static FrameGraphPhysicalPlan plan(List<DeferredPassSpec> passes, DeferredPassContext context, Layout layout) {
        Map<combatant.client.render.engine.framegraph.FrameGraphResourceKey, FrameGraphResourceDeclaration> declarations =
                new HashMap<>();
        ArrayList<FrameGraphPassContract> physicalContracts = new ArrayList<>(passes.size());

        for (DeferredPassSpec pass : passes) {
            ArrayList<FrameGraphResourceUse> uses = new ArrayList<>();
            for (FrameGraphResourceUse use : pass.resources()) {
                DeferredResource resource = resource(use);
                if (resource == null) continue;
                if (resource.key().lifetime() == FrameGraphResourceLifetime.EXTERNAL) {
                    uses.add(use);
                    continue;
                }
                if (!resource.graphManagedPhysicalResource()) continue;
                uses.add(use);
                declarations.computeIfAbsent(resource.key(), ignored ->
                        new FrameGraphResourceDeclaration(resource.key(), descriptor(resource, layout, context.settings())));
            }
            physicalContracts.add(new FrameGraphPassContract(
                    pass.stage().renderPhase(), pass.id(), pass.executionDomain(), uses, pass.externallyDriven()));
        }
        // Correctness-first renderer bring-up: do not alias deferred transient resources yet.
        // Several passes execute at Minecraft/Sodium integration boundaries rather than inside one
        // monolithic graph submission; keeping one physical allocation per logical resource makes
        // producer validity and debug inspection deterministic until those boundaries are proven.
        return FrameGraphPhysicalPlanner.plan(physicalContracts, declarations, false);
    }

    static combatant.client.render.engine.framegraph.FrameGraphPhysicalResourceDescriptor descriptor(
            DeferredResource resource,
            Layout layout,
            DeferredRuntimeConfig.Snapshot settings) {
        if (resource == null || !resource.graphManagedPhysicalResource() || resource.physicalSpec() == null) {
            throw new IllegalArgumentException("Deferred resource is not graph-managed: " + resource);
        }
        var descriptor = resource.physicalSpec().descriptor(
                resource, layout.renderWidth(), layout.renderHeight(), layout.outputWidth(), layout.outputHeight(),
                layout.sceneSamples(), settings);
        if (descriptor == null) {
            throw new IllegalStateException("Deferred physical spec returned no descriptor: " + resource);
        }
        if (descriptor.kind() != resource.key().kind()) {
            throw new IllegalStateException("Deferred resource kind/descriptor mismatch for "
                    + resource.key().name() + ": " + resource.key().kind() + " vs " + descriptor.kind());
        }
        return descriptor;
    }

    static Layout layout(DeferredPassContext context) {
        DeferredResourceBindings bindings = context.resources();
        GpuTextureView reference = bindings.explicitTexture(DeferredResource.SCENE_COLOR);
        if (reference == null) reference = bindings.explicitTexture(DeferredResource.MAIN_DEPTH);
        if (reference == null && context.frame().framebuffer() != null
                && context.frame().framebuffer().mainFramebuffer() != null) {
            reference = context.frame().framebuffer().mainFramebuffer().getColorTextureView();
        }

        DeferredPrimaryViewSource.FrameView primary = context.primaryView().current();
        int renderWidth = reference != null ? Math.max(1, reference.getWidth(0))
                : primary != null && primary.hasRenderResolution() ? primary.renderWidth() : 1;
        int renderHeight = reference != null ? Math.max(1, reference.getHeight(0))
                : primary != null && primary.hasRenderResolution() ? primary.renderHeight() : 1;
        int sceneSamples = samples(reference);

        int outputWidth = bindings.outputWidth();
        int outputHeight = bindings.outputHeight();
        if (outputWidth <= 0 || outputHeight <= 0) {
            if (primary != null && primary.outputWidth() > 0 && primary.outputHeight() > 0) {
                outputWidth = primary.outputWidth();
                outputHeight = primary.outputHeight();
            } else {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft != null && minecraft.getWindow() != null) {
                    outputWidth = Math.max(1, minecraft.getWindow().getWidth());
                    outputHeight = Math.max(1, minecraft.getWindow().getHeight());
                } else {
                    outputWidth = renderWidth;
                    outputHeight = renderHeight;
                }
            }
        }
        return new Layout(renderWidth, renderHeight, outputWidth, outputHeight, sceneSamples);
    }

    private static int samples(@Nullable GpuTextureView view) {
        return view != null && view.texture() instanceof IMsaaTexture msaa
                ? Math.max(1, msaa.combatant$getSamples()) : 1;
    }

    private static @Nullable DeferredResource resource(FrameGraphResourceUse use) {
        if (use == null) return null;
        for (DeferredResource resource : DeferredResource.values()) {
            if (resource.key().equals(use.resource())) return resource;
        }
        return null;
    }

    record Layout(int renderWidth, int renderHeight, int outputWidth, int outputHeight, int sceneSamples) {
        Layout {
            renderWidth = Math.max(1, renderWidth);
            renderHeight = Math.max(1, renderHeight);
            outputWidth = Math.max(1, outputWidth);
            outputHeight = Math.max(1, outputHeight);
            sceneSamples = Math.max(1, sceneSamples);
        }
    }
}
