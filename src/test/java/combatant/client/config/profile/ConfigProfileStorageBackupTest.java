package combatant.client.config.profile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ConfigProfileStorageBackupTest {
    @TempDir
    Path dir;

    private static byte[] profile(String name) throws IOException {
        ConfigProfileMeta meta = new ConfigProfileMeta("p", name, ConfigProfileType.MODULES, "me", 1L, 2L, 1);
        return new ConfigProfileBinaryCodec().write(new ConfigProfileSnapshot(meta, List.of()));
    }

    @Test
    void writeOverCorruptDestinationKeepsValidBackup() throws IOException {
        ConfigProfileStorage storage = new ConfigProfileStorage();
        Path dest = dir.resolve("p.cbcfg");
        byte[] v1 = profile("one");
        storage.writeValidated(dest, v1);
        storage.writeValidated(dest, profile("two")); // .bak = v1
        Files.write(dest, new byte[]{1, 2, 3, 4}); // corrupt destination
        storage.writeValidated(dest, profile("three"));
        assertArrayEquals(v1, Files.readAllBytes(dest.resolveSibling("p.cbcfg.bak")));
    }
}
