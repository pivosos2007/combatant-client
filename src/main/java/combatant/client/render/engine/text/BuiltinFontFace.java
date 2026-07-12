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

public class BuiltinFontFace extends FontFace {
    private final String name;
    private final boolean atlasOnly;

    public BuiltinFontFace(FontInfo info, String name) {
        this(info, name, false);
    }

    public BuiltinFontFace(FontInfo info, String name, boolean atlasOnly) {
        super(info);
        this.name = name;
        this.atlasOnly = atlasOnly;
    }

    @Override
    public InputStream toStream() {
        if (atlasOnly) {
            throw new RuntimeException("Builtin font " + name + " is atlas-only.");
        }
        InputStream in = FontUtils.streamBuiltin(name);
        if (in == null) throw new RuntimeException("Failed to load builtin font " + name + ".");
        return in;
    }

    @Override
    public boolean isAtlasOnly() {
        return atlasOnly;
    }

    @Override
    public String toString() {
        return super.toString() + " (builtin)";
    }
}
