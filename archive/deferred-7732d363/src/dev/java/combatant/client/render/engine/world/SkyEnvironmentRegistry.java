/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world;

import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.world.environment.OverworldAtmosphereSkyProvider;
import net.minecraft.resources.Identifier;

import java.util.concurrent.ConcurrentHashMap;

/** Registry selected by {@link WorldRenderState#skyProvider()} before graph execution. */
public final class SkyEnvironmentRegistry {
    private static final ConcurrentHashMap<Identifier, SkyEnvironmentProvider> PROVIDERS = new ConcurrentHashMap<>();

    static {
        register(OverworldAtmosphereSkyProvider.INSTANCE);
    }

    private SkyEnvironmentRegistry() {
    }

    public static AutoCloseable register(SkyEnvironmentProvider provider) {
        if (provider == null || provider.id() == null) return () -> { };
        Identifier id = provider.id();
        SkyEnvironmentProvider previous = PROVIDERS.put(id, provider);
        return () -> PROVIDERS.compute(id, (key, current) -> current == provider ? previous : current);
    }

    public static SkyEnvironmentProvider resolve(Identifier id) {
        return id == null ? null : PROVIDERS.get(id);
    }

    public static void releaseBackendResources(CombatantRhi owner) {
        for (SkyEnvironmentProvider provider : PROVIDERS.values()) {
            try {
                provider.releaseBackendResources(owner);
            } catch (Throwable ignored) {
            }
        }
    }
}
