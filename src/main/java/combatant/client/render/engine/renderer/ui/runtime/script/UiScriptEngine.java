/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.script;

/**
 * Evaluates loaded UI script modules against a render context.
 * Engines that do not track dependencies fall back to ordinary evaluation.
 */
public interface UiScriptEngine extends AutoCloseable {
    UiScriptRenderResult render(UiScriptModule module, UiScriptRenderContext context);

    /** Optional dependency-aware execution; other engines use conservative invalidation. */
    default UiScriptRenderResult renderTracked(UiScriptModule module, UiScriptRenderContext context) {
        return render(module, context);
    }

    @Override
    void close();
}
