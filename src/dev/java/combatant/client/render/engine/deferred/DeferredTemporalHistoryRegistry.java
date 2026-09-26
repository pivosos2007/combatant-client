/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.textures.GpuTextureView;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Central lifecycle/ownership registry for persistent temporal histories.
 *
 * <p>The registry does not impose one filtering algorithm. It owns the common semantics: producer,
 * current/previous resources, format contract version, epoch, age, reset reason and the exact frame
 * that successfully wrote the persistent snapshot.</p>
 */
public final class DeferredTemporalHistoryRegistry {
    private final EnumMap<DeferredTemporalHistoryId, Definition> definitions =
            new EnumMap<>(DeferredTemporalHistoryId.class);
    private final EnumMap<DeferredTemporalHistoryId, State> states =
            new EnumMap<>(DeferredTemporalHistoryId.class);

    private long frameId = Long.MIN_VALUE;
    private long epoch = Long.MIN_VALUE;
    private DeferredHistoryDescriptor primaryHistory =
            new DeferredHistoryDescriptor(0L, Long.MIN_VALUE, Long.MIN_VALUE, 0, false,
                    DeferredHistoryResetReason.FIRST_FRAME);
    private @Nullable DeferredResourceBindings resources;
    private DeferredRuntimeConfig.Snapshot settings = DeferredRuntimeConfig.current();

    public DeferredTemporalHistoryRegistry() {
        registerDefaults();
    }

    public void beginFrame(long frameId,
                           DeferredHistoryDescriptor primaryHistory,
                           DeferredResourceBindings resources,
                           DeferredRuntimeConfig.Snapshot settings) {
        if (primaryHistory == null) throw new IllegalArgumentException("primaryHistory");
        if (resources == null) throw new IllegalArgumentException("resources");
        this.frameId = frameId;
        this.primaryHistory = primaryHistory;
        this.resources = resources;
        this.settings = settings == null ? DeferredRuntimeConfig.current() : settings;

        if (epoch != primaryHistory.epoch()) {
            epoch = primaryHistory.epoch();
            invalidateAll(primaryHistory.resetReason() == DeferredHistoryResetReason.NONE
                    ? DeferredHistoryResetReason.PERSISTENT_RESOURCE_RECREATION
                    : primaryHistory.resetReason());
        }
    }

    public void reset(DeferredHistoryResetReason reason) {
        frameId = Long.MIN_VALUE;
        epoch = Long.MIN_VALUE;
        primaryHistory = new DeferredHistoryDescriptor(0L, Long.MIN_VALUE, Long.MIN_VALUE, 0,
                false, reason == null ? DeferredHistoryResetReason.RENDERER_RESET : reason);
        resources = null;
        invalidateAll(primaryHistory.resetReason());
    }

    public void invalidate(DeferredTemporalHistoryId id, DeferredHistoryResetReason reason) {
        State state = states.computeIfAbsent(id, ignored -> new State());
        state.invalidate(reason == null ? DeferredHistoryResetReason.RENDERER_RESET : reason);
    }

    public void invalidateAll(DeferredHistoryResetReason reason) {
        DeferredHistoryResetReason resolved = reason == null
                ? DeferredHistoryResetReason.RENDERER_RESET : reason;
        for (DeferredTemporalHistoryId id : definitions.keySet()) {
            states.computeIfAbsent(id, ignored -> new State()).invalidate(resolved);
        }
    }

    /** Called only after a history-producing pass completed successfully. */
    public void commit(DeferredTemporalHistoryId id) {
        Definition definition = definitions.get(id);
        if (definition == null || frameId == Long.MIN_VALUE) return;
        State state = states.computeIfAbsent(id, ignored -> new State());
        if (state.lastWrittenFrame == frameId) return;

        boolean consecutive = state.lastWrittenFrame == frameId - 1L && state.epoch == epoch;
        state.age = consecutive ? Math.max(1, state.age + 1) : 1;
        state.lastWrittenFrame = frameId;
        state.epoch = epoch;
        state.lastResetReason = DeferredHistoryResetReason.NONE;
    }

    public DeferredTemporalHistoryDescriptor descriptor(DeferredTemporalHistoryId id) {
        Definition definition = definitions.get(id);
        if (definition == null) {
            return unavailable(id);
        }
        State state = states.computeIfAbsent(id, ignored -> new State());
        DeferredResourceBindings bound = resources;

        boolean primaryValid = primaryHistory.valid();
        boolean consecutiveWrite = state.lastWrittenFrame == frameId - 1L && state.epoch == epoch;
        boolean resourcesValid = bound != null && bindAndValidatePrevious(definition, bound);
        boolean valid = primaryValid && consecutiveWrite && resourcesValid;

        DeferredHistoryResetReason reason = DeferredHistoryResetReason.NONE;
        if (!valid) {
            if (!primaryValid) {
                reason = primaryHistory.resetReason();
            } else if (!consecutiveWrite) {
                reason = state.lastResetReason != DeferredHistoryResetReason.NONE
                        ? state.lastResetReason
                        : state.lastWrittenFrame == Long.MIN_VALUE
                                ? DeferredHistoryResetReason.FIRST_FRAME
                                : DeferredHistoryResetReason.SUBSYSTEM_REENABLED;
            } else if (!resourcesValid) {
                reason = DeferredHistoryResetReason.PERSISTENT_RESOURCE_RECREATION;
            }
            state.lastResetReason = reason;
        }

        Shape shape = shape(definition, bound);
        return new DeferredTemporalHistoryDescriptor(
                id,
                definition.currentResources,
                definition.previousResources,
                definition.confidenceResource,
                shape.width,
                shape.height,
                shape.format,
                definition.contractVersion,
                epoch,
                valid ? state.age : 0,
                valid,
                valid ? DeferredHistoryResetReason.NONE : reason,
                definition.producer,
                definition.producerId,
                definition.storageMode,
                state.lastWrittenFrame
        );
    }

    public Map<DeferredTemporalHistoryId, DeferredTemporalHistoryDescriptor> snapshot() {
        EnumMap<DeferredTemporalHistoryId, DeferredTemporalHistoryDescriptor> result =
                new EnumMap<>(DeferredTemporalHistoryId.class);
        for (DeferredTemporalHistoryId id : definitions.keySet()) {
            result.put(id, descriptor(id));
        }
        return Map.copyOf(result);
    }

    public void register(Definition definition) {
        if (definition == null) throw new IllegalArgumentException("definition");
        definitions.put(definition.id, definition);
        states.putIfAbsent(definition.id, new State());
    }

    private boolean bindAndValidatePrevious(Definition definition, DeferredResourceBindings bound) {
        if (definition.previousResources.isEmpty()) return false;
        for (DeferredResource resource : definition.previousResources) {
            if (!bound.isBound(resource)) bound.bindExisting(resource, settings);
            if (!bound.isValid(resource)) return false;
            boolean physicalPresent = bound.texture(resource) != null
                    || bound.buffer(resource) != null
                    || bound.storageVolume(resource) != null;
            if (!physicalPresent) return false;
        }
        return true;
    }

    private static Shape shape(Definition definition, @Nullable DeferredResourceBindings bound) {
        DeferredResource representative = definition.previousResources.isEmpty()
                ? null : definition.previousResources.getFirst();
        if (representative == null) representative = definition.currentResources.isEmpty()
                ? null : definition.currentResources.getFirst();
        if (representative == null) return new Shape(0, 0, "unknown");

        GpuTextureView view = bound == null ? null : bound.texture(representative);
        if (view != null) {
            int width = view.getWidth(0);
            int height = view.getHeight(0);
            DeferredTextureSpec spec = representative.textureSpec();
            String format = spec == null || spec.format() == null ? "external" : spec.format().toString();
            return new Shape(width, height, format);
        }
        if (bound != null && bound.buffer(representative) != null) return new Shape(0, 0, "buffer");
        if (bound != null && bound.storageVolume(representative) != null) return new Shape(0, 0, "volume");
        return new Shape(0, 0, "unknown");
    }

    private DeferredTemporalHistoryDescriptor unavailable(DeferredTemporalHistoryId id) {
        return new DeferredTemporalHistoryDescriptor(
                id, List.of(), List.of(), null, 0, 0, "unregistered", 1,
                epoch, 0, false, DeferredHistoryResetReason.FIRST_FRAME,
                DeferredHistoryProducer.EXTENSION, id.name().toLowerCase(),
                DeferredHistoryStorageMode.STORE_AFTER_CONSUME, Long.MIN_VALUE
        );
    }

    private void registerDefaults() {
        register(new Definition(
                DeferredTemporalHistoryId.SCENE,
                List.of(DeferredResource.SCENE_COLOR, DeferredResource.FINAL_RESOLVED_DEPTH),
                List.of(DeferredResource.HISTORY_COLOR, DeferredResource.HISTORY_DEPTH),
                null, 1, DeferredHistoryProducer.SCENE_CAPTURE, "world.history.capture",
                DeferredHistoryStorageMode.STORE_AFTER_CONSUME
        ));
        register(new Definition(
                DeferredTemporalHistoryId.EXPOSURE,
                List.of(DeferredResource.EXPOSURE),
                List.of(DeferredResource.EXPOSURE),
                null, 1, DeferredHistoryProducer.EXPOSURE, "world.post.exposure",
                DeferredHistoryStorageMode.IN_PLACE
        ));
        register(new Definition(
                DeferredTemporalHistoryId.TAA,
                List.of(DeferredResource.TAA_RESOLVED_COLOR, DeferredResource.TAA_CONFIDENCE,
                        DeferredResource.TAA_LOCK),
                List.of(DeferredResource.HISTORY_TAA_COLOR, DeferredResource.HISTORY_TAA_CONFIDENCE,
                        DeferredResource.HISTORY_TAA_LOCK),
                DeferredResource.HISTORY_TAA_CONFIDENCE,
                1, DeferredHistoryProducer.TAA, "world.temporal.taa.history",
                DeferredHistoryStorageMode.STORE_AFTER_CONSUME
        ));
    }

    /**
     * One lifecycle definition. current/previous resource lists may contain subsystem metadata
     * (confidence, lock/age textures, moments, etc.) so those resources advance with the same
     * epoch/commit instead of inventing a parallel history lifecycle.
     */
    public record Definition(
            DeferredTemporalHistoryId id,
            List<DeferredResource> currentResources,
            List<DeferredResource> previousResources,
            @Nullable DeferredResource confidenceResource,
            int contractVersion,
            DeferredHistoryProducer producer,
            String producerId,
            DeferredHistoryStorageMode storageMode
    ) {
        public Definition {
            if (id == null) throw new IllegalArgumentException("id");
            currentResources = currentResources == null ? List.of() : List.copyOf(currentResources);
            previousResources = previousResources == null ? List.of() : List.copyOf(previousResources);
            contractVersion = Math.max(1, contractVersion);
            producer = producer == null ? DeferredHistoryProducer.EXTENSION : producer;
            producerId = producerId == null || producerId.isBlank() ? id.name().toLowerCase() : producerId;
            storageMode = storageMode == null ? DeferredHistoryStorageMode.STORE_AFTER_CONSUME : storageMode;
        }
    }

    private static final class State {
        private long epoch = Long.MIN_VALUE;
        private long lastWrittenFrame = Long.MIN_VALUE;
        private int age;
        private DeferredHistoryResetReason lastResetReason = DeferredHistoryResetReason.FIRST_FRAME;

        private void invalidate(DeferredHistoryResetReason reason) {
            epoch = Long.MIN_VALUE;
            lastWrittenFrame = Long.MIN_VALUE;
            age = 0;
            lastResetReason = reason;
        }
    }

    private record Shape(int width, int height, String format) { }
}
