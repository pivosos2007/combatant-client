/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.script;

import java.util.LinkedHashMap;
import java.util.Map;

/** Prop patch builder keyed by stable UI node keys. */
public final class UiScriptPatchSet {
    private final Map<String, Map<String, Object>> patches;

    private UiScriptPatchSet(int expectedNodes) {
        this.patches = new LinkedHashMap<>(Math.max(1, expectedNodes));
    }

    public static UiScriptPatchSet create() {
        return new UiScriptPatchSet(16);
    }

    public static UiScriptPatchSet create(int expectedNodes) {
        return new UiScriptPatchSet(expectedNodes);
    }

    public UiScriptPatchSet put(String nodeKey, String prop, Object value) {
        if (nodeKey != null && prop != null) {
            patch(nodeKey, 1).put(prop, value);
        }
        return this;
    }

    public UiScriptPatchSet put(String nodeKey, String propA, Object valueA, String propB, Object valueB) {
        if (nodeKey == null) return this;
        Map<String, Object> map = patch(nodeKey, 2);
        if (propA != null) map.put(propA, valueA);
        if (propB != null) map.put(propB, valueB);
        return this;
    }

    public UiScriptPatchSet props(String nodeKey, Object... pairs) {
        if (nodeKey == null || pairs == null || pairs.length == 0) return this;
        Map<String, Object> map = patch(nodeKey, Math.max(1, pairs.length / 2));
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (pairs[i] != null) {
                map.put(String.valueOf(pairs[i]), pairs[i + 1]);
            }
        }
        return this;
    }

    public UiScriptPatchSet text(String nodeKey, String text, String color) {
        Map<String, Object> map = patch(nodeKey, 2);
        map.put("text", text != null ? text : "");
        map.put("color", color != null ? color : "#FFFFFFFF");
        return this;
    }

    public UiScriptPatchSet image(String nodeKey, String asset, String tint) {
        Map<String, Object> map = patch(nodeKey, 2);
        map.put("asset", asset != null ? asset : "");
        map.put("tint", tint != null ? tint : "#FFFFFFFF");
        return this;
    }

    public UiScriptPatchSet clippedText(String nodeKey,
                                        float measuredWidth,
                                        float boxWidth,
                                        float scrollTime,
                                        float delay,
                                        float speed,
                                        float fadeWidth) {
        float overflow = Math.max(0.0f, measuredWidth - Math.max(0.0f, boxWidth));
        float offset = overflow <= 1.0f ? 0.0f : Math.min(overflow, Math.max(0.0f, scrollTime - delay) * speed);
        Map<String, Object> map = patch(nodeKey, 4);
        map.put("textOffsetX", -offset);
        map.put("textFade", overflow > 1.0f);
        map.put("fadeLeft", offset > 0.5f ? fadeWidth : 0.0f);
        map.put("fadeRight", overflow > 1.0f ? fadeWidth : 0.0f);
        return this;
    }

    public Map<String, ? extends Map<String, ?>> asMap() {
        return patches;
    }

    private Map<String, Object> patch(String nodeKey, int expectedProps) {
        return patches.computeIfAbsent(nodeKey, ignored -> new LinkedHashMap<>(Math.max(1, expectedProps)));
    }
}
