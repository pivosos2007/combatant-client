/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.navigation;

import combatant.client.automation.compat.baritone.BaritoneNavigationProvider;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Registry of {@link NavigationProvider}s. The built-in walker is always registered and is the fallback: asking
 * for a provider that is unknown or unavailable yields the built-in one rather than nothing, so a module that
 * prefers Baritone keeps working when it is not installed.
 */
public final class NavigationProviders {

    /** Process-wide registry: the built-in walker, plus Baritone when its mod is loaded. */
    public static final NavigationProviders INSTANCE = createDefault();

    private final NavigationProvider builtin;
    private final Map<String, NavigationProvider> providers = new LinkedHashMap<>();

    public NavigationProviders(NavigationProvider builtin) {
        this.builtin = Objects.requireNonNull(builtin, "builtin");
        providers.put(builtin.id(), builtin);
    }

    private static NavigationProviders createDefault() {
        NavigationProviders registry = new NavigationProviders(new WalkNavigationProvider());
        BaritoneNavigationProvider baritone = new BaritoneNavigationProvider();
        if (baritone.isAvailable()) registry.register(baritone);
        return registry;
    }

    /** Adds or replaces the provider with the same id. The built-in id cannot be replaced. */
    public synchronized void register(NavigationProvider provider) {
        Objects.requireNonNull(provider, "provider");
        if (provider.id().equals(builtin.id())) throw new IllegalArgumentException("built-in provider id is reserved: " + provider.id());
        providers.put(provider.id(), provider);
    }

    public synchronized void unregister(String id) {
        if (!builtin.id().equals(id)) providers.remove(id);
    }

    public NavigationProvider builtin() {
        return builtin;
    }

    /** The registered provider with that id, available or not; null when unknown. */
    public synchronized NavigationProvider get(String id) {
        return providers.get(id);
    }

    /** Ids of all registered providers, built-in first, whether available or not. */
    public synchronized java.util.List<String> ids() {
        return java.util.List.copyOf(providers.keySet());
    }

    /** Ids of the providers that are available right now. */
    public synchronized java.util.List<String> availableIds() {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (NavigationProvider provider : providers.values()) if (provider.isAvailable()) out.add(provider.id());
        return out;
    }

    /**
     * The provider with {@code preferredId} when it is registered and available, else the built-in one.
     * A null or blank id means the built-in provider.
     */
    public synchronized NavigationProvider select(String preferredId) {
        if (preferredId != null && !preferredId.isBlank()) {
            NavigationProvider preferred = providers.get(preferredId);
            if (preferred != null && preferred.isAvailable()) return preferred;
        }
        return builtin;
    }
}
