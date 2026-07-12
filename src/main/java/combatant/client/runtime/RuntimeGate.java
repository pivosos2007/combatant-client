/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.runtime;

import combatant.client.util.resources.RenderResourceReadiness;

public enum RuntimeGate {
    ;

    public static boolean canRunClientLogic() {
        return ClientRuntime.state() == ClientRuntimeState.ACTIVE;
    }

    public static boolean canRunRender() {
        return canRunClientLogic() && RenderResourceReadiness.isReady();
    }

    public static boolean canRunModules() {
        return canRunClientLogic();
    }

    public static boolean canRunHud() {
        return canRunClientLogic() && RenderResourceReadiness.isReady();
    }

    public static boolean canRunShaderBridge() {
        return canRunClientLogic() && RenderResourceReadiness.isReady();
    }

    public static boolean canRunRuntimeController() {
        ClientRuntimeState state = ClientRuntime.state();
        return state == ClientRuntimeState.ACTIVE || state == ClientRuntimeState.SOFT_PANIC;
    }

    public static boolean isPanic() {
        ClientRuntimeState state = ClientRuntime.state();
        return state == ClientRuntimeState.SOFT_PANIC || state == ClientRuntimeState.JAR_REPLACEMENT_PANIC;
    }

    public static boolean isJarReplacementMode() {
        return ClientRuntime.state() == ClientRuntimeState.JAR_REPLACEMENT_PANIC
                || ClientRuntime.state() == ClientRuntimeState.DEAD;
    }
}
