/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.addon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import combatant.client.config.ConfigSerializer;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

final class AddonStateStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private AddonStateStore() {
    }

    static Map<String, Boolean> loadEnabledOverrides() {
        Path file = stateFile();
        try {
            String text = ConfigSerializer.readJsonRecovering(file);
            if (text == null) return new HashMap<>();
            State state = GSON.fromJson(text, State.class);
            if (state == null || state.enabled == null) return new HashMap<>();
            return new HashMap<>(state.enabled);
        } catch (Throwable ignored) {
            return new HashMap<>();
        }
    }

    static void saveEnabledOverrides(Map<String, Boolean> enabled) {
        State state = new State();
        state.enabled = new HashMap<>(enabled);
        ConfigSerializer.requestJsonWrite(stateFile(), GSON.toJson(state));
    }

    private static Path stateFile() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("combatant")
                .resolve("addons.json");
    }

    private static final class State {
        Map<String, Boolean> enabled = new HashMap<>();
    }
}
