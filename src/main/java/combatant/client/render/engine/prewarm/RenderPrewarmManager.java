/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.prewarm;

import com.mojang.blaze3d.systems.RenderSystem;
import combatant.client.addon.ClickGuiSectionManager;
import combatant.client.events.Events;
import combatant.client.events.impl.RenderPrewarmCollectEvent;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.picker.PickerCatalogFactory;
import combatant.client.features.gui.clickgui.picker.PickerEntryData;
import combatant.client.features.gui.clickgui.settings.TextListSetting;
import combatant.client.features.gui.hud.script.HudScriptLayouts;
import combatant.client.render.engine.postprocess.MenuBackgroundRenderer;
import combatant.client.render.engine.renderer.ui.ItemBatchRenderer;
import combatant.client.render.engine.svg.SvgMsdfRegistry;
import combatant.client.render.engine.text.Fonts;
import combatant.client.runtime.RuntimeGate;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.resources.RenderResourceReadiness;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public enum RenderPrewarmManager {
    ;

    private static final TextListSetting.PickerMode[] ITEM_ATLAS_WARM_MODES = {
            TextListSetting.PickerMode.ITEMS,
            TextListSetting.PickerMode.BLOCKS
    };
    private static final int[] ITEM_ATLAS_WARM_LIMITS = {64, 32};
    private static final int ITEM_ATLAS_ITEMS_PER_TICK = Math.max(1,
            Integer.getInteger("combatant.render.prewarm.itemsPerTick", 1));

    private static boolean pickerCatalogPending;
    private static boolean gpuLifecycleReady;
    private static boolean gpuPrewarmPending;
    private static String pendingGpuReason = "deferred";
    private static boolean gpuAtlasPending;
    private static int atlasWarmModeIndex;
    private static int atlasWarmCursor;
    private static List<ItemStack> atlasWarmStacks = List.of();
    private static boolean atlasPopulationFailed;

    /**
     * Early entrypoint-safe warmup. This method deliberately performs only CPU-side work until
     * {@link #onClientStarted()} confirms that Blaze3D has created its final GpuDevice.
     *
     * <p>{@code RenderSystem.isOnRenderThread()} is not a device-readiness check: Fabric invokes
     * client entrypoints on the render thread while Minecraft is still constructing, before
     * {@code RenderSystem.getDevice()} is legal. GPU-backed fonts, SVG MSDF textures, fullscreen
     * meshes, menu textures and GuiItemAtlas allocation are therefore queued for the started
     * lifecycle instead of being touched here.</p>
     */
    public static void prewarm(String reason) {
        if (!RuntimeGate.canRunClientLogic()) return;

        long startedNs = System.nanoTime();

        // Finalize the ClassGraph result here, during initialization, instead of in ClickGui.init().
        ClickGuiSectionManager.prewarm();
        UiScriptStats uiScripts = prewarmUiScripts();

        // Registry-backed picker catalogs are not safe during Fabric's client entrypoint: 26.2
        // binds Holder component sets later in startup. Keep them pending until BOTH the client
        // lifecycle and the initial resource publication barrier are complete.
        pickerCatalogPending = true;

        resetDeferredItemAtlasWarmup();
        requestGpuPrewarm(reason);

        double elapsedMs = (System.nanoTime() - startedNs) / 1_000_000.0;
        DebugLog.renderThread("[Combatant][Prewarm] %s: cpu phase; ui-scripts=%d changed, %d cached, %d errors; gpu=%s; deferred picker+item warmup armed (%.2f ms)",
                reason, uiScripts.changed(), uiScripts.cached(), uiScripts.errors(),
                gpuLifecycleReady ? "ready" : "waiting-for-client-started", elapsedMs);
    }

    /**
     * Fabric CLIENT_STARTED is the first lifecycle point used by Combatant where Blaze3D's device
     * is guaranteed to exist. Materialize GPU-owned warmup resources before entering normal use.
     */
    public static void onClientStarted() {
        gpuLifecycleReady = true;
        if (!gpuPrewarmPending) {
            gpuPrewarmPending = true;
            pendingGpuReason = "client started";
        }
        runReadyWarmup();
    }

    /**
     * Called immediately after the shader/resource reload publishes its final resource generation.
     * Startup ordering differs between loaders/mixins, so this forms the second half of a two-way
     * barrier with {@link #onClientStarted()}: whichever side becomes ready last starts warmup.
     */
    public static void onRenderResourcesReady() {
        runReadyWarmup();
    }

    private static void requestGpuPrewarm(String reason) {
        gpuPrewarmPending = true;
        if (reason != null && !reason.isBlank()) {
            pendingGpuReason = reason;
        }
        if (gpuLifecycleReady) {
            runReadyWarmup();
        }
    }

    private static boolean warmupBarrierReady() {
        return gpuLifecycleReady
                && RenderResourceReadiness.isReady()
                && RenderSystem.isOnRenderThread();
    }

    private static void runReadyWarmup() {
        if (!warmupBarrierReady()) return;

        if (pickerCatalogPending) {
            if (PickerCatalogFactory.prewarmAsync()) {
                pickerCatalogPending = false;
            }
        }

        runPendingGpuPrewarm();
    }

    private static void runPendingGpuPrewarm() {
        if (!warmupBarrierReady() || !gpuPrewarmPending) return;

        String reason = pendingGpuReason;
        gpuPrewarmPending = false;
        long startedNs = System.nanoTime();

        RenderPrewarmCollectEvent event = new RenderPrewarmCollectEvent(reason);
        Events.BUS.post(event);

        int fonts = event.isEmpty() ? 0 : Fonts.prewarmRenderers(event.fonts());
        int svg = 0;
        for (Identifier id : event.svgMsdfIcons()) {
            try {
                if (SvgMsdfRegistry.preload(id)) svg++;
            } catch (RuntimeException failure) {
                DebugLog.warnOnce("prewarm-svg:" + id, "Failed to prewarm SVG MSDF: %s", id, failure);
            }
        }

        try {
            ClickGuiRenderer.prewarmUiObjects();
        } catch (RuntimeException failure) {
            DebugLog.warnOnce("prewarm-clickgui-ui", "Failed to prewarm ClickGUI UI objects", failure);
        }

        int menuTextures = 0;
        Minecraft mc = Minecraft.getInstance();
        try {
            menuTextures = MenuBackgroundRenderer.prewarm(mc);
        } catch (RuntimeException failure) {
            // Optional warmup must never turn client startup into a hard failure.
            DebugLog.warnOnce("prewarm-menu-background", "Failed to prewarm menu background resources", failure);
        }

        try {
            gpuAtlasPending = !ItemBatchRenderer.prewarmUiItemAtlas();
        } catch (RuntimeException failure) {
            gpuAtlasPending = true;
            DebugLog.warnOnce("prewarm-ui-item-atlas", "Failed to prewarm UI item atlas", failure);
        }

        double elapsedMs = (System.nanoTime() - startedNs) / 1_000_000.0;
        DebugLog.renderThread("[Combatant][Prewarm] %s: gpu phase; fonts=%d/%d svg-msdf=%d/%d menu-textures=%d/%d (%.2f ms)",
                reason, fonts, event.fonts().size(), svg, event.svgMsdfIcons().size(), menuTextures, 4, elapsedMs);
    }

    /**
     * Called from END_CLIENT_TICK. GPU work remains on the render thread, but only one tiny slice
     * is materialized per tick so a large item catalog cannot turn into another startup hitch.
     */
    public static void pumpDeferred() {
        if (!RuntimeGate.canRunClientLogic() || !RenderSystem.isOnRenderThread()) return;

        runReadyWarmup();
        if (!warmupBarrierReady()) return;

        if (gpuAtlasPending) {
            try {
                if (ItemBatchRenderer.prewarmUiItemAtlas()) {
                    gpuAtlasPending = false;
                } else {
                    return;
                }
            } catch (RuntimeException failure) {
                gpuAtlasPending = false;
                atlasPopulationFailed = true;
                DebugLog.warnOnce("prewarm-ui-item-atlas-deferred", "Deferred UI item atlas prewarm failed", failure);
                return;
            }
        }

        if (atlasPopulationFailed || atlasWarmModeIndex >= ITEM_ATLAS_WARM_MODES.length) return;

        if (atlasWarmStacks.isEmpty()) {
            TextListSetting.PickerMode mode = ITEM_ATLAS_WARM_MODES[atlasWarmModeIndex];
            List<PickerEntryData> entries = PickerCatalogFactory.completedEntries(mode);
            if (entries == null) return;

            int limit = Math.min(ITEM_ATLAS_WARM_LIMITS[atlasWarmModeIndex], entries.size());
            ArrayList<ItemStack> stacks = new ArrayList<>(limit);
            for (int i = 0; i < entries.size() && stacks.size() < limit; i++) {
                ItemStack stack = entries.get(i).stack();
                if (stack != null && !stack.isEmpty()) stacks.add(stack);
            }
            atlasWarmStacks = List.copyOf(stacks);
            atlasWarmCursor = 0;
            if (atlasWarmStacks.isEmpty()) {
                advanceAtlasWarmMode();
                return;
            }
        }

        try {
            int consumed = ItemBatchRenderer.prewarmUiItems(
                    atlasWarmStacks,
                    atlasWarmCursor,
                    ITEM_ATLAS_ITEMS_PER_TICK
            );
            if (consumed <= 0) return;
            atlasWarmCursor += consumed;
            if (atlasWarmCursor >= atlasWarmStacks.size()) {
                advanceAtlasWarmMode();
            }
        } catch (RuntimeException failure) {
            atlasPopulationFailed = true;
            DebugLog.warnOnce("prewarm-ui-item-models", "Incremental UI item model prewarm failed", failure);
        }
    }

    /** Resource/language reload invalidates labels and model-backed atlas contents. */
    public static void invalidateDeferred() {
        PickerCatalogFactory.invalidateAsyncCaches();
        pickerCatalogPending = true;
        ClickGuiRenderer.invalidateFontBindings();
        if (gpuLifecycleReady && RenderSystem.isOnRenderThread()) {
            ItemBatchRenderer.onResourceReload();
        }
        resetDeferredItemAtlasWarmup();
    }

    private static void resetDeferredItemAtlasWarmup() {
        gpuAtlasPending = true;
        atlasWarmModeIndex = 0;
        atlasWarmCursor = 0;
        atlasWarmStacks = List.of();
        atlasPopulationFailed = false;
    }

    private static void advanceAtlasWarmMode() {
        atlasWarmModeIndex++;
        atlasWarmCursor = 0;
        atlasWarmStacks = List.of();
    }

    private static UiScriptStats prewarmUiScripts() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getResourceManager() == null) {
            return new UiScriptStats(0, 0, 0);
        }
        var stats = HudScriptLayouts.prewarmRegistered(mc.getResourceManager());
        return new UiScriptStats(stats.changed(), stats.unchanged(), stats.errors());
    }

    private record UiScriptStats(int changed, int cached, int errors) {
    }
}
