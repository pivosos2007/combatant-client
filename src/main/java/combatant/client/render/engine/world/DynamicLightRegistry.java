/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.world;

import combatant.client.util.logging.DebugLog;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Thread-safe registry of explicit local-light producers. */
public final class DynamicLightRegistry {
    private static final CopyOnWriteArrayList<DynamicLightProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    private DynamicLightRegistry() {
    }

    public static AutoCloseable register(DynamicLightProvider provider) {
        if (provider == null) return () -> { };
        PROVIDERS.addIfAbsent(provider);
        return () -> PROVIDERS.remove(provider);
    }

    public static void collect(DynamicLightProvider.Context context, Consumer<LightDescriptor> output) {
        if (output == null) return;
        try {
            BuiltinDynamicLightProvider.INSTANCE.collect(context, descriptor -> {
                if (descriptor != null && descriptor.valid()) output.accept(descriptor);
            });
        } catch (Throwable error) {
            DebugLog.warnOnChange(
                    "dynamic-light-builtin-provider", error.getClass().getName() + ":" + error.getMessage(),
                    "[DynamicLight] built-in provider failed softly: %s: %s",
                    error.getClass().getSimpleName(), error.getMessage()
            );
        }
        for (DynamicLightProvider provider : PROVIDERS) {
            try {
                provider.collect(context, descriptor -> {
                    if (descriptor != null && descriptor.valid()) output.accept(descriptor);
                });
            } catch (Throwable error) {
                DebugLog.warnOnChange(
                        "dynamic-light-provider:" + provider.getClass().getName(),
                        error.getClass().getName() + ":" + error.getMessage(),
                        "[DynamicLight] provider %s failed softly: %s: %s",
                        provider.getClass().getName(), error.getClass().getSimpleName(), error.getMessage()
                );
            }
        }
    }
}
