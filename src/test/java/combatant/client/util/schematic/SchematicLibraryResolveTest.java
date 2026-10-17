/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SchematicLibraryResolveTest {

    @Test
    void exactNameWithOrWithoutExtensionWins(@TempDir Path root) throws IOException {
        Path dir = folder(root, "house.litematic", "house_big.litematic");
        assertEquals("house.litematic", fileName(SchematicLibrary.resolveIn(dir, "house")));
        assertEquals("house.litematic", fileName(SchematicLibrary.resolveIn(dir, "house.litematic")));
        assertEquals("house.litematic", fileName(SchematicLibrary.resolveIn(dir, "HOUSE")));
    }

    @Test
    void uniquePrefixResolvesAndSharedPrefixIsAmbiguous(@TempDir Path root) throws IOException {
        Path dir = folder(root, "castle_a.litematic", "castle_b.litematic", "tower.litematic");
        assertEquals("tower.litematic", fileName(SchematicLibrary.resolveIn(dir, "tow")));

        SchematicLibrary.Resolution shared = SchematicLibrary.resolveIn(dir, "castle");
        assertNull(shared.path());
        assertTrue(shared.ambiguous());
        assertEquals(List.of("castle_a", "castle_b"), shared.candidates().stream().sorted().toList());

        SchematicLibrary.Resolution none = SchematicLibrary.resolveIn(dir, "bridge");
        assertNull(none.path());
        assertFalse(none.ambiguous());
    }

    @Test
    void traversalOutsideTheFolderIsRejected(@TempDir Path root) throws IOException {
        Path dir = folder(root, "inside.litematic");
        Files.writeString(root.resolve("secret.litematic"), "x");
        assertNull(SchematicLibrary.resolveIn(dir, "../secret").path());
        assertNull(SchematicLibrary.resolveIn(dir, "../secret.litematic").path());
        assertNull(SchematicLibrary.resolveIn(dir, root.resolve("secret.litematic").toString()).path());
        assertNull(SchematicLibrary.resolveIn(dir, "").path());
        assertNull(SchematicLibrary.resolveIn(dir, null).path());
    }

    @Test
    void symlinkPointingOutsideTheFolderIsRejected(@TempDir Path root) throws IOException {
        Path dir = folder(root, "inside.litematic");
        Path outside = Files.writeString(root.resolve("outside.litematic"), "x");
        Path link = dir.resolve("escape.litematic");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            // Windows without developer mode cannot create symlinks; nothing to check there.
            assumeTrue(false, "symlinks unavailable: " + e);
        }
        assertNull(SchematicLibrary.resolveIn(dir, "escape").path());
        assertNull(SchematicLibrary.resolveIn(dir, "esc").path());
    }

    private static Path folder(Path root, String... files) throws IOException {
        Path dir = Files.createDirectories(root.resolve("schematics"));
        for (String file : files) Files.writeString(dir.resolve(file), "x");
        return dir;
    }

    private static String fileName(SchematicLibrary.Resolution resolution) {
        assertTrue(resolution.path() != null, "expected a match, got " + resolution);
        return resolution.path().getFileName().toString();
    }
}
