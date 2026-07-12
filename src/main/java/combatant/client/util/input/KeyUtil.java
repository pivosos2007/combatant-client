/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.input;

import org.lwjgl.glfw.GLFW;

import java.util.*;

public enum KeyUtil {
    ;

    public static final int NONE = GLFW.GLFW_KEY_UNKNOWN;

    private static final int MOUSE_CODE_BASE = -4096;
    private static final int SCANCODE_CODE_BASE = -1_048_576;

    private static final Map<String, Integer> NAME_TO_KEY = new HashMap<>();
    private static final Map<Integer, String> KEY_TO_NAME = new HashMap<>();

    static {
        for (char c = 'A'; c <= 'Z'; c++) {
            put(String.valueOf(c), GLFW.GLFW_KEY_A + (c - 'A'));
        }

        for (char c = '0'; c <= '9'; c++) {
            put(String.valueOf(c), GLFW.GLFW_KEY_0 + (c - '0'));
        }

        for (int i = 1; i <= 25; i++) {
            put("F" + i, GLFW.GLFW_KEY_F1 + (i - 1));
        }

        put("SPACE", GLFW.GLFW_KEY_SPACE);
        put("TAB", GLFW.GLFW_KEY_TAB);
        put("ENTER", GLFW.GLFW_KEY_ENTER);
        putAlias("RETURN", GLFW.GLFW_KEY_ENTER);
        put("ESCAPE", GLFW.GLFW_KEY_ESCAPE);
        putAlias("ESC", GLFW.GLFW_KEY_ESCAPE);
        put("BACKSPACE", GLFW.GLFW_KEY_BACKSPACE);
        put("DELETE", GLFW.GLFW_KEY_DELETE);
        put("INSERT", GLFW.GLFW_KEY_INSERT);
        put("HOME", GLFW.GLFW_KEY_HOME);
        put("END", GLFW.GLFW_KEY_END);
        put("PAGE_UP", GLFW.GLFW_KEY_PAGE_UP);
        putAlias("PGUP", GLFW.GLFW_KEY_PAGE_UP);
        put("PAGE_DOWN", GLFW.GLFW_KEY_PAGE_DOWN);
        putAlias("PGDN", GLFW.GLFW_KEY_PAGE_DOWN);

        put("UP", GLFW.GLFW_KEY_UP);
        put("DOWN", GLFW.GLFW_KEY_DOWN);
        put("LEFT", GLFW.GLFW_KEY_LEFT);
        put("RIGHT", GLFW.GLFW_KEY_RIGHT);

        put("LEFT_SHIFT", GLFW.GLFW_KEY_LEFT_SHIFT);
        put("RIGHT_SHIFT", GLFW.GLFW_KEY_RIGHT_SHIFT);
        putAlias("LSHIFT", GLFW.GLFW_KEY_LEFT_SHIFT);
        putAlias("RSHIFT", GLFW.GLFW_KEY_RIGHT_SHIFT);
        putAlias("SHIFT", GLFW.GLFW_KEY_LEFT_SHIFT);

        put("LEFT_CTRL", GLFW.GLFW_KEY_LEFT_CONTROL);
        put("RIGHT_CTRL", GLFW.GLFW_KEY_RIGHT_CONTROL);
        putAlias("LEFT_CONTROL", GLFW.GLFW_KEY_LEFT_CONTROL);
        putAlias("RIGHT_CONTROL", GLFW.GLFW_KEY_RIGHT_CONTROL);
        putAlias("LCTRL", GLFW.GLFW_KEY_LEFT_CONTROL);
        putAlias("RCTRL", GLFW.GLFW_KEY_RIGHT_CONTROL);
        putAlias("CTRL", GLFW.GLFW_KEY_LEFT_CONTROL);
        putAlias("CONTROL", GLFW.GLFW_KEY_LEFT_CONTROL);

        put("LEFT_ALT", GLFW.GLFW_KEY_LEFT_ALT);
        put("RIGHT_ALT", GLFW.GLFW_KEY_RIGHT_ALT);
        putAlias("LALT", GLFW.GLFW_KEY_LEFT_ALT);
        putAlias("RALT", GLFW.GLFW_KEY_RIGHT_ALT);
        putAlias("ALT", GLFW.GLFW_KEY_LEFT_ALT);

        put("LEFT_SUPER", GLFW.GLFW_KEY_LEFT_SUPER);
        put("RIGHT_SUPER", GLFW.GLFW_KEY_RIGHT_SUPER);
        putAlias("LEFT_WIN", GLFW.GLFW_KEY_LEFT_SUPER);
        putAlias("RIGHT_WIN", GLFW.GLFW_KEY_RIGHT_SUPER);
        putAlias("LWIN", GLFW.GLFW_KEY_LEFT_SUPER);
        putAlias("RWIN", GLFW.GLFW_KEY_RIGHT_SUPER);
        putAlias("SUPER", GLFW.GLFW_KEY_LEFT_SUPER);
        putAlias("WIN", GLFW.GLFW_KEY_LEFT_SUPER);

        put("CAPS_LOCK", GLFW.GLFW_KEY_CAPS_LOCK);
        put("SCROLL_LOCK", GLFW.GLFW_KEY_SCROLL_LOCK);
        put("NUM_LOCK", GLFW.GLFW_KEY_NUM_LOCK);
        put("PRINT_SCREEN", GLFW.GLFW_KEY_PRINT_SCREEN);
        put("PAUSE", GLFW.GLFW_KEY_PAUSE);
        put("MENU", GLFW.GLFW_KEY_MENU);

        put("GRAVE_ACCENT", GLFW.GLFW_KEY_GRAVE_ACCENT);
        putAlias("GRAVE", GLFW.GLFW_KEY_GRAVE_ACCENT);
        putAlias("BACKTICK", GLFW.GLFW_KEY_GRAVE_ACCENT);
        put("APOSTROPHE", GLFW.GLFW_KEY_APOSTROPHE);
        put("BACKSLASH", GLFW.GLFW_KEY_BACKSLASH);
        put("COMMA", GLFW.GLFW_KEY_COMMA);
        put("EQUAL", GLFW.GLFW_KEY_EQUAL);
        put("LEFT_BRACKET", GLFW.GLFW_KEY_LEFT_BRACKET);
        put("RIGHT_BRACKET", GLFW.GLFW_KEY_RIGHT_BRACKET);
        put("MINUS", GLFW.GLFW_KEY_MINUS);
        put("PERIOD", GLFW.GLFW_KEY_PERIOD);
        put("SEMICOLON", GLFW.GLFW_KEY_SEMICOLON);
        put("SLASH", GLFW.GLFW_KEY_SLASH);
        put("WORLD_1", GLFW.GLFW_KEY_WORLD_1);
        put("WORLD_2", GLFW.GLFW_KEY_WORLD_2);

        put("KP_0", GLFW.GLFW_KEY_KP_0);
        put("KP_1", GLFW.GLFW_KEY_KP_1);
        put("KP_2", GLFW.GLFW_KEY_KP_2);
        put("KP_3", GLFW.GLFW_KEY_KP_3);
        put("KP_4", GLFW.GLFW_KEY_KP_4);
        put("KP_5", GLFW.GLFW_KEY_KP_5);
        put("KP_6", GLFW.GLFW_KEY_KP_6);
        put("KP_7", GLFW.GLFW_KEY_KP_7);
        put("KP_8", GLFW.GLFW_KEY_KP_8);
        put("KP_9", GLFW.GLFW_KEY_KP_9);
        put("KP_DECIMAL", GLFW.GLFW_KEY_KP_DECIMAL);
        put("KP_DIVIDE", GLFW.GLFW_KEY_KP_DIVIDE);
        put("KP_MULTIPLY", GLFW.GLFW_KEY_KP_MULTIPLY);
        put("KP_SUBTRACT", GLFW.GLFW_KEY_KP_SUBTRACT);
        put("KP_ADD", GLFW.GLFW_KEY_KP_ADD);
        put("KP_ENTER", GLFW.GLFW_KEY_KP_ENTER);
        put("KP_EQUAL", GLFW.GLFW_KEY_KP_EQUAL);

        putMouseAliases("MOUSE_LEFT", GLFW.GLFW_MOUSE_BUTTON_LEFT);
        putMouseAliases("MOUSE_RIGHT", GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        putMouseAliases("MOUSE_MIDDLE", GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
        putMouseAlias("MOUSE1", GLFW.GLFW_MOUSE_BUTTON_LEFT);
        putMouseAlias("MOUSE_1", GLFW.GLFW_MOUSE_BUTTON_LEFT);
        putMouseAlias("MOUSE_BUTTON_1", GLFW.GLFW_MOUSE_BUTTON_LEFT);
        putMouseAlias("MOUSE2", GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        putMouseAlias("MOUSE_2", GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        putMouseAlias("MOUSE_BUTTON_2", GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        putMouseAlias("MOUSE3", GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
        putMouseAlias("MOUSE_3", GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
        putMouseAlias("MOUSE_BUTTON_3", GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
        putMouseAlias("MB1", GLFW.GLFW_MOUSE_BUTTON_LEFT);
        putMouseAlias("MB2", GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        putMouseAlias("MB3", GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
        for (int userButton = 4; userButton <= 8; userButton++) {
            int glfwButton = userButton - 1;
            putMouseAlias("MOUSE" + userButton, glfwButton);
            putMouseAlias("MOUSE_" + userButton, glfwButton);
            putMouseAlias("MOUSE_BUTTON_" + userButton, glfwButton);
            putMouseAlias("MB" + userButton, glfwButton);
        }

        putTranslationAliases();
    }

    private static void put(String name, int code) {
        String normalized = normalizeName(name);
        NAME_TO_KEY.put(normalized, code);
        KEY_TO_NAME.putIfAbsent(code, normalized);
    }

    private static void putAlias(String name, int code) {
        NAME_TO_KEY.put(normalizeName(name), code);
    }

    private static void putMouseAliases(String name, int button) {
        putMouseAlias(name, button);
    }

    private static void putMouseAlias(String name, int button) {
        NAME_TO_KEY.put(normalizeName(name), mouseButtonToCode(button));
    }

    private static void putTranslationAliases() {
        for (Map.Entry<Integer, String> entry : KEY_TO_NAME.entrySet()) {
            int code = entry.getKey();
            String name = entry.getValue().toLowerCase(Locale.ROOT).replace('_', '.');
            NAME_TO_KEY.put("KEY.KEYBOARD." + name.toUpperCase(Locale.ROOT), code);
        }
        putAlias("KEY.KEYBOARD.LEFT.CONTROL", GLFW.GLFW_KEY_LEFT_CONTROL);
        putAlias("KEY.KEYBOARD.RIGHT.CONTROL", GLFW.GLFW_KEY_RIGHT_CONTROL);
        putAlias("KEY.KEYBOARD.LEFT.WIN", GLFW.GLFW_KEY_LEFT_SUPER);
        putAlias("KEY.KEYBOARD.RIGHT.WIN", GLFW.GLFW_KEY_RIGHT_SUPER);
        putAlias("KEY.KEYBOARD.KEYPAD.0", GLFW.GLFW_KEY_KP_0);
        putAlias("KEY.KEYBOARD.KEYPAD.1", GLFW.GLFW_KEY_KP_1);
        putAlias("KEY.KEYBOARD.KEYPAD.2", GLFW.GLFW_KEY_KP_2);
        putAlias("KEY.KEYBOARD.KEYPAD.3", GLFW.GLFW_KEY_KP_3);
        putAlias("KEY.KEYBOARD.KEYPAD.4", GLFW.GLFW_KEY_KP_4);
        putAlias("KEY.KEYBOARD.KEYPAD.5", GLFW.GLFW_KEY_KP_5);
        putAlias("KEY.KEYBOARD.KEYPAD.6", GLFW.GLFW_KEY_KP_6);
        putAlias("KEY.KEYBOARD.KEYPAD.7", GLFW.GLFW_KEY_KP_7);
        putAlias("KEY.KEYBOARD.KEYPAD.8", GLFW.GLFW_KEY_KP_8);
        putAlias("KEY.KEYBOARD.KEYPAD.9", GLFW.GLFW_KEY_KP_9);
        putAlias("KEY.KEYBOARD.KEYPAD.ADD", GLFW.GLFW_KEY_KP_ADD);
        putAlias("KEY.KEYBOARD.KEYPAD.DECIMAL", GLFW.GLFW_KEY_KP_DECIMAL);
        putAlias("KEY.KEYBOARD.KEYPAD.DIVIDE", GLFW.GLFW_KEY_KP_DIVIDE);
        putAlias("KEY.KEYBOARD.KEYPAD.ENTER", GLFW.GLFW_KEY_KP_ENTER);
        putAlias("KEY.KEYBOARD.KEYPAD.EQUAL", GLFW.GLFW_KEY_KP_EQUAL);
        putAlias("KEY.KEYBOARD.KEYPAD.MULTIPLY", GLFW.GLFW_KEY_KP_MULTIPLY);
        putAlias("KEY.KEYBOARD.KEYPAD.SUBTRACT", GLFW.GLFW_KEY_KP_SUBTRACT);

        putMouseAlias("KEY.MOUSE.LEFT", GLFW.GLFW_MOUSE_BUTTON_LEFT);
        putMouseAlias("KEY.MOUSE.RIGHT", GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        putMouseAlias("KEY.MOUSE.MIDDLE", GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
        for (int userButton = 4; userButton <= 8; userButton++) {
            putMouseAlias("KEY.MOUSE." + userButton, userButton - 1);
        }
    }

    public static int mouseButtonToCode(int button) {
        return MOUSE_CODE_BASE - Math.max(0, button);
    }

    public static boolean isMouseCode(int code) {
        return code <= MOUSE_CODE_BASE && code > SCANCODE_CODE_BASE;
    }

    public static int codeToMouseButton(int code) {
        return MOUSE_CODE_BASE - code;
    }

    public static int scancodeToCode(int scancode) {
        return SCANCODE_CODE_BASE - Math.max(0, scancode);
    }

    public static boolean isScancodeCode(int code) {
        return code <= SCANCODE_CODE_BASE;
    }

    public static int codeToScancode(int code) {
        return SCANCODE_CODE_BASE - code;
    }

    public static int fromKeyInput(int key, int scancode) {
        if (key != GLFW.GLFW_KEY_UNKNOWN) return key;
        return scancode >= 0 ? scancodeToCode(scancode) : NONE;
    }

    public static int nameToKey(String name) {
        if (name == null) return NONE;

        String normalized = normalizeName(name);
        if (normalized.isEmpty() || normalized.equals("NONE")) return NONE;

        Integer mapped = NAME_TO_KEY.get(normalized);
        if (mapped != null) return mapped;

        if (normalized.startsWith("KEY_") && normalized.length() > 4) {
            Integer parsed = parseInteger(normalized.substring(4));
            return parsed != null ? parsed : NONE;
        }

        if (normalized.startsWith("SCANCODE_") && normalized.length() > 9) {
            Integer parsed = parseInteger(normalized.substring(9));
            return parsed != null ? scancodeToCode(parsed) : NONE;
        }

        Integer mouse = parseMouseName(normalized);
        if (mouse != null) return mouseButtonToCode(mouse);

        return NONE;
    }

    public static String keyToName(int key) {
        if (key == NONE) return "NONE";

        if (isMouseCode(key)) {
            int button = codeToMouseButton(key);
            return switch (button) {
                case GLFW.GLFW_MOUSE_BUTTON_LEFT -> "MOUSE_LEFT";
                case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> "MOUSE_RIGHT";
                case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> "MOUSE_MIDDLE";
                default -> "MOUSE_" + (button + 1);
            };
        }

        if (isScancodeCode(key)) {
            return "SCANCODE_" + codeToScancode(key);
        }

        String known = KEY_TO_NAME.get(key);
        if (known != null) return known;

        return "KEY_" + key;
    }

    public static Set<Integer> stringToCombo(String str) {
        Set<Integer> set = new HashSet<>();
        if (str == null || str.isBlank()) return set;

        for (String part : str.split("\\+")) {
            int code = nameToKey(part.trim());
            if (code != NONE) set.add(code);
        }

        return set;
    }

    public static String comboToString(Set<Integer> keys) {
        if (keys == null || keys.isEmpty()) return "NONE";

        List<Integer> sorted = new ArrayList<>(keys);
        sorted.sort(KeyUtil::compareKeysForDisplay);

        List<String> names = new ArrayList<>(sorted.size());
        for (int key : sorted) {
            names.add(keyToName(key));
        }

        return String.join("+", names);
    }

    public static boolean isModifierCode(int code) {
        return code == GLFW.GLFW_KEY_LEFT_SHIFT
                || code == GLFW.GLFW_KEY_RIGHT_SHIFT
                || code == GLFW.GLFW_KEY_LEFT_CONTROL
                || code == GLFW.GLFW_KEY_RIGHT_CONTROL
                || code == GLFW.GLFW_KEY_LEFT_ALT
                || code == GLFW.GLFW_KEY_RIGHT_ALT
                || code == GLFW.GLFW_KEY_LEFT_SUPER
                || code == GLFW.GLFW_KEY_RIGHT_SUPER;
    }

    private static int compareKeysForDisplay(int a, int b) {
        int ar = sortRank(a);
        int br = sortRank(b);
        if (ar != br) return Integer.compare(ar, br);
        return keyToName(a).compareTo(keyToName(b));
    }

    private static int sortRank(int code) {
        if (isModifierCode(code)) return 0;
        if (isMouseCode(code)) return 2;
        if (isScancodeCode(code)) return 3;
        return 1;
    }

    private static Integer parseMouseName(String normalized) {
        String s = normalized;
        if (s.startsWith("MOUSE_BUTTON_")) s = s.substring("MOUSE_BUTTON_".length());
        else if (s.startsWith("MOUSE_")) s = s.substring("MOUSE_".length());
        else if (s.startsWith("MOUSE")) s = s.substring("MOUSE".length());
        else if (s.startsWith("MB")) s = s.substring(2);
        else return null;

        return switch (s) {
            case "LEFT" -> GLFW.GLFW_MOUSE_BUTTON_LEFT;
            case "RIGHT" -> GLFW.GLFW_MOUSE_BUTTON_RIGHT;
            case "MIDDLE" -> GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
            default -> {
                Integer parsed = parseInteger(s);
                if (parsed == null || parsed < 1 || parsed > 8) yield null;
                yield parsed - 1;
            }
        };
    }

    private static Integer parseInteger(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String normalizeName(String name) {
        return name.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }
}
