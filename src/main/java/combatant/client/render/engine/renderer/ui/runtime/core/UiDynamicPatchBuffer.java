/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer.ui.runtime.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Render-thread-owned, reusable typed patch program for a retained UI tree.
 *
 * <p>String-based writes are automatically compiled by their position in the
 * previous frame's instruction stream. A stable stream therefore resolves its
 * keys once, not once per frame. A changing stream (conditional nodes) falls
 * back to the key index and retrains only the affected instruction positions.
 * Explicit integer handles can bypass this lookup altogether.</p>
 *
 * <p>Only slots written during this frame are visited on apply; a registered
 * but inactive slot cannot cause a patch. Node bindings are cached until the
 * tree revision changes. All methods are render-thread-only.</p>
 */
public final class UiDynamicPatchBuffer implements UiPatchWriter {
    private static final int OBJECT = 0;
    private static final int FLOAT = 1;
    private static final int BOUNDS = 2;
    private static final int INT = 3;
    private static final int BOOLEAN = 4;
    private static final int MAX_SLOTS = 2048;
    private static final int MAX_CACHED_OPERATIONS = 8192;

    private final Map<String, Map<String, Slot>> propSlots = new HashMap<>();
    private final Map<String, Slot> boundsSlots = new HashMap<>();
    private final List<Slot> slots = new ArrayList<>(32);
    private Slot[] instructionCache = new Slot[32];
    private Slot[] activeProps = new Slot[32];
    private Slot[] activeBounds = new Slot[32];
    private int instructionCursor;
    private int activePropCount;
    private int activeBoundsCount;
    private int epoch;
    private int propChanges;
    private int boundsChanges;
    private int indexedHits;
    private int keyLookups;
    private boolean requiresLayout;

    /** Resets frame-local cursors but retains the compiled program and slot storage. */
    public void beginFrame() {
        if (epoch == Integer.MAX_VALUE) {
            for (Slot slot : slots) slot.touched = 0;
            epoch = 0;
        }
        epoch++;
        instructionCursor = 0;
        activePropCount = 0;
        activeBoundsCount = 0;
        indexedHits = 0;
        keyLookups = 0;
        propChanges = 0;
        boundsChanges = 0;
        requiresLayout = false;
    }

    /**
     * Release bound runtime nodes after the owning script has been reset.
     * Keeps compiled key positions and integer handles for the next tree.
     */
    public void invalidateBindings() {
        for (Slot slot : slots) {
            slot.nodes = List.of();
            slot.boundRuntime = null;
            slot.boundRevision = Long.MIN_VALUE;
            slot.applied = false;
            slot.lastObject = null;
            // A stale ItemStack/object value must not keep a removed UI alive.
            if (slot.kind == OBJECT) slot.value = null;
        }
    }

    /** Compiles a stable prop key to a handle; allocate during UI initialization, not per frame. */
    public int propertyHandle(String nodeKey, String property) {
        if (invalid(nodeKey) || invalid(property)) throw new IllegalArgumentException("Invalid UI prop handle");
        return propertySlot(nodeKey, property).index;
    }

    /** Compiles a stable bounds key to a handle, independent of the current tree. */
    public int boundsHandle(String nodeKey) {
        if (invalid(nodeKey)) throw new IllegalArgumentException("Invalid UI bounds handle");
        return boundsSlot(nodeKey).index;
    }

    /** Precompiled object instruction; no key/index lookup on writes. */
    public void put(int handle, Object value) {
        Slot slot = handle(handle, false);
        touch(slot);
        slot.kind = OBJECT;
        slot.value = value;
    }

    public void putFloat(int handle, float value) {
        Slot slot = handle(handle, false);
        touch(slot);
        slot.kind = FLOAT;
        slot.number = value;
        slot.value = null;
    }

    public void putInt(int handle, int value) {
        Slot slot = handle(handle, false);
        touch(slot);
        slot.kind = INT;
        slot.integer = value;
        slot.value = null;
    }

    public void putBoolean(int handle, boolean value) {
        Slot slot = handle(handle, false);
        touch(slot);
        slot.kind = BOOLEAN;
        slot.bool = value;
        slot.value = null;
    }

    public void putBounds(int handle, float x, float y, float width, float height) {
        Slot slot = handle(handle, true);
        touch(slot);
        setBounds(slot, x, y, width, height);
    }

    @Override
    public void put(String nodeKey, String property, Object value) {
        if (invalid(nodeKey) || invalid(property)) return;
        Slot slot = compiledOrIndexed(nodeKey, property, false);
        touch(slot);
        slot.kind = OBJECT;
        slot.value = value;
    }

    @Override
    public void putFloat(String nodeKey, String property, float value) {
        if (invalid(nodeKey) || invalid(property)) return;
        Slot slot = compiledOrIndexed(nodeKey, property, false);
        touch(slot);
        slot.kind = FLOAT;
        slot.number = value;
        slot.value = null;
    }

    @Override
    public void putInt(String nodeKey, String property, int value) {
        if (invalid(nodeKey) || invalid(property)) return;
        Slot slot = compiledOrIndexed(nodeKey, property, false);
        touch(slot);
        slot.kind = INT;
        slot.integer = value;
        slot.value = null;
    }

    @Override
    public void putBoolean(String nodeKey, String property, boolean value) {
        if (invalid(nodeKey) || invalid(property)) return;
        Slot slot = compiledOrIndexed(nodeKey, property, false);
        touch(slot);
        slot.kind = BOOLEAN;
        slot.bool = value;
        slot.value = null;
    }

    @Override
    public void putBounds(String nodeKey, float x, float y, float width, float height) {
        if (invalid(nodeKey)) return;
        Slot slot = compiledOrIndexed(nodeKey, null, true);
        touch(slot);
        setBounds(slot, x, y, width, height);
    }

    private static void setBounds(Slot slot, float x, float y, float width, float height) {
        slot.kind = BOUNDS;
        slot.x = x;
        slot.y = y;
        // Match the legacy CompactHudStatModel bounds writer.
        slot.width = Math.max(0.0f, width);
        slot.height = Math.max(0.0f, height);
    }

    public int propChanges() { return propChanges; }
    public int boundsChanges() { return boundsChanges; }
    public boolean requiresLayout() { return requiresLayout; }
    public int slotCount() { return slots.size(); }
    /** Number of ordinal instruction-cache hits during the latest frame. */
    public int indexedHits() { return indexedHits; }
    /** Number of named-slot lookups during the latest frame (excluding handle declarations). */
    public int keyLookups() { return keyLookups; }
    public int activeSlotCount() { return activePropCount + activeBoundsCount; }

    void applyProps(UiRuntime runtime) {
        propChanges = 0;
        requiresLayout = false;
        for (int i = 0; i < activePropCount; i++) {
            Slot slot = activeProps[i];
            boolean rebound = slot.bind(runtime, false);
            if (!rebound && slot.valueUnchanged(runtime.legacyPropRevision())) continue;
            for (UiNode node : slot.nodes) {
                boolean changed = switch (slot.kind) {
                    case FLOAT -> node.patchFloatProp(slot.property, slot.number);
                    case INT -> node.patchIntProp(slot.property, slot.integer);
                    case BOOLEAN -> node.patchBooleanProp(slot.property, slot.bool);
                    default -> node.patchProp(slot.property, slot.value);
                };
                if (changed) {
                    propChanges++;
                    if (!requiresLayout && UiPropLayoutDependency.affectsLayout(node.type(), slot.property)) {
                        requiresLayout = true;
                    }
                }
            }
            slot.rememberApplied(runtime.legacyPropRevision());
        }
    }

    void applyBounds(UiRuntime runtime) {
        boundsChanges = 0;
        for (int i = 0; i < activeBoundsCount; i++) {
            Slot slot = activeBounds[i];
            slot.bind(runtime, true);
            // Layout/scroll can overwrite bounds even when input values match;
            // compare live bounds without allocating unless an update is required.
            for (UiNode node : slot.nodes) {
                UiBounds current = node.bounds();
                if (Float.compare(current.x(), slot.x) == 0
                        && Float.compare(current.y(), slot.y) == 0
                        && Float.compare(current.width(), slot.width) == 0
                        && Float.compare(current.height(), slot.height) == 0) continue;
                node.setBounds(new UiBounds(slot.x, slot.y, slot.width, slot.height));
                boundsChanges++;
            }
        }
    }

    private Slot handle(int index, boolean bounds) {
        if (index < 0 || index >= slots.size()) throw new IllegalArgumentException("Invalid UI slot handle: " + index);
        Slot slot = slots.get(index);
        if ((slot.property == null) != bounds) {
            throw new IllegalArgumentException("UI slot handle " + index + " has wrong instruction kind");
        }
        return slot;
    }

    private Slot compiledOrIndexed(String nodeKey, String property, boolean bounds) {
        int ordinal = instructionCursor++;
        if (ordinal < instructionCache.length) {
            Slot cached = instructionCache[ordinal];
            if (cached != null && cached.nodeKey.equals(nodeKey) && Objects.equals(cached.property, property)) {
                indexedHits++;
                return cached;
            }
        }
        keyLookups++;
        Slot slot = bounds ? boundsSlot(nodeKey) : propertySlot(nodeKey, property);
        if (ordinal < MAX_CACHED_OPERATIONS) {
            if (ordinal >= instructionCache.length) {
                instructionCache = Arrays.copyOf(instructionCache,
                        Math.min(MAX_CACHED_OPERATIONS, Math.max(ordinal + 1, instructionCache.length * 2)));
            }
            instructionCache[ordinal] = slot;
        }
        return slot;
    }

    private void touch(Slot slot) {
        if (slot.touched == epoch) return;
        slot.touched = epoch;
        if (slot.property == null) {
            if (activeBoundsCount == activeBounds.length) activeBounds = Arrays.copyOf(activeBounds, activeBounds.length * 2);
            activeBounds[activeBoundsCount++] = slot;
        } else {
            if (activePropCount == activeProps.length) activeProps = Arrays.copyOf(activeProps, activeProps.length * 2);
            activeProps[activePropCount++] = slot;
        }
    }

    private Slot propertySlot(String nodeKey, String property) {
        Map<String, Slot> props = propSlots.get(nodeKey);
        if (props == null) {
            props = new HashMap<>(4);
            propSlots.put(nodeKey, props);
        }
        Slot slot = props.get(property);
        if (slot == null) {
            slot = create(nodeKey, property);
            props.put(property, slot);
        }
        return slot;
    }

    private Slot boundsSlot(String nodeKey) {
        Slot slot = boundsSlots.get(nodeKey);
        if (slot == null) {
            slot = create(nodeKey, null);
            boundsSlots.put(nodeKey, slot);
        }
        return slot;
    }

    private Slot create(String nodeKey, String property) {
        if (slots.size() >= MAX_SLOTS) {
            throw new IllegalStateException("Exceeded maximum dynamic UI slots (" + MAX_SLOTS + ")");
        }
        Slot slot = new Slot(slots.size(), nodeKey, property);
        slots.add(slot);
        return slot;
    }

    private static boolean invalid(String text) {
        return text == null || text.isBlank();
    }

    private static final class Slot {
        final int index;
        final String nodeKey;
        final String property;
        List<UiNode> nodes = List.of();
        UiRuntime boundRuntime;
        long boundRevision = Long.MIN_VALUE;
        long appliedLegacyRevision = Long.MIN_VALUE;
        int touched;
        int kind;
        int appliedKind = -1;
        boolean applied;
        Object value;
        Object lastObject;
        float number;
        float lastNumber;
        int integer;
        int lastInteger;
        boolean bool;
        boolean lastBool;
        float x, y, width, height;

        Slot(int index, String nodeKey, String property) {
            this.index = index;
            this.nodeKey = nodeKey;
            this.property = property;
        }

        boolean bind(UiRuntime runtime, boolean bounds) {
            long revision = runtime.treeRevision();
            if (runtime == boundRuntime && revision == boundRevision) return false;
            nodes = runtime.nodesForDynamicPatch(nodeKey, bounds ? "bounds" : "props");
            boundRevision = revision;
            boundRuntime = runtime;
            applied = false;
            return true;
        }

        boolean valueUnchanged(long legacyRevision) {
            if (!applied || appliedKind != kind || appliedLegacyRevision != legacyRevision) return false;
            return switch (kind) {
                case FLOAT -> Float.floatToIntBits(lastNumber) == Float.floatToIntBits(number);
                case INT -> lastInteger == integer;
                case BOOLEAN -> lastBool == bool;
                default -> stableValue(value) && Objects.equals(lastObject, value);
            };
        }

        // Never assume a mutable object remained identical because its identity did.
        private static boolean stableValue(Object value) {
            return value == null || value instanceof String || value instanceof Boolean
                    || value instanceof Number || value instanceof Character || value instanceof Enum<?>;
        }

        void rememberApplied(long legacyRevision) {
            applied = true;
            appliedKind = kind;
            appliedLegacyRevision = legacyRevision;
            if (kind == FLOAT) {
                lastNumber = number;
                lastObject = null;
            } else if (kind == INT) {
                lastInteger = integer;
                lastObject = null;
            } else if (kind == BOOLEAN) {
                lastBool = bool;
                lastObject = null;
            } else {
                lastObject = value;
            }
        }
    }
}
