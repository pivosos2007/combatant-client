/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.runtime;

import java.util.ArrayList;
import java.util.List;

public final class RuntimeDiagnostics {
    private final ClientRuntimeState state;
    private final boolean canRunModules;
    private final boolean canRunRender;
    private final boolean canRunHud;
    private final boolean canRunShaderBridge;
    private final boolean jarReplacementMode;
    private final boolean restartRequired;
    private final int callbacksRegistered;
    private final int modulesRegistered;
    private final int addonsActive;
    private final RuntimeCleanupReport lastCleanupReport;

    RuntimeDiagnostics(ClientRuntimeState state,
                       boolean canRunModules,
                       boolean canRunRender,
                       boolean canRunHud,
                       boolean canRunShaderBridge,
                       boolean jarReplacementMode,
                       boolean restartRequired,
                       int callbacksRegistered,
                       int modulesRegistered,
                       int addonsActive,
                       RuntimeCleanupReport lastCleanupReport) {
        this.state = state;
        this.canRunModules = canRunModules;
        this.canRunRender = canRunRender;
        this.canRunHud = canRunHud;
        this.canRunShaderBridge = canRunShaderBridge;
        this.jarReplacementMode = jarReplacementMode;
        this.restartRequired = restartRequired;
        this.callbacksRegistered = callbacksRegistered;
        this.modulesRegistered = modulesRegistered;
        this.addonsActive = addonsActive;
        this.lastCleanupReport = lastCleanupReport;
    }

    public ClientRuntimeState state() {
        return state;
    }

    public List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add("Runtime state: " + state);
        out.add("Can run modules: " + canRunModules);
        out.add("Can run render: " + canRunRender);
        out.add("Can run HUD: " + canRunHud);
        out.add("Can run shader bridge: " + canRunShaderBridge);
        out.add("Callbacks active: " + callbacksRegistered);
        out.add("Modules registered: " + modulesRegistered);
        out.add("Addons active: " + addonsActive);
        out.add("Jar replacement mode: " + jarReplacementMode);
        out.add("Restart required: " + restartRequired);
        if (lastCleanupReport != null) {
            out.add("Last cleanup: " + lastCleanupReport.summaryLine());
        }
        return out;
    }
}
