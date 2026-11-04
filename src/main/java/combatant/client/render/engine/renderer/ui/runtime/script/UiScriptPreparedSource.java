/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer.ui.runtime.script;

import combatant.client.render.engine.renderer.ui.runtime.debug.UiRuntimeValidation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared, bounded cache of transformed JS source across independent V8 UI isolates.
 *
 * <p>Strip/transformation is pure and does not need Javet. A script module can be
 * installed into many runtimes without repeating the regex/transformation work.
 * Compilations occur on V8 workers; the render thread never performs them.</p>
 */
final class UiScriptPreparedSource {
    private static final int MAX_ENTRIES = 64;
    private static final Map<SourceKey, String> CACHE = new LinkedHashMap<>(64, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<SourceKey, String> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    private UiScriptPreparedSource() { }

    static String prepare(UiScriptModule module) {
        SourceKey key = new SourceKey(module.sourceKind(), module.source(), UiRuntimeValidation.enabled());
        synchronized (CACHE) {
            String cached = CACHE.get(key);
            if (cached != null) return cached;
        }
        String source = key.kind == UiScriptSourceKind.TYPESCRIPT
                ? UiTypeScriptStripper.strip(key.source)
                : key.source;
        String prepared = UiScriptModuleTransform.toExecutableScript(source);
        synchronized (CACHE) {
            String cached = CACHE.get(key);
            if (cached != null) return cached;
            CACHE.put(key, prepared);
        }
        return prepared;
    }

    private record SourceKey(UiScriptSourceKind kind, String source, boolean validationEnabled) { }
}
