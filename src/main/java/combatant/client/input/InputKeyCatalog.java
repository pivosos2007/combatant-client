/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.input;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class InputKeyCatalog {
    private static final Map<String, InputKey> BY_NAME = new HashMap<>();
    private static final Map<PhysicalId, InputKey> BY_PHYSICAL = new HashMap<>();

    static {
        for (char letter = 'A'; letter <= 'Z'; letter++) {
            keyboard(String.valueOf(letter), 4 + letter - 'A');
        }
        for (int digit = 1; digit <= 9; digit++) {
            keyboard(String.valueOf(digit), 29 + digit);
        }
        keyboard("0", InputKeys.DIGIT_0);
        for (int function = 1; function <= 24; function++) {
            int physical = function <= 12 ? 57 + function : 91 + function;
            keyboard("F" + function, physical);
        }

        keyboard("SPACE", InputKeys.SPACE);
        keyboard("TAB", InputKeys.TAB);
        keyboard("ENTER", InputKeys.ENTER, "RETURN");
        keyboard("ESCAPE", InputKeys.ESCAPE, "ESC");
        keyboard("BACKSPACE", InputKeys.BACKSPACE);
        keyboard("DELETE", InputKeys.DELETE);
        keyboard("INSERT", InputKeys.INSERT);
        keyboard("HOME", InputKeys.HOME);
        keyboard("END", InputKeys.END);
        keyboard("PAGE_UP", InputKeys.PAGE_UP, "PGUP");
        keyboard("PAGE_DOWN", InputKeys.PAGE_DOWN, "PGDN");
        keyboard("UP", InputKeys.UP);
        keyboard("DOWN", InputKeys.DOWN);
        keyboard("LEFT", InputKeys.LEFT);
        keyboard("RIGHT", InputKeys.RIGHT);
        keyboard("LEFT_SHIFT", InputKeys.LEFT_SHIFT, "LSHIFT", "SHIFT");
        keyboard("RIGHT_SHIFT", InputKeys.RIGHT_SHIFT, "RSHIFT");
        keyboard("LEFT_CTRL", InputKeys.LEFT_CONTROL, "LEFT_CONTROL", "LCTRL", "CTRL", "CONTROL");
        keyboard("RIGHT_CTRL", InputKeys.RIGHT_CONTROL, "RIGHT_CONTROL", "RCTRL");
        keyboard("LEFT_ALT", InputKeys.LEFT_ALT, "LALT", "ALT");
        keyboard("RIGHT_ALT", InputKeys.RIGHT_ALT, "RALT");
        keyboard("LEFT_SUPER", InputKeys.LEFT_SUPER, "LEFT_WIN", "LWIN", "SUPER", "WIN");
        keyboard("RIGHT_SUPER", InputKeys.RIGHT_SUPER, "RIGHT_WIN", "RWIN");
        keyboard("CAPS_LOCK", InputKeys.CAPS_LOCK);
        keyboard("SCROLL_LOCK", InputKeys.SCROLL_LOCK);
        keyboard("NUM_LOCK", InputKeys.NUM_LOCK);
        keyboard("PRINT_SCREEN", InputKeys.PRINT_SCREEN);
        keyboard("PAUSE", InputKeys.PAUSE);
        keyboard("MENU", InputKeys.MENU);
        keyboard("GRAVE_ACCENT", InputKeys.GRAVE, "GRAVE", "BACKTICK");
        keyboard("APOSTROPHE", InputKeys.APOSTROPHE);
        keyboard("BACKSLASH", InputKeys.BACKSLASH);
        keyboard("COMMA", InputKeys.COMMA);
        keyboard("EQUAL", InputKeys.EQUAL);
        keyboard("LEFT_BRACKET", InputKeys.LEFT_BRACKET);
        keyboard("RIGHT_BRACKET", InputKeys.RIGHT_BRACKET);
        keyboard("MINUS", InputKeys.MINUS);
        keyboard("PERIOD", InputKeys.PERIOD);
        keyboard("SEMICOLON", InputKeys.SEMICOLON);
        keyboard("SLASH", InputKeys.SLASH);
        keyboard("WORLD_1", InputKeys.WORLD_1);
        keyboard("WORLD_2", InputKeys.WORLD_2);

        for (int keypad = 0; keypad <= 9; keypad++) {
            int physical = keypad == 0 ? InputKeys.KP_0 : 88 + keypad;
            keyboard("KP_" + keypad, physical, "KEYPAD_" + keypad);
        }
        keyboard("KP_DECIMAL", InputKeys.KP_DECIMAL, "KEYPAD_DECIMAL");
        keyboard("KP_DIVIDE", InputKeys.KP_DIVIDE, "KEYPAD_DIVIDE");
        keyboard("KP_MULTIPLY", InputKeys.KP_MULTIPLY, "KEYPAD_MULTIPLY");
        keyboard("KP_SUBTRACT", InputKeys.KP_SUBTRACT, "KEYPAD_SUBTRACT");
        keyboard("KP_ADD", InputKeys.KP_ADD, "KEYPAD_ADD");
        keyboard("KP_ENTER", InputKeys.KP_ENTER, "KEYPAD_ENTER");
        keyboard("KP_EQUAL", 103, "KEYPAD_EQUAL");

        mouse("MOUSE_LEFT", InputKeys.MOUSE_LEFT, "MOUSE1", "MOUSE_1", "MOUSE_BUTTON_1", "MB1");
        mouse("MOUSE_RIGHT", InputKeys.MOUSE_RIGHT, "MOUSE2", "MOUSE_2", "MOUSE_BUTTON_2", "MB2");
        mouse("MOUSE_MIDDLE", InputKeys.MOUSE_MIDDLE, "MOUSE3", "MOUSE_3", "MOUSE_BUTTON_3", "MB3");
        for (int button = 4; button <= 8; button++) {
            mouse("MOUSE_" + button, button, "MOUSE" + button, "MOUSE_BUTTON_" + button, "MB" + button);
        }

        alias("KEY.KEYBOARD.LEFT.CONTROL", "LEFT_CTRL");
        alias("KEY.KEYBOARD.RIGHT.CONTROL", "RIGHT_CTRL");
        alias("KEY.KEYBOARD.LEFT.WIN", "LEFT_SUPER");
        alias("KEY.KEYBOARD.RIGHT.WIN", "RIGHT_SUPER");
        for (int keypad = 0; keypad <= 9; keypad++) {
            alias("KEY.KEYBOARD.KEYPAD." + keypad, "KP_" + keypad);
        }
        alias("KEY.KEYBOARD.KEYPAD.ADD", "KP_ADD");
        alias("KEY.KEYBOARD.KEYPAD.DECIMAL", "KP_DECIMAL");
        alias("KEY.KEYBOARD.KEYPAD.DIVIDE", "KP_DIVIDE");
        alias("KEY.KEYBOARD.KEYPAD.ENTER", "KP_ENTER");
        alias("KEY.KEYBOARD.KEYPAD.EQUAL", "KP_EQUAL");
        alias("KEY.KEYBOARD.KEYPAD.MULTIPLY", "KP_MULTIPLY");
        alias("KEY.KEYBOARD.KEYPAD.SUBTRACT", "KP_SUBTRACT");
    }

    private InputKeyCatalog() {
    }

    private static void keyboard(String canonicalName, int physicalKey, String... aliases) {
        register(new InputKey(physicalKey, physicalKey, InputDevice.KEYBOARD, canonicalName), aliases);
    }

    private static void mouse(String canonicalName, int button, String... aliases) {
        register(new InputKey(button, button, InputDevice.MOUSE, canonicalName), aliases);
    }

    private static void register(InputKey key, String... aliases) {
        BY_NAME.put(normalize(key.canonicalName()), key);
        BY_PHYSICAL.put(new PhysicalId(key.device(), key.physicalKey()), key);
        for (String alias : aliases) BY_NAME.put(normalize(alias), key);

        if (key.device() == InputDevice.KEYBOARD) {
            String translation = "KEY.KEYBOARD." + key.canonicalName().toLowerCase(Locale.ROOT).replace('_', '.');
            BY_NAME.put(normalize(translation), key);
        } else {
            String suffix = switch (key.physicalKey()) {
                case InputKeys.MOUSE_LEFT -> "LEFT";
                case InputKeys.MOUSE_RIGHT -> "RIGHT";
                case InputKeys.MOUSE_MIDDLE -> "MIDDLE";
                default -> Integer.toString(key.physicalKey());
            };
            BY_NAME.put("KEY.MOUSE." + suffix, key);
        }
    }

    private static void alias(String alias, String canonicalName) {
        InputKey key = BY_NAME.get(normalize(canonicalName));
        if (key != null) BY_NAME.put(normalize(alias), key);
    }

    public static InputKey keyboardEvent(int physicalKey, int shortcutKey) {
        InputKey known = BY_PHYSICAL.get(new PhysicalId(InputDevice.KEYBOARD, physicalKey));
        String canonical = known != null ? known.canonicalName() : "UNKNOWN";
        return new InputKey(physicalKey, shortcutKey, InputDevice.KEYBOARD, canonical);
    }

    public static InputKey mouseEvent(int button) {
        InputKey known = BY_PHYSICAL.get(new PhysicalId(InputDevice.MOUSE, button));
        String canonical = known != null ? known.canonicalName() : "MOUSE_" + button;
        return new InputKey(button, button, InputDevice.MOUSE, canonical);
    }

    public static InputKey fromName(String name) {
        if (name == null) return null;
        String normalized = normalize(name);
        if (normalized.isEmpty() || normalized.equals("NONE")) return null;

        InputKey known = BY_NAME.get(normalized);
        if (known != null) return known;
        if (normalized.startsWith("SCANCODE_")) {
            Integer value = parseInteger(normalized.substring("SCANCODE_".length()));
            if (value != null && value >= 0) {
                return new InputKey(value, value, InputDevice.KEYBOARD, "UNKNOWN");
            }
        }
        return null;
    }

    public static InputChord parseChord(String serialized) {
        if (serialized == null || serialized.isBlank()) return new InputChord(List.of());
        Map<PhysicalId, InputKey> unique = new LinkedHashMap<>();
        for (String part : serialized.split("\\+")) {
            InputKey key = fromName(part);
            if (key != null) unique.put(new PhysicalId(key.device(), key.physicalKey()), key);
        }
        return new InputChord(new ArrayList<>(unique.values()));
    }

    public static boolean isValidChord(String serialized) {
        if (serialized == null || serialized.isBlank()) return false;
        if (serialized.trim().equalsIgnoreCase("NONE")) return true;
        for (String part : serialized.split("\\+", -1)) {
            if (fromName(part) == null) return false;
        }
        return true;
    }

    public static String serializeChord(Iterable<InputKey> keys) {
        if (keys == null) return "NONE";
        LinkedHashSet<InputKey> unique = new LinkedHashSet<>();
        keys.forEach(key -> {
            if (key != null && !key.canonicalName().equals("UNKNOWN")) unique.add(key);
        });
        if (unique.isEmpty()) return "NONE";

        List<InputKey> sorted = new ArrayList<>(unique);
        sorted.sort((left, right) -> {
            int rank = Integer.compare(sortRank(left), sortRank(right));
            return rank != 0 ? rank : left.canonicalName().compareTo(right.canonicalName());
        });
        return String.join("+", sorted.stream().map(InputKey::canonicalName).toList());
    }

    private static int sortRank(InputKey key) {
        if (InputKeys.isModifier(key)) return 0;
        return key.device() == InputDevice.MOUSE ? 2 : 1;
    }

    private static Integer parseInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String normalize(String name) {
        return name.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private record PhysicalId(InputDevice device, int key) {
    }
}
