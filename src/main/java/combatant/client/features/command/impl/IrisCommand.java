/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.command.impl;

import combatant.client.features.command.ClientCommand;
import combatant.client.features.command.CommandContext;
import combatant.client.features.command.CommandOutput;
import combatant.client.render.iris.IrisCompatibilityFeature;
import combatant.client.render.iris.IrisCompatibilityProfile;
import combatant.client.render.iris.IrisRuntime;
import combatant.client.render.iris.IrisRuntimeSnapshot;
import combatant.client.render.iris.patch.ShaderPatchEngine;
import combatant.client.runtime.RuntimeGate;

import java.util.List;
import java.util.stream.Collectors;

public final class IrisCommand implements ClientCommand {
    private static String formatFeature(IrisCompatibilityFeature feature) {
        return feature.name().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public String name() {
        return "iris";
    }

    @Override
    public List<String> aliases() {
        return List.of("shaderpack");
    }

    @Override
    public String usage() {
        return "@iris";
    }

    @Override
    public boolean isAvailable() {
        return !RuntimeGate.isPanic();
    }

    @Override
    public boolean execute(CommandContext ctx) {
        IrisRuntimeSnapshot snapshot = IrisRuntime.snapshot();
        CommandOutput.send(snapshot.shortLine());

        if (!snapshot.modLoaded()) {
            return true;
        }
        CommandOutput.send("Iris API: " + (snapshot.apiAvailable() ? "available" : "unavailable")
                + ", status: " + snapshot.status()
                + ", shadow pass: " + snapshot.renderingShadowPass());

        IrisCompatibilityProfile profile = snapshot.profile();
        String features = profile.features().isEmpty()
                ? "none"
                : profile.features().stream()
                .map(IrisCommand::formatFeature)
                .collect(Collectors.joining(", "));
        CommandOutput.send("Iris profile: " + profile.getId()
                + " (" + profile.displayName() + "), features: " + features);
        List<String> patchDiagnostics = ShaderPatchEngine.diagnostics();
        CommandOutput.send("Iris patch compiler: " + (patchDiagnostics.isEmpty() ? "no session data" : "session data follows"));
        for (String diagnostic : patchDiagnostics) {
            CommandOutput.send("patch " + diagnostic);
        }
        return true;
    }
}
