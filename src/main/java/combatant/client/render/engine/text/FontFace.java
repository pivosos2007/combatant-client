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

public abstract class FontFace {
    public final FontInfo info;

    protected FontFace(FontInfo info) {
        this.info = info;
    }

    public abstract InputStream toStream();

    public boolean isAtlasOnly() {
        return false;
    }

    @Override
    public String toString() {
        return info.toString();
    }
}
