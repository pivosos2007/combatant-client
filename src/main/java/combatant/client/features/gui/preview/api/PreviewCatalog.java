/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.preview.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Registry of presentation implementations. A preview supplies *state*, not another
 * drawing algorithm: the live and sample paths must call the same presenter.
 * Hosts provide their own typed render context (HUD, vanilla GUI, or 3D scene).
 */
public final class PreviewCatalog {
    public enum Surface { HUD, GUI_OVERLAY, MODEL_3D }
    public enum Availability { READY, REQUIRES_WORLD, UNAVAILABLE }
    /** Projection in which a sample invokes the production presenter. */
    public enum PreviewSpace { LOGICAL_HUD, FRAMEBUFFER, VANILLA_GUI }

    public record Id(Surface surface, String owner) {
        public Id {
            Objects.requireNonNull(surface, "surface");
            if (owner == null || owner.isBlank()) throw new IllegalArgumentException("owner");
        }
    }

    public interface Definition<S, R> {
        Id id();
        Class<R> rendererType();
        Availability availability(PreviewFrame frame);
        S capture(PreviewFrame frame);
        void present(R renderer, S state);
        default PreviewSpace previewSpace() { return PreviewSpace.LOGICAL_HUD; }
    }

    private static final Map<Id, Definition<?, ?>> ENTRIES = new LinkedHashMap<>();

    private PreviewCatalog() { }

    public static synchronized <S, R> void register(Definition<S, R> definition) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(definition.id(), "id");
        Objects.requireNonNull(definition.rendererType(), "rendererType");
        ENTRIES.put(definition.id(), definition);
    }

    public static synchronized void unregister(Id id) {
        ENTRIES.remove(id);
    }

    public static synchronized Map<Id, Definition<?, ?>> entries() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(ENTRIES));
    }

    public static synchronized Availability availability(Id id, PreviewFrame frame) {
        Definition<?, ?> definition = ENTRIES.get(id);
        return definition == null ? Availability.UNAVAILABLE : definition.availability(frame);
    }

    public static boolean render(Id id, PreviewFrame frame, Object renderContext) {
        Definition<?, ?> definition;
        synchronized (PreviewCatalog.class) { definition = ENTRIES.get(id); }
        return renderDefinition(definition, frame, renderContext);
    }

    private static <S, R> boolean renderDefinition(Definition<S, R> definition,
                                                   PreviewFrame frame, Object context) {
        if (definition == null || frame == null || context == null
                || definition.availability(frame) != Availability.READY
                || !definition.rendererType().isInstance(context)) return false;
        S state = definition.capture(frame);
        if (state == null) return false;
        definition.present(definition.rendererType().cast(context), state);
        return true;
    }

    /** Convenience for components whose live and sample state share a presenter. */
    public static <S, R> Definition<S, R> definition(Id id, Class<R> rendererType,
                                                      Function<PreviewFrame, Availability> availability,
                                                      Function<PreviewFrame, S> capture,
                                                      BiConsumer<R, S> present) {
        return definition(id, rendererType, availability, capture, present, PreviewSpace.LOGICAL_HUD);
    }

    public static <S, R> Definition<S, R> definition(Id id, Class<R> rendererType,
                                                      Function<PreviewFrame, Availability> availability,
                                                      Function<PreviewFrame, S> capture,
                                                      BiConsumer<R, S> present,
                                                      PreviewSpace previewSpace) {
        Objects.requireNonNull(previewSpace, "previewSpace");
        return new Definition<>() {
            @Override public Id id() { return id; }
            @Override public Class<R> rendererType() { return rendererType; }
            @Override public Availability availability(PreviewFrame frame) { return availability.apply(frame); }
            @Override public S capture(PreviewFrame frame) { return capture.apply(frame); }
            @Override public void present(R renderer, S state) { present.accept(renderer, state); }
            @Override public PreviewSpace previewSpace() { return previewSpace; }
        };
    }
}
