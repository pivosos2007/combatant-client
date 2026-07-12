/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.values.BindMode;
import combatant.client.features.command.CommandOutput;
import combatant.client.features.gui.clickgui.settings.FunctionBindSetting;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.RuntimeControlModule;
import combatant.client.runtime.ClientRuntime;
import combatant.client.runtime.ClientRuntimeState;
import combatant.client.runtime.RuntimeDiagnostics;

@ModuleInfo(
        id = "panic",
        displayName = "Panic", aliases = {"selfdestruct"},
        category = ModuleCategory.MISC,
        enabledByDefault = true
)
public final class Panic extends Module implements RuntimeControlModule {
    private final FunctionBindSetting disableRestoreBind =
            action("disable_restore", "Z+X+C", BindMode.PRESS);
    private final FunctionBindSetting jarReplacementBind =
            action("prepare_jar_replacement", "LEFT_CTRL+LEFT_SHIFT+J", BindMode.PRESS);
    private final FunctionBindSetting diagnosticsBind =
            action("runtime_diagnostics", "LEFT_CTRL+LEFT_SHIFT+D", BindMode.PRESS);

    {
        setDefaultBind("NONE");
    }

    @Override
    public void onRuntimeControlTick() {
        if (disableRestoreBind.isPressed()) {
            toggleSoftPanic("panic module disable/restore bind");
        }
        if (diagnosticsBind.isPressed()) {
            sendDiagnostics();
        }
        if (jarReplacementBind.isPressed()) {
            prepareJarReplacement("panic module jar replacement bind");
        }
    }

    private void toggleSoftPanic(String reason) {
        ClientRuntimeState state = ClientRuntime.state();
        if (state == ClientRuntimeState.JAR_REPLACEMENT_PANIC || state == ClientRuntimeState.DEAD) {
            CommandOutput.send("Client is fully disabled for safe JAR replacement. Restart Minecraft to use the new JAR.");
            return;
        }
        boolean changed = ClientRuntime.toggleSoftPanic(reason);
        if (!changed) {
            CommandOutput.send("Runtime state: " + ClientRuntime.state());
            return;
        }
        if (ClientRuntime.state() == ClientRuntimeState.SOFT_PANIC) {
            CommandOutput.send("Panic Mode: ON");
        } else {
            CommandOutput.send("Panic Mode: OFF");
        }
    }

    private void prepareJarReplacement(String reason) {
        if (ClientRuntime.prepareJarReplacement(reason)) {
            CommandOutput.send("Client is fully disabled for safe JAR replacement. Restart Minecraft to use the new JAR.");
        }
    }

    private void sendDiagnostics() {
        RuntimeDiagnostics diagnostics = ClientRuntime.diagnostics();
        for (String line : diagnostics.lines()) {
            CommandOutput.send(line);
        }
    }
}
