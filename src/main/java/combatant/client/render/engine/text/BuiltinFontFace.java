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

import net.minecraft.resources.Identifier;
import combatant.client.runtime.distribution.DistributionCapabilities;

import java.io.InputStream;

public class BuiltinFontFace extends FontFace {
    private final Identifier resource;
    private final boolean atlasOnly;

    public BuiltinFontFace(FontInfo info, String name) {
        this(info, Identifier.fromNamespaceAndPath("combatant", "font/" + name), false);
    }

    public BuiltinFontFace(FontInfo info, String name, boolean atlasOnly) {
        this(info, Identifier.fromNamespaceAndPath("combatant", "font/" + name), atlasOnly);
    }

    public BuiltinFontFace(FontInfo info, Identifier resource, boolean atlasOnly) {
        super(info);
        this.resource = resource;
        this.atlasOnly = atlasOnly || DistributionCapabilities.isAtlasOnlyBuiltinFont(resource);
    }

    @Override
    public InputStream toStream() {
        if (atlasOnly) {
            throw new RuntimeException("Builtin font " + resource + " is atlas-only.");
        }
        InputStream in = FontUtils.streamBuiltin(resource);
        if (in == null) throw new RuntimeException("Failed to load builtin font " + resource + ".");
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
