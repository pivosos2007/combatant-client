/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.command.impl;

import combatant.client.features.gui.diagnostics.FailureText;
import combatant.client.features.gui.chat.diagnostics.FailureDiagnostics;

import combatant.client.runtime.error.FailureIsolation;

import combatant.client.features.command.ClientCommand;
import combatant.client.features.command.CommandContext;
import combatant.client.features.command.CommandInfo;
import combatant.client.features.command.CommandOutput;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleManager;
import combatant.client.runtime.error.ErrorHandler;
import combatant.client.runtime.error.FailureRegistry.Failure;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

@CommandInfo(id = "errors", usage = "@errors [list|show <id>|retry <module>]", descriptionKey = "command.errors.description")
public final class ErrorsCommand implements ClientCommand {
    @Override
    public boolean execute(CommandContext ctx) {
        String action = ctx.arg(0);
        if ("show".equalsIgnoreCase(action)) {
            Failure failure = byId(ctx.arg(1));
            FailureDiagnostics.show(failure);
            return true;
        }
        if ("retry".equalsIgnoreCase(action)) {
            Module module = ModuleManager.get(ctx.arg(1));
            if (module == null || ErrorHandler.failure(module) == null) {
                CommandOutput.warning(FailureText.tr("command.no_module"));
                return true;
            }
            boolean recovered = FailureIsolation.retryModule(module);
            if (!recovered) CommandOutput.warning(FailureText.tr("recovery.still_unavailable", module.getDisplayName()));
            else CommandOutput.success(FailureText.tr("recovery.success", module.getDisplayName()));
            return true;
        }
        List<Failure> history = ErrorHandler.history();
        if (history.isEmpty()) {
            CommandOutput.send(FailureText.tr("command.empty"));
            return true;
        }
        MutableComponent out = Component.literal(FailureText.tr("command.recent"));
        for (Failure failure : history.reversed().stream().limit(12).toList()) {
            out.append(Component.literal("\n" + FailureText.tr("command.entry", failure.id(), failure.component(), FailureText.state(failure.state()))));
            out.append(Component.literal("[" + FailureText.tr("action.show_short") + "]").withStyle(style -> style.withColor(0xFFFF7777)
                    .withUnderlined(true)
                    .withClickEvent(new ClickEvent.RunCommand("@errors show " + failure.id()))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal(FailureText.tr("action.show"))))));
        }
        CommandOutput.send(out);
        return true;
    }

    private static Failure byId(String text) {
        try { return ErrorHandler.byId(Long.parseLong(text)); }
        catch (NumberFormatException | NullPointerException e) { return null; }
    }
}
