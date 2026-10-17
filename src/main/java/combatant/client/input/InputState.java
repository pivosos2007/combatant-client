/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.input;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class InputState {
    private final Set<Integer> keyboard = ConcurrentHashMap.newKeySet();
    private final Set<Integer> mouse = ConcurrentHashMap.newKeySet();
    private volatile double mouseX;
    private volatile double mouseY;

    public void apply(InputKey key, InputAction action) {
        Set<Integer> pressed = key.device() == InputDevice.KEYBOARD ? keyboard : mouse;
        if (action.isDown()) pressed.add(key.physicalKey());
        else pressed.remove(key.physicalKey());
    }

    public boolean isPressed(InputKey key) {
        if (key == null) return false;
        Set<Integer> pressed = key.device() == InputDevice.KEYBOARD ? keyboard : mouse;
        return pressed.contains(key.physicalKey());
    }

    public boolean isPressed(InputDevice device, int physicalKey) {
        return (device == InputDevice.KEYBOARD ? keyboard : mouse).contains(physicalKey);
    }

    public void updateMousePosition(double x, double y) {
        mouseX = x;
        mouseY = y;
    }

    public double mouseX() {
        return mouseX;
    }

    public double mouseY() {
        return mouseY;
    }

    public void clear() {
        keyboard.clear();
        mouse.clear();
    }
}
