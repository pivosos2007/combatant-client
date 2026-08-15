/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings.subsystem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/** Runtime registry plus Java SPI discovery for Main Settings sections. */
public final class MainSettingsRegistry {
    private static final MainSettingsRegistry INSTANCE = new MainSettingsRegistry();

    private final Map<String, MainSettingsContributor> contributors = new LinkedHashMap<>();
    private long revision;

    private MainSettingsRegistry() {
        registerBuiltIns();
        discover();
    }

    private void registerBuiltIns() {
        register(new VisualSettingsContributor());
        register(new SecuritySettingsContributor());
        register(new InventorySettingsContributor());
        register(new RuntimeSettingsContributor());
    }

    public static MainSettingsRegistry get() {
        return INSTANCE;
    }

    public synchronized Registration register(MainSettingsContributor contributor) {
        if (contributor == null) return Registration.NOOP;
        String id = normalizeId(contributor.id());
        if (id.isEmpty()) return Registration.NOOP;

        MainSettingsContributor previous = contributors.put(id, contributor);
        if (previous != contributor) revision++;
        return () -> unregister(id, contributor);
    }

    public synchronized List<MainSettingsContributor> snapshot() {
        List<MainSettingsContributor> result = new ArrayList<>();
        for (MainSettingsContributor contributor : contributors.values()) {
            try {
                if (contributor.available()) result.add(contributor);
            } catch (RuntimeException ignored) {
            }
        }
        result.sort(Comparator
                .comparingInt(MainSettingsContributor::order)
                .thenComparing(MainSettingsContributor::id));
        return List.copyOf(result);
    }

    public synchronized long revision() {
        return revision;
    }

    private void discover() {
        ServiceLoader<MainSettingsContributor> loader = ServiceLoader.load(
                MainSettingsContributor.class,
                MainSettingsContributor.class.getClassLoader()
        );
        var iterator = loader.iterator();
        while (true) {
            try {
                if (!iterator.hasNext()) break;
                register(iterator.next());
            } catch (ServiceConfigurationError | RuntimeException ignored) {
                // A third-party contributor must not take the whole ClickGUI down.
            }
        }
    }

    private synchronized void unregister(String id, MainSettingsContributor expected) {
        if (contributors.get(id) != expected) return;
        contributors.remove(id);
        revision++;
    }

    private static String normalizeId(String id) {
        return id == null ? "" : id.trim().toLowerCase(java.util.Locale.ROOT);
    }

    @FunctionalInterface
    public interface Registration extends AutoCloseable {
        Registration NOOP = () -> { };

        @Override
        void close();
    }
}
