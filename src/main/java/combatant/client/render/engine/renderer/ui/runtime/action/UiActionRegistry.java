/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.action;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

public final class UiActionRegistry {
    private final Object2ObjectOpenHashMap<String, UiAction> actions = new Object2ObjectOpenHashMap<>();

    public void register(String key, UiAction action) {
        if (key == null || key.isBlank() || action == null) return;
        actions.put(key, action);
    }

    public void unregister(String key) {
        if (key == null || key.isBlank()) return;
        actions.remove(key);
    }

    public void clear() {
        actions.clear();
    }

    public boolean contains(String key) {
        return key != null && actions.containsKey(key);
    }

    public boolean dispatch(String rawRef, UiActionContext baseContext) {
        UiActionRef ref = UiActionRef.parse(rawRef);
        UiAction action = actions.get(ref.key());
        if (action == null) return false;
        UiActionContext context = new UiActionContext(
                baseContext != null ? baseContext.node() : null,
                ref,
                baseContext != null ? baseContext.event() : null
        );
        return action.run(context);
    }
}
