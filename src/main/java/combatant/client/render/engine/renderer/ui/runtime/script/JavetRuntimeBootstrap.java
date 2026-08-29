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
import combatant.client.util.logging.DebugLog;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Shared owner of Combatant's bundled Javet V8 native runtime. */
public final class JavetRuntimeBootstrap {
    private static final Map<AutoCloseable, V8Runtime> RUNTIMES = new IdentityHashMap<>();
    private static boolean shuttingDown;
    private static boolean nativeUsed;

    private JavetRuntimeBootstrap() {
    }

    public static synchronized void installNativeLoader() {
        if (shuttingDown) return;
        JavetNativeResourceLoader.install();
    }

    public static synchronized V8Runtime createRuntime(AutoCloseable owner) throws JavetException {
        if (owner == null) throw new IllegalArgumentException("Javet runtime owner must not be null");
        if (shuttingDown) throw new IllegalStateException("Javet runtime is shutting down");

        JavetNativeResourceLoader.install();
        V8Runtime previous = RUNTIMES.remove(owner);
        if (previous != null) {
            try {
                previous.close();
            } catch (Throwable ignored) {
            }
        }

        V8Runtime runtime = V8Host.getV8Instance().createV8Runtime();
        nativeUsed = true;
        RUNTIMES.put(owner, runtime);
        return runtime;
    }

    public static void closeRuntime(AutoCloseable owner, V8Runtime runtime) {
        if (runtime == null) return;
        synchronized (JavetRuntimeBootstrap.class) {
            if (owner != null && RUNTIMES.get(owner) == runtime) {
                RUNTIMES.remove(owner);
            } else {
                RUNTIMES.values().removeIf(candidate -> candidate == runtime);
            }
        }
        try {
            runtime.close();
        } catch (Throwable ignored) {
        }
    }

    /** Final process-exit cleanup. This is intentionally one-way. */
    public static void shutdown() {
        synchronized (JavetRuntimeBootstrap.class) {
            if (shuttingDown) return;
            shuttingDown = true;
        }

        closeRuntimeOwners();
        if (nativeUsed) {
            unloadNativeLibrary();
        }
    }

    private static void closeRuntimeOwners() {
        List<AutoCloseable> owners;
        synchronized (JavetRuntimeBootstrap.class) {
            owners = new ArrayList<>(RUNTIMES.keySet());
        }

        for (AutoCloseable owner : owners) {
            try {
                owner.close();
            } catch (Throwable t) {
                DebugLog.errorOnce("javet-owner-shutdown-" + owner.getClass().getName(),
                        "[Javet] Failed to close runtime owner " + owner.getClass().getName(), t);
            }
        }

        // Safety net for a broken owner.close(): make sure no isolate remains registered.
        List<V8Runtime> remaining;
        synchronized (JavetRuntimeBootstrap.class) {
            remaining = new ArrayList<>(RUNTIMES.values());
            RUNTIMES.clear();
        }
        for (V8Runtime runtime : remaining) {
            try {
                runtime.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void unloadNativeLibrary() {
        boolean previousReloadable = false;
        boolean reloadableChanged = false;
        try {
            V8Host host = V8Host.getV8Instance();
            if (!host.isLibraryLoaded()) return;
            if (host.getV8RuntimeCount() != 0) {
                DebugLog.warn("[Javet] Refusing native unload with %d V8 runtime(s) still registered",
                        host.getV8RuntimeCount());
                return;
            }

            previousReloadable = V8Host.isLibraryReloadable();
            V8Host.setLibraryReloadable(true);
            reloadableChanged = true;
            if (!host.unloadLibrary()) {
                DebugLog.warn("[Javet] Native V8 library remained loaded during client shutdown");
            }
        } catch (Throwable t) {
            DebugLog.errorOnce("javet-native-shutdown", "[Javet] Failed to unload native V8 library", t);
        } finally {
            if (reloadableChanged) {
                try {
                    V8Host.setLibraryReloadable(previousReloadable);
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
