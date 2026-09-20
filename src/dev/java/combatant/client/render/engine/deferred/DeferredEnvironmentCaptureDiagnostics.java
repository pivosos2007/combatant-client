/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.deferred;

/** Typed diagnostics for frame-local world/environment provider capture. */
public record DeferredEnvironmentCaptureDiagnostics(
        Producer weather,
        Producer celestial,
        Producer atmosphere,
        Producer baselineLight
) {
    public static DeferredEnvironmentCaptureDiagnostics unknown(long frameId) {
        Producer unavailable = Producer.unavailable("", "not_captured", frameId);
        return new DeferredEnvironmentCaptureDiagnostics(unavailable, unavailable, unavailable, unavailable);
    }

    public DeferredEnvironmentCaptureDiagnostics {
        weather = weather == null ? Producer.unavailable("", "unknown", Long.MIN_VALUE) : weather;
        celestial = celestial == null ? Producer.unavailable("", "unknown", Long.MIN_VALUE) : celestial;
        atmosphere = atmosphere == null ? Producer.unavailable("", "unknown", Long.MIN_VALUE) : atmosphere;
        baselineLight = baselineLight == null ? Producer.unavailable("minecraft:light_semantics", "unknown", Long.MIN_VALUE) : baselineLight;
    }

    public record Producer(
            DeferredResourceStatus status,
            String producerId,
            String reasonCode,
            String message,
            long frameId
    ) {
        public Producer {
            status = status == null ? DeferredResourceStatus.UNAVAILABLE : status;
            producerId = producerId == null ? "" : producerId;
            reasonCode = reasonCode == null ? "" : reasonCode;
            message = message == null ? "" : message;
        }

        public static Producer produced(String producerId, long frameId) {
            return new Producer(DeferredResourceStatus.PRODUCED, producerId, "", "", frameId);
        }

        public static Producer unavailable(String producerId, String reasonCode, long frameId) {
            return new Producer(DeferredResourceStatus.UNAVAILABLE, producerId, reasonCode, "", frameId);
        }

        public static Producer failed(String producerId, String reasonCode, Throwable failure, long frameId) {
            String message = failure == null ? "" : failure.getClass().getSimpleName()
                    + (failure.getMessage() == null ? "" : ": " + failure.getMessage());
            return new Producer(DeferredResourceStatus.FAILED, producerId, reasonCode, message, frameId);
        }
    }
}
