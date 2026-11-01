/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.model.io;

import combatant.client.util.resources.ResourceAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public final class MinecraftModelResolver implements ModelResourceResolver {
    private final ResourceManager resources;
    public MinecraftModelResolver(ResourceManager resources) {
        this.resources = Objects.requireNonNull(resources, "resources");
    }
    @Override public InputStream open(Identifier id) throws IOException {
        return ResourceAccess.open(resources, id);
    }
}
