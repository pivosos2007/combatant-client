/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer.ui.runtime.script;

import combatant.client.render.engine.renderer.ui.runtime.core.UiProps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UiScriptPreparedSourceTest {
    @Test
    void reusesTransformedSourceAcrossIndependentModules() {
        String source = "export function render(ctx) { return { type: 'text' }; }";
        UiScriptModuleId id = UiScriptModuleId.of("combatant:test/ui");
        UiScriptModule first = new UiScriptModule(id, UiScriptSourceKind.JAVASCRIPT, source, UiProps.EMPTY);
        UiScriptModule second = new UiScriptModule(id, UiScriptSourceKind.JAVASCRIPT, source, new UiProps(java.util.Map.of("other", true)));
        String compiled = UiScriptPreparedSource.prepare(first);
        assertSame(compiled, UiScriptPreparedSource.prepare(second));
        assertTrue(compiled.contains("function render(ctx)"));
    }

    @Test
    void differentSourceVersionsProduceDifferentPreparedContent() {
        UiScriptModuleId id = UiScriptModuleId.of("combatant:test/ui");
        UiScriptModule first = new UiScriptModule(id, "export function render() { return 1; }", UiProps.EMPTY);
        UiScriptModule second = new UiScriptModule(id, "export function render() { return 2; }", UiProps.EMPTY);
        assertNotEquals(UiScriptPreparedSource.prepare(first), UiScriptPreparedSource.prepare(second));
    }
}
