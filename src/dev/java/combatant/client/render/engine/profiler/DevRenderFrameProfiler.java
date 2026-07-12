package combatant.client.render.engine.profiler;

import combatant.client.render.engine.core.RenderPhase;
import combatant.client.render.engine.rhi.RhiStatsSnapshot;
import combatant.client.render.engine.rhi.uniform.UniformAllocatorStatsSnapshot;

/**
 * Single profiler bridge for the RHI/FrameGraph lifecycle.
 * Existing DevRenderProfiler2D/3D trees are preserved, but phase ownership moves here.
 */
public enum DevRenderFrameProfiler {
    ;
    private static boolean worldFrameOpen;
    private static boolean uiFrameOpen;

    public static void beginFrame(long frameId) {
        // The sampling profiler is still driven by 2D/3D beginFrame calls below.
        DevRenderCostProfiler.beginFrame(frameId);
    }

    public static PhaseScope phase(RenderPhase phase, String label) {
        RenderPhase actualPhase = phase == null ? RenderPhase.NONE : phase;
        String actualLabel = label == null || label.isBlank() ? defaultLabel(actualPhase) : label;

        DevProfilerPhase.Scope cpu = DevProfilerPhase.scope(actualLabel);
        DevRenderCostProfiler.Scope cost = DevRenderCostProfiler.phase(actualLabel);
        DevTracyGpuProfiler.Scope gpu = DevTracyGpuProfiler.beginZone(actualLabel);
        AutoCloseable tree = beginTreeSection(actualPhase, actualLabel);
        return new PhaseScope(cpu, cost, gpu, tree);
    }

    public static void endFrame(RhiStatsSnapshot rhi, UniformAllocatorStatsSnapshot uniforms) {
        DevRenderCostProfiler.endFrame();
        if (worldFrameOpen) {
            DevRenderProfiler3D.endFrame();
            worldFrameOpen = false;
        }
        if (uiFrameOpen) {
            DevRenderProfiler2D.endFrame();
            uiFrameOpen = false;
        }
        DevGlSyncTracker.emitTracyFrame();
        DevTracyProfiler.markFrame();
    }

    private static AutoCloseable beginTreeSection(RenderPhase phase, String label) {
        if (isWorld(phase)) {
            if (!worldFrameOpen) {
                DevRenderProfiler3D.beginFrame("world");
                worldFrameOpen = true;
            }
            return DevRenderProfiler3D.section(stripDomain(label));
        }
        if (isUi(phase)) {
            if (!uiFrameOpen) {
                DevRenderProfiler2D.beginFrame("ui");
                uiFrameOpen = true;
            }
            return DevRenderProfiler2D.section(stripDomain(label));
        }
        return () -> {
        };
    }

    private static boolean isWorld(RenderPhase phase) {
        return phase == RenderPhase.WORLD_BEFORE_ENTITIES
                || phase == RenderPhase.WORLD_AFTER_ENTITIES
                || phase == RenderPhase.WORLD_BEFORE_TRANSLUCENT
                || phase == RenderPhase.WORLD_AFTER_TRANSLUCENT
                || phase == RenderPhase.WORLD_POST_PRE_HAND
                || phase == RenderPhase.WORLD_POST_HAND;
    }

    private static boolean isUi(RenderPhase phase) {
        return phase == RenderPhase.HUD_CAPTURE
                || phase == RenderPhase.HUD_MAIN
                || phase == RenderPhase.HUD_EFFECTS
                || phase == RenderPhase.SCREEN_TOP
                || phase == RenderPhase.SCREEN;
    }

    private static String defaultLabel(RenderPhase phase) {
        return switch (phase) {
            case WORLD_BEFORE_ENTITIES -> "3d:world_before_entities";
            case WORLD_AFTER_ENTITIES -> "3d:world_after_entities";
            case WORLD_BEFORE_TRANSLUCENT -> "3d:world_before_translucent";
            case WORLD_AFTER_TRANSLUCENT -> "3d:world_after_translucent";
            case WORLD_POST_PRE_HAND -> "3d:post_pre_hand";
            case WORLD_POST_HAND -> "3d:post_hand";
            case HUD_CAPTURE -> "2d:hud_capture";
            case HUD_MAIN -> "2d:hud_main";
            case HUD_EFFECTS -> "2d:hud_effects";
            case SCREEN_TOP -> "2d:screen_top";
            case SCREEN -> "2d:screen";
            case LEGACY_SHADOW_TERRAIN -> "3d:shadow_terrain_legacy";
            default -> "render:none";
        };
    }

    private static String stripDomain(String label) {
        int idx = label.indexOf(':');
        if (idx >= 0 && idx + 1 < label.length()) {
            return label.substring(idx + 1);
        }
        return label;
    }

    public static final class PhaseScope implements AutoCloseable {
        private final DevProfilerPhase.Scope cpu;
        private final DevRenderCostProfiler.Scope cost;
        private final DevTracyGpuProfiler.Scope gpu;
        private final AutoCloseable tree;
        private boolean closed;

        private PhaseScope(DevProfilerPhase.Scope cpu, DevRenderCostProfiler.Scope cost, DevTracyGpuProfiler.Scope gpu, AutoCloseable tree) {
            this.cpu = cpu;
            this.cost = cost;
            this.gpu = gpu;
            this.tree = tree;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            try {
                if (tree != null) tree.close();
            } catch (Exception ignored) {
            }
            if (gpu != null) gpu.close();
            if (cost != null) cost.close();
            if (cpu != null) cpu.close();
        }
    }
}
