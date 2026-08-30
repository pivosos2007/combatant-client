/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.script;

import combatant.client.util.resources.asset.AssetAutoLoader;
import combatant.client.util.resources.asset.AssetLoad;
import combatant.client.util.resources.asset.AssetLoadPhase;
import combatant.client.util.resources.asset.UiScriptAsset;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.glfw.GLFW;
import combatant.client.features.gui.hud.draggable.impl.HudNotifier;
import combatant.client.render.engine.renderer.ui.runtime.core.UiRuntime;
import combatant.client.render.engine.renderer.ui.runtime.script.*;
import combatant.client.util.logging.DebugLog;
import java.util.LinkedHashMap;

public enum HudScriptLayouts {
    ;
    private static final UiScriptModuleRegistry REGISTRY = new UiScriptModuleRegistry();
    private static final LinkedHashMap<String, String> LAST_RENDER_LOG = new LinkedHashMap<>();
    private static final CachedUiScriptRuntime.Reporter RUNTIME_REPORTER = new CachedUiScriptRuntime.Reporter() {
        @Override
        public void reportRuntimeError(UiScriptModuleHandle handle, UiScriptRuntimeError error) {
            HudScriptLayouts.reportRuntimeError(handle, error);
        }

        @Override
        public void reportRenderState(UiScriptModuleHandle handle, UiRuntime runtime, float width, float height) {
            HudScriptLayouts.reportRenderState(handle, runtime, width, height);
        }
    };
    private static boolean reloadComboDown;

    public static UiScriptModuleHandle handle(String id) {
        UiScriptModuleHandle handle = REGISTRY.handle(UiScriptModuleId.of(id));
        DebugLog.info("[UI Scripts] handle requested id=%s", handle.getId());
        return handle;
    }

    public static UiScriptModuleHandle handle(Class<?> owner) {
        if (owner == null) throw new IllegalArgumentException("UI script owner cannot be null");
        UiScriptAsset asset = owner.getAnnotation(UiScriptAsset.class);
        if (asset == null || asset.value().isBlank()) {
            throw new IllegalArgumentException(owner.getName() + " must be annotated with @UiScriptAsset");
        }
        return handle(asset.value());
    }

    @AssetLoad(value = AssetLoadPhase.INITIALIZE, order = 200)
    public static void registerDiscoveredAssets() {
        for (String id : AssetAutoLoader.uiScriptIds()) {
            REGISTRY.handle(UiScriptModuleId.of(id));
        }
    }

    public static CachedUiScriptRuntime.Reporter runtimeReporter() {
        return RUNTIME_REPORTER;
    }

    public static UiScriptModuleRegistry.ReloadStats prewarmRegistered(ResourceManager manager) {
        if (manager == null) {
            return new UiScriptModuleRegistry.ReloadStats(0, 0, 0, "", null);
        }
        UiScriptModuleRegistry.ReloadStats stats = REGISTRY.ensureLoaded(manager);
        if (stats.errors() > 0) {
            DebugLog.error("[UI Scripts] prewarm failed: " + stats.firstError(), stats.firstCause());
        }
        return stats;
    }

    public static void reportLoadError(UiScriptModuleHandle handle) {
        if (handle == null) return;
        if (!handle.recordLoadError(handle.lastError(), handle.lastErrorCause())) return;
        report(handle.getId().toString(), handle.lastError(), handle.lastErrorCause(), "");
    }

    public static void reportRuntimeError(UiScriptModuleHandle handle, UiScriptRuntimeError error) {
        if (handle == null) return;
        if (!handle.recordRuntimeError(error)) return;
        String stack = error != null ? error.stackTrace() : "";
        report(handle.getId().toString(), handle.lastError(), handle.lastErrorCause(), stack);
    }

    public static void reportRenderState(UiScriptModuleHandle handle, UiRuntime runtime, float width, float height) {
        if (handle == null || runtime == null) return;
        int nodes = runtime.diagnostics().counters().nodeCount();
        String error = runtime.diagnostics().counters().lastError();
        String key = handle.getId().toString();
        String signature = error == null || error.isBlank() ? "ok" : "error|" + error;
        String prev = LAST_RENDER_LOG.put(key, signature);
        if (signature.equals(prev)) return;
        DebugLog.info("[UI Scripts] render state id=%s nodes=%d size=%sx%s error=%s",
                key,
                nodes,
                width,
                height,
                error == null || error.isBlank() ? "none" : error);
    }

    public static void pollReloadCombo(Minecraft mc) {
        if (mc == null || mc.getWindow() == null) return;
        long window = mc.getWindow().handle();
        boolean ctrl = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        boolean shift = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        boolean key = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
        boolean down = ctrl && shift && key;
        if (down && !reloadComboDown) {
            reloadChanged(mc);
        }
        reloadComboDown = down;
    }

    public static void reloadChanged(Minecraft mc) {
        if (mc == null || mc.getResourceManager() == null) return;
        UiScriptModuleRegistry.ReloadStats stats = REGISTRY.reloadChanged(mc.getResourceManager());
        if (stats.errors() > 0) {
            DebugLog.error("[UI Scripts] reload failed: " + stats.firstError(), stats.firstCause());
            HudNotifier.pushMessage("UI scripts reload errors: " + stats.firstError(), HudNotifier.NotifyType.NO);
            return;
        }
        if (stats.changed() > 0) {
            HudNotifier.pushMessage("UI scripts reloaded: " + stats.changed() + " changed", HudNotifier.NotifyType.INFO);
        } else {
            HudNotifier.pushMessage("UI scripts unchanged", HudNotifier.NotifyType.INFO);
        }
    }

    private static void report(String id, String message, Throwable cause, String stack) {
        String line = message != null && !message.isBlank() ? message : "unknown error";
        if (stack != null && !stack.isBlank()) {
            DebugLog.error("[UI Scripts] " + id + " failed: " + line + "\n" + stack);
        } else {
            DebugLog.error("[UI Scripts] " + id + " failed: " + line, cause);
        }
        HudNotifier.pushMessage("UI script error " + id + ": " + line, HudNotifier.NotifyType.NO);
    }
}
