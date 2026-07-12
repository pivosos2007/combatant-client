/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.command;

import net.minecraft.client.Minecraft;

import java.util.List;

public record CommandContext(Minecraft mc, String raw, String name, List<String> args) {

    public String arg(int idx) {
        if (idx < 0 || idx >= args.size()) return null;
        return args.get(idx);
    }
}
