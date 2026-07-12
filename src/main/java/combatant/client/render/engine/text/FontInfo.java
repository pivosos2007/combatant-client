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

public record FontInfo(String family, Type type) {
    @Override
    public String toString() {
        return family + " " + type;
    }

    public boolean equals(FontInfo info) {
        if (this == info) return true;
        if (info == null || family == null || type == null) return false;
        return family.equals(info.family) && type == info.type;
    }

    public enum Type {
        Regular,
        Bold,
        Italic,
        BoldItalic;

        public static Type fromString(String str) {
            return switch (str) {
                case "Bold" -> Bold;
                case "Italic" -> Italic;
                case "Bold Italic", "BoldItalic" -> BoldItalic;
                default -> Regular;
            };
        }

        @Override
        public String toString() {
            return switch (this) {
                case Bold -> "Bold";
                case Italic -> "Italic";
                case BoldItalic -> "Bold Italic";
                default -> "Regular";
            };
        }
    }
}
