/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.deferred;

/** Immutable semantic provenance for one deferred logical resource in one frame. */
public record DeferredResourceProvenance(
        DeferredResource resource,
        String producerPassId,
        long frameId,
        long generation,
        DeferredResourceStatus status,
        String reasonCode,
        String message,
        DeferredResource upstreamResource
) {
    public DeferredResourceProvenance {
        if (resource == null) throw new IllegalArgumentException("resource");
        producerPassId = producerPassId == null ? "" : producerPassId;
        status = status == null ? DeferredResourceStatus.UNAVAILABLE : status;
        reasonCode = reasonCode == null ? "" : reasonCode;
        message = message == null ? "" : message;
    }
}
