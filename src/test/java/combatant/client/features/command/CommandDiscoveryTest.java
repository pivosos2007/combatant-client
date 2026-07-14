/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.command;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandDiscoveryTest {
    @Test
    void discoversAnnotatedProductionCommandsWithoutManualRegistration() {
        List<ClientCommand> commands = CommandManager.getCommands();

        assertTrue(commands.size() >= 18);
        assertNotNull(CommandManager.find("help"));
        assertNotNull(CommandManager.find("friend"));
        assertNotNull(CommandManager.find("coords"));
        assertNull(CommandManager.find("clear"));
        assertNull(CommandManager.find("say"));
        assertNull(CommandManager.find("profiler"));

        HashSet<String> ids = new HashSet<>();
        for (ClientCommand command : commands) {
            CommandMetadata metadata = command.metadata();
            assertTrue(ids.add(metadata.id()), "duplicate command id: " + metadata.id());
            assertNotNull(command.getClass().getAnnotation(CommandInfo.class));
            assertFalse(metadata.descriptionKey().isBlank(), "missing description i18n key: " + metadata.id());
        }
    }

    @Test
    @SuppressWarnings("removal")
    void keepsLegacyV0MetadataDefaultsWorking() {
        ClientCommand legacy = new LegacyCommand();

        assertEquals("legacy", legacy.metadata().id());
        assertEquals(List.of(), legacy.metadata().aliases());
        assertEquals("legacy", legacy.metadata().usage());
        assertEquals("legacy", legacy.metadata().description());
    }

    @SuppressWarnings("removal")
    private static final class LegacyCommand implements ClientCommand {
        @Override
        public String name() {
            return "legacy";
        }

        @Override
        public boolean execute(CommandContext ctx) {
            return true;
        }
    }
}
