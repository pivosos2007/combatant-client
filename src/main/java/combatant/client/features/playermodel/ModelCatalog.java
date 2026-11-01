/* Combatant Client (GPL-3.0), Copyright (c) 2026 pivosos2007. */
package combatant.client.features.playermodel;

import com.google.gson.Gson;
import combatant.client.render.engine.asset.model.io.ModelResourceReader;
import combatant.client.render.engine.asset.model.io.ModelResourceResolver;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ModelCatalog {
    public static final String DIRECTORY = "combatant/player_models";
    public record Entry(String id, String name, String model, String texture, String icon,
                        String atlas, String profile) {
        public String geometryId() { return model; }
        public String label() { return name == null || name.isBlank() ? ModelCatalog.id(id).getPath().replace('_', ' ') : name; }
    }
    private record Manifest(int format, List<Entry> entries) {}
    public record Collection(List<Entry> entries, List<String> errors) {}
    private static Collection cached;
    private static ResourceManager cachedResources;

    private ModelCatalog() {}
    public static Identifier id(String path) { return Identifier.parse(path); }

    public static byte[] read(ModelResourceResolver resolver, String path) throws IOException {
        try (var input = resolver.open(id(path))) {
            return ModelResourceReader.read(input, 32 * 1024 * 1024, path);
        }
    }

    public static List<Entry> load(ModelResourceResolver resolver, Identifier catalog) throws IOException {
        try (var input = resolver.open(catalog)) {
            Manifest manifest = new Gson().fromJson(new String(ModelResourceReader.read(input, 512 * 1024,
                    catalog.toString()), StandardCharsets.UTF_8), Manifest.class);
            if (manifest == null || manifest.format != 1 || manifest.entries == null || manifest.entries.size() > 256)
                throw new IOException("Unsupported or invalid player model catalog: " + catalog);
            Set<String> ids = new HashSet<>();
            List<Entry> result = new ArrayList<>();
            for (Entry entry : manifest.entries) {
                if (entry == null) throw new IOException("Null model entry");
                String identifier = qualified(entry.id, catalog.getNamespace());
                if (!id(identifier).getNamespace().equals(catalog.getNamespace()) || !ids.add(identifier))
                    throw new IOException("Duplicate or foreign model identifier: " + identifier);
                if (entry.name != null && entry.name.length() > 160) throw new IOException("Model name too long");
                result.add(new Entry(identifier, entry.name,
                        qualified(entry.model, catalog.getNamespace()), qualified(entry.texture, catalog.getNamespace()),
                        optional(entry.icon, catalog.getNamespace()), qualified(entry.atlas, catalog.getNamespace()),
                        optional(entry.profile, catalog.getNamespace())));
            }
            return List.copyOf(result);
        } catch (RuntimeException failure) { throw new IOException("Invalid player model catalog: " + catalog, failure); }
    }

    private static String optional(String path, String namespace) throws IOException {
        return path == null ? null : qualified(path, namespace);
    }
    private static String qualified(String path, String namespace) throws IOException {
        if (path == null || path.isBlank() || path.length() > 512 || path.contains("..") || path.startsWith("/"))
            throw new IOException("Invalid player model resource: " + path);
        Identifier value = Identifier.parse(path.contains(":") ? path : namespace + ":" + path);
        if (value.getPath().startsWith("/")) throw new IOException("Absolute model resource path");
        return value.toString();
    }

    public static Collection discover(ResourceManager resources) {
        if (cached != null && cachedResources == resources) return cached;
        Map<String, Entry> entries = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        var resolver = new combatant.client.render.engine.asset.model.io.MinecraftModelResolver(resources);
        var catalogs = resources.listResources(DIRECTORY, id -> id.getPath().endsWith(".json"));
        for (Identifier catalog : catalogs.keySet().stream().sorted(Comparator.comparing(Identifier::toString)).toList()) {
            try {
                List<Entry> loaded = load(resolver, catalog);
                for (Entry entry : loaded) if (entries.containsKey(entry.id))
                    throw new IOException("Duplicate appearance across catalogs: " + entry.id);
                for (Entry entry : loaded) entries.put(entry.id, entry);
            } catch (IOException failure) {
                errors.add(failure.getMessage());
                combatant.client.util.logging.DebugLog.warnOnChange("player-catalog:" + catalog,
                        failure.getMessage(), "[PlayerModel] %s", failure.getMessage());
            }
        }
        cachedResources = resources;
        return cached = new Collection(List.copyOf(entries.values()), List.copyOf(errors));
    }

    public static void invalidate() { cached = null; cachedResources = null; }
}
