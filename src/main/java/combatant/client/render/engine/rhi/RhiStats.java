/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi;

import combatant.client.render.engine.guard.LegacyRenderPath;
import combatant.client.render.engine.rhi.pipeline.RenderPipelineSpec;
import combatant.client.render.engine.shader.ShaderCostEstimate;
import combatant.client.render.engine.shader.ShaderCostRegistry;

import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class RhiStats {
    private final EnumMap<LegacyRenderPath, Long> legacyPathBreakdown = new EnumMap<>(LegacyRenderPath.class);
    private long frameId;
    private long drawCalls;
    private long multiDrawCalls;
    private long multiDrawLogicalDraws;
    private long renderPasses;
    private long renderPassAttachmentSwitches;
    private long pipelineBinds;
    private long pipelineBindSkips;
    private long pipelineSwitches;
    private long uniformBinds;
    private long samplerBinds;
    private long estimatedShaderAluOps;
    private long estimatedShaderTranscendentalOps;
    private long estimatedShaderTextureOps;
    private long estimatedShaderBranchOps;
    private long estimatedShaderLoopOps;
    private long fullscreenPasses;
    private long textureFastCopies;
    private long textureShaderCopies;
    private long meshUploads;
    private long uploadedVertexBytes;
    private long uploadedIndexBytes;
    private long ringWraps;
    private long ringStalls;
    private long immediateFallbackUploads;
    private long temporaryOwnedMeshes;
    private long legacyPathUses;
    private int lastRenderPassColorIdentity;
    private int lastRenderPassDepthIdentity;
    private int lastPipelineIdentity;
    private final IdentityHashMap<Object, Boolean> framePipelines = new IdentityHashMap<>();
    private final IdentityHashMap<Object, PipelineFrameStats> framePipelineBreakdown = new IdentityHashMap<>();
    private boolean detailedPipelineStats;
    private long dynamicArenaAllocations;
    private long dynamicPersistentArenaAllocations;
    private long dynamicSpillArenaAllocations;
    private long dynamicArenaReuses;
    private long dynamicArenaRetires;
    private long dynamicFenceChecks;
    private long dynamicFenceCompletions;
    private long dynamicArenaBacklogEvents;
    private long dynamicPersistentArenaBytes;
    private long dynamicSpillArenaBytes;
    private long dynamicMappedUploadBytes;
    private long dynamicBufferSubDataUploadBytes;
    private long dynamicMappedUploads;
    private long dynamicBufferSubDataUploads;
    private long dynamicArenaVertexHighWaterBytes;
    private long dynamicArenaIndexHighWaterBytes;
    private long dynamicArenaVertexCapacityObservedBytes;
    private long dynamicArenaIndexCapacityObservedBytes;
    private long dynamicArenaVertexUsedObservedBytes;
    private long dynamicArenaIndexUsedObservedBytes;
    private long dynamicLargestVertexAllocationBytes;
    private long dynamicLargestIndexAllocationBytes;

    public void beginFrame(long frameId) {
        this.frameId = frameId;
        drawCalls = multiDrawCalls = multiDrawLogicalDraws = renderPasses = renderPassAttachmentSwitches = fullscreenPasses = textureFastCopies = textureShaderCopies = 0L;
        pipelineBinds = pipelineBindSkips = pipelineSwitches = uniformBinds = samplerBinds = 0L;
        estimatedShaderAluOps = estimatedShaderTranscendentalOps = estimatedShaderTextureOps = 0L;
        estimatedShaderBranchOps = estimatedShaderLoopOps = 0L;
        meshUploads = uploadedVertexBytes = uploadedIndexBytes = 0L;
        ringWraps = ringStalls = immediateFallbackUploads = temporaryOwnedMeshes = 0L;
        legacyPathUses = 0L;
        lastRenderPassColorIdentity = 0;
        lastRenderPassDepthIdentity = 0;
        lastPipelineIdentity = 0;
        framePipelines.clear();
        framePipelineBreakdown.clear();
        legacyPathBreakdown.clear();
        dynamicArenaAllocations = 0L;
        dynamicArenaReuses = 0L;
        dynamicArenaRetires = 0L;
        dynamicFenceChecks = 0L;
        dynamicFenceCompletions = 0L;
        dynamicArenaBacklogEvents = 0L;
        dynamicSpillArenaAllocations = 0L;
        dynamicSpillArenaBytes = 0L;
        dynamicMappedUploadBytes = 0L;
        dynamicBufferSubDataUploadBytes = 0L;
        dynamicMappedUploads = 0L;
        dynamicBufferSubDataUploads = 0L;
        dynamicArenaVertexHighWaterBytes = 0L;
        dynamicArenaIndexHighWaterBytes = 0L;
        dynamicArenaVertexCapacityObservedBytes = 0L;
        dynamicArenaIndexCapacityObservedBytes = 0L;
        dynamicArenaVertexUsedObservedBytes = 0L;
        dynamicArenaIndexUsedObservedBytes = 0L;
        dynamicLargestVertexAllocationBytes = 0L;
        dynamicLargestIndexAllocationBytes = 0L;
        // Persistent arena allocation totals are lifetime-level and intentionally not reset here.
    }

    public void drawCall() {
        drawCalls++;
    }

    /** One backend multi-draw call which executes {@code logicalDraws} indexed draws. */
    public void multiDrawCall(int logicalDraws) {
        drawCalls++;
        multiDrawCalls++;
        multiDrawLogicalDraws += Math.max(0, logicalDraws);
    }

    public void renderPass(Object colorAttachment, Object depthAttachment) {
        int colorIdentity = System.identityHashCode(colorAttachment);
        int depthIdentity = System.identityHashCode(depthAttachment);
        if (renderPasses > 0 && (colorIdentity != lastRenderPassColorIdentity || depthIdentity != lastRenderPassDepthIdentity)) {
            renderPassAttachmentSwitches++;
        }
        renderPasses++;
        lastRenderPassColorIdentity = colorIdentity;
        lastRenderPassDepthIdentity = depthIdentity;
    }

    /** Records one draw's binding workload and whether the backend had to emit setPipeline. */
    public void pipelineUse(Object pipeline, RenderPipelineSpec spec, int uniforms, int samplers, boolean bindPipeline) {
        int identity = System.identityHashCode(pipeline);
        boolean switched = bindPipeline && pipelineBinds > 0 && identity != lastPipelineIdentity;
        if (bindPipeline) {
            if (switched) pipelineSwitches++;
            pipelineBinds++;
            lastPipelineIdentity = identity;
        } else {
            pipelineBindSkips++;
        }
        uniformBinds += Math.max(0, uniforms);
        samplerBinds += Math.max(0, samplers);
        if (pipeline != null) framePipelines.put(pipeline, Boolean.TRUE);
        if (!detailedPipelineStats) return;
        ShaderCostEstimate vertex = ShaderCostEstimate.NONE;
        ShaderCostEstimate fragment = ShaderCostEstimate.NONE;
        if (spec != null) {
            vertex = ShaderCostRegistry.get(spec.vertexShaderId(), "vertex");
            fragment = ShaderCostRegistry.get(spec.fragmentShaderId(), "fragment");
            addShaderEstimate(vertex);
            addShaderEstimate(fragment);
        }
        if (pipeline != null) {
            PipelineFrameStats pipelineStats = framePipelineBreakdown.computeIfAbsent(
                    pipeline,
                    ignored -> new PipelineFrameStats(spec)
            );
            pipelineStats.record(bindPipeline, switched, vertex, fragment);
        }
    }

    /** Compatibility entry point for call sites that always emit a real pipeline bind. */
    public void pipelineBind(Object pipeline, RenderPipelineSpec spec, int uniforms, int samplers) {
        pipelineUse(pipeline, spec, uniforms, samplers, true);
    }

    /** Enables allocation-bearing shader/pipeline attribution only while an external profiler needs it. */
    public void setDetailedPipelineStats(boolean enabled) {
        detailedPipelineStats = enabled;
        if (!enabled) framePipelineBreakdown.clear();
    }

    private void addShaderEstimate(ShaderCostEstimate estimate) {
        if (estimate == null) return;
        estimatedShaderAluOps += estimate.aluOps();
        estimatedShaderTranscendentalOps += estimate.transcendentalOps();
        estimatedShaderTextureOps += estimate.textureOps();
        estimatedShaderBranchOps += estimate.branchOps();
        estimatedShaderLoopOps += estimate.loopOps();
    }

    public void fullscreenPass() {
        fullscreenPasses++;
    }

    public void textureFastCopy() {
        textureFastCopies++;
    }

    public void textureShaderCopy() {
        textureShaderCopies++;
    }

    public void meshUpload(long vertexBytes, long indexBytes) {
        meshUploads++;
        uploadedVertexBytes += vertexBytes;
        uploadedIndexBytes += indexBytes;
    }

    public void ringWrap() {
        ringWraps++;
    }

    public void ringStall() {
        ringStalls++;
    }

    public void immediateFallbackUpload(long vertexBytes, long indexBytes) {
        immediateFallbackUploads++;
        meshUpload(vertexBytes, indexBytes);
    }

    public void temporaryOwnedMesh() {
        temporaryOwnedMeshes++;
    }

    public void legacyPath(LegacyRenderPath path) {
        legacyPathUses++;
        if (path != null) legacyPathBreakdown.merge(path, 1L, Long::sum);
    }

    public void dynamicArenaCreated(int count, long vertexBytes, long indexBytes, boolean spill) {
        dynamicArenaAllocations += count;
        long totalBytes = (vertexBytes + indexBytes) * count;
        if (spill) {
            dynamicSpillArenaAllocations += count;
            dynamicSpillArenaBytes += totalBytes;
        } else {
            dynamicPersistentArenaAllocations += count;
            dynamicPersistentArenaBytes += totalBytes;
        }
    }

    public void dynamicArenaAllocation(long vertexBytes, long indexBytes, boolean persistent) {
        dynamicLargestVertexAllocationBytes = Math.max(dynamicLargestVertexAllocationBytes, Math.max(0L, vertexBytes));
        dynamicLargestIndexAllocationBytes = Math.max(dynamicLargestIndexAllocationBytes, Math.max(0L, indexBytes));
    }

    public void dynamicArenaUploadPath(long bytes, boolean persistentMapped) {
        long safeBytes = Math.max(0L, bytes);
        if (persistentMapped) {
            dynamicMappedUploads++;
            dynamicMappedUploadBytes += safeBytes;
        } else {
            dynamicBufferSubDataUploads++;
            dynamicBufferSubDataUploadBytes += safeBytes;
        }
    }

    public void dynamicArenaFrameUsage(long vertexUsed, long vertexCapacity,
                                       long indexUsed, long indexCapacity) {
        dynamicArenaVertexHighWaterBytes = Math.max(dynamicArenaVertexHighWaterBytes, Math.max(0L, vertexUsed));
        dynamicArenaIndexHighWaterBytes = Math.max(dynamicArenaIndexHighWaterBytes, Math.max(0L, indexUsed));
        dynamicArenaVertexUsedObservedBytes += Math.max(0L, vertexUsed);
        dynamicArenaIndexUsedObservedBytes += Math.max(0L, indexUsed);
        dynamicArenaVertexCapacityObservedBytes += Math.max(0L, vertexCapacity);
        dynamicArenaIndexCapacityObservedBytes += Math.max(0L, indexCapacity);
    }

    public void dynamicArenaReuse() {
        dynamicArenaReuses++;
    }

    public void dynamicArenaRetired() {
        dynamicArenaRetires++;
    }

    public void dynamicFenceCheck() {
        dynamicFenceChecks++;
    }

    public void dynamicFenceCompleted() {
        dynamicFenceCompletions++;
    }

    public void dynamicArenaBacklog() {
        dynamicArenaBacklogEvents++;
    }


    public long drawCalls() {
        return drawCalls;
    }

    public long multiDrawCalls() {
        return multiDrawCalls;
    }

    public long multiDrawLogicalDraws() {
        return multiDrawLogicalDraws;
    }

    public long renderPasses() {
        return renderPasses;
    }

    public long renderPassAttachmentSwitches() {
        return renderPassAttachmentSwitches;
    }

    public long pipelineBinds() {
        return pipelineBinds;
    }

    public long pipelineBindSkips() {
        return pipelineBindSkips;
    }

    public long pipelineSwitches() {
        return pipelineSwitches;
    }

    public long uniquePipelines() {
        return framePipelines.size();
    }

    public long uniformBinds() {
        return uniformBinds;
    }

    public long samplerBinds() {
        return samplerBinds;
    }

    public long estimatedShaderAluOps() {
        return estimatedShaderAluOps;
    }

    public long estimatedShaderTranscendentalOps() {
        return estimatedShaderTranscendentalOps;
    }

    public long estimatedShaderTextureOps() {
        return estimatedShaderTextureOps;
    }

    public long estimatedShaderBranchOps() {
        return estimatedShaderBranchOps;
    }

    public long estimatedShaderLoopOps() {
        return estimatedShaderLoopOps;
    }

    public long fullscreenPasses() {
        return fullscreenPasses;
    }

    public long textureFastCopies() {
        return textureFastCopies;
    }

    public long textureShaderCopies() {
        return textureShaderCopies;
    }

    public long meshUploads() {
        return meshUploads;
    }

    public long uploadedVertexBytes() {
        return uploadedVertexBytes;
    }

    public long uploadedIndexBytes() {
        return uploadedIndexBytes;
    }

    public long ringWraps() {
        return ringWraps;
    }

    public long ringStalls() {
        return ringStalls;
    }

    public long immediateFallbackUploads() {
        return immediateFallbackUploads;
    }

    public long temporaryOwnedMeshes() {
        return temporaryOwnedMeshes;
    }

    public long legacyPathUses() {
        return legacyPathUses;
    }

    public RhiStatsSnapshot snapshot() {
        return snapshot(false);
    }

    public RhiStatsSnapshot snapshot(boolean includePipelineBreakdown) {
        List<RhiPipelineStatsSnapshot> pipelineBreakdown = includePipelineBreakdown
                ? framePipelineBreakdown.values().stream()
                .map(PipelineFrameStats::snapshot)
                .sorted(java.util.Comparator.comparingLong(RhiPipelineStatsSnapshot::draws).reversed())
                .toList()
                : List.of();
        return new RhiStatsSnapshot(frameId, drawCalls, multiDrawCalls, multiDrawLogicalDraws, renderPasses, renderPassAttachmentSwitches,
                pipelineBinds, pipelineBindSkips, pipelineSwitches, framePipelines.size(), uniformBinds, samplerBinds,
                estimatedShaderAluOps, estimatedShaderTranscendentalOps, estimatedShaderTextureOps,
                estimatedShaderBranchOps, estimatedShaderLoopOps,
                fullscreenPasses, textureFastCopies, textureShaderCopies,
                meshUploads, uploadedVertexBytes, uploadedIndexBytes, ringWraps, ringStalls,
                immediateFallbackUploads, temporaryOwnedMeshes,
                dynamicArenaAllocations, dynamicPersistentArenaAllocations, dynamicSpillArenaAllocations,
                dynamicArenaReuses, dynamicArenaRetires, dynamicFenceChecks, dynamicFenceCompletions,
                dynamicArenaBacklogEvents, dynamicPersistentArenaBytes, dynamicSpillArenaBytes,
                dynamicMappedUploads, dynamicMappedUploadBytes, dynamicBufferSubDataUploads, dynamicBufferSubDataUploadBytes,
                dynamicArenaVertexHighWaterBytes, dynamicArenaIndexHighWaterBytes,
                dynamicArenaVertexUsedObservedBytes, dynamicArenaIndexUsedObservedBytes,
                dynamicArenaVertexCapacityObservedBytes, dynamicArenaIndexCapacityObservedBytes,
                dynamicLargestVertexAllocationBytes, dynamicLargestIndexAllocationBytes,
                legacyPathUses, legacyPathBreakdown.isEmpty() ? Map.of() : new EnumMap<>(legacyPathBreakdown),
                pipelineBreakdown);
    }

    private static final class PipelineFrameStats {
        private final String pipelineId;
        private final String vertexShaderId;
        private final String fragmentShaderId;
        private long draws;
        private long binds;
        private long switchesInto;
        private long estimatedAluOps;
        private long estimatedTranscendentalOps;
        private long estimatedTextureOps;
        private long estimatedBranchOps;
        private long estimatedLoopOps;

        private PipelineFrameStats(RenderPipelineSpec spec) {
            pipelineId = spec != null ? spec.id() : "external";
            vertexShaderId = spec != null ? spec.vertexShaderId() : "";
            fragmentShaderId = spec != null ? spec.fragmentShaderId() : "";
        }

        private void record(boolean bound,
                            boolean switched,
                            ShaderCostEstimate vertex,
                            ShaderCostEstimate fragment) {
            draws++;
            if (bound) binds++;
            if (switched) switchesInto++;
            add(vertex);
            add(fragment);
        }

        private void add(ShaderCostEstimate estimate) {
            if (estimate == null) return;
            estimatedAluOps += estimate.aluOps();
            estimatedTranscendentalOps += estimate.transcendentalOps();
            estimatedTextureOps += estimate.textureOps();
            estimatedBranchOps += estimate.branchOps();
            estimatedLoopOps += estimate.loopOps();
        }

        private RhiPipelineStatsSnapshot snapshot() {
            return new RhiPipelineStatsSnapshot(
                    pipelineId, vertexShaderId, fragmentShaderId,
                    draws, binds, switchesInto,
                    estimatedAluOps, estimatedTranscendentalOps, estimatedTextureOps,
                    estimatedBranchOps, estimatedLoopOps
            );
        }
    }
}
