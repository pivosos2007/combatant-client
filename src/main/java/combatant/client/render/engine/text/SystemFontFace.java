/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on, adapted from, or implemented
 * with reference to Meteor Client
 * (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 *
 * Licensed under the GNU General Public License v3.0.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.render.engine.text;

import java.io.InputStream;
import java.nio.file.Path;

public class SystemFontFace extends FontFace {
    private final Path path;

    public SystemFontFace(FontInfo info, Path path) {
        super(info);
        this.path = path;
    }

    @Override
    public InputStream toStream() {
        if (!path.toFile().exists()) {
            throw new RuntimeException("Tried to load font that no longer exists.");
        }

        InputStream in = FontUtils.streamFile(path.toFile());
        if (in == null) throw new RuntimeException("Failed to load font from " + path + ".");
        return in;
    }

    @Override
    public String toString() {
        return super.toString() + " (" + path + ")";
    }
}
