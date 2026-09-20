/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Typed metadata for one subsystem history generation. */
public record DeferredTemporalHistoryDescriptor(
        DeferredTemporalHistoryId id,
        List<DeferredResource> currentResources,
        List<DeferredResource> previousResources,
        @Nullable DeferredResource confidenceResource,
        int width,
        int height,
        String format,
        int contractVersion,
        long epoch,
        int age,
        boolean valid,
        DeferredHistoryResetReason resetReason,
        DeferredHistoryProducer producer,
        String producerId,
        DeferredHistoryStorageMode storageMode,
        long lastWrittenFrame
) {
    public DeferredTemporalHistoryDescriptor {
        currentResources = currentResources == null ? List.of() : List.copyOf(currentResources);
        previousResources = previousResources == null ? List.of() : List.copyOf(previousResources);
        width = Math.max(0, width);
        height = Math.max(0, height);
        format = format == null ? "unknown" : format;
        contractVersion = Math.max(1, contractVersion);
        age = Math.max(0, age);
        resetReason = resetReason == null ? DeferredHistoryResetReason.RENDERER_RESET : resetReason;
        producer = producer == null ? DeferredHistoryProducer.EXTENSION : producer;
        producerId = producerId == null || producerId.isBlank() ? id.name().toLowerCase() : producerId;
        storageMode = storageMode == null ? DeferredHistoryStorageMode.STORE_AFTER_CONSUME : storageMode;
        if (valid && resetReason != DeferredHistoryResetReason.NONE) {
            throw new IllegalArgumentException("Valid history cannot carry reset reason " + resetReason);
        }
        if (!valid) age = 0;
    }

    public @Nullable DeferredResource currentResource() {
        return currentResources.isEmpty() ? null : currentResources.getFirst();
    }

    public @Nullable DeferredResource previousResource() {
        return previousResources.isEmpty() ? null : previousResources.getFirst();
    }
}
