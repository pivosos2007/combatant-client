/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc.serverutil;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Recognises login/registration prompts from auth plugins and builds the matching command. */
public final class AuthPromptDetector {

    public enum Prompt { NONE, LOGIN, REGISTER }

    private static final Pattern LOGIN = Pattern.compile("/(?:login|log|l)(?![a-z])");
    private static final Pattern REGISTER = Pattern.compile("/(?:register|reg)(?![a-z])");
    private static final Pattern TWO_PLACEHOLDERS =
            Pattern.compile("/(?:register|reg)\\s+\\S+\\s+(?:[<\\[(]\\S+[>\\])]|\\S*(?:pass|pw)\\S*)");

    private AuthPromptDetector() {
    }

    /** Which prompt the line is. When it mentions both commands, the one mentioned first wins. */
    public static Prompt detect(String message) {
        String body = ServerChatText.stripSection(message).toLowerCase(Locale.ROOT);
        if (body.isEmpty()) return Prompt.NONE;
        int login = indexOf(LOGIN, body);
        int register = indexOf(REGISTER, body);
        if (login < 0 && register < 0) return Prompt.NONE;
        if (login < 0) return Prompt.REGISTER;
        if (register < 0) return Prompt.LOGIN;
        return login < register ? Prompt.LOGIN : Prompt.REGISTER;
    }

    /** True when the registration prompt asks for the password twice. */
    public static boolean registerNeedsConfirmation(String message) {
        String body = ServerChatText.stripSection(message).toLowerCase(Locale.ROOT);
        if (body.contains("confirm") || body.contains("repeat") || body.contains("again")
                || body.contains("повтор") || body.contains("подтверд")) {
            return true;
        }
        return TWO_PLACEHOLDERS.matcher(body).find();
    }

    /**
     * Command line (without the leading slash) to answer the prompt, or null when it cannot be answered safely
     * (no prompt, empty password, or a password that would break the command).
     */
    public static String buildCommand(Prompt prompt, String password, boolean confirm) {
        if (prompt == null || prompt == Prompt.NONE || !isSafePassword(password)) return null;
        return switch (prompt) {
            case LOGIN -> "login " + password;
            case REGISTER -> confirm ? "register " + password + " " + password : "register " + password;
            case NONE -> null;
        };
    }

    public static boolean isSafePassword(String password) {
        if (password == null || password.isEmpty()) return false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c)) return false;
        }
        return true;
    }

    private static int indexOf(Pattern pattern, String body) {
        Matcher m = pattern.matcher(body);
        return m.find() ? m.start() : -1;
    }
}
