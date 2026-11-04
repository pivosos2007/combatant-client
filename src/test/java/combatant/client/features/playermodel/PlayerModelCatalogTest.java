package combatant.client.features.playermodel;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class PlayerModelCatalogTest {
    private static java.util.List<ModelCatalog.Entry> load(String namespace, String json) throws IOException {
        return ModelCatalog.load(id -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                ModelCatalog.id(namespace + ":combatant/player_models/test.json"));
    }
    private static final String ENTRY = """
        {"id":"armor","model":"models/custom/body.obj","texture":"textures/blue.png",
         "atlas":"textures/default.png"}
        """;
    @Test void resolvesNamespaceWithoutClientPathsOrMandatoryIconsAndProfiles() throws Exception {
        var a = load("community_a", "{\"format\":1,\"entries\":[" + ENTRY + "]}").getFirst();
        var b = load("community_b", "{\"format\":1,\"entries\":[" + ENTRY + "]}").getFirst();
        assertEquals("community_a:armor", a.id());
        assertNotEquals(a.id(), b.id());
        assertEquals("community_a:models/custom/body.obj", a.model());
        assertNull(a.icon()); assertNull(a.profile());
        assertEquals("replace", a.bodyMode());
        assertFalse(a.usesPlayerSkin());
        assertTrue(a.renderGroups().isEmpty()); assertTrue(a.clothingSprites().isEmpty());
        assertEquals(0, load("empty", "{\"format\":1,\"entries\":[]}").size());
    }
    @Test void rejectsVersionsDuplicateIdsTraversalAndForeignAppearanceIds() {
        for (String entry : new String[]{ENTRY.replace("armor", "foreign:armor"),
                ENTRY.replace("models/custom/body.obj", "../body.obj"), ENTRY + "," + ENTRY}) {
            assertThrows(IOException.class, () -> load("test", "{\"format\":1,\"entries\":[" + entry + "]}"));
        }
        assertThrows(IOException.class, () -> load("test", "{\"format\":2,\"entries\":[]}"));
    }
    @Test void supportsLivePlayerSkinAndOptionalClothingSprites() throws Exception {
        String entry = ENTRY.replace("\"atlas\":", "\"bodyMode\":\"player_skin\","
                + "\"renderGroups\":[\"headModel\"],"
                + "\"clothingSprites\":[\"textures/clothes/coat.png\"],\"atlas\":");
        var model = load("test", "{\"format\":1,\"entries\":[" + entry + "]}").getFirst();
        assertTrue(model.usesPlayerSkin());
        assertEquals(java.util.List.of("headModel"), model.renderGroups());
        assertEquals(java.util.List.of("test:textures/clothes/coat.png"), model.clothingSprites());
        for (String bad : new String[]{
                entry.replace("player_skin", "unsupported"),
                entry.replace("headModel", "../other"),
                entry.replace("coat.png", "../coat.png"),
                entry.replace("coat.png", "coat.jpg"),
                entry.replace("player_skin", "replace")}) {
            assertThrows(IOException.class, () -> load("test", "{\"format\":1,\"entries\":[" + bad + "]}"));
        }
    }

    @Test void discoversMultipleAddonsIsolatesBrokenCatalogAndReloads() {
        var files = new java.util.LinkedHashMap<net.minecraft.resources.Identifier, net.minecraft.server.packs.resources.Resource>();
        java.util.function.BiConsumer<String, String> add = (id, json) -> files.put(ModelCatalog.id(id),
                new net.minecraft.server.packs.resources.Resource(null,
                        () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
        String json = "{\"format\":1,\"entries\":[" + ENTRY + "]}";
        add.accept("alpha:combatant/player_models/first.json", json);
        add.accept("beta:combatant/player_models/first.json", json);
        add.accept("broken:combatant/player_models/first.json", "{}");
        var manager = (net.minecraft.server.packs.resources.ResourceManager) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{net.minecraft.server.packs.resources.ResourceManager.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "listResources" -> files;
                    case "getResource" -> java.util.Optional.ofNullable(files.get(args[0]));
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        ModelCatalog.invalidate();
        try {
            var first = ModelCatalog.discover(manager);
            assertEquals(2, first.entries().size()); assertEquals(1, first.errors().size());
            assertNotEquals(first.entries().get(0).id(), first.entries().get(1).id());
            files.clear();
            assertSame(first, ModelCatalog.discover(manager));
            ModelCatalog.invalidate();
            assertTrue(ModelCatalog.discover(manager).entries().isEmpty());
        } finally { ModelCatalog.invalidate(); }
    }

}
