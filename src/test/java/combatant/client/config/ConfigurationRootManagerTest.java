package combatant.client.config;

import combatant.client.config.storage.StorageFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationRootManagerTest {
    @TempDir Path temporary;

    @Test void migrationWaitsForRestartAndPreservesUnknownFilesAndSource() throws Exception {
        Path game = temporary.resolve("instance");
        Path target = temporary.resolve("перенос configs");
        Path source;
        try (var manager = ConfigurationRootManager.open(game)) {
            source = manager.root();
            Files.createDirectories(source.resolve("addon/empty"));
            Files.writeString(source.resolve("addon/unknown.data"), "original");
            manager.schedule(target);
            assertEquals(source, manager.root());
            assertFalse(Files.exists(target));
            Files.writeString(source.resolve("late-save.json"), "saved before shutdown");
            assertThrows(IOException.class, () -> manager.schedule(temporary.resolve("another")));
        }
        try (var manager = ConfigurationRootManager.open(game)) {
            assertEquals(target, manager.root());
            assertEquals("COMMITTED", manager.migrationState());
            assertEquals("original", Files.readString(target.resolve("addon/unknown.data")));
            assertEquals("saved before shutdown", Files.readString(target.resolve("late-save.json")));
            assertTrue(Files.isDirectory(target.resolve("addon/empty")));
            assertEquals("original", Files.readString(source.resolve("addon/unknown.data")));
        }
        try (var manager = ConfigurationRootManager.open(game)) { assertEquals(target, manager.root()); }
    }

    @Test void rejectsOverlapConflictAndAnotherInstance() throws Exception {
        try (var manager = ConfigurationRootManager.open(temporary)) {
            assertThrows(IOException.class, () -> manager.schedule(manager.root()));
            assertThrows(IOException.class, () -> manager.schedule(manager.root().resolve("nested")));
            assertThrows(IOException.class, () -> manager.schedule(manager.root().getParent()));
            Path occupied = Files.createDirectory(temporary.resolve("occupied"));
            Files.writeString(occupied.resolve("keep"), "untouched");
            assertThrows(FileAlreadyExistsException.class, () -> manager.schedule(occupied));
            assertEquals("untouched", Files.readString(occupied.resolve("keep")));
            assertThrows(IOException.class, () -> ConfigurationRootManager.open(temporary));
        }
    }

    @Test void unavailableConfiguredRootNeverFallsBackOrRecreatesDirectory() throws Exception {
        Path game = temporary.resolve("instance");
        Path custom = temporary.resolve("custom");
        try (var manager = ConfigurationRootManager.open(game)) { manager.schedule(custom); }
        try (var manager = ConfigurationRootManager.open(game)) { assertEquals(custom, manager.root()); }
        Path disconnected = temporary.resolve("disconnected");
        Files.move(custom, disconnected);
        assertThrows(IOException.class, () -> ConfigurationRootManager.open(game));
        assertFalse(Files.exists(custom));
    }

    @Test void restoreDefaultRejectsPreservedBackupUntilExplicitlyFreed() throws Exception {
        Path game = temporary.resolve("instance");
        Path custom = temporary.resolve("custom");
        Path original;
        try (var manager = ConfigurationRootManager.open(game)) {
            original = manager.root();
            Files.writeString(original.resolve("config.json"), "v1");
            manager.schedule(custom);
        }
        try (var manager = ConfigurationRootManager.open(game)) {
            assertThrows(FileAlreadyExistsException.class, manager::restoreDefault);
        }
        Files.move(original, original.resolveSibling("backup"));
        try (var manager = ConfigurationRootManager.open(game)) {
            Files.writeString(custom.resolve("config.json"), "v2");
            manager.restoreDefault();
        }
        try (var manager = ConfigurationRootManager.open(game)) {
            assertEquals(original, manager.root());
            assertEquals("v2", Files.readString(original.resolve("config.json")));
        }
    }

    @Test void cancelledSelectionDoesNotMoveData() throws Exception {
        try (var manager = ConfigurationRootManager.open(temporary)) {
            manager.schedule(temporary.resolve("custom"));
            manager.cancelScheduled();
        }
        try (var manager = ConfigurationRootManager.open(temporary)) {
            assertEquals(manager.defaultRoot(), manager.root());
            assertEquals("IDLE", manager.migrationState());
        }
    }

    @Test void interruptedCopyIsNeverActivated() throws Exception {
        Path destination = temporary.resolve("destination");
        Path abandoned = temporary.resolve("partial");
        try (var manager = ConfigurationRootManager.open(temporary)) {
            Files.writeString(manager.root().resolve("data"), "complete");
            manager.schedule(destination);
            Files.createDirectory(abandoned);
            Files.writeString(abandoned.resolve("data"), "partial");
            manager.metadata().setProperty("staging", abandoned.toString());
            manager.persist("COPYING");
        }
        try (var manager = ConfigurationRootManager.open(temporary)) {
            assertEquals(destination, manager.root());
            assertEquals("complete", Files.readString(destination.resolve("data")));
            assertEquals("partial", Files.readString(abandoned.resolve("data")));
        }
    }

    @Test void crashAfterRenameResumesCommitIdempotently() throws Exception {
        Path destination = temporary.resolve("destination");
        prepareCommit(destination, false);
        try (var manager = ConfigurationRootManager.open(temporary)) {
            assertEquals(destination, manager.root());
            assertEquals("COMMITTED", manager.migrationState());
        }
        try (var manager = ConfigurationRootManager.open(temporary)) { assertEquals(destination, manager.root()); }
    }

    @Test void crashBeforeRenameResumesVerifiedStaging() throws Exception {
        Path destination = temporary.resolve("destination");
        prepareCommit(destination, false);
        Path staging = temporary.resolve(".combatant-transfer-test");
        Files.move(destination, staging);
        try (var manager = ConfigurationRootManager.open(temporary)) {
            assertEquals(destination, manager.root());
            assertEquals("complete", Files.readString(destination.resolve("data")));
            assertFalse(Files.exists(staging));
        }
    }

    @Test void destinationCreatedDuringInterruptedCommitIsNeverOverwritten() throws Exception {
        Path destination = temporary.resolve("destination");
        prepareCommit(destination, false);
        Path staging = temporary.resolve(".combatant-transfer-test");
        Files.move(destination, staging);
        Files.createDirectory(destination);
        Files.writeString(destination.resolve("unrelated"), "keep");
        assertThrows(IOException.class, () -> ConfigurationRootManager.open(temporary));
        assertEquals("keep", Files.readString(destination.resolve("unrelated")));
        assertEquals("complete", Files.readString(staging.resolve("data")));
    }

    @Test @EnabledOnOs(OS.WINDOWS)
    void windowsJunctionInsideSourceIsRejected() throws Exception {
        Path outside = Files.createDirectory(temporary.resolve("outside"));
        Files.writeString(outside.resolve("data"), "keep");
        try (var manager = ConfigurationRootManager.open(temporary)) {
            Path junction = manager.root().resolve("junction");
            Process process = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", junction.toString(), outside.toString())
                    .redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes());
            assertEquals(0, process.waitFor(), output);
            try {
                assertThrows(IOException.class, () -> ConfigurationMigrationService.snapshot(manager.root()));
            } finally {
                Files.delete(junction);
            }
        }
        assertEquals("keep", Files.readString(outside.resolve("data")));
    }

    @Test @EnabledOnOs(OS.LINUX)
    void linuxSymlinkInsideSourceIsRejected() throws Exception {
        Path outside = Files.createDirectory(temporary.resolve("outside"));
        try (var manager = ConfigurationRootManager.open(temporary)) {
            Files.createSymbolicLink(manager.root().resolve("link"), outside);
            assertThrows(IOException.class, () -> ConfigurationMigrationService.snapshot(manager.root()));
        }
    }

    @Test void corruptedDestinationIsNotActivated() throws Exception {
        Path destination = temporary.resolve("destination");
        prepareCommit(destination, true);
        assertThrows(IOException.class, () -> ConfigurationRootManager.open(temporary));
        Properties metadata = StorageFiles.read(temporary.resolve("config/combatant-storage.properties"));
        assertEquals(temporary.resolve("config/combatant").toString(), metadata.getProperty("active"));
        assertEquals("complete", Files.readString(temporary.resolve("config/combatant/data")));
        ConfigurationRootManager.cancelPendingMigration(temporary);
        try (var manager = ConfigurationRootManager.open(temporary)) {
            assertEquals(manager.defaultRoot(), manager.root());
            assertEquals("IDLE", manager.migrationState());
        }
        assertEquals("corrupted", Files.readString(destination.resolve("data")));
    }

    private void prepareCommit(Path destination, boolean corrupt) throws Exception {
        try (var manager = ConfigurationRootManager.open(temporary)) {
            Files.writeString(manager.root().resolve("data"), "complete");
            manager.schedule(destination);
            Path manifest = manager.bootstrapFile().resolveSibling("test-manifest.properties");
            StorageFiles.write(manifest, ConfigurationMigrationService.snapshot(manager.root()));
            manager.metadata().setProperty("manifest", manifest.toString());
            manager.metadata().setProperty("staging", temporary.resolve(".combatant-transfer-test").toString());
            manager.persist("COMMITTING");
            Files.createDirectory(destination);
            Files.writeString(destination.resolve("data"), corrupt ? "corrupted" : "complete");
        }
    }

    @Test void corruptedBootstrapFailsClosed() throws Exception {
        Files.createDirectories(temporary.resolve("config"));
        Files.writeString(temporary.resolve("config/combatant-storage.properties"), "schema=99\nactive=relative\n");
        assertThrows(IOException.class, () -> ConfigurationRootManager.open(temporary));
        assertFalse(Files.exists(temporary.resolve("config/combatant")));
    }

    @Test void secondInstanceCannotUseSameCustomRoot() throws Exception {
        Path first = temporary.resolve("first");
        Path second = temporary.resolve("second");
        Path custom = temporary.resolve("shared");
        try (var manager = ConfigurationRootManager.open(first)) { manager.schedule(custom); }
        try (var manager = ConfigurationRootManager.open(first)) {
            Files.createDirectories(second.resolve("config"));
            Properties properties = new Properties();
            properties.setProperty("schema", "1");
            properties.setProperty("active", custom.toString());
            properties.setProperty("state", "IDLE");
            StorageFiles.write(second.resolve("config/combatant-storage.properties"), properties);
            assertThrows(IOException.class, () -> ConfigurationRootManager.open(second));
        }
    }
}
