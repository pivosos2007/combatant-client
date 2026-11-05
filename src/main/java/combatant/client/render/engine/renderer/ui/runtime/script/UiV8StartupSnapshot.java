/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer.ui.runtime.script;

import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.V8Host;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.interop.options.V8RuntimeOptions;
import combatant.client.util.logging.DebugLog;

/**
 * JVM-lifetime snapshot of the resource-pack-independent UI core. V8 functions, contracts,
 * and dependency tracking are installed once in a temporary isolate and reused by UI workers.
 * The blob is never persisted: native V8 versions and the embedded UI API must match exactly.
 */
final class UiV8StartupSnapshot {
    private static byte[] snapshot;
    private static boolean disabled;

    private UiV8StartupSnapshot() { }

    static synchronized PreparedRuntime createRuntime(AutoCloseable owner) throws JavetException {
        if (!disabled && snapshot == null) initialize();
        if (snapshot != null) {
            try {
                V8RuntimeOptions options = new V8RuntimeOptions();
                options.setSnapshotBlob(snapshot);
                V8Runtime runtime = JavetRuntimeBootstrap.createRuntime(owner, options);
                // A native version or snapshot incompatibility may return an empty context
                // without throwing. Verify the actual API before skipping the normal bootstrap.
                if (!runtime.getExecutor("typeof ui === 'object' && "
                        + "typeof ui.root === 'function' && "
                        + "typeof __combatant_render_tracked === 'function'").executeBoolean()) {
                    throw new IllegalStateException("Snapshot restored without the Combatant UI API");
                }
                return new PreparedRuntime(runtime, true);
            } catch (JavetException | RuntimeException error) {
                disable("V8 rejected the UI startup snapshot", error);
            }
        }
        return new PreparedRuntime(JavetRuntimeBootstrap.createRuntime(owner), false);
    }

    private static void initialize() {
        try {
            JavetRuntimeBootstrap.installNativeLoader();
            V8RuntimeOptions options = new V8RuntimeOptions();
            options.setCreateSnapshotEnabled(true);
            try (V8Runtime runtime = V8Host.getV8Instance().createV8Runtime(options)) {
                JavetUiEngine.installCore(runtime);
                byte[] created = runtime.createSnapshot();
                if (created == null || created.length == 0) {
                    disable("V8 returned an empty UI startup snapshot", null);
                    return;
                }
                snapshot = created;
            }
        } catch (Exception | LinkageError error) {
            disable("Cannot initialize V8 UI startup snapshot", error);
        }
    }

    private static void disable(String reason, Throwable error) {
        snapshot = null;
        disabled = true;
        DebugLog.warn("[Javet] %s; falling back to ordinary UI bootstrap%s",
                reason, error != null ? ": " + error : "");
    }

    record PreparedRuntime(V8Runtime runtime, boolean hasUiCore) { }
}
