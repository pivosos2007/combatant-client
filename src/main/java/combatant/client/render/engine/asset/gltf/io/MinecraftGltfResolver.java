/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.gltf.io;

import combatant.client.util.resources.ResourceAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/** @deprecated Use MinecraftModelResolver for all supported formats. */
@Deprecated
public final class MinecraftGltfResolver implements GltfResolver {
    private final ResourceManager resources;

    public MinecraftGltfResolver(ResourceManager resources) {
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    @Override
    public InputStream open(Identifier location) throws IOException {
        return ResourceAccess.open(resources, location);
    }
}
