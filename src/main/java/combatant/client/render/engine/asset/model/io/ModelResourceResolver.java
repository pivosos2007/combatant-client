/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.model.io;

import net.minecraft.resources.Identifier;
import java.io.IOException;
import java.io.InputStream;

/** Namespace-scoped resource source shared by every imported model backend. */
@FunctionalInterface
public interface ModelResourceResolver {
    InputStream open(Identifier id) throws IOException;
}
