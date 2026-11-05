/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
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
                        String atlas, String profile, String bodyMode,
                        List<String> renderGroups, List<String> clothingSprites, List<String> clothingParts,
                        List<String> skinParts) {
        public Entry(String id, String name, String model, String texture, String icon,
                     String atlas, String profile) {
            this(id, name, model, texture, icon, atlas, profile, "replace", List.of(), List.of(), List.of(), List.of());
        }

        public boolean usesPlayerSkin() { return "player_skin".equals(bodyMode); }
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
                if (entry.clothingSprites != null && !entry.clothingSprites.isEmpty()
                        && !"player_skin".equals(bodyMode(entry.bodyMode)))
                    throw new IOException("Clothing sprites require player_skin body mode");
                if (entry.clothingSprites != null && !entry.clothingSprites.isEmpty()
                        && (entry.clothingParts == null || entry.clothingParts.isEmpty()))
                    throw new IOException("Clothing sprites require explicit clothingParts");
                if (entry.clothingParts != null && !entry.clothingParts.isEmpty()
                        && (entry.clothingSprites == null || entry.clothingSprites.isEmpty()))
                    throw new IOException("clothingParts require clothingSprites");
                if ("replace".equals(bodyMode(entry.bodyMode)) && entry.skinParts != null && !entry.skinParts.isEmpty())
                    throw new IOException("Skin render parts require player_skin body mode");
                if ("replace".equals(bodyMode(entry.bodyMode))
                        && entry.renderGroups != null && !entry.renderGroups.isEmpty())
                    throw new IOException("Render groups require player_skin body mode");
                result.add(new Entry(identifier, entry.name,
                        qualified(entry.model, catalog.getNamespace()), qualified(entry.texture, catalog.getNamespace()),
                        optional(entry.icon, catalog.getNamespace()), qualified(entry.atlas, catalog.getNamespace()),
                        optional(entry.profile, catalog.getNamespace()),
                        bodyMode(entry.bodyMode), groups(entry.renderGroups),
                        sprites(entry.clothingSprites, catalog.getNamespace()), skinParts(entry.clothingParts),
                        skinParts(entry.skinParts)));
            }
            return List.copyOf(result);
        } catch (RuntimeException failure) { throw new IOException("Invalid player model catalog: " + catalog, failure); }
    }

    private static String bodyMode(String mode) throws IOException {
        if (mode == null || mode.isBlank() || mode.equals("replace")) return "replace";
        if (mode.equals("player_skin")) return mode;
        throw new IOException("Unknown model body mode: " + mode);
    }

    private static List<String> groups(List<String> names) throws IOException {
        if (names == null || names.isEmpty()) return List.of();
        if (names.size() > 64) throw new IOException("Too many render groups");
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String name : names) {
            if (name == null || !name.matches("[a-zA-Z0-9_.-]{1,128}") || !result.add(name))
                throw new IOException("Invalid or repeated model render group: " + name);
        }
        return List.copyOf(result);
    }

    private static List<String> skinParts(List<String> parts) throws IOException {
        if (parts == null || parts.isEmpty()) return List.of();
        Set<String> allowed = Set.of("head", "hat", "body", "jacket", "left_arm", "right_arm",
                "left_sleeve", "right_sleeve", "left_leg", "right_leg", "left_pants", "right_pants");
        if (parts.size() > allowed.size() || !allowed.containsAll(parts)
                || new HashSet<>(parts).size() != parts.size())
            throw new IOException("Invalid clothing render part list");
        return List.copyOf(parts);
    }

    private static List<String> sprites(List<String> paths, String namespace) throws IOException {
        if (paths == null || paths.isEmpty()) return List.of();
        if (paths.size() > 4) throw new IOException("Too many clothing sprites");
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String path : paths) {
            String qualified = qualified(path, namespace);
            if (!qualified.endsWith(".png") || !result.add(qualified))
                throw new IOException("Invalid or repeated clothing sprite: " + path);
        }
        return List.copyOf(result);
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
            }
        }
        cachedResources = resources;
        return cached = new Collection(List.copyOf(entries.values()), List.copyOf(errors));
    }

    public static void invalidate() { cached = null; cachedResources = null; }
}
