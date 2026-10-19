/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc.serverutil;

import combatant.client.features.module.modules.misc.serverutil.AuthPromptDetector.Prompt;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerChatParsersTest {

    private static final TeleportRequestParser DEFAULTS = TeleportRequestParser.of(List.of(), true);

    @Test
    void parsesCommonTeleportRequests() {
        assertEquals("Steve", DEFAULTS.senderOf("Steve has requested to teleport to you."));
        assertEquals("Alex_1", DEFAULTS.senderOf("Alex_1 has requested that you teleport to them."));
        assertEquals("Steve", DEFAULTS.senderOf("§eSteve §6wants to teleport to you"));
        assertEquals("Notch", DEFAULTS.senderOf("[VIP] Notch sent you a teleport request"));
        assertEquals("Bob", DEFAULTS.senderOf("Teleport request from Bob"));
    }

    @Test
    void ignoresUnrelatedLines() {
        assertNull(DEFAULTS.senderOf("Steve: please tp me"));
        assertNull(DEFAULTS.senderOf(""));
        assertNull(DEFAULTS.senderOf(null));
    }

    @Test
    void customPatternsUseNamedOrFirstGroup() {
        TeleportRequestParser parser = TeleportRequestParser.of(
                List.of("TPA from (\\w+)", "(?<name>\\w+) asks to join you"), false);
        assertEquals("Zed", parser.senderOf("TPA from Zed"));
        assertEquals("Amy", parser.senderOf("Amy asks to join you"));
        assertNull(parser.senderOf("Steve has requested to teleport to you"));
    }

    @Test
    void invalidCustomPatternIsSkippedNotFatal() {
        TeleportRequestParser parser = TeleportRequestParser.of(List.of("([unclosed", "TPA from (\\w+)"), false);
        assertEquals(1, parser.patternCount());
        assertEquals("Zed", parser.senderOf("TPA from Zed"));
        assertNotNull(TeleportRequestParser.validate("([unclosed"));
        assertNull(TeleportRequestParser.validate("ok(\\w+)"));
    }

    @Test
    void rejectsNamesThatAreNotPlayerNames() {
        TeleportRequestParser parser = TeleportRequestParser.of(List.of("req: (.+)"), false);
        assertNull(parser.senderOf("req: not a valid name!"));
        assertEquals("Ok_Name", parser.senderOf("req: Ok_Name"));
    }

    @Test
    void detectsLoginAndRegisterPrompts() {
        assertEquals(Prompt.LOGIN, AuthPromptDetector.detect("Please login with /login <password>"));
        assertEquals(Prompt.REGISTER, AuthPromptDetector.detect("§cPlease register with /register <password> <password>"));
        assertEquals(Prompt.NONE, AuthPromptDetector.detect("You have successfully logged in."));
        assertEquals(Prompt.NONE, AuthPromptDetector.detect("Welcome to the lobby/ shop"));
        assertEquals(Prompt.LOGIN, AuthPromptDetector.detect("Use /login or /register"));
        assertEquals(Prompt.REGISTER, AuthPromptDetector.detect("Use /register, or /login if registered"));
    }

    @Test
    void registerConfirmationDetection() {
        assertTrue(AuthPromptDetector.registerNeedsConfirmation("/register <password> <confirmPassword>"));
        assertTrue(AuthPromptDetector.registerNeedsConfirmation("Please register: /register <pass> <pass>"));
        assertTrue(AuthPromptDetector.registerNeedsConfirmation("Repeat your password"));
        assertFalse(AuthPromptDetector.registerNeedsConfirmation("Please /register <password> to continue"));
    }

    @Test
    void buildsAuthCommandsAndRefusesUnsafePasswords() {
        assertEquals("login hunter2", AuthPromptDetector.buildCommand(Prompt.LOGIN, "hunter2", false));
        assertEquals("register hunter2", AuthPromptDetector.buildCommand(Prompt.REGISTER, "hunter2", false));
        assertEquals("register hunter2 hunter2", AuthPromptDetector.buildCommand(Prompt.REGISTER, "hunter2", true));
        assertNull(AuthPromptDetector.buildCommand(Prompt.NONE, "hunter2", false));
        assertNull(AuthPromptDetector.buildCommand(Prompt.LOGIN, "", false));
        assertNull(AuthPromptDetector.buildCommand(Prompt.LOGIN, "two words", false));
        assertNull(AuthPromptDetector.buildCommand(Prompt.LOGIN, "bad\nline", false));
    }

    @Test
    void keywordMatchingIsCaseInsensitiveAndStripsFormatting() {
        assertEquals("staff", ServerChatText.firstKeyword("§cA STAFF member joined", List.of("admin", "staff")));
        assertNull(ServerChatText.firstKeyword("nothing here", List.of("admin")));
        assertNull(ServerChatText.firstKeyword("anything", List.of("  ", "")));
        assertNull(ServerChatText.firstKeyword(null, List.of("x")));
    }
}
