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

public final class InputBindingRegistry {
    private final InputState state;
    private final Map<String, Binding> bindings = new HashMap<>();

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
        binding.chords.add(chord);
        binding.held = binding.isHeld(state);
        DebugLog.info("[InputBindings] Registered %s = %s", id, serializedChord);
    }

    public synchronized void unbind(String id) {
        if (id == null) return;
        if (bindings.remove(id) != null) DebugLog.info("[InputBindings] Unregistered %s", id);
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
            binding.pendingGame = 0;
            return false;
        }
        if (binding.pendingGame == 0) return false;
        binding.pendingGame--;
        return true;
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
        if (focus.allowsGameBindings()) return;
        // Presses queued in-game must not be replayed after opening/closing a screen.
        for (Binding binding : bindings.values()) binding.pendingGame = 0;
    }

    synchronized void onStateChanged(InputFocus.Snapshot focus) {
        onFocusChanged(focus);
        for (Binding binding : bindings.values()) {
            boolean held = binding.isHeld(state);
            if (held && !binding.held) {
                binding.pendingGlobal++;
                if (focus.allowsGameBindings()) binding.pendingGame++;
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
