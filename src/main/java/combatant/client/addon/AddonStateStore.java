/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.addon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

final class AddonStateStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private AddonStateStore() {
    }

    static Map<String, Boolean> loadEnabledOverrides() {
        Path file = stateFile();
        if (!Files.isRegularFile(file)) return new HashMap<>();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            State state = GSON.fromJson(reader, State.class);
            if (state == null || state.enabled == null) return new HashMap<>();
            return new HashMap<>(state.enabled);
        } catch (Throwable ignored) {
            return new HashMap<>();
        }
    }

    static void saveEnabledOverrides(Map<String, Boolean> enabled) {
        Path file = stateFile();
        try {
            Files.createDirectories(file.getParent());
            State state = new State();
            state.enabled = new HashMap<>(enabled);
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(state, writer);
            }
        } catch (IOException ignored) {
        }
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
