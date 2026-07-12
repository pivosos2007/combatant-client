/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi;

import combatant.client.render.engine.guard.LegacyRenderPath;

import java.util.EnumMap;
import java.util.Map;

public final class RhiStats {
    private final EnumMap<LegacyRenderPath, Long> legacyPathBreakdown = new EnumMap<>(LegacyRenderPath.class);
    private long frameId;
    private long drawCalls;
    private long renderPasses;
    private long renderPassAttachmentSwitches;
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

    public void beginFrame(long frameId) {
        this.frameId = frameId;
        drawCalls = renderPasses = renderPassAttachmentSwitches = fullscreenPasses = textureFastCopies = textureShaderCopies = 0L;
        meshUploads = uploadedVertexBytes = uploadedIndexBytes = 0L;
        ringWraps = ringStalls = immediateFallbackUploads = temporaryOwnedMeshes = 0L;
        legacyPathUses = 0L;
        lastRenderPassColorIdentity = 0;
        lastRenderPassDepthIdentity = 0;
        legacyPathBreakdown.clear();
        dynamicArenaAllocations = 0L;
        dynamicArenaReuses = 0L;
        dynamicArenaRetires = 0L;
        dynamicFenceChecks = 0L;
        dynamicFenceCompletions = 0L;
        dynamicArenaBacklogEvents = 0L;
        dynamicSpillArenaAllocations = 0L;
        dynamicSpillArenaBytes = 0L;
        // Persistent arena totals are lifetime-level and intentionally not reset here.
    }

    public void drawCall() {
        drawCalls++;
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
        // Per-mesh arena suballocation. The byte totals still live in meshUpload(); this counter exists for
        // backend-specific diagnostics and symmetry with future staging paths.
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

    public long renderPasses() {
        return renderPasses;
    }

    public long renderPassAttachmentSwitches() {
        return renderPassAttachmentSwitches;
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
        return new RhiStatsSnapshot(frameId, drawCalls, renderPasses, renderPassAttachmentSwitches,
                fullscreenPasses, textureFastCopies, textureShaderCopies,
                meshUploads, uploadedVertexBytes, uploadedIndexBytes, ringWraps, ringStalls,
                immediateFallbackUploads, temporaryOwnedMeshes,
                dynamicArenaAllocations, dynamicPersistentArenaAllocations, dynamicSpillArenaAllocations,
                dynamicArenaReuses, dynamicArenaRetires, dynamicFenceChecks, dynamicFenceCompletions,
                dynamicArenaBacklogEvents, dynamicPersistentArenaBytes, dynamicSpillArenaBytes,
                legacyPathUses, legacyPathBreakdown.isEmpty() ? Map.of() : new EnumMap<>(legacyPathBreakdown));
    }
}
