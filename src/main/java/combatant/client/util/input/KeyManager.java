/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.input;

import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.ClickGuiSearch;
import combatant.client.util.logging.DebugLog;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public enum KeyManager {
    ;

    /**
     * func -> combos
     */
    private static final Map<String, Set<Set<Integer>>> comboBinds = new HashMap<>();

    /**
     * combo -> func(s); allow multiple modules to share the same combo
     */
    private static final Map<Set<Integer>, Set<String>> reverseLookup = new HashMap<>();

    /**
     * per-function debounce state for wasPressed
     */
    private static final Map<String, Boolean> prevFuncState = new HashMap<>();

    /**
     * Event-backed state for mouse buttons and scancode-only keys.
     */
    private static final Map<Integer, Boolean> liveInputState = new HashMap<>();

    // ============================================================
    //                     Registration / removal
    // ============================================================

    public static void registerCombo(String func, String comboString) {
        Set<Integer> combo = KeyUtil.stringToCombo(comboString);
        if (combo.isEmpty()) return;

        comboBinds.computeIfAbsent(func, k -> new HashSet<>()).add(combo);
        reverseLookup.computeIfAbsent(combo, k -> new HashSet<>()).add(func);

        DebugLog.info("[KeyManager] Registered %s = %s", func, comboString);
    }

    public static void unregisterCombo(String func, String comboString) {
        Set<Integer> combo = KeyUtil.stringToCombo(comboString);
        if (combo.isEmpty()) return;

        if (!comboBinds.containsKey(func)) return;

        comboBinds.get(func).remove(combo);
        Set<String> funcs = reverseLookup.get(combo);
        if (funcs != null) {
            funcs.remove(func);
            if (funcs.isEmpty()) reverseLookup.remove(combo);
        }

        if (comboBinds.get(func).isEmpty()) {
            comboBinds.remove(func);
        }

        DebugLog.info("[KeyManager] Unregistered %s = %s", func, comboString);
    }

    /**
     * Remove all combos bound to a function.
     */
    public static void unregisterAll(String func) {
        if (!comboBinds.containsKey(func)) return;

        for (Set<Integer> combo : comboBinds.get(func)) {
            Set<String> funcs = reverseLookup.get(combo);
            if (funcs != null) {
                funcs.remove(func);
                if (funcs.isEmpty()) reverseLookup.remove(combo);
            }
        }

        comboBinds.remove(func);
        prevFuncState.remove(func);

        DebugLog.info("[KeyManager] Unregistered ALL combos for %s", func);
    }

    // ============================================================
    //                       Event state bridge
    // ============================================================

    public static void handleKeyEvent(int key, int scancode, int action) {
        boolean pressed = action != GLFW.GLFW_RELEASE;

        if (key != GLFW.GLFW_KEY_UNKNOWN) {
            liveInputState.put(key, pressed);
        }

        if (scancode >= 0) {
            liveInputState.put(KeyUtil.scancodeToCode(scancode), pressed);
        }
    }

    public static void handleMouseButtonEvent(int button, int action) {
        if (button < GLFW.GLFW_MOUSE_BUTTON_1 || button > GLFW.GLFW_MOUSE_BUTTON_LAST) return;
        liveInputState.put(KeyUtil.mouseButtonToCode(button), action != GLFW.GLFW_RELEASE);
    }

    // ============================================================
    //                          Query
    // ============================================================

    /**
     * True once when combo transitions from not-pressed to pressed for this func.
     */
    public static boolean wasPressed(String func) {
        if (ClickGuiSearch.isActive()) return false;
        if (!comboBinds.containsKey(func)) return false;

        boolean anyPressed = false;
        for (Set<Integer> combo : comboBinds.get(func)) {
            boolean allPressed = true;
            for (int key : combo) {
                if (!isKeyPressed(key, false)) {
                    allPressed = false;
                    break;
                }
            }
            if (allPressed) {
                anyPressed = true;
                break;
            }
        }

        boolean prev = prevFuncState.getOrDefault(func, false);
        if (anyPressed && !prev) {
            prevFuncState.put(func, true);
            return true;
        }

        if (!anyPressed) {
            prevFuncState.put(func, false);
        }

        return false;
    }

    /**
     * True while any combo for the function is held down.
     */
    public static boolean isHeld(String func) {
        return isHeldInternal(func, false);
    }

    /**
     * True while any combo for the function is held down, even when a screen is open.
     */
    public static boolean isHeldAllowScreen(String func) {
        return isHeldInternal(func, true);
    }

    /**
     * True while the given combo is held down, even when a screen is open.
     */
    public static boolean isComboHeldAllowScreen(String comboString) {
        if (comboString == null || comboString.isEmpty() || comboString.equalsIgnoreCase("NONE")) return false;
        Set<Integer> combo = KeyUtil.stringToCombo(comboString);
        if (combo.isEmpty()) return false;
        for (int key : combo) {
            if (!isKeyPressed(key, true)) {
                return false;
            }
        }
        return true;
    }

    // ============================================================
    //                     Low-level input helpers
    // ============================================================

    private static boolean isKeyPressed(int key, boolean allowScreen) {
        if (key == KeyUtil.NONE) return false;
        if (ClickGuiRenderer.waitingForKey || ClickGuiRenderer.isTextEditorActive()) return false;

        Minecraft client = Minecraft.getInstance();
        if (!allowScreen && ClientScreen.current(client) != null) return false;

        if (KeyUtil.isScancodeCode(key)) {
            return liveInputState.getOrDefault(key, false);
        }

        long win = client.getWindow().handle();

        if (KeyUtil.isMouseCode(key)) {
            int button = KeyUtil.codeToMouseButton(key);
            if (button < GLFW.GLFW_MOUSE_BUTTON_1 || button > GLFW.GLFW_MOUSE_BUTTON_LAST) return false;
            return GLFW.glfwGetMouseButton(win, button) == GLFW.GLFW_PRESS
                    || liveInputState.getOrDefault(key, false);
        }

        return GLFW.glfwGetKey(win, key) == GLFW.GLFW_PRESS
                || liveInputState.getOrDefault(key, false);
    }

    private static boolean isHeldInternal(String func, boolean allowScreen) {
        if (ClickGuiSearch.isActive()) return false;
        if (!comboBinds.containsKey(func)) return false;

        for (Set<Integer> combo : comboBinds.get(func)) {
            boolean allPressed = true;
            for (int key : combo) {
                if (!isKeyPressed(key, allowScreen)) {
                    allPressed = false;
                    break;
                }
            }
            if (allPressed) return true;
        }

        return false;
    }

    // ============================================================
    //                      Debug / helpers
    // ============================================================

    public static boolean isComboUsed(String comboString) {
        Set<Integer> combo = KeyUtil.stringToCombo(comboString);
        Set<String> funcs = reverseLookup.get(combo);
        return funcs != null && !funcs.isEmpty();
    }
}
