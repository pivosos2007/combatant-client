/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.command.impl;

import combatant.client.compat.seedcracker.SeedCrackerRuntime;
import combatant.client.compat.seedcracker.SeedSnapshot;
import combatant.client.compat.seedcracker.SeedSnapshots;
import combatant.client.config.subsystem.SeedCrackerConfig;
import combatant.client.features.command.ClientCommand;
import combatant.client.features.command.CommandContext;
import combatant.client.features.command.CommandInfo;
import combatant.client.features.command.CommandOutput;
import java.util.List;
import java.util.Locale;

@CommandInfo(id = "seed", aliases = {"seedcracker"},
        usage = "@seed help|status|start|stop|set <seed>|profile <version>|config ...|cracker|data|finder|render|version|gui|database",
        descriptionKey = "command.seed.description")
public final class SeedCommand implements ClientCommand {
    private static final List<String> SUBCOMMANDS = List.of("help", "status", "start", "stop", "reset", "set", "forget", "profile", "saved", "persist", "use", "config",
            "cracker", "data", "finder", "render", "version", "gui", "database");
    private static final List<String> OPTIONS = List.of("offers", "autosave", "saved", "nativecommands", "overlay", "hashguard");
    private static final List<String> PROFILES = List.of("auto", "26.3", "26.2", "26.1", "1.21.11", "1.21.4", "1.20.4", "1.18.2", "1.16.5");
    @Override public boolean execute(CommandContext ctx) {
        String mode = ctx.arg(0) == null ? "status" : ctx.arg(0).toLowerCase(Locale.ROOT);
        switch (mode) {
            case "help" -> {
                CommandOutput.send("SeedCrackerX commands (via Combatant @seed):");
                CommandOutput.send("@seed cracker [ON|OFF|debug [ON|OFF]], @seed data <bits|clear|restore>, "
                        + "@seed finder <type|category|reload> ..., @seed render outlines [OFF|ON|XRAY]");
                CommandOutput.send("@seed version <mc_version>, @seed gui, @seed database. "
                        + "Combatant: @seed status|start|stop|reset|saved|persist|set|forget|profile|use|config.");
            }
            case "cracker", "data", "finder", "render", "version", "gui", "database" -> {
                SeedCrackerRuntime.executeNative(ctx.args());
            }
            case "status" -> CommandOutput.send(SeedCrackerRuntime.status());
            case "start" -> SeedCrackerRuntime.start();
            case "stop" -> SeedCrackerRuntime.stop();
            case "reset" -> SeedCrackerRuntime.reset();
            case "set" -> {
                if (ctx.arg(1) == null) { CommandOutput.warning("Usage: @seed set <signed 64-bit integer>"); break; }
                try {
                    long seed = Long.parseLong(ctx.arg(1));
                    if (SeedSnapshots.save(seed, SeedSnapshot.Origin.MANUAL))
                        CommandOutput.success("World seed saved to disk for the current server/dimension/profile: " + seed);
                    else CommandOutput.error("Could not save seed: join a world and check the log.");
                } catch (NumberFormatException ex) { CommandOutput.error("Seed must be a signed 64-bit integer."); }
            }
            case "forget" -> {
                if (SeedSnapshots.forget()) CommandOutput.success("Saved seed cleared for this server/dimension/profile.");
                else CommandOutput.warning("No saved seed for this server/dimension/profile.");
            }
            case "saved" -> CommandOutput.send(SeedSnapshots.currentStored()
                    .map(s -> "Seed " + s.worldSeed() + " (" + s.origin() + ") for " + s.server()
                            + ", dimension=" + s.dimension() + ", profile=" + s.worldgen()
                            + (SeedSnapshots.currentIsPersisted() ? " [on disk]" : " [session only; @seed persist to save]"))
                    .orElse("No saved seed for this server/dimension/profile."));
            case "persist" -> {
                if (SeedSnapshots.persistCurrent()) CommandOutput.success("Current seed saved to disk for this server/dimension/profile.");
                else CommandOutput.warning("No seed to persist, or disk write failed. Check the log.");
            }
            case "profile" -> {
                String value = ctx.arg(1);
                if (value == null) { CommandOutput.send("Current worldgen profile: " + SeedCrackerConfig.get().worldgen() + " for " + SeedCrackerConfig.get().contextDescription()); break; }
                if (!PROFILES.contains(value)) { CommandOutput.warning("Valid profiles: " + String.join(", ", PROFILES)); break; }
                SeedCrackerConfig.get().setWorldgen(value);
                CommandOutput.success("World generation profile for current server/dimension: " + value + ".");
            }
            case "use" -> {
                String value = ctx.arg(1);
                if ("on".equals(value) || "off".equals(value)) {
                    SeedCrackerConfig.get().setUseSaved("on".equals(value));
                    CommandOutput.success("Saved seeds for modules: " + value);
                } else CommandOutput.warning("Usage: @seed use on|off");
            }
            case "config" -> {
                String option = ctx.arg(1);
                String value = ctx.arg(2);
                if (option == null || (!"on".equals(value) && !"off".equals(value))) {
                    CommandOutput.warning("Usage: @seed config <" + String.join("|", OPTIONS) + "> on|off");
                    break;
                }
                if (!OPTIONS.contains(option.toLowerCase(Locale.ROOT))) {
                    CommandOutput.warning("Unknown seed setting: " + option);
                    break;
                }
                boolean enabled = "on".equals(value);
                SeedCrackerConfig cfg = SeedCrackerConfig.get();
                switch (option.toLowerCase(Locale.ROOT)) {
                    case "offers" -> cfg.setOfferOnDemand(enabled);
                    case "autosave" -> cfg.setSaveRecovered(enabled);
                    case "saved" -> cfg.setUseSaved(enabled);
                    case "nativecommands" -> {
                        cfg.setHideNativeCommands(!enabled);
                        CommandOutput.warning("Reconnect for native command registration changes to apply.");
                    }
                    case "overlay" -> cfg.setShowNativeOverlay(enabled);
                    case "hashguard" -> {
                        cfg.setIgnoreServerHash(enabled);
                        CommandOutput.warning(enabled
                                ? "Hash guard enabled: server seed hash will not constrain cracking. Recollect observations after switching."
                                : "Hash guard disabled: reconnect to receive the original server seed hash again.");
                    }
                    default -> throw new IllegalStateException("Unreachable seed setting: " + option);
                }
                CommandOutput.success("SeedCracker setting " + option + " = " + value);
            }
            default -> CommandOutput.warning("Usage: " + metadata().usage() + " (or @seed help)");
        }
        return true;
    }
    @Override public List<String> suggest(CommandContext ctx, int argIndex, String token) {
        String prefix = token == null ? "" : token.toLowerCase(Locale.ROOT);
        if (argIndex == 1) return SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).toList();
        if (argIndex == 2 && "profile".equalsIgnoreCase(ctx.arg(0))) return PROFILES.stream().filter(s -> s.startsWith(prefix)).toList();
        if (argIndex >= 2 && SeedCrackerRuntime.nativeCommands().contains(ctx.arg(0) == null ? "" : ctx.arg(0).toLowerCase(Locale.ROOT))) {
            List<String> before = ctx.args().subList(0, Math.min(ctx.args().size(), argIndex - 1));
            return SeedCrackerRuntime.nativeSuggestions(before, token);
        }
        if (argIndex == 2 && "use".equalsIgnoreCase(ctx.arg(0))) return List.of("on", "off").stream().filter(s -> s.startsWith(prefix)).toList();
        if (argIndex == 2 && "config".equalsIgnoreCase(ctx.arg(0))) return OPTIONS.stream().filter(s -> s.startsWith(prefix)).toList();
        if (argIndex == 3 && "config".equalsIgnoreCase(ctx.arg(0))) return List.of("on", "off").stream().filter(s -> s.startsWith(prefix)).toList();
        return List.of();
    }
}
