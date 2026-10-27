/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.input;

import combatant.client.util.logging.DebugLog;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Edge-triggered bindings. The physical-key index avoids revisiting every chord
 * for an unrelated keyboard/mouse event; all registry mutations remain serialized.
 */
public final class InputBindingRegistry {
    private final InputState state;
    private final Map<String, Binding> bindings = new HashMap<>();
    private final Map<PhysicalId, Set<Binding>> byPhysicalKey = new HashMap<>();
    // Exact number of queued game-scope presses (not just bindings with pending presses).
    private int pendingGamePresses;

    InputBindingRegistry(InputState state) {
        this.state = state;
    }

    public synchronized void bind(String id, String serializedChord) {
        if (id == null || id.isBlank()) return;
        // Invalid configuration must never degrade into a different, partial hotkey.
        if (!InputKeyCatalog.isValidChord(serializedChord)) return;
        InputChord chord = InputKeyCatalog.parseChord(serializedChord);
        if (chord.isEmpty()) return;
        Binding binding = bindings.computeIfAbsent(id, ignored -> new Binding());
        if (!binding.chords.add(chord)) return;
        for (InputKey key : chord.keys()) {
            byPhysicalKey.computeIfAbsent(PhysicalId.of(key), ignored -> new LinkedHashSet<>()).add(binding);
        }
        // As before, registering a chord while held never synthesizes a press edge.
        binding.held = binding.isHeld(state);
        DebugLog.info("[InputBindings] Registered %s = %s", id, serializedChord);
    }

    public synchronized void unbind(String id) {
        if (id == null) return;
        Binding binding = bindings.remove(id);
        if (binding == null) return;
        pendingGamePresses -= binding.pendingGame;
        for (InputChord chord : binding.chords) {
            for (InputKey key : chord.keys()) {
                PhysicalId physical = PhysicalId.of(key);
                Set<Binding> indexed = byPhysicalKey.get(physical);
                if (indexed == null) continue;
                indexed.remove(binding);
                if (indexed.isEmpty()) byPhysicalKey.remove(physical);
            }
        }
        DebugLog.info("[InputBindings] Unregistered %s", id);
    }

    public synchronized boolean consumePress(String id, InputScope scope) {
        Binding binding = bindings.get(id);
        if (binding == null) return false;
        if (scope == InputScope.GLOBAL) {
            if (binding.pendingGlobal == 0) return false;
            binding.pendingGlobal--;
            return true;
        }
        if (!CombatantInput.focus().snapshot().allowsGameBindings()) {
            // Clearing one binding is consistent with the original consumer behavior.
            pendingGamePresses -= binding.pendingGame;
            binding.pendingGame = 0;
            return false;
        }
        if (binding.pendingGame == 0) return false;
        binding.pendingGame--;
        pendingGamePresses--;
        return true;
    }

    /** A cheap guard for the module-keybind handler on ordinary game input. */
    public synchronized boolean hasPendingGamePress() {
        return pendingGamePresses != 0;
    }

    public synchronized boolean isHeld(String id, InputScope scope) {
        Binding binding = bindings.get(id);
        if (binding == null || !binding.isHeld(state)) return false;
        return scope == InputScope.GLOBAL || CombatantInput.focus().snapshot().allowsGameBindings();
    }

    public synchronized boolean isChordHeld(String serializedChord, InputScope scope) {
        InputChord chord = InputKeyCatalog.parseChord(serializedChord);
        if (!chord.isHeld(state)) return false;
        return scope == InputScope.GLOBAL || CombatantInput.focus().snapshot().allowsGameBindings();
    }

    public synchronized boolean isBound(String serializedChord) {
        InputChord requested = InputKeyCatalog.parseChord(serializedChord);
        if (requested.isEmpty()) return false;
        for (Binding binding : bindings.values()) {
            if (binding.chords.contains(requested)) return true;
        }
        return false;
    }

    public synchronized void onFocusChanged(InputFocus.Snapshot focus) {
        if (focus.allowsGameBindings() || pendingGamePresses == 0) return;
        // Queued in-game presses must not replay after closing a screen.
        for (Binding binding : bindings.values()) binding.pendingGame = 0;
        pendingGamePresses = 0;
    }

    synchronized void onStateChanged(InputKey changedKey, InputFocus.Snapshot focus) {
        onFocusChanged(focus);
        Set<Binding> affected = byPhysicalKey.get(PhysicalId.of(changedKey));
        if (affected == null) return;
        for (Binding binding : affected) {
            boolean held = binding.isHeld(state);
            if (held && !binding.held) {
                binding.pendingGlobal++;
                if (focus.allowsGameBindings()) {
                    binding.pendingGame++;
                    pendingGamePresses++;
                }
            }
            binding.held = held;
        }
    }

    public synchronized void clearState() {
        for (Binding binding : bindings.values()) {
            binding.held = false;
            binding.pendingGame = 0;
            binding.pendingGlobal = 0;
        }
        pendingGamePresses = 0;
    }

    private record PhysicalId(InputDevice device, int key) {
        private static PhysicalId of(InputKey key) {
            return new PhysicalId(key.device(), key.physicalKey());
        }
    }

    private static final class Binding {
        private final Set<InputChord> chords = new LinkedHashSet<>();
        private boolean held;
        private int pendingGame;
        private int pendingGlobal;

        private boolean isHeld(InputState state) {
            for (InputChord chord : chords) {
                if (chord.isHeld(state)) return true;
            }
            return false;
        }
    }
}
