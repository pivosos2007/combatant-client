/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.command.impl;

import combatant.client.addon.AddonIssue;
import combatant.client.addon.AddonManager;
import combatant.client.addon.AddonSnapshot;
import combatant.client.features.command.ClientCommand;
import combatant.client.features.command.CommandContext;
import combatant.client.features.command.CommandOutput;

import java.util.List;
import java.util.Locale;

public final class AddonsCommand implements ClientCommand {
    @Override
    public String name() {
        return "addons";
    }

    @Override
    public String usage() {
        return "@addons [list|scan|enable|disable] [id]";
    }

    @Override
    public List<String> suggest(CommandContext ctx, int argIndex, String token) {
        String lower = token == null ? "" : token.toLowerCase(Locale.ROOT);
        if (argIndex == 1) {
            return List.of("list", "scan", "enable", "disable").stream()
                    .filter(value -> value.startsWith(lower))
                    .toList();
        }
        if (argIndex == 2) {
            return AddonManager.snapshots().stream()
                    .map(AddonSnapshot::id)
                    .filter(id -> lower.isEmpty() || id.startsWith(lower))
                    .toList();
        }
        return List.of();
    }

    @Override
    public boolean execute(CommandContext ctx) {
        String action = ctx.arg(0);
        if (action == null || action.isBlank() || "list".equalsIgnoreCase(action)) {
            list();
            return true;
        }
        if ("scan".equalsIgnoreCase(action)) {
            scan();
            return true;
        }
        if ("enable".equalsIgnoreCase(action) || "disable".equalsIgnoreCase(action)) {
            String id = ctx.arg(1);
            if (id == null || id.isBlank()) {
                CommandOutput.send("Usage: " + usage());
                return true;
            }
            boolean enabled = "enable".equalsIgnoreCase(action);
            boolean ok = AddonManager.setEnabled(id, enabled);
            CommandOutput.send(ok
                    ? "Addon " + id + " " + (enabled ? "enabled" : "disabled")
                    : "Addon not found: " + id);
            return true;
        }
        CommandOutput.send("Usage: " + usage());
        return true;
    }

    private static void list() {
        List<AddonSnapshot> addons = AddonManager.snapshots();
        if (addons.isEmpty()) {
            CommandOutput.send("No Combatant addons installed.");
            return;
        }
        for (AddonSnapshot addon : addons) {
            CommandOutput.send(addon.id() + " " + addon.version()
                    + " [" + addon.status().name().toLowerCase(Locale.ROOT) + "]"
                    + " modules=" + addon.modules()
                    + " hud=" + (addon.draggableHudElements() + addon.staticHudElements())
                    + " commands=" + addon.commands()
                    + " clickGui=" + addon.clickGuiSections()
                    + " extensions=" + addon.moduleExtensions()
                    + " irisPatches=" + addon.irisPatchManifests()
                    + (addon.restartRequired() ? " restart-required" : ""));
        }
    }

    private static void scan() {
        for (AddonIssue issue : AddonManager.scan()) {
            CommandOutput.send("Addon scan " + issue.severity().name().toLowerCase(Locale.ROOT)
                    + " [" + issue.addonId() + "]: " + issue.message()
                    + (issue.detail() == null || issue.detail().isBlank() ? "" : " (" + issue.detail() + ")"));
        }
    }
}
