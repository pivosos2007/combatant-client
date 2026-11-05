/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.hud.nondraggable.debug;

import combatant.client.runtime.RuntimeGate;
import combatant.client.util.logging.DebugLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry for additional lines in Minecraft's F3/debug overlay.
 *
 * <p>Register a producer once during initialization:
 * {@code DebugHudLineRegistry.register("addon:example", out -> out.add("example: " + value));}
 * The producer runs only when F3 lines are extracted. It must be cheap and must not call GL
 * from an arbitrary thread. The registry never invokes producers during Panic, and it keeps
 * vanilla debug lines unchanged while Panic is active.</p>
 *
 * <p>Producer failures are isolated and cannot break the vanilla debug HUD. The registration
 * handle can be closed on addon shutdown to avoid leaking callbacks after unloading.</p>
 */
public final class DebugHudLineRegistry {
    private static final CopyOnWriteArrayList<Entry> ENTRIES = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<VanillaEntry> VANILLA_ENTRIES = new CopyOnWriteArrayList<>();

    static {
        registerVanilla("combatant:graphics_capabilities", GraphicsDebugHudProducer::insert);
        register("combatant:engine_telemetry", EngineDebugHudProducer::append);
    }

    private DebugHudLineRegistry() {
    }

    @FunctionalInterface
    public interface LineSink {
        void add(String line);
    }

    @FunctionalInterface
    public interface Producer {
        void append(LineSink lines);
    }

    /** May insert additional text after a vanilla line; original lines are never overwritten. */
    @FunctionalInterface
    public interface VanillaLineSink {
        void after(int vanillaLineIndex, String line);
    }

    @FunctionalInterface
    public interface VanillaLineProducer {
        void append(List<String> vanilla, VanillaLineSink lines);
    }

    public interface Registration extends AutoCloseable {
        @Override
        void close();
    }

    /** Register a producer with a stable namespace-qualified ID. Duplicate IDs are rejected. */
    public static synchronized Registration register(String id, Producer producer) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(producer, "producer");
        if (id.isBlank()) throw new IllegalArgumentException("Debug HUD producer ID must not be blank");
        for (Entry entry : ENTRIES) {
            if (entry.id.equals(id)) throw new IllegalArgumentException("Duplicate Debug HUD producer: " + id);
        }
        Entry entry = new Entry(id, producer);
        ENTRIES.add(entry);
        return () -> ENTRIES.remove(entry);
    }

    /** Add extra lines adjacent to a vanilla debug line; registration is independent of left-column producers. */
    public static synchronized Registration registerVanilla(String id, VanillaLineProducer producer) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(producer, "producer");
        if (id.isBlank()) throw new IllegalArgumentException("Debug HUD producer ID must not be blank");
        for (VanillaEntry entry : VANILLA_ENTRIES) {
            if (entry.id.equals(id)) throw new IllegalArgumentException("Duplicate vanilla debug producer: " + id);
        }
        VanillaEntry entry = new VanillaEntry(id, producer);
        VANILLA_ENTRIES.add(entry);
        return () -> VANILLA_ENTRIES.remove(entry);
    }

    /**
     * Right-column wrapper. The vanilla list stays byte-for-byte unchanged. All producer
     * executions and insertions are suppressed centrally while Panic is active.
     */
    public static List<String> decorateVanillaRight(List<String> vanilla) {
        // extractRenderState may request the right column with no F3/debug lines.
        // Do not let our producer turn an empty column into a visible overlay.
        if (vanilla == null || vanilla.isEmpty() || RuntimeGate.isPanic()
                || !RuntimeGate.canRunHud() || VANILLA_ENTRIES.isEmpty()) {
            return vanilla;
        }
        ArrayList<Insertion> insertions = new ArrayList<>(2);
        for (VanillaEntry entry : VANILLA_ENTRIES) {
            if (RuntimeGate.isPanic() || !RuntimeGate.canRunHud()) return vanilla;
            int start = insertions.size();
            try {
                entry.producer.append(vanilla, (lineIndex, line) -> {
                    if (line == null || line.isBlank()) return;
                    if (lineIndex < -1 || lineIndex >= vanilla.size()) {
                        throw new IndexOutOfBoundsException("Invalid vanilla line index: " + lineIndex);
                    }
                    insertions.add(new Insertion(lineIndex, line));
                });
            } catch (RuntimeException | LinkageError error) {
                insertions.subList(start, insertions.size()).clear();
                DebugLog.errorOnce("debughud.vanilla.producer." + entry.id,
                        "[DebugHud] vanilla line producer {} failed: {}", entry.id, error);
            }
        }
        if (insertions.isEmpty() || RuntimeGate.isPanic() || !RuntimeGate.canRunHud()) return vanilla;
        ArrayList<String> merged = new ArrayList<>(vanilla.size() + insertions.size());
        // Small producer lists: retain registration order, including equal insertion indices.
        for (Insertion addition : insertions) if (addition.lineIndex == -1) merged.add(addition.line);
        for (int i = 0; i < vanilla.size(); ++i) {
            merged.add(vanilla.get(i));
            for (Insertion addition : insertions) if (addition.lineIndex == i) merged.add(addition.line);
        }
        return merged;
    }

    /**
     * Shared Combatant wrapper. Does not allocate, run a callback, or alter vanilla lines in
     * Panic/disabled runtime. Called from the existing left-column DebugScreenOverlay hook.
     */
    public static List<String> decorate(List<String> vanilla) {
        if (vanilla == null || RuntimeGate.isPanic() || !RuntimeGate.canRunHud()) return vanilla;
        if (ENTRIES.isEmpty()) return vanilla;

        ArrayList<String> custom = new ArrayList<>(12);
        LineSink lines = line -> {
            if (line != null && !line.isBlank()) custom.add(line);
        };
        for (Entry entry : ENTRIES) {
            // Panic might have been toggled by an input event between callbacks.
            if (RuntimeGate.isPanic() || !RuntimeGate.canRunHud()) return vanilla;
            int start = custom.size();
            try {
                entry.producer.append(lines);
            } catch (RuntimeException | LinkageError error) {
                custom.subList(start, custom.size()).clear();
                DebugLog.errorOnce("debughud.producer." + entry.id,
                        "[DebugHud] producer {} failed: {}", entry.id, error);
            }
        }
        if (custom.isEmpty() || RuntimeGate.isPanic() || !RuntimeGate.canRunHud()) return vanilla;

        ArrayList<String> merged = new ArrayList<>(vanilla.size() + custom.size() + 1);
        merged.addAll(custom);
        merged.add("");
        merged.addAll(vanilla);
        return merged;
    }

    private record Entry(String id, Producer producer) {
    }

    private record VanillaEntry(String id, VanillaLineProducer producer) {
    }

    private record Insertion(int lineIndex, String line) {
    }
}
