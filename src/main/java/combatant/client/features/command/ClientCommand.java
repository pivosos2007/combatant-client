/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.command;

import java.util.List;

public interface ClientCommand {
    default CommandMetadata metadata() {
        return CommandMetadata.from(this);
    }

    /** @deprecated Use {@link CommandInfo} metadata. */
    @Deprecated(forRemoval = true)
    default String name() {
        return CommandMetadata.fromAnnotation(getClass()).id();
    }

    /** @deprecated Use {@link CommandInfo} metadata. */
    @Deprecated(forRemoval = true)
    default List<String> aliases() {
        CommandMetadata metadata = CommandMetadata.annotated(getClass());
        return metadata == null ? List.of() : metadata.aliases();
    }

    /** @deprecated Use {@link CommandInfo} metadata. */
    @Deprecated(forRemoval = true)
    @SuppressWarnings("removal")
    default String usage() {
        CommandMetadata metadata = CommandMetadata.annotated(getClass());
        return metadata == null ? name() : metadata.usage();
    }

    /** @deprecated Use {@link CommandInfo} metadata. */
    @Deprecated(forRemoval = true)
    @SuppressWarnings("removal")
    default String description() {
        CommandMetadata metadata = CommandMetadata.annotated(getClass());
        return metadata == null ? usage() : metadata.description();
    }

    default boolean isAvailable() {
        return true;
    }

    default List<String> suggest(CommandContext ctx, int argIndex, String token) {
        return List.of();
    }

    boolean execute(CommandContext ctx);
}
