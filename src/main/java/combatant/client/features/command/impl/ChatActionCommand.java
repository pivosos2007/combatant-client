package combatant.client.features.command.impl;

import combatant.client.features.gui.diagnostics.FailureText;
import combatant.client.features.command.ClientCommand;
import combatant.client.features.command.CommandContext;
import combatant.client.features.command.CommandInfo;
import combatant.client.features.command.CommandOutput;
import combatant.client.features.gui.chat.actions.ChatMessageActions;
import combatant.client.features.gui.chat.actions.MessageActionRegistry;

/** Local-only transport for inline message actions, including vanilla chat fallback. */
@CommandInfo(id = "chataction", usage = "@chataction <token> <action>", descriptionKey = "command.chataction.description")
public final class ChatActionCommand implements ClientCommand {
    @Override public boolean execute(CommandContext ctx) {
        MessageActionRegistry.Result result = ChatMessageActions.invoke(ctx.arg(0), ctx.arg(1));
        if (result != MessageActionRegistry.Result.EXECUTED)
            CommandOutput.warning(FailureText.tr("action.unavailable"));
        return true;
    }
    @Override public boolean isAvailable() { return true; }
}
