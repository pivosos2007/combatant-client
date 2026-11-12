/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.shader;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Inserts the view-obstruction fade marker without replacing Minecraft's entity shader contract. */
public final class EntityFadeShaderSource {
    private static final Pattern VERTEX_COLOR_MULTIPLY = Pattern.compile(
            "(?m)^\\h*color\\s*\\*=\\s*faceVertexColor\\s*\\*\\s*ColorModulator\\s*;\\h*$");

    private EntityFadeShaderSource() { }

    /** Returns null if the source lacks exactly one supported insertion point. */
    public static String patch(String source, String fadeMarker) {
        if (source == null || fadeMarker == null || fadeMarker.isBlank()
                || source.contains("COMBATANT_ENTITY_FADE_MARKER")) return null;
        Matcher matcher = VERTEX_COLOR_MULTIPLY.matcher(source);
        if (!matcher.find()) return null;
        int insertionPoint = matcher.start();
        if (matcher.find()) return null;
        return source.substring(0, insertionPoint) + fadeMarker + "\n" + source.substring(insertionPoint);
    }
}
