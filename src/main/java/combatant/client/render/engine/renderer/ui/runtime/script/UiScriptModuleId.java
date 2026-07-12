/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.script;

import java.util.Locale;

public record UiScriptModuleId(String namespace, String path) {
    public UiScriptModuleId {
        namespace = normalizeNamespace(namespace);
        path = normalizePath(path);
    }

    public static UiScriptModuleId of(String raw) {
        if (raw == null || raw.isBlank()) {
            return new UiScriptModuleId("combatant", "main");
        }
        int colon = raw.indexOf(':');
        if (colon < 0) {
            return new UiScriptModuleId("combatant", raw);
        }
        return new UiScriptModuleId(raw.substring(0, colon), raw.substring(colon + 1));
    }

    private static String normalizeNamespace(String raw) {
        if (raw == null || raw.isBlank()) return "combatant";
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizePath(String raw) {
        if (raw == null || raw.isBlank()) return "main";
        return raw.trim().replace('\\', '/');
    }

    public String resourcePath() {
        return "assets/" + namespace + "/ui/" + path;
    }

    public String resourceManagerPath() {
        return "ui/" + path;
    }

    public boolean hasExtension() {
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        return dot > slash;
    }

    public UiScriptModuleId withExtension(String extension) {
        if (hasExtension()) return this;
        String suffix = extension != null && extension.startsWith(".") ? extension : "." + extension;
        return new UiScriptModuleId(namespace, path + suffix);
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
