/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic;

import combatant.client.config.storage.StorageFiles;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.Properties;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

/** Filesystem operations owned by SchematicLibrary, independent of the movable configuration root. */
final class SchematicStorage {
    private final Path defaultDirectory;
    private final Path settings;

    SchematicStorage(Path gameDirectory) {
        Path game = gameDirectory.toAbsolutePath().normalize();
        defaultDirectory = game.resolve("schematics");
        settings = game.resolve("config/combatant-schematics.properties");
    }

    private Selection selection() throws IOException {
        Properties properties = StorageFiles.read(settings);
        String custom = properties.getProperty("directory");
        Path directory;
        try {
            directory = custom == null ? defaultDirectory : Path.of(custom);
        } catch (InvalidPathException e) {
            throw new IOException("Invalid schematic directory override", e);
        }
        if (!directory.isAbsolute()) throw new IOException("Schematic directory override must be absolute");
        return new Selection(directory.normalize(), custom != null);
    }

    Path configuredDirectory() throws IOException { return selection().path(); }

    Path directory() throws IOException {
        Selection selection = selection();
        if (!selection.custom()) {
            StorageFiles.checked(selection.path());
            Files.createDirectories(selection.path());
        }
        return accessible(selection.path());
    }

    void select(Path directory) throws IOException {
        Path target = accessible(directory);
        Properties properties = new Properties();
        properties.setProperty("schema", "1");
        properties.setProperty("directory", target.toString());
        StorageFiles.write(settings, properties);
    }

    void restoreDefault() throws IOException {
        StorageFiles.checked(defaultDirectory);
        Files.createDirectories(defaultDirectory);
        accessible(defaultDirectory);
        Properties properties = new Properties();
        properties.setProperty("schema", "1");
        StorageFiles.write(settings, properties);
    }

    Path importFile(Path source, Validator validator) throws IOException {
        Path from = StorageFiles.checked(source);
        if (!Files.isRegularFile(from, NOFOLLOW_LINKS) || !Files.isReadable(from)) {
            throw new IOException("Schematic file is not readable: " + from);
        }
        Path directory = directory();
        String fileName = from.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) throw new IOException("Schematic filename has no extension");
        String stem = fileName.substring(0, dot);
        String extension = fileName.substring(dot);
        // Parse the copied bytes, not a source that could change between validation and copying.
        Path staging = Files.createTempDirectory(directory, ".combatant-import-");
        Path copied = staging.resolve(fileName);
        try {
            Files.copy(from, copied, NOFOLLOW_LINKS);
            StorageFiles.checked(copied);
            validator.validate(copied);
            try (FileChannel channel = FileChannel.open(copied, StandardOpenOption.WRITE, NOFOLLOW_LINKS)) {
                channel.force(true);
            }
            for (int number = 0; number < 10000; number++) {
                Path destination = directory.resolve(number == 0 ? fileName : stem + " (" + number + ")" + extension);
                try {
                    Files.move(copied, destination);
                    StorageFiles.forceDirectory(directory);
                    return destination;
                } catch (FileAlreadyExistsException ignored) { }
            }
            throw new IOException("Too many schematic files with the same name: " + fileName);
        } finally {
            Files.deleteIfExists(copied);
            Files.deleteIfExists(staging);
        }
    }

    private static Path accessible(Path directory) throws IOException {
        Path target = StorageFiles.checked(directory);
        if (!Files.isDirectory(target, NOFOLLOW_LINKS) || !Files.isReadable(target)) {
            throw new IOException("Schematic directory unavailable: " + target);
        }
        return target;
    }

    @FunctionalInterface interface Validator {
        void validate(Path path) throws IOException;
    }

    private record Selection(Path path, boolean custom) { }
}
