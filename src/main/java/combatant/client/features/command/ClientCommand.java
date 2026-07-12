/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.command;

import java.util.List;

public interface ClientCommand {
    String name();

    default List<String> aliases() {
        return List.of();
    }

    default String usage() {
        return name();
    }

    default boolean isAvailable() {
        return true;
    }

    default List<String> suggest(CommandContext ctx, int argIndex, String token) {
        return List.of();
    }

    boolean execute(CommandContext ctx);
}
