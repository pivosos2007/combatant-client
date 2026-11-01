/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Portions of the relative-path resolver were moved from the GFBS-glTF-derived JgltfAssetLoader.
 * See THIRD_PARTY_LICENSES/GFBS-glTF-MIT.txt.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.model.io;

import net.minecraft.resources.Identifier;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.Deque;

/** Resolves references inside one resource namespace. Rejects schemes, queries and root escapes. */
public final class ModelResourcePaths {
    private ModelResourcePaths() {}

    public static Identifier sibling(Identifier base, String relative) throws IOException {
        if (relative == null || relative.isBlank()) throw new IOException("Empty model resource reference");
        final URI uri;
        try { uri = URI.create(relative.replace('\\', '/')); }
        catch (IllegalArgumentException ex) { throw new IOException("Invalid model resource URI: " + relative, ex); }
        if (uri.isAbsolute() || uri.getRawAuthority() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IOException("External model resource URI is forbidden: " + relative);
        }
        String path = uri.getPath();
        if (path == null || path.isBlank() || path.startsWith("/")) {
            throw new IOException("Model reference must be relative: " + relative);
        }
        String root = base.getPath();
        String parent = root.contains("/") ? root.substring(0, root.lastIndexOf('/') + 1) : "";
        Deque<String> parts = new ArrayDeque<>();
        for (String part : (parent + path).split("/")) {
            if (part.isBlank() || part.equals(".")) continue;
            if (part.equals("..")) {
                if (parts.isEmpty()) throw new IOException("Model resource escapes its namespace: " + relative);
                parts.removeLast();
            } else parts.addLast(part);
        }
        if (parts.isEmpty()) throw new IOException("Model resource resolves to namespace root");
        try { return Identifier.fromNamespaceAndPath(base.getNamespace(), String.join("/", parts)); }
        catch (RuntimeException ex) { throw new IOException("Invalid model resource path: " + relative, ex); }
    }
}
