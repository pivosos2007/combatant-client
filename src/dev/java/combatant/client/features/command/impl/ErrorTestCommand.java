package combatant.client.features.command.impl;

import combatant.client.features.gui.diagnostics.FailureText;
import combatant.client.features.command.ClientCommand;
import combatant.client.features.command.CommandContext;
import combatant.client.features.command.CommandInfo;
import combatant.client.features.command.CommandOutput;
import combatant.client.features.module.ModuleManager;
import combatant.client.features.module.modules.misc.DevErrorProbe;
import combatant.client.runtime.CombatantBuild;

/** Explicit development harness. No production class or command depends on this source set. */
@CommandInfo(id = "errortest", usage = "@errortest <tick|event|setting|component|enable|cleanup|recovery|status|reset>", descriptionKey = "command.errortest.description")
public final class ErrorTestCommand implements ClientCommand {
    @Override public boolean isAvailable() { return CombatantBuild.isDevelopmentBuild(); }
    @Override public boolean execute(CommandContext ctx) {
        DevErrorProbe probe = ModuleManager.get(DevErrorProbe.class);
        if (probe == null) { CommandOutput.warning(FailureText.tr("dev.not_registered")); return true; }
        String action = ctx.arg(0);
        if (action == null) {
            if (!probe.isEnabled()) probe.setEnabledManual(true);
            probe.runScenario("event");
            return true;
        }
        switch (action.toLowerCase(java.util.Locale.ROOT)) {
            case "status" -> probe.status();
            case "reset" -> probe.resetFixture();
            case "tick" -> probe.armTick();
            case "event", "setting", "component", "enable", "cleanup", "recovery" -> {
                if ("cleanup".equalsIgnoreCase(action)) {
                    // The command itself is not a module dispatch boundary: arm the UI/tick path instead.
                    probe.armScenario("Cleanup");
                } else probe.runScenario(action);
            }
            default -> CommandOutput.warning(FailureText.tr("dev.usage"));
        }
        return true;
    }
}
