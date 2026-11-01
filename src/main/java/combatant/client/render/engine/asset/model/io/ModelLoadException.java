/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.model.io;

import net.minecraft.resources.Identifier;
import java.io.IOException;

/** Invalid or unsupported source model data. */
public final class ModelLoadException extends IOException {
    public ModelLoadException(String message) { super(message); }
    public ModelLoadException(String message, Throwable cause) { super(message, cause); }
    public ModelLoadException(Identifier asset, String message) { super("Failed to load " + asset + ": " + message); }
    public ModelLoadException(Identifier asset, String message, Throwable cause) {
        super("Failed to load " + asset + ": " + message, cause);
    }
}
