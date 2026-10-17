/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.input;

public final class InputKeys {
    public static final int UNKNOWN = 0;
    public static final int A = 4;
    public static final int B = 5;
    public static final int C = 6;
    public static final int D = 7;
    public static final int E = 8;
    public static final int F = 9;
    public static final int G = 10;
    public static final int H = 11;
    public static final int I = 12;
    public static final int J = 13;
    public static final int K = 14;
    public static final int L = 15;
    public static final int M = 16;
    public static final int N = 17;
    public static final int O = 18;
    public static final int P = 19;
    public static final int Q = 20;
    public static final int R = 21;
    public static final int S = 22;
    public static final int T = 23;
    public static final int U = 24;
    public static final int V = 25;
    public static final int W = 26;
    public static final int X = 27;
    public static final int Y = 28;
    public static final int Z = 29;
    public static final int DIGIT_0 = 39;
    public static final int DIGIT_1 = 30;
    public static final int DIGIT_2 = 31;
    public static final int DIGIT_3 = 32;
    public static final int DIGIT_4 = 33;
    public static final int DIGIT_5 = 34;
    public static final int DIGIT_6 = 35;
    public static final int DIGIT_7 = 36;
    public static final int DIGIT_8 = 37;
    public static final int DIGIT_9 = 38;
    public static final int ENTER = 40;
    public static final int ESCAPE = 41;
    public static final int BACKSPACE = 42;
    public static final int TAB = 43;
    public static final int SPACE = 44;
    public static final int MINUS = 45;
    public static final int EQUAL = 46;
    public static final int LEFT_BRACKET = 47;
    public static final int RIGHT_BRACKET = 48;
    public static final int BACKSLASH = 49;
    public static final int WORLD_2 = 50;
    public static final int SEMICOLON = 51;
    public static final int APOSTROPHE = 52;
    public static final int GRAVE = 53;
    public static final int COMMA = 54;
    public static final int PERIOD = 55;
    public static final int SLASH = 56;
    public static final int CAPS_LOCK = 57;
    public static final int F1 = 58;
    public static final int F3 = 60;
    public static final int F8 = 65;
    public static final int F2 = 59;
    public static final int F4 = 61;
    public static final int F5 = 62;
    public static final int F6 = 63;
    public static final int F7 = 64;
    public static final int F9 = 66;
    public static final int F10 = 67;
    public static final int F11 = 68;
    public static final int F12 = 69;
    public static final int PRINT_SCREEN = 70;
    public static final int SCROLL_LOCK = 71;
    public static final int PAUSE = 72;
    public static final int INSERT = 73;
    public static final int HOME = 74;
    public static final int PAGE_UP = 75;
    public static final int DELETE = 76;
    public static final int END = 77;
    public static final int PAGE_DOWN = 78;
    public static final int RIGHT = 79;
    public static final int LEFT = 80;
    public static final int DOWN = 81;
    public static final int UP = 82;
    public static final int NUM_LOCK = 83;
    public static final int KP_DIVIDE = 84;
    public static final int KP_MULTIPLY = 85;
    public static final int KP_SUBTRACT = 86;
    public static final int KP_ADD = 87;
    public static final int KP_ENTER = 88;
    public static final int KP_1 = 89;
    public static final int KP_0 = 98;
    public static final int KP_DECIMAL = 99;
    public static final int WORLD_1 = 100;
    public static final int MENU = 118;
    public static final int LEFT_CONTROL = 224;
    public static final int LEFT_SHIFT = 225;
    public static final int LEFT_ALT = 226;
    public static final int LEFT_SUPER = 227;
    public static final int RIGHT_CONTROL = 228;
    public static final int RIGHT_SHIFT = 229;
    public static final int RIGHT_ALT = 230;
    public static final int RIGHT_SUPER = 231;

    public static final int MOUSE_LEFT = 1;
    public static final int MOUSE_MIDDLE = 2;
    public static final int MOUSE_RIGHT = 3;
    public static final int MOUSE_FIRST = 1;
    public static final int MOUSE_LAST = 8;

    private InputKeys() {
    }

    public static boolean isModifier(InputKey key) {
        if (key == null || key.device() != InputDevice.KEYBOARD) return false;
        return switch (key.physicalKey()) {
            case LEFT_CONTROL, LEFT_SHIFT, LEFT_ALT, LEFT_SUPER,
                    RIGHT_CONTROL, RIGHT_SHIFT, RIGHT_ALT, RIGHT_SUPER -> true;
            default -> false;
        };
    }
}
