/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.diagnostics;

import combatant.client.runtime.error.FailureRegistry.Scope;
import combatant.client.runtime.error.FailureRegistry.State;
import net.minecraft.client.resources.language.I18n;

/** Presentation-only translations. Never changes failure state or recovery policy. */
public final class FailureText {
    private FailureText() { }

    public static String tr(String key, Object... args) {
        return I18n.get("combatant.error." + key, args);
    }

    public static String state(State state) {
        if (state == null) return tr("state.unknown");
        return switch (state) {
            case HEALTHY -> tr("state.healthy");
            case FAILED -> tr("state.failed");
            case QUARANTINED -> tr("state.quarantined");
            case RECOVERING -> tr("state.recovering");
        };
    }

    public static String scope(Scope scope) {
        if (scope == null) return tr("scope.component");
        return switch (scope) {
            case MODULE -> tr("scope.module");
            case SETTING -> tr("scope.setting");
            case COMPONENT -> tr("scope.component");
        };
    }

    /** Known phase identifiers only; extension-owned identifiers remain verbatim. */
    public static String phase(String phase) {
        if (phase == null || phase.isBlank()) return tr("phase.unknown");
        String normalized = phase.trim();
        String key = switch (normalized) {
            case "tick" -> "tick";
            case "frame" -> "frame";
            case "key", "keybind" -> "key";
            case "render", "render2D", "renderHudEngine", "renderWorldEngine" -> "render";
            case "enable", "onEnable" -> "enable";
            case "disable", "onDisable" -> "disable";
            case "cleanup", "recovery cleanup" -> "cleanup";
            case "recovery" -> "recovery";
            case "availability" -> "availability";
            case "visibility" -> "visibility";
            case "action", "execute" -> "execute";
            case "actions" -> "actions";
            case "event" -> "event";
            case "prewarm" -> "prewarm";
            case "config load" -> "config_load";
            case "runtime control" -> "runtime_control";
            case "extension" -> "extension";
            case "world" -> "world";
            case "dev-test" -> "dev_test";
            default -> null;
        };
        if (key == null) {
            if (normalized.startsWith("event ")) key = "event";
            else if (normalized.startsWith("render ")) key = "render";
            else if (normalized.startsWith("mouse ") || normalized.startsWith("key ")) key = "key";
        }
        return key == null ? normalized : tr("phase." + key);
    }
}
