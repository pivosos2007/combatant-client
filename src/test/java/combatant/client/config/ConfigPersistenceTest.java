package combatant.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import combatant.client.config.persist.AtomicFileStore;
import combatant.client.config.persist.ConfigDocument;
import combatant.client.config.persist.ConfigWriteScheduler;
import combatant.client.config.profile.ConfigProfileBinaryCodec;
import combatant.client.config.profile.ConfigProfileEntry;
import combatant.client.config.profile.ConfigProfileMeta;
import combatant.client.config.profile.ConfigProfileSnapshot;
import combatant.client.config.profile.ConfigProfileType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConfigPersistenceTest {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AtomicFileStore.Validator OBJ =
            b -> ConfigDocument.parseObject(b) != null;

    @TempDir
    Path dir;

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static String s(Path p) throws IOException {
        return Files.readString(p);
    }

    @Test
    void atomicWriteRotatesBackupAndLeavesNoTemp() throws IOException {
        Path f = dir.resolve("a.json");
        AtomicFileStore.write(f, b("{\"v\":1}"), OBJ);
        assertFalse(Files.exists(AtomicFileStore.backupOf(f)));
        AtomicFileStore.write(f, b("{\"v\":2}"), OBJ);
        AtomicFileStore.write(f, b("{\"v\":3}"), OBJ);
        assertEquals("{\"v\":3}", s(f));
        assertEquals("{\"v\":2}", s(AtomicFileStore.backupOf(f)));
        try (var list = Files.list(dir)) {
            assertEquals(2, list.count());
        }
    }

    @Test
    void corruptBackupNeverReplacesGoodBackup() throws IOException {
        Path f = dir.resolve("a.json");
        AtomicFileStore.write(f, b("{\"v\":1}"), OBJ);
        AtomicFileStore.write(f, b("{\"v\":2}"), OBJ);
        Files.writeString(f, "{\"v\":"); // corrupt primary
        AtomicFileStore.write(f, b("{\"v\":3}"), OBJ);
        assertEquals("{\"v\":1}", s(AtomicFileStore.backupOf(f)));
    }

    @Test
    void corruptPrimaryRecoversFromBackupAndQuarantines() throws IOException {
        Path f = dir.resolve("a.json");
        AtomicFileStore.write(f, b("{\"v\":1}"), OBJ);
        AtomicFileStore.write(f, b("{\"v\":2}"), OBJ);
        Files.writeString(f, "garbage!!");
        AtomicFileStore.ReadResult r = AtomicFileStore.readRecovering(f, OBJ);
        assertEquals(AtomicFileStore.Origin.BACKUP, r.origin());
        assertEquals("{\"v\":1}", new String(r.bytes(), StandardCharsets.UTF_8));
        assertEquals("{\"v\":1}", s(f));
        try (var list = Files.list(dir)) {
            assertEquals(1, list.filter(p -> p.getFileName().toString().contains(AtomicFileStore.CORRUPT_INFIX)).count());
        }
    }

    @Test
    void truncatedAndEmptyFilesAreUnusableWithoutBackup() throws IOException {
        Path f = dir.resolve("t.json");
        Files.writeString(f, "{\"a\": 1, \"b\": [1, 2");
        AtomicFileStore.ReadResult r = AtomicFileStore.readRecovering(f, OBJ);
        assertEquals(AtomicFileStore.Origin.UNUSABLE, r.origin());
        assertFalse(r.found());
        assertFalse(Files.exists(f));

        Path e = dir.resolve("e.json");
        Files.writeString(e, "");
        assertEquals(AtomicFileStore.Origin.UNUSABLE, AtomicFileStore.readRecovering(e, OBJ).origin());
        assertEquals(AtomicFileStore.Origin.MISSING,
                AtomicFileStore.readRecovering(dir.resolve("none.json"), OBJ).origin());
    }

    @Test
    void schedulerCoalescesManySavesIntoOneWrite() throws Exception {
        AtomicInteger writes = new AtomicInteger();
        Path f = dir.resolve("c.json");
        ConfigWriteScheduler sched = new ConfigWriteScheduler(80, (p, d) -> {
            writes.incrementAndGet();
            AtomicFileStore.write(p, d.render(AtomicFileStore.readIfExists(p)), d::isValid);
        });
        for (int i = 0; i < 50; i++) {
            sched.schedule(f, ConfigDocument.json(GSON, "{\"n\":" + i + "}", Set.of()));
        }
        assertEquals(1, sched.pendingCount());
        long deadline = System.currentTimeMillis() + 5000;
        while (sched.pendingCount() > 0 && System.currentTimeMillis() < deadline) Thread.sleep(10);
        Thread.sleep(150);
        assertEquals(1, writes.get());
        assertTrue(s(f).contains("49"));
    }

    @Test
    void flushAllWritesPendingSynchronously() throws IOException {
        Path f = dir.resolve("f.json");
        ConfigWriteScheduler sched = new ConfigWriteScheduler(60_000, ConfigWriteScheduler.fileSink());
        sched.schedule(f, ConfigDocument.json(GSON, "{\"x\":1}", Set.of()));
        assertFalse(Files.exists(f));
        sched.flushAll();
        assertEquals(0, sched.pendingCount());
        assertTrue(s(f).contains("\"x\""));
    }

    @Test
    void unknownFieldsAreWrittenBack() throws IOException {
        Path f = dir.resolve("u.json");
        Files.writeString(f, "{\"known\":1,\"addonKey\":{\"deep\":[1,2]},\"old\":5}");
        ConfigWriteScheduler sched = new ConfigWriteScheduler(10, ConfigWriteScheduler.fileSink());
        sched.writeNow(f, ConfigDocument.json(GSON, "{\"known\":2}", Set.of("old")));
        var root = ConfigDocument.parseObject(Files.readAllBytes(f));
        assertEquals(2, root.get("known").getAsInt());
        assertEquals(2, root.getAsJsonObject("addonKey").getAsJsonArray("deep").size());
        assertFalse(root.has("old"));
    }

    private static ConfigProfileSnapshot snapshot(String savedBy) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("enabled", true);
        Map<String, Object> addonValues = new LinkedHashMap<>();
        addonValues.put("speed", 1.5d);
        addonValues.put("names", List.of("a", "b"));
        ConfigProfileMeta meta = new ConfigProfileMeta("p", "P", ConfigProfileType.MODULES, "me", 1L, 2L, 1)
                .withCombatantVersion(savedBy);
        return new ConfigProfileSnapshot(meta, List.of(
                new ConfigProfileEntry("module:x", "X", values),
                new ConfigProfileEntry("addon:foo", "Foo", addonValues)));
    }

    @Test
    void oldProfileWithoutVersionLoads() throws IOException {
        ConfigProfileBinaryCodec codec = new ConfigProfileBinaryCodec();
        byte[] bytes = codec.write(snapshot(""));
        ConfigProfileSnapshot read = codec.read(bytes);
        assertEquals("", read.meta().combatantVersion());
        assertEquals("", codec.readMeta(bytes).combatantVersion());
        assertEquals(2, read.entries().size());
    }

    @Test
    void newProfileWritesVersionAndKeepsAddonData() throws IOException {
        ConfigProfileBinaryCodec codec = new ConfigProfileBinaryCodec();
        byte[] bytes = codec.write(snapshot("0.3.0"));
        ConfigProfileSnapshot read = codec.read(bytes);
        assertEquals("0.3.0", read.meta().combatantVersion());
        assertEquals("0.3.0", codec.readMeta(bytes).combatantVersion());
        ConfigProfileEntry addon = read.entries().get(1);
        assertEquals("addon:foo", addon.ownerId());
        assertEquals(1.5d, addon.values().get("speed"));
        assertEquals(List.of("a", "b"), addon.values().get("names"));
        assertEquals(snapshot("0.3.0").entries().get(0).values(), read.entries().get(0).values());
    }

    @Test
    void failedQuarantineNeverRotatesCorruptPrimaryIntoBackup() throws IOException {
        Path f = dir.resolve("q.json");
        AtomicFileStore.write(f, b("{\"v\":1}"), OBJ);
        AtomicFileStore.write(f, b("{\"v\":2}"), OBJ);
        Files.writeString(f, "{broken");
        AtomicFileStore.quarantineOverride = p -> false;
        try {
            AtomicFileStore.ReadResult r = AtomicFileStore.readRecovering(f, OBJ);
            assertEquals("{\"v\":1}", new String(r.bytes(), StandardCharsets.UTF_8));
            assertEquals("{\"v\":1}", s(AtomicFileStore.backupOf(f)));
            assertEquals("{broken", s(f));
        } finally {
            AtomicFileStore.quarantineOverride = null;
        }
        // a later save must also not rotate the corrupt primary over the good backup
        AtomicFileStore.write(f, b("{\"v\":3}"), OBJ);
        assertEquals("{\"v\":1}", s(AtomicFileStore.backupOf(f)));
    }
}
