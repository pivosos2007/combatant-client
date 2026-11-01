/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.model.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Bounded binary/text source reads for format importers. The caller closes the input. */
public final class ModelResourceReader {
    private ModelResourceReader() {}
    public static byte[] read(InputStream input, int limit, String label) throws IOException {
        if (limit <= 0) throw new IllegalArgumentException("Resource limit must be positive");
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (count == 0) continue;
            if (count > limit - total) throw new IOException(label + " exceeds " + limit + " bytes");
            total += count;
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}
