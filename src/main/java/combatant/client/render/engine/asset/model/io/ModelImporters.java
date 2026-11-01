/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Registry shape adapted from GFBS-glTF 1.5.1
 * (commit 8906d4083c23b5d5bbf5191a0555d149a5272900).
 * Copyright (c) 2026 LytharaLab. Original portions are licensed under the MIT License.
 * See THIRD_PARTY_LICENSES/GFBS-glTF-MIT.txt.
 *
 * Combatant modifications are distributed under GNU GPL v3.0 as part of Combatant.
 */
package combatant.client.render.engine.asset.model.io;

import combatant.client.render.engine.asset.model.data.ModelAsset;
import combatant.client.render.engine.asset.gltf.io.GltfAssetImporter;
import combatant.client.render.engine.asset.obj.io.ObjAssetImporter;
import combatant.client.render.engine.asset.blockbench.io.BlockbenchGeckoLibImporter;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Backend-neutral registry for model importers. Rendering ownership never enters this layer. */
public final class ModelImporters {
    private static final Map<String, ModelImporter> IMPORTERS = new LinkedHashMap<>();

    static {
        registerBuiltin(new GltfAssetImporter());
        registerBuiltin(new ObjAssetImporter());
        registerBuiltin(new BlockbenchGeckoLibImporter());
    }

    private ModelImporters() {}

    public static synchronized void register(ModelImporter importer) {
        Objects.requireNonNull(importer, "importer");
        Collection<String> extensions = Objects.requireNonNull(importer.extensions(), "extensions");
        if (extensions.isEmpty()) throw new IllegalArgumentException("Importer must declare an extension");
        for (String extension : extensions) {
            String normalized = normalize(extension);
            ModelImporter existing = IMPORTERS.get(normalized);
            if (existing != null && existing != importer) {
                throw new IllegalStateException("An importer is already registered for ." + normalized);
            }
        }
        for (String extension : extensions) IMPORTERS.put(normalize(extension), importer);
    }

    public static synchronized void replace(ModelImporter importer) {
        Objects.requireNonNull(importer, "importer");
        for (String extension : importer.extensions()) IMPORTERS.put(normalize(extension), importer);
    }

    public static synchronized boolean unregister(ModelImporter importer) {
        Objects.requireNonNull(importer, "importer");
        return IMPORTERS.entrySet().removeIf(entry -> entry.getValue() == importer);
    }

    public static synchronized Optional<ModelImporter> find(Identifier location) {
        Objects.requireNonNull(location, "location");
        String path = location.getPath().toLowerCase(Locale.ROOT);
        ModelImporter matched = null;
        int longest = -1;
        for (Map.Entry<String, ModelImporter> entry : IMPORTERS.entrySet()) {
            String suffix = "." + entry.getKey();
            if (path.endsWith(suffix) && suffix.length() > longest) {
                matched = entry.getValue();
                longest = suffix.length();
            }
        }
        return Optional.ofNullable(matched);
    }

    public static synchronized Set<String> extensions() {
        return Set.copyOf(IMPORTERS.keySet());
    }

    public static ModelAsset load(Identifier location, ModelResourceResolver resolver) throws IOException {
        ModelImporter importer = find(location).orElseThrow(() ->
                new IOException("No model importer is registered for " + location));
        return Objects.requireNonNull(importer.load(location, resolver),
                "Importer returned null for " + location);
    }

    public static boolean supports(Identifier location) {
        return find(location).isPresent();
    }

    private static void registerBuiltin(ModelImporter importer) {
        register(importer);
    }

    private static String normalize(String extension) {
        String normalized = Objects.requireNonNull(extension, "extension").trim().toLowerCase(Locale.ROOT);
        while (normalized.startsWith(".")) normalized = normalized.substring(1);
        if (normalized.isEmpty() || normalized.indexOf('/') >= 0 || normalized.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Invalid model extension: " + extension);
        }
        return normalized;
    }
}
