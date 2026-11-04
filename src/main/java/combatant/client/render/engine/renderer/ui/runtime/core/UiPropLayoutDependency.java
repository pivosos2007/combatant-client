/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer.ui.runtime.core;

import java.util.Map;
import java.util.Objects;

/**
 * The prop portion of the {@code UiLayoutEngine} dependency graph.
 *
 * <p>The layout engine reads text only for TEXT nodes, and intrinsicWidth /
 * intrinsicHeight only for intrinsically-sized leaf nodes. All other props are
 * paint, hit-test, or animation data; changes to them must not invalidate layout.
 * Keep this classifier aligned with the reads in UiLayoutEngine.measure().</p>
 */
public final class UiPropLayoutDependency {
    private UiPropLayoutDependency() { }

    public static boolean requiresLayout(UiNodeType type, UiProps before,
                                         Map<String, ?> patch) {
        if (type == UiNodeType.TEXT) {
            return changed("text", before, patch);
        }
        boolean intrinsic = switch (type) {
            case IMAGE, SVG, SHAPE, CONNECTOR, PATH, ITEM, SPACER, DIVIDER,
                 INPUT, INPUT_TEXT, CHECKBOX, SLIDER -> true;
            default -> false;
        };
        return intrinsic && (changed("intrinsicWidth", before, patch)
                || changed("intrinsicHeight", before, patch));
    }

    /** Layout dependency lookup for one prebound property (no temporary patch map). */
    public static boolean affectsLayout(UiNodeType type, String property) {
        if (type == UiNodeType.TEXT) return "text".equals(property);
        boolean intrinsic = switch (type) {
            case IMAGE, SVG, SHAPE, CONNECTOR, PATH, ITEM, SPACER, DIVIDER,
                 INPUT, INPUT_TEXT, CHECKBOX, SLIDER -> true;
            default -> false;
        };
        return intrinsic && ("intrinsicWidth".equals(property) || "intrinsicHeight".equals(property));
    }

    private static boolean changed(String key, UiProps before, Map<String, ?> patch) {
        return patch.containsKey(key) && !Objects.equals(before.get(key), patch.get(key));
    }
}
