/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.script;

import combatant.client.render.engine.profiler.RenderCostProfiler;
import combatant.client.render.engine.renderer.ui.runtime.core.UiBounds;
import combatant.client.render.engine.renderer.ui.runtime.core.UiDynamicPatchBuffer;
import combatant.client.render.engine.renderer.ui.runtime.core.UiProps;
import combatant.client.render.engine.renderer.ui.runtime.core.UiRuntime;
import combatant.client.render.engine.renderer.ui.runtime.debug.UiRuntimeValidation;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.util.logging.DebugLog;
import net.minecraft.util.Util;

import java.lang.reflect.Array;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

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
    private static final AtomicInteger ASYNC_REJECTED = new AtomicInteger();

    private final LinkedHashMap<String, State> states = new LinkedHashMap<>();
    private final Reporter reporter;
    private final int workerLane = UiScriptWorkerPool.selectLane();
    // Owned exclusively by workerLane; never call the engine from the render thread.
    private UiScriptEngine workerEngine;
    private long workerGeneration = Long.MIN_VALUE;
    private volatile long generation;
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
        ASYNC_REJECTED.set(0);
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
            State previous = states.get(key != null && !key.isEmpty() ? key : "default");
            if (previous == null || !previous.treeReady) return null;
            ensureLayout(previous, handle, key, textRenderer, layoutX, layoutY, layoutW, layoutH);
            return previous.runtime;
        }

        State state = state(key);
        resetPatchCounters(state);
        TreeUpdate treeUpdate = ensureTree(
                state, handle, module, key, treeSignature, scriptWidth, scriptHeight, propsFactory
        );
        if (treeUpdate == TreeUpdate.FAILED || !state.treeReady) return null;

        if (patchFactory != null) {
            // UiProps.withPatch() already performs exact per-node change detection.
            // Do not deeply hash the entire patch object graph before doing the same work.
            // Always replay on a newly received async tree: its snapshot may be older than
            // the current frame's dynamic values.
            applyPropPatches(state, handle, key, safePatches(patchFactory));
            if (state.runtime.lastPropPatchRequiresLayout()) state.layoutReady = false;
        }

        ensureLayout(state, handle, key, textRenderer, layoutX, layoutY, layoutW, layoutH);
        applyBoundsPatches(state, handle, key, boundsPatchFactory);
        reporter.reportRenderState(handle, state.runtime, scriptWidth, scriptHeight);
        return state.runtime;
    }

    /**
     * Dynamic-patch bake for HUDs whose initial tree contains live data and
     * whose per-frame props are updated without rebuilding nested patch maps.
     * The typed writer runs on the owning render thread, before layout; async
     * JavaScript only receives the safe snapshot from ensureTree().
     */
    public UiRuntime bakeDynamic(UiScriptModuleHandle handle,
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
                                 DynamicPatchFactory dynamicPatchFactory) {
        return updatePersistentInternal(handle, module, key, treeSignature,
                scriptWidth, scriptHeight, textRenderer, layoutX, layoutY,
                layoutW, layoutH, propsFactory, null, null, dynamicPatchFactory);
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
     * Preallocated, typed-patch variant for frequently updated retained UI.
     * The writer operates on the render thread. It must not retain Minecraft
     * objects in worker snapshots; it only updates the local render tree.
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
                                      DynamicPatchFactory dynamicPatchFactory) {
        return updatePersistentInternal(handle, module, key, treeSignature, scriptWidth, scriptHeight,
                textRenderer, layoutX, layoutY, layoutW, layoutH, templatePropsFactory,
                null, null, dynamicPatchFactory);
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
        return updatePersistentInternal(handle, module, key, treeSignature, scriptWidth, scriptHeight,
                textRenderer, layoutX, layoutY, layoutW, layoutH, templatePropsFactory,
                patchFactory, boundsPatchFactory, null);
    }

    private UiRuntime updatePersistentInternal(UiScriptModuleHandle handle,
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
                                               BoundsPatchFactory boundsPatchFactory,
                                               DynamicPatchFactory dynamicPatchFactory) {
        if (handle == null || module == null || textRenderer == null || templatePropsFactory == null) return null;
        if (handle.isRuntimeBlocked()) {
            FRAME_STATS.blockedCalls++;
            State previous = states.get(key != null && !key.isEmpty() ? key : "default");
            if (previous == null || !previous.treeReady) return null;
            ensureLayout(previous, handle, key, textRenderer, layoutX, layoutY, layoutW, layoutH);
            return previous.runtime;
        }

        State state = state(key);
        resetPatchCounters(state);
        boolean treeNeedsRebuild = !state.treeReady || state.treeSignature != treeSignature;
        if (treeNeedsRebuild) FRAME_STATS.bakeCalls++;
        TreeUpdate treeUpdate = ensureTree(
                state, handle, module, key, treeSignature, scriptWidth, scriptHeight, templatePropsFactory
        );
        if (treeUpdate == TreeUpdate.FAILED || !state.treeReady) return null;

        if (dynamicPatchFactory != null) {
            UiDynamicPatchBuffer patches = state.dynamicPatches;
            patches.beginFrame();
            dynamicPatchFactory.write(patches);
            FRAME_STATS.propPatchPasses++;
            FRAME_STATS.propPatches += state.runtime.applyDynamicProps(patches);
            if (state.runtime.lastPropPatchRequiresLayout()) state.layoutReady = false;
        } else if (patchFactory != null) {
            applyPropPatches(state, handle, key, safePatches(patchFactory));
            if (state.runtime.lastPropPatchRequiresLayout()) state.layoutReady = false;
        }

        ensureLayout(state, handle, key, textRenderer, layoutX, layoutY, layoutW, layoutH);
        if (dynamicPatchFactory != null) {
            FRAME_STATS.boundsPatchPasses++;
            FRAME_STATS.boundsPatches += state.runtime.applyDynamicBounds(state.dynamicPatches);
        } else {
            applyBoundsPatches(state, handle, key, boundsPatchFactory);
        }
        reporter.reportRenderState(handle, state.runtime, scriptWidth, scriptHeight);
        return state.runtime;
    }

    /**
     * Poll-only render-thread path. A new tree is evaluated off-thread, then committed here.
     * The previous successful runtime remains renderable while a new result is pending.
     */
    private TreeUpdate ensureTree(State state,
                                  UiScriptModuleHandle handle,
                                  UiScriptModule module,
                                  String key,
                                  long treeSignature,
                                  float scriptWidth,
                                  float scriptHeight,
                                  PropsFactory propsFactory) {
        Completed completed = state.completed.getAndSet(null);
        if (completed != null && completed.generation == generation
                && completed.signature == state.requestedSignature
                && completed.module == state.requestedModule) {
            FRAME_STATS.queueWaitNanos += completed.queueWaitNanos;
            FRAME_STATS.asyncRunNanos += completed.runNanos;
            FRAME_STATS.asyncResults++;
            UiScriptRenderResult result = completed.result;
            if (!result.success()) {
                FRAME_STATS.failedRenders++;
                reportTreeError(state, handle, result.error());
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
                // Don't retry a broken module every frame; keep the last successful tree.
                state.failedSignature = completed.signature;
                state.failedModule = completed.module;
            } else {
                try {
                    state.runtime.setTree(result.root());
                    String reconcileError = state.runtime.diagnostics().counters().lastError();
                    if (reconcileError != null && !reconcileError.isBlank()) {
                        throw new IllegalStateException(reconcileError);
                    }
                    state.treeSignature = completed.signature;
                    state.treeModule = completed.module;
                    state.treeReady = true;
                    state.layoutReady = false;
                    FRAME_STATS.jsRenders++;
                    return TreeUpdate.REBUILT;
                } catch (RuntimeException error) {
                    FRAME_STATS.failedRenders++;
                    reportTreeError(state, handle, new UiScriptRuntimeError(
                            module.getId(), "reconcile", error.getMessage(), error));
                    if (UiRuntimeValidation.enabled()) throw error;
                    state.failedSignature = completed.signature;
                    state.failedModule = completed.module;
                }
            }
        }
        if (state.treeReady && state.treeSignature == treeSignature && state.treeModule == module) {
            return TreeUpdate.UNCHANGED;
        }
        if (!state.requestMatches(generation, module, treeSignature)
                && !(state.failedSignature == treeSignature && state.failedModule == module)) {
            // Factories read live Minecraft state. Snapshot on the owning thread, before dispatch.
            try {
                long snapshotStarted = System.nanoTime();
                Map<String, Object> props = UiScriptSnapshot.capture(propsFactory.create());
                FRAME_STATS.snapshotNanos += System.nanoTime() - snapshotStarted;
                long now = Util.getMillis();
                double delta = lastFrameMs > 0L ? Math.max(0.0, (now - lastFrameMs) / 1000.0) : 0.0;
                lastFrameMs = now;
                RenderRequest request = new RenderRequest(
                        generation, module, treeSignature,
                        new UiScriptRenderContext(frame++, now / 1000.0, delta,
                                scriptWidth, scriptHeight, UiProps.fromDetachedSnapshot(props)), System.nanoTime());
                state.requestedSignature = treeSignature;
                state.requestedModule = module;
                state.requestedGeneration = generation;
                if (state.mailbox.getAndSet(request) != null) FRAME_STATS.asyncCoalesced++;
                schedule(state);
            } catch (RuntimeException error) {
                FRAME_STATS.failedRenders++;
                UiScriptRuntimeError runtimeError = new UiScriptRuntimeError(
                        module.getId(), "snapshot", error.getMessage(), error);
                reportTreeError(state, handle, runtimeError);
                if (UiRuntimeValidation.enabled()) throw error;
                state.failedSignature = treeSignature;
                state.failedModule = module;
            }
        }
        if (state.mailbox.get() != null) schedule(state); // retry after queue saturation
        // An old tree is still useful until the new one has been successfully committed.
        return state.treeReady ? TreeUpdate.PENDING : TreeUpdate.FAILED;
    }

    private void reportTreeError(State state, UiScriptModuleHandle handle, UiScriptRuntimeError error) {
        if (!state.treeReady) {
            // No usable tree: preserve the existing failure-isolation behaviour.
            reporter.reportRuntimeError(handle, error);
            return;
        }
        // A failed speculative rebuild must not disable an already-working HUD.
        // Log it once and retain its working tree until the script changes again.
        String id = runtimeLabel(handle, "async");
        String phase = error != null ? error.phase() : "render";
        String message = error != null ? error.message() : "unknown";
        DebugLog.errorOnce("ui-async-last-good:" + id + ":" + phase + ":" + message,
                "[UI Scripts] %s %s failed, retained the last successful UI: %s",
                id, phase, message, error != null ? error.cause() : null);
    }

    private void schedule(State state) {
        if (!state.scheduled.compareAndSet(false, true)) return;
        if (!UiScriptWorkerPool.submit(workerLane, () -> evaluate(state))) {
            ASYNC_REJECTED.incrementAndGet();
            state.scheduled.set(false);
            // Keep the latest mailbox request; later frames attempt dispatch again.
        }
    }

    private void evaluate(State state) {
        try {
            RenderRequest request = state.mailbox.getAndSet(null);
            if (request == null || request.generation != generation) return;
            long start = System.nanoTime();
            long queueWaitNanos = Math.max(0L, start - request.enqueuedNanos);
            try {
                if (workerEngine == null || workerGeneration != request.generation) {
                    closeWorkerEngine();
                    workerEngine = UiScriptEngineProvider.javet();
                    UiScriptWorkerPool.trackEngine(workerLane, workerEngine);
                    workerGeneration = request.generation;
                }
                UiScriptRenderResult result;
                try (RenderCostProfiler.Scope ignored = RenderCostProfiler.uiRuntime("js:" + request.module.getId())) {
                    result = workerEngine.render(request.module, request.context);
                }
                if (request.generation == generation) {
                    state.completed.set(new Completed(request.generation, request.module,
                            request.signature, result, queueWaitNanos, System.nanoTime() - start));
                }
            } catch (Throwable error) {
                if (request.generation == generation) {
                    state.completed.set(new Completed(request.generation, request.module, request.signature,
                            UiScriptRenderResult.failed(new UiScriptRuntimeError(
                                    request.module.getId(), "render", error.getMessage(), error)),
                            queueWaitNanos, System.nanoTime() - start));
                }
            }
        } finally {
            state.scheduled.set(false);
            if (state.mailbox.get() != null) schedule(state);
        }
    }

    private void closeWorkerEngine() {
        if (workerEngine == null) return;
        try {
            UiScriptWorkerPool.untrackEngine(workerLane, workerEngine);
            workerEngine.close();
        } catch (Throwable ignored) {
        } finally {
            workerEngine = null;
            workerGeneration = Long.MIN_VALUE;
        }
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
        generation++; // invalidates in-flight results and queued work without waiting
        states.clear();
        lastFrameMs = 0L;
        // V8 must be closed on the same lane that owns it. A future request also
        // observes generation changes and closes the old engine before execution.
        UiScriptWorkerPool.submit(workerLane, () -> {
            if (workerGeneration != generation) closeWorkerEngine();
        });
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

    @FunctionalInterface
    public interface DynamicPatchFactory {
        void write(UiDynamicPatchBuffer patches);
    }

    public record FrameStatsSnapshot(int bakeCalls,
                                     int jsRenders,
                                     int propPatchPasses,
                                     int propPatches,
                                     int boundsPatchPasses,
                                     int boundsPatches,
                                     int layoutPasses,
                                     int blockedCalls,
                                     int failedRenders,
                                     int asyncResults,
                                     int asyncCoalesced,
                                     int asyncRejected,
                                     long snapshotNanos,
                                     long queueWaitNanos,
                                     long asyncRunNanos) {
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
        private int asyncResults;
        private int asyncCoalesced;
        private long snapshotNanos;
        private long queueWaitNanos;
        private long asyncRunNanos;

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
            asyncResults = 0;
            asyncCoalesced = 0;
            snapshotNanos = 0L;
            queueWaitNanos = 0L;
            asyncRunNanos = 0L;
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
                    failedRenders,
                    asyncResults,
                    asyncCoalesced,
                    ASYNC_REJECTED.get(),
                    snapshotNanos,
                    queueWaitNanos,
                    asyncRunNanos
            );
        }
    }


    private enum TreeUpdate {
        UNCHANGED,
        REBUILT,
        PENDING,
        FAILED
    }

    private record RenderRequest(long generation, UiScriptModule module,
                                 long signature, UiScriptRenderContext context, long enqueuedNanos) {
    }

    private record Completed(long generation, UiScriptModule module,
                             long signature, UiScriptRenderResult result,
                             long queueWaitNanos, long runNanos) {
    }

    private static final class State {
        private final UiRuntime runtime = new UiRuntime();
        private final UiDynamicPatchBuffer dynamicPatches = new UiDynamicPatchBuffer();
        private final AtomicReference<RenderRequest> mailbox = new AtomicReference<>();
        private final AtomicReference<Completed> completed = new AtomicReference<>();
        private final AtomicBoolean scheduled = new AtomicBoolean();
        private UiScriptModule requestedModule;
        private UiScriptModule treeModule;
        private UiScriptModule failedModule;
        private long requestedSignature = Long.MIN_VALUE;
        private long requestedGeneration = Long.MIN_VALUE;
        private long failedSignature = Long.MIN_VALUE;
        private long treeSignature = Long.MIN_VALUE;
        private TextRenderer layoutTextRenderer;
        private float layoutX;
        private float layoutY;
        private float layoutW;
        private float layoutH;
        private boolean treeReady;
        private boolean layoutReady;

        private boolean requestMatches(long generation, UiScriptModule module, long signature) {
            return requestedGeneration == generation && requestedModule == module
                    && requestedSignature == signature;
        }
    }
}
