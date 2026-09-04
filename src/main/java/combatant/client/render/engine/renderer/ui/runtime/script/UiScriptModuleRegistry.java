/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.script;

import net.minecraft.server.packs.resources.ResourceManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class UiScriptModuleRegistry {
    private final Map<String, UiScriptModuleHandle> handles = new LinkedHashMap<>();

    public UiScriptModuleHandle handle(UiScriptModuleId id) {
        UiScriptModuleId moduleId = id != null ? id : UiScriptModuleId.of("combatant:main");
        String key = moduleId.toString();
        UiScriptModuleHandle existing = handles.get(key);
        if (existing != null) return existing;
        UiScriptModuleHandle created = new UiScriptModuleHandle(moduleId);
        handles.put(key, created);
        return created;
    }

    public ReloadStats reloadChanged(ResourceManager manager) {
        return reload(manager, false);
    }

    public ReloadStats ensureLoaded(ResourceManager manager) {
        return reload(manager, true);
    }

    private ReloadStats reload(ResourceManager manager, boolean initialOnly) {
        int changed = 0;
        int unchanged = 0;
        int errors = 0;
        String firstError = "";
        Throwable firstCause = null;
        ArrayList<ReloadFailure> failures = new ArrayList<>();
        for (UiScriptModuleHandle handle : handles.values()) {
            UiScriptModuleHandle.ReloadResult result = initialOnly && handle.module() == null
                    ? (handle.ensureLoaded(manager) ? UiScriptModuleHandle.ReloadResult.CHANGED : UiScriptModuleHandle.ReloadResult.ERROR)
                    : handle.reloadIfChanged(manager);
            switch (result) {
                case CHANGED -> changed++;
                case UNCHANGED -> unchanged++;
                case ERROR -> {
                    errors++;
                    String moduleId = handle.getId().toString();
                    String message = handle.lastError();
                    Throwable cause = handle.lastErrorCause();
                    failures.add(new ReloadFailure(moduleId, message, cause));
                    if (firstError.isBlank()) {
                        firstError = moduleId + ": " + message;
                        firstCause = cause;
                    }
                }
            }
        }
        return new ReloadStats(changed, unchanged, errors, firstError, firstCause, failures);
    }

    public record ReloadStats(
            int changed,
            int unchanged,
            int errors,
            String firstError,
            Throwable firstCause,
            List<ReloadFailure> failures
    ) {
        public ReloadStats {
            failures = failures == null ? List.of() : List.copyOf(failures);
        }

        public ReloadStats(int changed, int unchanged, int errors, String firstError, Throwable firstCause) {
            this(changed, unchanged, errors, firstError, firstCause, List.of());
        }
    }

    public record ReloadFailure(String moduleId, String message, Throwable cause) {
    }
}
