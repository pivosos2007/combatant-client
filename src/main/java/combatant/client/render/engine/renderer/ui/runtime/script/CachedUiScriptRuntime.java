/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.script;

import java.util.LinkedHashMap;
import net.minecraft.util.Util;
import combatant.client.render.engine.profiler.RenderCostProfiler;
import combatant.client.render.engine.renderer.ui.runtime.core.UiBounds;
import combatant.client.render.engine.renderer.ui.runtime.core.UiProps;
import combatant.client.render.engine.renderer.ui.runtime.core.UiRuntime;
import combatant.client.render.engine.text.TextRenderer;

import java.util.Map;

/**
 * Caches script output as runtime trees.
 *
 * <p>JS execution, object conversion, style resolving, reconciliation, and layout
 * are skipped while the caller-provided signatures are unchanged. The render pass
 * still runs every frame because it writes the already baked tree to the current
 * draw context.</p>
 */
public final class CachedUiScriptRuntime {
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

    public static long signature(Map<String, ?> values) {
        if (values == null || values.isEmpty()) return 0xcbf29ce484222325L;
        long h = 0xcbf29ce484222325L;
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            long e = 0xcbf29ce484222325L;
            e = mix(e, entry.getKey());
            e = mixObject(e, entry.getValue());
            h ^= e;
            h *= 0x100000001b3L;
        }
        return h;
    }

    public static long mix(long hash, boolean value) {
        return mix(hash, value ? 1 : 0);
    }

    public static long mix(long hash, int value) {
        hash ^= value;
        return hash * 0x100000001b3L;
    }

    public static long mix(long hash, float value) {
        return mix(hash, Float.floatToIntBits(value));
    }

    public static long mix(long hash, String value) {
        return mix(hash, value != null ? value.hashCode() : 0);
    }

    public static long mixObject(long hash, Object value) {
        if (value instanceof Boolean b) return mix(hash, b);
        if (value instanceof Integer i) return mix(hash, i);
        if (value instanceof Float f) return mix(hash, f);
        if (value instanceof Number n) return mix(hash, Float.floatToIntBits(n.floatValue()));
        return mix(hash, value != null ? value.toString() : "");
    }

    public UiRuntime bake(UiScriptModuleHandle handle,
                          UiScriptModule module,
                          String key,
                          long treeSignature,
                          long layoutSignature,
                          float scriptWidth,
                          float scriptHeight,
                          TextRenderer textRenderer,
                          float layoutX,
                          float layoutY,
                          float layoutW,
                          float layoutH,
                          PropsFactory propsFactory) {
        return bake(handle, module, key, treeSignature, 0L, layoutSignature, scriptWidth, scriptHeight,
                textRenderer, layoutX, layoutY, layoutW, layoutH, propsFactory, null, null);
    }

    public UiRuntime bake(UiScriptModuleHandle handle,
                          UiScriptModule module,
                          String key,
                          long treeSignature,
                          long dataSignature,
                          long layoutSignature,
                          float scriptWidth,
                          float scriptHeight,
                          TextRenderer textRenderer,
                          float layoutX,
                          float layoutY,
                          float layoutW,
                          float layoutH,
                          PropsFactory propsFactory,
                          RuntimePatchFactory patchFactory) {
        return bake(handle, module, key, treeSignature, dataSignature, layoutSignature, scriptWidth, scriptHeight,
                textRenderer, layoutX, layoutY, layoutW, layoutH, propsFactory, patchFactory, null);
    }

    public UiRuntime bake(UiScriptModuleHandle handle,
                          UiScriptModule module,
                          String key,
                          long treeSignature,
                          long dataSignature,
                          long layoutSignature,
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
        state.runtime.diagnostics().counters().setPatchNanos(0L);
        state.runtime.diagnostics().counters().setPropPatchCount(0);
        state.runtime.diagnostics().counters().setBoundsPatchCount(0);
        if (!state.treeReady || state.treeSignature != treeSignature) {
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
                return null;
            }
            FRAME_STATS.jsRenders++;
            state.runtime.setTree(result.root());
            state.treeSignature = treeSignature;
            state.dataSignature = dataSignature;
            state.layoutReady = false;
            state.treeReady = true;
        } else if (state.dataSignature != dataSignature) {
            if (patchFactory != null) {
                FRAME_STATS.propPatchPasses++;
                try (RenderCostProfiler.Scope ignored = RenderCostProfiler.uiRuntime("patchProps:" + runtimeLabel(handle, key))) {
                    FRAME_STATS.propPatches += state.runtime.patchPropsByKey(patchFactory.create());
                }
            }
            state.dataSignature = dataSignature;
        }

        if (!state.layoutReady || state.layoutSignature != layoutSignature) {
            FRAME_STATS.layoutPasses++;
            try (RenderCostProfiler.Scope ignored = RenderCostProfiler.uiRuntime("layout:" + runtimeLabel(handle, key))) {
                state.runtime.layout(textRenderer, layoutX, layoutY, layoutW, layoutH);
            }
            state.layoutSignature = layoutSignature;
            state.layoutReady = true;
        }

        if (boundsPatchFactory != null) {
            FRAME_STATS.boundsPatchPasses++;
            try (RenderCostProfiler.Scope ignored = RenderCostProfiler.uiRuntime("patchBounds:" + runtimeLabel(handle, key))) {
                FRAME_STATS.boundsPatches += state.runtime.patchBoundsByKey(boundsPatchFactory.create());
            }
        }

        reporter.reportRenderState(handle, state.runtime, scriptWidth, scriptHeight);
        return state.runtime;
    }

    public UiRuntime updatePersistent(UiScriptModuleHandle handle,
                                      UiScriptModule module,
                                      String key,
                                      long treeSignature,
                                      long layoutSignature,
                                      float scriptWidth,
                                      float scriptHeight,
                                      TextRenderer textRenderer,
                                      float layoutX,
                                      float layoutY,
                                      float layoutW,
                                      float layoutH,
                                      PropsFactory templatePropsFactory,
                                      RuntimePatchFactory patchFactory) {
        return updatePersistent(handle, module, key, treeSignature, layoutSignature, scriptWidth, scriptHeight,
                textRenderer, layoutX, layoutY, layoutW, layoutH, templatePropsFactory, patchFactory, null);
    }

    public UiRuntime updatePersistent(UiScriptModuleHandle handle,
                                      UiScriptModule module,
                                      String key,
                                      long treeSignature,
                                      long layoutSignature,
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
        state.runtime.diagnostics().counters().setPatchNanos(0L);
        state.runtime.diagnostics().counters().setPropPatchCount(0);
        state.runtime.diagnostics().counters().setBoundsPatchCount(0);
        if (!state.treeReady || state.treeSignature != treeSignature) {
            FRAME_STATS.bakeCalls++;
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
                                new UiProps(templatePropsFactory.create())
                        )
                );
            }
            if (!result.success()) {
                FRAME_STATS.failedRenders++;
                reporter.reportRuntimeError(handle, result.error());
                return null;
            }
            FRAME_STATS.jsRenders++;
            state.runtime.setTree(result.root());
            state.treeSignature = treeSignature;
            state.layoutReady = false;
            state.treeReady = true;
        }

        if (!state.layoutReady || state.layoutSignature != layoutSignature) {
            FRAME_STATS.layoutPasses++;
            try (RenderCostProfiler.Scope ignored = RenderCostProfiler.uiRuntime("layout:" + runtimeLabel(handle, key))) {
                state.runtime.layout(textRenderer, layoutX, layoutY, layoutW, layoutH);
            }
            state.layoutSignature = layoutSignature;
            state.layoutReady = true;
        }

        if (patchFactory != null) {
            FRAME_STATS.propPatchPasses++;
            try (RenderCostProfiler.Scope ignored = RenderCostProfiler.uiRuntime("patchProps:" + runtimeLabel(handle, key))) {
                FRAME_STATS.propPatches += state.runtime.patchPropsByKey(patchFactory.create());
            }
        }

        if (boundsPatchFactory != null) {
            FRAME_STATS.boundsPatchPasses++;
            try (RenderCostProfiler.Scope ignored = RenderCostProfiler.uiRuntime("patchBounds:" + runtimeLabel(handle, key))) {
                FRAME_STATS.boundsPatches += state.runtime.patchBoundsByKey(boundsPatchFactory.create());
            }
        }

        reporter.reportRenderState(handle, state.runtime, scriptWidth, scriptHeight);
        return state.runtime;
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

    private static final class State {
        private final UiRuntime runtime = new UiRuntime();
        private long treeSignature = Long.MIN_VALUE;
        private long dataSignature = Long.MIN_VALUE;
        private long layoutSignature = Long.MIN_VALUE;
        private boolean treeReady;
        private boolean layoutReady;
    }
}
