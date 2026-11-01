package combatant.client.util.schematic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;

class SchematicStorageTest {
    @TempDir Path temporary;

    @Test void selectionPersistsAndRestoreDoesNotMoveFiles() throws Exception {
        Path game = temporary.resolve("instance");
        SchematicStorage storage = new SchematicStorage(game);
        Path original = storage.directory();
        Path custom = Files.createDirectory(temporary.resolve("схемы library"));
        Files.writeString(custom.resolve("keep.nbt"), "keep");
        storage.select(custom);
        assertEquals(custom, new SchematicStorage(game).directory());
        storage.restoreDefault();
        assertEquals(original, storage.directory());
        assertEquals("keep", Files.readString(custom.resolve("keep.nbt")));
    }

    @Test void unavailableCustomDirectoryDoesNotCreateOrFallBack() throws Exception {
        SchematicStorage storage = new SchematicStorage(temporary.resolve("instance"));
        Path custom = Files.createDirectory(temporary.resolve("custom"));
        storage.select(custom);
        Files.delete(custom);
        assertThrows(IOException.class, storage::directory);
        assertFalse(Files.exists(custom));
    }

    @Test void duplicateImportPreservesBothFilesAndOriginal() throws Exception {
        SchematicStorage storage = new SchematicStorage(temporary.resolve("instance"));
        Path source = Files.writeString(temporary.resolve("test.nbt"), "first");
        Path first = storage.importFile(source, p -> assertEquals("first", Files.readString(p)));
        Files.writeString(source, "second");
        Path second = storage.importFile(source, p -> assertEquals("second", Files.readString(p)));
        assertNotEquals(first, second);
        assertEquals("first", Files.readString(first));
        assertEquals("second", Files.readString(second));
        assertEquals("second", Files.readString(source));
    }

    @Test void parserFailureLeavesLibraryEmpty() throws Exception {
        SchematicStorage storage = new SchematicStorage(temporary.resolve("instance"));
        Path source = Files.writeString(temporary.resolve("bad.schem"), "not NBT");
        assertThrows(IOException.class, () -> storage.importFile(source, p -> { throw new IOException("Invalid NBT"); }));
        try (var files = Files.list(storage.directory())) { assertEquals(0, files.count()); }
        assertEquals("not NBT", Files.readString(source));
    }
}
