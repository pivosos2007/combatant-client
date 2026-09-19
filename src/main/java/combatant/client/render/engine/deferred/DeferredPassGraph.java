/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.RenderFrameContext;
import combatant.client.render.engine.core.RenderPhaseScope;
import combatant.client.render.engine.framegraph.CompiledFrameGraph;
import combatant.client.render.engine.framegraph.FrameGraphContractCompiler;
import combatant.client.render.engine.framegraph.FrameGraphAccess;
import combatant.client.render.engine.framegraph.FrameGraphPassContract;
import combatant.client.render.engine.framegraph.FrameGraphPhysicalPlan;
import combatant.client.render.engine.framegraph.FrameGraphResourceKey;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.RhiResourceBarrier;
import combatant.client.render.engine.rhi.shader.RhiStorageBuffer;
import combatant.client.render.engine.rhi.shader.RhiStorageImage;
import combatant.client.render.engine.rhi.shader.RhiStorageVolume;
import combatant.client.render.engine.rhi.shader.RhiShaderStage;
import combatant.client.render.engine.world.WorldRenderState;
import combatant.client.util.logging.DebugLog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Ordered world-pass registry. Geometry producers remain externally driven during migration;
 * compute/tessellation/post stages register here and execute only at explicit phase boundaries.
 */
public final class DeferredPassGraph {
    private static final Comparator<DeferredPassSpec> ORDER = Comparator
            .comparingInt((DeferredPassSpec pass) -> pass.stage().ordinal())
            .thenComparingInt(DeferredPassSpec::priority)
            .thenComparing(DeferredPassSpec::id);

    private final ArrayList<DeferredPassSpec> corePasses = new ArrayList<>();
    private final ArrayList<DeferredPassSpec> extensionPasses = new ArrayList<>();
    private final DeferredBackendPasses backendPasses = new DeferredBackendPasses();
    private List<DeferredPassSpec> ordered = List.of();
    private CompiledFrameGraph compiled = new CompiledFrameGraph(List.of(), List.of());
    private FrameGraphPhysicalPlan physicalPlan = FrameGraphPhysicalPlan.EMPTY;
    private DeferredFrameGraphResourcePlanner.Layout physicalLayout;
    private CombatantRhi physicalOwner;
    private long physicalFrameId = Long.MIN_VALUE;
    private long compileGeneration;
    private long physicalCompileGeneration = Long.MIN_VALUE;
    private boolean dirty = true;

    public DeferredPassGraph() {
        corePasses.add(DeferredPassSpec.builder("world.geometry.opaque", DeferredStage.OPAQUE_GEOMETRY)
                .write(DeferredResource.SCENE_COLOR, DeferredResource.MAIN_DEPTH,
                        DeferredResource.GBUFFER_SURFACE, DeferredResource.GBUFFER_GEOMETRY,
                        DeferredResource.GBUFFER_AUXILIARY, DeferredResource.GBUFFER_MATERIAL,
                        DeferredResource.GBUFFER_MATERIAL_ID)
                .external().build());
        corePasses.add(DeferredPassSpec.builder("world.geometry.cutout", DeferredStage.CUTOUT_GEOMETRY)
                .readWrite(DeferredResource.SCENE_COLOR, DeferredResource.MAIN_DEPTH,
                        DeferredResource.GBUFFER_SURFACE, DeferredResource.GBUFFER_GEOMETRY,
                        DeferredResource.GBUFFER_AUXILIARY, DeferredResource.GBUFFER_MATERIAL,
                        DeferredResource.GBUFFER_MATERIAL_ID)
                .external().build());
        corePasses.add(DeferredPassSpec.builder("world.lighting.neutral", DeferredStage.LIGHTING)
                .read(DeferredResource.GBUFFER_SURFACE, DeferredResource.GBUFFER_GEOMETRY,
                        DeferredResource.GBUFFER_AUXILIARY, DeferredResource.GBUFFER_MATERIAL,
                        DeferredResource.GBUFFER_DEPTH, DeferredResource.RESOLVED_DEPTH,
                        DeferredResource.ENVIRONMENT_IRRADIANCE)
                .optionalRead(DeferredResource.SHADOW_COLOR, DeferredResource.CLOUD_SHADOW_VISIBILITY,
                        DeferredResource.AMBIENT_OCCLUSION)
                .write(DeferredResource.DIRECT_LIGHTING_COLOR, DeferredResource.SCENE_COLOR)
                .external().build());
        corePasses.add(DeferredPassSpec.builder("world.forward.opaque", DeferredStage.FORWARD_OPAQUE)
                .readWrite(DeferredResource.SCENE_COLOR, DeferredResource.MAIN_DEPTH)
                .external().build());
        corePasses.add(DeferredPassSpec.builder("world.translucency.forward", DeferredStage.TRANSLUCENCY)
                .readWrite(DeferredResource.SCENE_COLOR, DeferredResource.MAIN_DEPTH)
                .external().build());
        corePasses.add(DeferredPassSpec.builder("world.postprocess.external", DeferredStage.POST_PROCESS)
                .read(DeferredResource.MAIN_DEPTH)
                .readWrite(DeferredResource.SCENE_COLOR)
                .external().build());
        backendPasses.install(corePasses);
    }

    /** Releases native compute pipelines before the active RHI/device is destroyed or switched. */
    public synchronized void releaseBackendResources(CombatantRhi owner) {
        releasePhysicalResources(owner);
        backendPasses.release(owner);
    }

    /** Releases only the deferred graph scope; ownership/destruction stays inside Combatant RHI. */
    public synchronized void releasePhysicalResources(CombatantRhi owner) {
        CombatantRhi resolved = physicalOwner != null ? physicalOwner : owner;
        physicalOwner = null;
        physicalPlan = FrameGraphPhysicalPlan.EMPTY;
        physicalLayout = null;
        physicalFrameId = Long.MIN_VALUE;
        physicalCompileGeneration = Long.MIN_VALUE;
        if (resolved == null) return;
        try {
            resolved.resources().frameGraphResources().releaseScope(this);
        } catch (RuntimeException ignored) {
            // Backend teardown may already have retired this scope.
        }
    }

    /** Compiles native deferred backend programs only after the deferred runtime asset scope activates. */
    public synchronized void prepareBackendResources() {
        backendPasses.prepare(CombatantRenderSystem.rhi());
    }

    public synchronized AutoCloseable register(DeferredPassSpec pass) {
        if (pass == null) return () -> { };
        if (containsId(pass.id())) throw new IllegalArgumentException("Duplicate deferred pass id: " + pass.id());
        extensionPasses.add(pass);
        dirty = true;
        return () -> unregister(pass);
    }

    public synchronized CompiledFrameGraph compile() {
        ensureCompiled();
        return compiled;
    }

    public void execute(DeferredStage stage, RenderFrameContext frame, DeferredResourceBindings resources) {
        execute(stage, frame, resources, new DeferredSecondaryViewRegistry(), new DeferredPrimaryViewSource(),
                new DeferredTemporalHistoryRegistry(), WorldRenderState.unknown(0L), DeferredRuntimeConfig.current());
    }

    public void execute(DeferredStage stage,
                        RenderFrameContext frame,
                        DeferredResourceBindings resources,
                        DeferredSecondaryViewRegistry secondaryViews,
                        DeferredPrimaryViewSource primaryView) {
        execute(stage, frame, resources, secondaryViews, primaryView, new DeferredTemporalHistoryRegistry(),
                WorldRenderState.unknown(0L), DeferredRuntimeConfig.current());
    }

    public void execute(DeferredStage stage,
                        RenderFrameContext frame,
                        DeferredResourceBindings resources,
                        DeferredSecondaryViewRegistry secondaryViews,
                        DeferredPrimaryViewSource primaryView,
                        DeferredTemporalHistoryRegistry temporalHistory,
                        WorldRenderState worldState,
                        DeferredRuntimeConfig.Snapshot settings) {
        if (stage == null || frame == null || resources == null) return;
        if (secondaryViews == null) throw new IllegalArgumentException("secondaryViews");
        if (primaryView == null) throw new IllegalArgumentException("primaryView");
        if (temporalHistory == null) throw new IllegalArgumentException("temporalHistory");
        List<DeferredPassSpec> snapshot;
        CompiledFrameGraph compiledSnapshot;
        synchronized (this) {
            ensureCompiled();
            snapshot = ordered;
            compiledSnapshot = compiled;
        }

        CombatantRhi rhi = CombatantRenderSystem.rhi();
        DeferredPassContext context = new DeferredPassContext(
                stage, frame, rhi, resources, secondaryViews, primaryView, temporalHistory, worldState, settings
        );
        ensurePhysicalPlan(snapshot, context);
        try (RenderPhaseScope ignored = CombatantRenderSystem.phase(stage.renderPhase(), "deferred:" + stage.name().toLowerCase())) {
            for (int passIndex = 0; passIndex < snapshot.size(); passIndex++) {
                DeferredPassSpec pass = snapshot.get(passIndex);
                if (pass.stage() != stage || pass.externallyDriven()) continue;
                if (!supports(pass, rhi)) {
                    DebugLog.warnOnChange(
                            "deferred.pass.unsupported." + pass.id(),
                            pass.requiredShaderStages().toString(),
                            "[Deferred] skipping pass %s; required shader stages are unavailable: %s",
                            pass.id(), pass.requiredShaderStages()
                    );
                    continue;
                }
                try {
                    if (pass.feature() != null && !context.featureEnabled(pass.feature())) continue;
                    if (!pass.condition().test(context)) continue;
                    prepareResources(pass, context);
                    validateGraphReads(pass, context.resources());
                    prepareGraphAccesses(pass, context);
                    lowerAdvancedBarriers(passIndex, snapshot, compiledSnapshot, context);
                    pass.executor().execute(context);
                    markWrites(pass, context.resources());
                } catch (Throwable t) {
                    markFailedWrites(pass, context.resources(), t);
                    DebugLog.warnOnChange(
                            "deferred.pass.failed." + pass.id(),
                            t.getClass().getSimpleName() + "|" + t.getMessage(),
                            "[Deferred] pass %s failed: %s: %s",
                            pass.id(), t.getClass().getSimpleName(), t.getMessage()
                    );
                    DebugLog.errorOnce(
                            "deferred.pass.failed.stack." + pass.id() + "." + t.getClass().getName()
                                    + "." + String.valueOf(t.getMessage()),
                            "[Deferred] first failure stack for pass {}", pass.id(), t
                    );
                }
            }
        }
    }

    /**
     * Applies the same physical-resource activation, validity checks and barriers to a pass whose
     * draw submission is still driven by the Minecraft/Sodium integration layer.
     */
    public boolean prepareExternalPass(String passId, DeferredPassContext context) {
        if (passId == null || passId.isBlank() || context == null) return false;
        List<DeferredPassSpec> snapshot;
        CompiledFrameGraph compiledSnapshot;
        int passIndex = -1;
        synchronized (this) {
            ensureCompiled();
            snapshot = ordered;
            compiledSnapshot = compiled;
            for (int index = 0; index < snapshot.size(); index++) {
                if (snapshot.get(index).id().equals(passId)) {
                    passIndex = index;
                    break;
                }
            }
        }
        if (passIndex < 0) throw new IllegalArgumentException("Unknown deferred external pass: " + passId);
        DeferredPassSpec pass = snapshot.get(passIndex);
        if (!pass.externallyDriven()) throw new IllegalArgumentException("Deferred pass is not externally driven: " + passId);
        if (pass.stage() != context.stage()) {
            throw new IllegalArgumentException("External pass stage mismatch for " + passId + ": "
                    + pass.stage() + " vs " + context.stage());
        }
        if (!supports(pass, context.rhi())
                || (pass.feature() != null && !context.featureEnabled(pass.feature()))
                || !pass.condition().test(context)) return false;
        ensurePhysicalPlan(snapshot, context);
        prepareResources(pass, context);
        validateGraphReads(pass, context.resources());
        prepareGraphAccesses(pass, context);
        lowerAdvancedBarriers(passIndex, snapshot, compiledSnapshot, context);
        return true;
    }

    /** Publishes graph-owned outputs only after the externally-driven GPU submission succeeded. */
    public void completeExternalPass(String passId, DeferredResourceBindings resources) {
        if (passId == null || passId.isBlank() || resources == null) return;
        DeferredPassSpec pass = null;
        synchronized (this) {
            ensureCompiled();
            for (DeferredPassSpec candidate : ordered) {
                if (candidate.id().equals(passId)) {
                    pass = candidate;
                    break;
                }
            }
        }
        if (pass == null) throw new IllegalArgumentException("Unknown deferred external pass: " + passId);
        if (!pass.externallyDriven()) throw new IllegalArgumentException("Deferred pass is not externally driven: " + passId);
        markWrites(pass, resources);
    }

    public synchronized List<DeferredPassSpec> passes() {
        ensureCompiled();
        return ordered;
    }

    private synchronized void unregister(DeferredPassSpec pass) {
        if (extensionPasses.remove(pass)) dirty = true;
    }

    private boolean containsId(String id) {
        for (DeferredPassSpec pass : corePasses) if (pass.id().equals(id)) return true;
        for (DeferredPassSpec pass : extensionPasses) if (pass.id().equals(id)) return true;
        return false;
    }

    private void ensureCompiled() {
        if (!dirty) return;
        ArrayList<DeferredPassSpec> next = new ArrayList<>(corePasses.size() + extensionPasses.size());
        next.addAll(corePasses);
        next.addAll(extensionPasses);
        next.sort(ORDER);

        Set<String> ids = new HashSet<>();
        ArrayList<FrameGraphPassContract> contracts = new ArrayList<>(next.size());
        for (DeferredPassSpec pass : next) {
            if (!ids.add(pass.id())) throw new IllegalStateException("Duplicate deferred pass id: " + pass.id());
            contracts.add(pass.contract());
        }
        compiled = FrameGraphContractCompiler.compile(contracts);
        ordered = List.copyOf(next);
        compileGeneration++;
        dirty = false;
    }

    private synchronized void ensurePhysicalPlan(List<DeferredPassSpec> passes, DeferredPassContext context) {
        DeferredFrameGraphResourcePlanner.Layout layout = DeferredFrameGraphResourcePlanner.layout(context);
        CombatantRhi rhi = context.rhi();
        long frameId = context.frame().frameId();

        if (physicalOwner != null && physicalOwner != rhi) {
            releasePhysicalResources(physicalOwner);
        }
        boolean rebuild = physicalOwner != rhi
                || physicalFrameId != frameId
                || physicalCompileGeneration != compileGeneration
                || !layout.equals(physicalLayout);
        if (rebuild) {
            if (physicalFrameId == frameId && physicalLayout != null && !physicalLayout.equals(layout)
                    && context.resources().hasGraphAccessThisFrame()) {
                throw new IllegalStateException("Deferred frame-graph physical extent changed after GPU access began: "
                        + physicalLayout + " -> " + layout);
            }
            physicalPlan = DeferredFrameGraphResourcePlanner.plan(passes, context, layout);
            physicalOwner = rhi;
            physicalFrameId = frameId;
            physicalCompileGeneration = compileGeneration;
            physicalLayout = layout;
        }
        boolean scopeMissing = !rhi.resources().frameGraphResources().hasScope(this);
        if (rebuild || scopeMissing) {
            if (scopeMissing && !rebuild) context.resources().detachFrameGraph();
            rhi.resources().frameGraphResources().materialize(this, physicalPlan, rhi);
        }
        context.resources().attachFrameGraph(this, rhi, physicalPlan);
    }

    public synchronized FrameGraphPhysicalPlan physicalPlan() {
        return physicalPlan;
    }

    public synchronized String physicalDebugDump(DeferredResourceBindings bindings) {
        StringBuilder out = new StringBuilder(physicalPlan.debugDump());
        out.append("Deferred produced state:\n");
        for (FrameGraphPhysicalPlan.LogicalResourcePlan logical : physicalPlan.logicalResources()) {
            DeferredResource resource = resource(logical.resource());
            out.append("  ").append(logical.resource().name())
                    .append(" physical=").append(logical.physicalAllocationId() < 0 ? "external" : logical.physicalAllocationId())
                    .append(" aliasGroup=").append(logical.aliasGroup() < 0 ? "-" : logical.aliasGroup())
                    .append(" produced=").append(resource != null && bindings != null && bindings.isValid(resource));
            DeferredResourceProvenance provenance = resource == null || bindings == null ? null : bindings.provenance(resource);
            if (provenance != null) {
                out.append(" status=").append(provenance.status())
                        .append(" producer=").append(provenance.producerPassId());
                if (!provenance.reasonCode().isBlank()) out.append(" reason=").append(provenance.reasonCode());
            }
            out.append('\n');
        }
        return out.toString();
    }

    private static boolean supports(DeferredPassSpec pass, CombatantRhi rhi) {
        for (RhiShaderStage stage : pass.requiredShaderStages()) {
            if (!rhi.advancedShaders().supports(stage)) return false;
        }
        return true;
    }

    private static void prepareResources(DeferredPassSpec pass, DeferredPassContext context) {
        for (var use : pass.resources()) {
            DeferredResource resource = resource(use.resource());
            if (resource == null) continue;

            if (use.access().reads() && resource.key().lifetime() == combatant.client.render.engine.framegraph.FrameGraphResourceLifetime.PERSISTENT) {
                context.resources().bindExisting(resource, context.settings());
            }
            if (use.access().writes() && context.resources().isGraphManaged(resource)) {
                context.resources().ensurePhysicalResource(resource);
            }
        }
    }

    private static void prepareGraphAccesses(DeferredPassSpec pass, DeferredPassContext context) {
        RhiResourceBarrier.Stage stage = barrierStage(pass);
        for (var use : pass.resources()) {
            DeferredResource resource = resource(use.resource());
            if (resource == null || !context.resources().isGraphManaged(resource)) continue;
            if (use.access() == FrameGraphAccess.READ && !context.resources().isValid(resource)) continue;
            context.resources().prepareGraphAccess(resource, use.access(), stage, context.rhi());
        }
    }

    private static void validateGraphReads(DeferredPassSpec pass, DeferredResourceBindings resources) {
        for (var use : pass.resources()) {
            if (!use.access().reads()) continue;
            DeferredResource resource = resource(use.resource());
            if (resource == null || !resources.isGraphManaged(resource)) continue;
            if (resource.key().lifetime() == combatant.client.render.engine.framegraph.FrameGraphResourceLifetime.PERSISTENT) {
                continue;
            }
            if (pass.optionalReads().contains(resource)) continue;
            if (!resources.isValid(resource)) {
                throw new IllegalStateException("Deferred pass '" + pass.id() + "' would read graph resource '"
                        + resource.key().name() + "' before its producer completed");
            }
        }
    }

    private static void markWrites(DeferredPassSpec pass, DeferredResourceBindings resources) {
        for (var use : pass.resources()) {
            if (!use.access().writes()) continue;
            DeferredResource resource = resource(use.resource());
            if (resource == null) continue;
            resources.publishProducedIfAbsentForPass(resource, pass.id());
            resources.markWritten(resource);
        }
    }

    private static void markFailedWrites(DeferredPassSpec pass, DeferredResourceBindings resources, Throwable failure) {
        String message = failure == null ? "" : failure.getClass().getSimpleName()
                + (failure.getMessage() == null ? "" : ": " + failure.getMessage());
        for (var use : pass.resources()) {
            if (!use.access().writes()) continue;
            DeferredResource resource = resource(use.resource());
            if (resource == null) continue;
            resources.publishStatus(resource, pass.id(), DeferredResourceStatus.FAILED,
                    "pass_exception", message, null);
        }
    }

    private static void lowerAdvancedBarriers(int consumerIndex,
                                              List<DeferredPassSpec> passes,
                                              CompiledFrameGraph graph,
                                              DeferredPassContext context) {
        Map<BarrierKey, BarrierResources> grouped = new LinkedHashMap<>();
        Set<BarrierKey> genericTextureBarriers = new HashSet<>();
        for (CompiledFrameGraph.Dependency dependency : graph.dependencies()) {
            if (dependency.consumerIndex() != consumerIndex) continue;
            DeferredResource resource = resource(dependency.resource());
            if (resource == null) continue;

            RhiStorageBuffer buffer = context.resources().buffer(resource);
            RhiStorageImage image = context.resources().storageImage(resource);
            RhiStorageVolume volume = context.resources().storageVolume(resource);
            boolean genericTexture = image == null && volume == null && context.resources().texture(resource) != null;
            if (buffer == null && image == null && volume == null && !genericTexture) continue;

            DeferredPassSpec producer = passes.get(dependency.producerIndex());
            DeferredPassSpec consumer = passes.get(dependency.consumerIndex());
            FrameGraphAccess consumerAccess = access(consumer, dependency.resource());
            if (context.resources().isGraphManaged(resource)
                    && consumerAccess == FrameGraphAccess.READ
                    && !context.resources().isValid(resource)) {
                continue;
            }
            RhiResourceBarrier.Access sourceAccess = barrierAccess(access(producer, dependency.resource()));
            RhiResourceBarrier.Access destinationAccess = barrierAccess(consumerAccess);
            if (genericTexture) {
                // Mojang-owned render targets/atlas views do not expose a Combatant RhiStorageImage.
                // Use a conservative global memory dependency so framebuffer writes are visible to
                // later compute/sampled consumers on both GL and Vulkan.
                genericTextureBarriers.add(new BarrierKey(
                        RhiResourceBarrier.Stage.ALL, sourceAccess,
                        RhiResourceBarrier.Stage.ALL, destinationAccess
                ));
            }

            if (buffer == null && image == null && volume == null) continue;
            BarrierKey key = new BarrierKey(
                    barrierStage(producer), sourceAccess,
                    barrierStage(consumer), destinationAccess
            );
            BarrierResources values = grouped.computeIfAbsent(key, ignored -> new BarrierResources());
            if (buffer != null && !values.buffers.contains(buffer)) values.buffers.add(buffer);
            if (image != null && !values.images.contains(image)) values.images.add(image);
            if (volume != null && !values.volumes.contains(volume)) values.volumes.add(volume);
        }

        for (Map.Entry<BarrierKey, BarrierResources> entry : grouped.entrySet()) {
            BarrierKey key = entry.getKey();
            BarrierResources resources = entry.getValue();
            context.advancedShaders().barrier(new RhiResourceBarrier(
                    key.sourceStage, key.sourceAccess,
                    key.destinationStage, key.destinationAccess,
                    resources.buffers, resources.images, resources.volumes
            ));
        }
        for (BarrierKey key : genericTextureBarriers) {
            context.advancedShaders().barrier(new RhiResourceBarrier(
                    key.sourceStage, key.sourceAccess,
                    key.destinationStage, key.destinationAccess,
                    List.of(), List.of()
            ));
        }
    }

    private static DeferredResource resource(FrameGraphResourceKey key) {
        for (DeferredResource resource : DeferredResource.values()) {
            if (resource.key().equals(key)) return resource;
        }
        return null;
    }

    private static FrameGraphAccess access(DeferredPassSpec pass, FrameGraphResourceKey resource) {
        for (var use : pass.resources()) if (use.resource().equals(resource)) return use.access();
        throw new IllegalStateException("Pass " + pass.id() + " does not declare " + resource.name());
    }

    private static RhiResourceBarrier.Stage barrierStage(DeferredPassSpec pass) {
        return switch (pass.executionDomain()) {
            case GRAPHICS -> RhiResourceBarrier.Stage.GRAPHICS;
            case COMPUTE -> RhiResourceBarrier.Stage.COMPUTE;
            case TRANSFER -> RhiResourceBarrier.Stage.TRANSFER;
        };
    }

    private static RhiResourceBarrier.Access barrierAccess(FrameGraphAccess access) {
        return switch (access) {
            case READ -> RhiResourceBarrier.Access.READ;
            case WRITE -> RhiResourceBarrier.Access.WRITE;
            case READ_WRITE -> RhiResourceBarrier.Access.READ_WRITE;
        };
    }

    private record BarrierKey(
            RhiResourceBarrier.Stage sourceStage,
            RhiResourceBarrier.Access sourceAccess,
            RhiResourceBarrier.Stage destinationStage,
            RhiResourceBarrier.Access destinationAccess
    ) {
    }

    private static final class BarrierResources {
        private final ArrayList<RhiStorageBuffer> buffers = new ArrayList<>();
        private final ArrayList<RhiStorageImage> images = new ArrayList<>();
        private final ArrayList<RhiStorageVolume> volumes = new ArrayList<>();
    }
}
