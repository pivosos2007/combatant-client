package combatant.client.compat.seedcracker;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import combatant.client.config.subsystem.SeedCrackerConfig;
import combatant.client.features.command.CommandOutput;
import combatant.client.util.logging.DebugLog;
import kaptainwutax.seedcrackerX.command.ClientCommand;
import kaptainwutax.seedcrackerX.config.Config;
import kaptainwutax.seedcrackerX.init.ClientCommands;
import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Private Brigadier dispatcher for the ORIGINAL SeedCrackerX command objects.
 * It is never registered with Minecraft's slash-command dispatcher: @seed owns
 * their entry points, while the upstream implementation remains untouched.
 */
public final class SeedCrackerNativeCommands {
    private static CommandDispatcher<FabricClientCommandSource> dispatcher;
    private static boolean executing;

    /** Intercept original Config.save while a virtual command is in flight. */
    public static boolean isExecuting() { return executing; }

    /** Prevent the native config screen from writing global settings over other worlds. */
    public static boolean saveScopedConfig() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || !mc.isSameThread()) return executing;
        if (!executing) {
            SeedCrackerNativeState.capture();
            SeedCrackerConfig.get().setShowNativeOverlay(Config.get().render != Config.RenderType.OFF);
        }
        return true;
    }

    private SeedCrackerNativeCommands() { }

    private static CommandDispatcher<FabricClientCommandSource> commands() {
        if (dispatcher == null) {
            var local = new CommandDispatcher<FabricClientCommandSource>();
            // Call each original command directly. ClientCommands.registerCommands
            // is deliberately intercepted by the optional native-command mixin.
            for (ClientCommand command : ClientCommands.COMMANDS) command.register(local);
            dispatcher = local;
        }
        return dispatcher;
    }

    static List<String> commandNames() {
        return ClientCommands.COMMANDS.stream().map(ClientCommand::getName).toList();
    }

    static List<String> suggest(List<String> args, String token) {
        try {
            CommandNode<FabricClientCommandSource> node = commands().getRoot().getChild(ClientCommands.PREFIX);
            if (node == null) return List.of();
            for (String arg : args) {
                if (arg == null || arg.isBlank()) continue;
                CommandNode<FabricClientCommandSource> next = child(node, arg);
                if (next == null) return List.of();
                node = next;
            }
            String prefix = token == null ? "" : token.toLowerCase(Locale.ROOT);
            return node.getChildren().stream().map(CommandNode::getName)
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
        } catch (LinkageError | RuntimeException e) {
            DebugLog.error("SeedCrackerX command completion failed", e);
            return List.of();
        }
    }

    private static CommandNode<FabricClientCommandSource> child(CommandNode<FabricClientCommandSource> node, String name) {
        for (var possible : node.getChildren()) {
            if (possible.getName().equalsIgnoreCase(name)) return possible;
        }
        return null;
    }

    /** Returns true when a command was handled, including a syntax error. */
    static boolean execute(List<String> input) {
        if (input.isEmpty()) return false;
        try {
            CommandNode<FabricClientCommandSource> node = commands().getRoot().getChild(ClientCommands.PREFIX);
            List<String> canonical = new ArrayList<>(input.size());
            for (String arg : input) {
                CommandNode<FabricClientCommandSource> next = child(node, arg);
                if (next == null) {
                    CommandOutput.warning("Unknown SeedCrackerX argument '" + arg + "'. Options: "
                            + String.join(", ", suggest(canonical, "")));
                    return true;
                }
                canonical.add(next.getName());
                node = next;
            }
            SeedCrackerNativeState.sync();
            // Original commands do not access the FabricClientCommandSource.
            // Their effects and messages are routed by the optional output mixins.
            executing = true;
            try {
                commands().execute(ClientCommands.PREFIX + " " + String.join(" ", canonical), null);
            } finally {
                executing = false;
            }
            String family = canonical.getFirst();
            if ("render".equals(family) || "finder".equals(family)
                    || "cracker".equals(family)) {
                SeedCrackerNativeState.capture();
            }
            if ("render".equals(family)) {
                SeedCrackerConfig.get().setShowNativeOverlay(Config.get().render != Config.RenderType.OFF);
            }
            if ("data".equals(family) && canonical.size() > 1 && "clear".equals(canonical.get(1))) {
                // A native data reset invalidates the backfill deduplication too.
                SeedCrackerChunkBackfill.clear();
            }
            if ("cracker".equals(family) && canonical.size() <= 2) {
                if (Config.get().active) SeedCrackerChunkBackfill.clear();
                SeedCrackerConfig.get().setEnabled(Config.get().active);
                SeedCrackerIntegration.refreshActiveState();
            }
            if ("version".equals(family) && canonical.size() == 2) {
                SeedCrackerConfig.get().setWorldgen(canonical.get(1));
                SeedCrackerIntegration.sync();
            }
            return true;
        } catch (CommandSyntaxException exception) {
            CommandOutput.warning("SeedCrackerX: " + exception.getRawMessage().getString());
        } catch (LinkageError | RuntimeException exception) {
            DebugLog.error("SeedCrackerX native command failed", exception);
            CommandOutput.error("SeedCrackerX command failed: " + exception.getClass().getSimpleName()
                    + ". See latest.log.");
        }
        return true;
    }
}
