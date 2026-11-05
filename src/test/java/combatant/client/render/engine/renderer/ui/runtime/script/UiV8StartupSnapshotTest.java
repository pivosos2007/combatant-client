/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer.ui.runtime.script;

import com.caoccao.javet.interop.V8Runtime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiV8StartupSnapshotTest {
    @Test
    void startupApiIsPresentInIndependentContextsWithOrWithoutNativeSnapshotSupport() throws Exception {
        AutoCloseable firstOwner = () -> {};
        AutoCloseable secondOwner = () -> {};
        V8Runtime first = null;
        V8Runtime second = null;
        try {
            var firstPrepared = UiV8StartupSnapshot.createRuntime(firstOwner);
            first = firstPrepared.runtime();
            if (!firstPrepared.hasUiCore()) JavetUiEngine.installCore(first);
            assertTrue(first.getExecutor("typeof ui.root === 'function'").executeBoolean());
            assertTrue(first.getExecutor("typeof __combatant_render_tracked === 'function'").executeBoolean());
            first.getExecutor("globalThis.__snapshot_test_marker = 17").executeVoid();

            var secondPrepared = UiV8StartupSnapshot.createRuntime(secondOwner);
            second = secondPrepared.runtime();
            if (!secondPrepared.hasUiCore()) JavetUiEngine.installCore(second);
            assertTrue(second.getExecutor("typeof ui.root === 'function'").executeBoolean());
            assertTrue(second.getExecutor("typeof __combatant_render_tracked === 'function'").executeBoolean());
            assertTrue(second.getExecutor("typeof globalThis.__snapshot_test_marker === 'undefined'").executeBoolean());
        } finally {
            JavetRuntimeBootstrap.closeRuntime(firstOwner, first);
            JavetRuntimeBootstrap.closeRuntime(secondOwner, second);
        }
    }
}
