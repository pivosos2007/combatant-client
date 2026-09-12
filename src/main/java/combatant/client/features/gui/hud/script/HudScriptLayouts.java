/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.script;

import combatant.client.features.hmi_recode.HoldMyItems;
import combatant.client.features.playeranimator.PlayerAnimator;
import combatant.client.features.command.CommandOutput;
import combatant.client.util.resources.asset.AssetAutoLoader;
import combatant.client.util.resources.asset.AssetLoad;
import combatant.client.util.resources.asset.AssetLoadPhase;
import combatant.client.util.resources.asset.UiScriptAsset;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.glfw.GLFW;
import combatant.client.features.gui.hud.draggable.impl.HudNotifier;
import combatant.client.features.gui.hud.BaseHudElement;
import combatant.client.render.engine.renderer.ui.runtime.core.UiRuntime;
import combatant.client.render.engine.renderer.ui.runtime.script.*;
import combatant.client.util.logging.DebugLog;
import combatant.client.runtime.error.ErrorHandler;
import combatant.client.runtime.error.FailureIsolation;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

public enum HudScriptLayouts {
    ;
    private static final UiScriptModuleRegistry REGISTRY = new UiScriptModuleRegistry();
    private static final LinkedHashMap<String, String> LAST_RENDER_LOG = new LinkedHashMap<>();
    private static final LinkedHashSet<String> REPORTED_FAILURES = new LinkedHashSet<>();
    private static final Set<BaseHudElement> FAILED_SCRIPT_OWNERS =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final ThreadLocal<BaseHudElement> ACTIVE_OWNER = new ThreadLocal<>();
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

    /**
     * Resource reload ordering is intentionally HMI (450) -> PlayerAnimator (500) -> UI (550).
     * The animation runtimes invalidate lazily; UI modules can reload immediately against the new
     * ResourceManager. Keeping this as an @AssetLoad hook makes resource reload and manual reload
     * use the same subsystem order.
     */
    @AssetLoad(value = AssetLoadPhase.RELOAD, order = 550)
    public static void reloadRegisteredAssets(ResourceManager manager) {
        UiScriptModuleRegistry.ReloadStats stats = REGISTRY.reloadChanged(manager);
        logUiReloadFailures("resource reload", stats);
        if (stats.errors() == 0 && stats.changed() > 0) clearScriptOwnerFailures();
    }

    public static CachedUiScriptRuntime.Reporter runtimeReporter() {
        return RUNTIME_REPORTER;
    }

    public static OwnerScope ownerScope(BaseHudElement owner) {
        BaseHudElement previous = ACTIVE_OWNER.get();
        if (owner == null) ACTIVE_OWNER.remove();
        else ACTIVE_OWNER.set(owner);
        return () -> {
            if (previous == null) ACTIVE_OWNER.remove();
            else ACTIVE_OWNER.set(previous);
        };
    }

    public static UiScriptModuleRegistry.ReloadStats prewarmRegistered(ResourceManager manager) {
        if (manager == null) {
            return new UiScriptModuleRegistry.ReloadStats(0, 0, 0, "", null);
        }
        UiScriptModuleRegistry.ReloadStats stats = REGISTRY.ensureLoaded(manager);
        return stats;
    }

    public static void reportLoadError(UiScriptModuleHandle handle) {
        if (handle == null) return;
        boolean first = handle.recordLoadError(handle.lastError(), handle.lastErrorCause());
        if (reportToActiveOwner(handle.lastError(), handle.lastErrorCause(), "")) return;
        if (first) report(handle.getId().toString(), handle.lastError(), handle.lastErrorCause(), "");
    }

    public static void reportRuntimeError(UiScriptModuleHandle handle, UiScriptRuntimeError error) {
        if (handle == null) return;
        boolean first = handle.recordRuntimeError(error);
        String stack = error != null ? error.stackTrace() : "";
        if (reportToActiveOwner(handle.lastError(), handle.lastErrorCause(), stack)) return;
        if (first) report(handle.getId().toString(), handle.lastError(), handle.lastErrorCause(), stack);
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

        ArrayList<ReloadFailure> failures = new ArrayList<>();

        // Keep the exact same order as @AssetLoad resource reload hooks:
        // HMI (450) -> PlayerAnimator (500) -> UI (550).
        invalidateRuntime("HMI", HoldMyItems::invalidateScripts, failures);
        invalidateRuntime("PlayerAnimator", PlayerAnimator::invalidateScripts, failures);

        UiScriptModuleRegistry.ReloadStats uiStats = REGISTRY.reloadChanged(mc.getResourceManager());
        for (UiScriptModuleRegistry.ReloadFailure failure : uiStats.failures()) {
            failures.add(new ReloadFailure(
                    "UI " + failure.moduleId(),
                    failure.message(),
                    failure.cause()
            ));
        }

        if (!failures.isEmpty()) {
            for (ReloadFailure failure : failures) {
                String message = failure.message() == null || failure.message().isBlank()
                        ? "unknown error"
                        : failure.message();
                if (markFailure(message, failure.cause())) {
                    DebugLog.error("[Scripts] " + failure.runtime() + " reload failed: " + message, failure.cause());
                }
            }
            ReloadFailure first = failures.getFirst();
            String firstMessage = first.message() == null || first.message().isBlank()
                    ? "unknown error"
                    : first.message();
            DebugLog.error("[Scripts] reload completed with " + failures.size() + " error(s)");
            CommandOutput.error("Scripts reload errors (" + failures.size() + "): "
                    + first.runtime() + ": " + firstMessage);
            return;
        }

        if (uiStats.changed() > 0) clearScriptOwnerFailures();
        synchronized (REPORTED_FAILURES) { REPORTED_FAILURES.clear(); }

        if (uiStats.changed() > 0) {
            HudNotifier.pushMessage(
                    "Scripts reloaded: UI " + uiStats.changed() + " changed; HMI/PlayerAnimator invalidated",
                    HudNotifier.NotifyType.INFO
            );
        } else {
            HudNotifier.pushMessage(
                    "Scripts reloaded: UI unchanged; HMI/PlayerAnimator invalidated",
                    HudNotifier.NotifyType.INFO
            );
        }
    }

    private static void invalidateRuntime(String name, Runnable invalidation, List<ReloadFailure> failures) {
        try {
            invalidation.run();
        } catch (Throwable error) {
            failures.add(new ReloadFailure(name, error.getMessage(), error));
        }
    }

    private static void logUiReloadFailures(String reason, UiScriptModuleRegistry.ReloadStats stats) {
        if (stats == null || stats.failures().isEmpty()) return;
        for (UiScriptModuleRegistry.ReloadFailure failure : stats.failures()) {
            String message = failure.message() == null || failure.message().isBlank()
                    ? "unknown error"
                    : failure.message();
            if (markFailure(message, failure.cause())) {
                DebugLog.error(
                        "[UI Scripts] " + reason + " failed for " + failure.moduleId() + ": " + message,
                        failure.cause()
                );
            }
        }
    }

    private record ReloadFailure(String runtime, String message, Throwable cause) {
    }

    private static void report(String id, String message, Throwable cause, String stack) {
        String line = message != null && !message.isBlank() ? message : "unknown error";
        if (!markFailure(line, cause)) return;
        if (stack != null && !stack.isBlank()) {
            DebugLog.error("[UI Scripts] " + id + " failed: " + line + "\n" + stack);
        } else {
            DebugLog.error("[UI Scripts] " + id + " failed: " + line, cause);
        }
        CommandOutput.error("UI script error " + id + ": " + line);
    }

    private static boolean reportToActiveOwner(String message, Throwable cause, String stack) {
        BaseHudElement owner = ACTIVE_OWNER.get();
        if (owner == null) return false;
        if (ErrorHandler.failure(owner) == null) {
            FailureIsolation.reportComponent(owner, owner.getTitle(), "script",
                    new HudScriptFailure(message, stack, cause));
            synchronized (FAILED_SCRIPT_OWNERS) { FAILED_SCRIPT_OWNERS.add(owner); }
        }
        return true;
    }

    private static void clearScriptOwnerFailures() {
        synchronized (FAILED_SCRIPT_OWNERS) {
            for (BaseHudElement owner : FAILED_SCRIPT_OWNERS) {
                ErrorHandler.unregister(owner);
            }
            FAILED_SCRIPT_OWNERS.clear();
        }
    }

    private static String failureSignature(String message, Throwable cause) {
        Throwable root = cause;
        while (root != null && root.getCause() != null) root = root.getCause();
        String type = root == null ? "" : root.getClass().getName();
        String detail = root == null || root.getMessage() == null ? message : root.getMessage();
        return type + '|' + (detail == null ? "" : detail.trim());
    }

    private static boolean markFailure(String message, Throwable cause) {
        String signature = failureSignature(message, cause);
        synchronized (REPORTED_FAILURES) {
            if (!REPORTED_FAILURES.add(signature)) return false;
            while (REPORTED_FAILURES.size() > 256) REPORTED_FAILURES.removeFirst();
            return true;
        }
    }

    private static final class HudScriptFailure extends RuntimeException {
        private final String originalStack;

        private HudScriptFailure(String message, String originalStack, Throwable cause) {
            super(message == null || message.isBlank() ? "UI script failed" : message);
            this.originalStack = originalStack == null || originalStack.isBlank()
                    ? cause == null ? "" : stackTrace(cause)
                    : originalStack;
        }

        @Override
        public void printStackTrace(PrintWriter writer) {
            super.printStackTrace(writer);
            if (!originalStack.isBlank()) {
                writer.println("Original script failure:");
                writer.print(originalStack);
            }
        }

        private static String stackTrace(Throwable cause) {
            java.io.StringWriter out = new java.io.StringWriter(2048);
            cause.printStackTrace(new PrintWriter(out));
            return out.toString();
        }
    }

    @FunctionalInterface
    public interface OwnerScope extends AutoCloseable {
        @Override
        void close();
    }
}
