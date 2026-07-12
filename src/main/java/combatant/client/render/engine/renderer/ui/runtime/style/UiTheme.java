/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.style;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

public final class UiTheme {
    private final String id;
    private final int version;
    private final Object2IntOpenHashMap<String> colors = new Object2IntOpenHashMap<>();

    public UiTheme(String id, int version) {
        this.id = id != null ? id : "default";
        this.version = Math.max(1, version);
        colors.defaultReturnValue(Integer.MIN_VALUE);
        colors.put("surface", 0xCC1D1D1D);
        colors.put("surface-hover", 0xD82A2A2A);
        colors.put("primary", 0xFFFFFFFF);
        colors.put("primary-text", 0xFFFFFFFF);
        colors.put("muted", 0xFF9B9B9B);
        colors.put("subtle", 0x665A5A5A);
        colors.put("black", 0xFF000000);
        colors.put("white", 0xFFFFFFFF);
        colors.put("transparent", 0x00000000);
    }

    public String id() {
        return id;
    }

    public int version() {
        return version;
    }

    public UiTheme setColor(String name, int argb) {
        if (name != null && !name.isBlank()) {
            colors.put(name, argb);
        }
        return this;
    }

    public int color(String name, int fallback) {
        if (name == null || name.isBlank()) return fallback;
        int color = colors.getInt(name);
        return color != Integer.MIN_VALUE ? color : fallback;
    }
}
