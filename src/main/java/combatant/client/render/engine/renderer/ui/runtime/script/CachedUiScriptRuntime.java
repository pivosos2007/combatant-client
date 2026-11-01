/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.script;

import combatant.client.render.engine.profiler.RenderCostProfiler;
import combatant.client.render.engine.renderer.ui.runtime.core.UiBounds;
import combatant.client.render.engine.renderer.ui.runtime.core.UiProps;
import combatant.client.render.engine.renderer.ui.runtime.core.UiRuntime;
import combatant.client.render.engine.renderer.ui.runtime.debug.UiRuntimeValidation;
import combatant.client.render.engine.text.TextRenderer;
import net.minecraft.util.Util;

import java.lang.reflect.Array;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Caches script output as persistent runtime trees.
 *
 * <p>The caller owns only the structural signature: it decides when script execution is required
 * because the declarative tree shape/template changed. Runtime patch invalidation is derived from
 * the actual patch map, and layout invalidation is derived from the actual layout inputs. This
 * prevents callers from maintaining parallel data/layout hashes that can silently drift away from
 * the values they are supposed to describe.</p>
 */
public final class CachedUiScriptRuntime {
    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final FrameStats FRAME_STATS = new FrameStats();

    private final LinkedHashMap<String, State> states = new LinkedHashMap<>();
    private final Reporter reporter;
    private UiScriptEngine scriptEngine = UiScriptEngineProvider.javet();
    private long frame;
    private long lastFrameMs;

    public CachedUiScriptRuntime() {
        this(Reporter.NOOP);
    }

    public CachedUiScriptRuntime(Reporter reporter) {
        this.reporter = reporter != null ? reporter : Reporter.NOOP;
    }

    private static String runtimeLabel(UiScriptModuleHandle handle, String key) {
        String id = handle != null && handle.getId() != null ? handle.getId().toString() : "unknown";
        String k = key != null && !key.isBlank() ? key : "default";
        return id + ":" + k;
    }

    public static void resetFrameStats() {
        FRAME_STATS.reset();
    }

    public static FrameStatsSnapshot frameStatsSnapshot() {
        return FRAME_STATS.snapshot();
    }

    /** Stable deep signature for maps used as script props or runtime patch payloads. */
    public static long signature(Map<String, ?> values) {
        return mixMap(FNV_OFFSET, values);
    }

    public static long mix(long hash, boolean value) {
        return mix(hash, value ? 1 : 0);
    }

    public static long mix(long hash, int value) {
        hash ^= value;
        return hash * FNV_PRIME;
    }

    public static long mix(long hash, long value) {
        hash = mix(hash, (int) value);
        return mix(hash, (int) (value >>> 32));
    }

    public static long mix(long hash, float value) {
        return mix(hash, Float.floatToIntBits(value));
    }

    public static long mix(long hash, String value) {
        return mix(hash, value != null ? value.hashCode() : 0);
    }

    /**
     * Deeply mixes common script payload shapes. Maps are order-independent; iterables and arrays
     * retain order because UI point/row sequences are semantically ordered.
     */
    public static long mixObject(long hash, Object value) {
        if (value == null) return mix(hash, 0);
        if (value instanceof Boolean b) return mix(hash, b);
        if (value instanceof Byte || value instanceof Short || value instanceof Integer) {
            return mix(hash, ((Number) value).intValue());
        }
        if (value instanceof Long l) return mix(hash, l.longValue());
        if (value instanceof Float f) return mix(hash, f.floatValue());
        if (value instanceof Double d) return mix(hash, Double.doubleToLongBits(d));
        if (value instanceof Number n) return mix(hash, Double.doubleToLongBits(n.doubleValue()));
        if (value instanceof CharSequence chars) return mix(hash, chars.toString());
        if (value instanceof Enum<?> e) return mix(hash, e.name());
        if (value instanceof Map<?, ?> map) return mixMapAny(hash, map);
        if (value instanceof Iterable<?> iterable) {
            long h = mix(hash, 0x49544552); // ITER
            for (Object element : iterable) h = mixObject(h, element);
            return h;
        }
        Class<?> type = value.getClass();
        if (type.isArray()) {
            long h = mix(hash, 0x41525259); // ARRY
            int length = Array.getLength(value);
            h = mix(h, length);
            for (int i = 0; i < length; i++) h = mixObject(h, Array.get(value, i));
            return h;
        }
        return mix(hash, value.toString());
    }

    private static long mixMap(long hash, Map<String, ?> values) {
        if (values == null || values.isEmpty()) return mix(hash, 0);
        long combined = 0L;
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            long e = FNV_OFFSET;
            e = mix(e, entry.getKey());
            e = mixObject(e, entry.getValue());
            combined ^= e;
        }
        return mix(mix(hash, values.size()), combined);
    }

    private static long mixMapAny(long hash, Map<?, ?> values) {
        if (values == null || values.isEmpty()) return mix(hash, 0);
        long combined = 0L;
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            long e = FNV_OFFSET;
            e = mixObject(e, entry.getKey());
            e = mixObject(e, entry.getValue());
            combined ^= e;
        }
        return mix(mix(hash, values.size()), combined);
    }

    public UiRuntime bake(UiScriptModuleHandle handle,
                          UiScriptModule module,
                          String key,
                          long treeSignature,
                          float scriptWidth,
                          float scriptHeight,
                          TextRenderer textRenderer,
                          float layoutX,
                          float layoutY,
                          float layoutW,
                          float layoutH,
                          PropsFactory propsFactory) {
        return bake(handle, module, key, treeSignature, scriptWidth, scriptHeight,
                textRenderer, layoutX, layoutY, layoutW, layoutH, propsFactory, null, null);
    }

    public UiRuntime bake(UiScriptModuleHandle handle,
                          UiScriptModule module,
                          String key,
                          long treeSignature,
                          float scriptWidth,
                          float scriptHeight,
                          TextRenderer textRenderer,
                          float layoutX,
                          float layoutY,
                          float layoutW,
                          float layoutH,
                          PropsFactory propsFactory,
                          RuntimePatchFactory patchFactory) {
        return bake(handle, module, key, treeSignature, scriptWidth, scriptHeight,
                textRenderer, layoutX, layoutY, layoutW, layoutH, propsFactory, patchFactory, null);
    }

    /**
     * Cached tree path for scripts whose initial props already contain current dynamic values.
     * Patches are applied only after the tree exists and the actual patch payload changes.
     */
    public UiRuntime bake(UiScriptModuleHandle handle,
                          UiScriptModule module,
                          String key,
                          long treeSignature,
                          float scriptWidth,
                          float scriptHeight,
                          TextRenderer textRenderer,
                          float layoutX,
                          float layoutY,
                          float layoutW,
                          float layoutH,
                          PropsFactory propsFactory,
                          RuntimePatchFactory patchFactory,
                          BoundsPatchFactory boundsPatchFactory) {
        FRAME_STATS.bakeCalls++;
        if (handle == null || module == null || textRenderer == null || propsFactory == null) return null;
        if (handle.isRuntimeBlocked()) {
            FRAME_STATS.blockedCalls++;
            return null;
        }

        State state = state(key);
        resetPatchCounters(state);
        TreeUpdate treeUpdate = ensureTree(
                state, handle, module, key, treeSignature, scriptWidth, scriptHeight, propsFactory
        );
        if (treeUpdate == TreeUpdate.FAILED) return null;
        boolean treeRebuilt = treeUpdate == TreeUpdate.REBUILT;

        if (patchFactory != null) {
            Map<String, ? extends Map<String, ?>> patches = safePatches(patchFactory);
            long nextPatchSignature = signaturePatchMap(patches);
            if (treeRebuilt) {
                // Full props were used to build this tree; remember the equivalent patch state
                // without replaying it immediately.
                state.patchSignature = nextPatchSignature;
                state.patchSignatureReady = true;
            } else if (!state.patchSignatureReady || state.patchSignature != nextPatchSignature) {
                if (applyPropPatches(state, handle, key, patches) > 0) {
                    // Without a property schema we cannot safely assume a changed prop is paint-only.
                    // Conservatively relayout only when an actual runtime node changed.
                    state.layoutReady = false;
                }
                state.patchSignature = nextPatchSignature;
                state.patchSignatureReady = true;
            }
        } else {
            state.patchSignatureReady = false;
        }

        ensureLayout(state, handle, key, textRenderer, layoutX, layoutY, layoutW, layoutH);
        applyBoundsPatches(state, handle, key, boundsPatchFactory);
        reporter.reportRenderState(handle, state.runtime, scriptWidth, scriptHeight);
        return state.runtime;
    }

    public UiRuntime updatePersistent(UiScriptModuleHandle handle,
                                      UiScriptModule module,
                                      String key,
                                      long treeSignature,
                                      float scriptWidth,
                                      float scriptHeight,
                                      TextRenderer textRenderer,
                                      float layoutX,
                                      float layoutY,
                                      float layoutW,
                                      float layoutH,
                                      PropsFactory templatePropsFactory,
                                      RuntimePatchFactory patchFactory) {
        return updatePersistent(handle, module, key, treeSignature, scriptWidth, scriptHeight,
                textRenderer, layoutX, layoutY, layoutW, layoutH, templatePropsFactory, patchFactory, null);
    }

    /**
     * Persistent-template path. Template props may intentionally contain placeholders, therefore
     * the first runtime patch is forced after a tree rebuild. Later patches are invalidated from
     * the patch payload itself rather than a caller-maintained data signature.
     */
    public UiRuntime updatePersistent(UiScriptModuleHandle handle,
                                      UiScriptModule module,
                                      String key,
                                      long treeSignature,
                                      float scriptWidth,
                                      float scriptHeight,
                                      TextRenderer textRenderer,
                                      float layoutX,
                                      float layoutY,
                                      float layoutW,
                                      float layoutH,
                                      PropsFactory templatePropsFactory,
                                      RuntimePatchFactory patchFactory,
                                      BoundsPatchFactory boundsPatchFactory) {
        if (handle == null || module == null || textRenderer == null || templatePropsFactory == null) return null;
        if (handle.isRuntimeBlocked()) {
            FRAME_STATS.blockedCalls++;
            return null;
        }

        State state = state(key);
        resetPatchCounters(state);
        boolean treeNeedsRebuild = !state.treeReady || state.treeSignature != treeSignature;
        if (treeNeedsRebuild) FRAME_STATS.bakeCalls++;
        TreeUpdate treeUpdate = ensureTree(
                state, handle, module, key, treeSignature, scriptWidth, scriptHeight, templatePropsFactory
        );
        if (treeUpdate == TreeUpdate.FAILED) return null;
        boolean treeRebuilt = treeUpdate == TreeUpdate.REBUILT;

        if (patchFactory != null) {
            Map<String, ? extends Map<String, ?>> patches = safePatches(patchFactory);
            long nextPatchSignature = signaturePatchMap(patches);
            if (treeRebuilt || !state.patchSignatureReady || state.patchSignature != nextPatchSignature) {
                if (applyPropPatches(state, handle, key, patches) > 0) {
                    // Persistent templates often patch text/content after tree creation. Layout must
                    // observe those values; the old caller-maintained layout hash could miss them.
                    state.layoutReady = false;
                }
                state.patchSignature = nextPatchSignature;
                state.patchSignatureReady = true;
            }
        } else {
            state.patchSignatureReady = false;
        }

        ensureLayout(state, handle, key, textRenderer, layoutX, layoutY, layoutW, layoutH);
        applyBoundsPatches(state, handle, key, boundsPatchFactory);
        reporter.reportRenderState(handle, state.runtime, scriptWidth, scriptHeight);
        return state.runtime;
    }

    private TreeUpdate ensureTree(State state,
                               UiScriptModuleHandle handle,
                               UiScriptModule module,
                               String key,
                               long treeSignature,
                               float scriptWidth,
                               float scriptHeight,
                               PropsFactory propsFactory) {
        if (state.treeReady && state.treeSignature == treeSignature) return TreeUpdate.UNCHANGED;

        long now = Util.getMillis();
        double delta = lastFrameMs > 0L ? Math.max(0.0, (now - lastFrameMs) / 1000.0) : 0.0;
        lastFrameMs = now;

        UiScriptRenderResult result;
        try (RenderCostProfiler.Scope ignored = RenderCostProfiler.uiRuntime("js:" + runtimeLabel(handle, key))) {
            result = scriptEngine.render(
                    module,
                    new UiScriptRenderContext(
                            frame++,
                            now / 1000.0,
                            delta,
                            scriptWidth,
                            scriptHeight,
                            new UiProps(propsFactory.create())
                    )
            );
        }
        if (!result.success()) {
            FRAME_STATS.failedRenders++;
            reporter.reportRuntimeError(handle, result.error());
            if (UiRuntimeValidation.enabled()) {
                UiScriptRuntimeError error = result.error();
                String phase = error != null && !error.phase().isBlank() ? error.phase() : "render";
                String message = error != null && !error.message().isBlank() ? error.message() : "unknown script error";
                Throwable cause = error != null ? error.cause() : null;
                throw new IllegalStateException(
                        "UI script " + phase + " failed for " + runtimeLabel(handle, key) + ": " + message,
                        cause
                );
            }
            return TreeUpdate.FAILED;
        }

        FRAME_STATS.jsRenders++;
        state.runtime.setTree(result.root());
        state.treeSignature = treeSignature;
        state.treeReady = true;
        state.layoutReady = false;
        state.patchSignatureReady = false;
        return TreeUpdate.REBUILT;
    }

    private static void resetPatchCounters(State state) {
        state.runtime.diagnostics().counters().setPatchNanos(0L);
        state.runtime.diagnostics().counters().setPropPatchCount(0);
        state.runtime.diagnostics().counters().setBoundsPatchCount(0);
    }

    private static Map<String, ? extends Map<String, ?>> safePatches(RuntimePatchFactory patchFactory) {
        Map<String, ? extends Map<String, ?>> patches = patchFactory.create();
        return patches != null ? patches : Map.of();
    }

    private static long signaturePatchMap(Map<String, ? extends Map<String, ?>> patches) {
        @SuppressWarnings("unchecked")
        Map<String, ?> values = (Map<String, ?>) patches;
        return signature(values);
    }

    private static int applyPropPatches(State state,
                                        UiScriptModuleHandle handle,
                                        String key,
                                        Map<String, ? extends Map<String, ?>> patches) {
        FRAME_STATS.propPatchPasses++;
        try (RenderCostProfiler.Scope ignored = RenderCostProfiler.uiRuntime("patchProps:" + runtimeLabel(handle, key))) {
            int patched = state.runtime.patchPropsByKey(patches);
            FRAME_STATS.propPatches += patched;
            return patched;
        }
    }

    private static void applyBoundsPatches(State state,
                                           UiScriptModuleHandle handle,
                                           String key,
                                           BoundsPatchFactory boundsPatchFactory) {
        if (boundsPatchFactory == null) return;
        Map<String, UiBounds> patches = boundsPatchFactory.create();
        if (patches == null || patches.isEmpty()) return;
        FRAME_STATS.boundsPatchPasses++;
        try (RenderCostProfiler.Scope ignored = RenderCostProfiler.uiRuntime("patchBounds:" + runtimeLabel(handle, key))) {
            FRAME_STATS.boundsPatches += state.runtime.patchBoundsByKey(patches);
        }
    }

    private static void ensureLayout(State state,
                                     UiScriptModuleHandle handle,
                                     String key,
                                     TextRenderer textRenderer,
                                     float layoutX,
                                     float layoutY,
                                     float layoutW,
                                     float layoutH) {
        if (state.layoutReady
                && state.layoutTextRenderer == textRenderer
                && Float.floatToIntBits(state.layoutX) == Float.floatToIntBits(layoutX)
                && Float.floatToIntBits(state.layoutY) == Float.floatToIntBits(layoutY)
                && Float.floatToIntBits(state.layoutW) == Float.floatToIntBits(layoutW)
                && Float.floatToIntBits(state.layoutH) == Float.floatToIntBits(layoutH)) {
            return;
        }

        FRAME_STATS.layoutPasses++;
        try (RenderCostProfiler.Scope ignored = RenderCostProfiler.uiRuntime("layout:" + runtimeLabel(handle, key))) {
            state.runtime.layout(textRenderer, layoutX, layoutY, layoutW, layoutH);
        }
        state.layoutTextRenderer = textRenderer;
        state.layoutX = layoutX;
        state.layoutY = layoutY;
        state.layoutW = layoutW;
        state.layoutH = layoutH;
        state.layoutReady = true;
    }

    public void reset() {
        try {
            scriptEngine.close();
        } catch (Exception ignored) {
        }
        scriptEngine = UiScriptEngineProvider.javet();
        states.clear();
        lastFrameMs = 0L;
    }

    private State state(String key) {
        String resolved = key != null && !key.isEmpty() ? key : "default";
        State existing = states.get(resolved);
        if (existing != null) return existing;
        State created = new State();
        states.put(resolved, created);
        return created;
    }

    public interface Reporter {
        Reporter NOOP = new Reporter() {
            @Override
            public void reportRuntimeError(UiScriptModuleHandle handle, UiScriptRuntimeError error) {
            }

            @Override
            public void reportRenderState(UiScriptModuleHandle handle, UiRuntime runtime, float width, float height) {
            }
        };

        void reportRuntimeError(UiScriptModuleHandle handle, UiScriptRuntimeError error);

        void reportRenderState(UiScriptModuleHandle handle, UiRuntime runtime, float width, float height);
    }

    public interface PropsFactory {
        Map<String, ?> create();
    }

    public interface RuntimePatchFactory {
        Map<String, ? extends Map<String, ?>> create();
    }

    public interface BoundsPatchFactory {
        Map<String, UiBounds> create();
    }

    public record FrameStatsSnapshot(int bakeCalls,
                                     int jsRenders,
                                     int propPatchPasses,
                                     int propPatches,
                                     int boundsPatchPasses,
                                     int boundsPatches,
                                     int layoutPasses,
                                     int blockedCalls,
                                     int failedRenders) {
    }

    private static final class FrameStats {
        private int bakeCalls;
        private int jsRenders;
        private int propPatchPasses;
        private int propPatches;
        private int boundsPatchPasses;
        private int boundsPatches;
        private int layoutPasses;
        private int blockedCalls;
        private int failedRenders;

        private void reset() {
            bakeCalls = 0;
            jsRenders = 0;
            propPatchPasses = 0;
            propPatches = 0;
            boundsPatchPasses = 0;
            boundsPatches = 0;
            layoutPasses = 0;
            blockedCalls = 0;
            failedRenders = 0;
        }

        private FrameStatsSnapshot snapshot() {
            return new FrameStatsSnapshot(
                    bakeCalls,
                    jsRenders,
                    propPatchPasses,
                    propPatches,
                    boundsPatchPasses,
                    boundsPatches,
                    layoutPasses,
                    blockedCalls,
                    failedRenders
            );
        }
    }


    private enum TreeUpdate {
        UNCHANGED,
        REBUILT,
        FAILED
    }

    private static final class State {
        private final UiRuntime runtime = new UiRuntime();
        private long treeSignature = Long.MIN_VALUE;
        private long patchSignature = Long.MIN_VALUE;
        private TextRenderer layoutTextRenderer;
        private float layoutX;
        private float layoutY;
        private float layoutW;
        private float layoutH;
        private boolean treeReady;
        private boolean patchSignatureReady;
        private boolean layoutReady;
    }
}
