/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic;

import java.nio.file.Path;

/** What the file browser shows before a schematic is fully parsed. {@code error} is non-empty for unreadable files. */
public record SchematicHeader(Path path,
                              String fileName,
                              String name,
                              String author,
                              int sizeX,
                              int sizeY,
                              int sizeZ,
                              long totalBlocks,
                              int regionCount,
                              long lastModified,
                              String error) {

    public boolean failed() {
        return error != null && !error.isEmpty();
    }

    public String dimensions() {
        return sizeX + "x" + sizeY + "x" + sizeZ;
    }

    public static SchematicHeader failed(Path path, long lastModified, String error) {
        String file = path.getFileName().toString();
        return new SchematicHeader(path, file, SchematicFiles.baseName(path), "", 0, 0, 0, -1, 0, lastModified,
                error == null || error.isBlank() ? "unreadable" : error);
    }
}
