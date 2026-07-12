/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.addon;

import combatant.client.api.v0.addon.CombatantAddon;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AddonRegistration {
    final AddonDescriptor descriptor;
    final EntrypointContainer<CombatantAddon> entrypoint;
    final List<String> modules = new ArrayList<>();
    final List<String> disabledBuiltInModules = new ArrayList<>();
    final List<String> draggableHudElements = new ArrayList<>();
    final List<String> staticHudElements = new ArrayList<>();
    final List<String> commands = new ArrayList<>();
    final List<String> clickGuiSections = new ArrayList<>();
    final List<String> moduleExtensions = new ArrayList<>();
    final List<String> irisPatchManifests = new ArrayList<>();
    final List<String> renderCallbacks = new ArrayList<>();
    final List<String> postProcessPasses = new ArrayList<>();
    final List<AddonIssue> issues = new ArrayList<>();
    final Map<String, Boolean> suspendedModules = new LinkedHashMap<>();
    final Map<String, Boolean> suspendedDraggableHudElements = new LinkedHashMap<>();
    final Map<String, Boolean> suspendedStaticHudElements = new LinkedHashMap<>();
    CombatantAddon addon;
    boolean moduleConfigurationApplied;
    boolean initialized;
    boolean clientReadyNotified;
    AddonStatus status;
    boolean enabled;
    boolean restartRequired;

    AddonRegistration(AddonDescriptor descriptor,
                      EntrypointContainer<CombatantAddon> entrypoint,
                      boolean enabled,
                      AddonStatus status) {
        this.descriptor = descriptor;
        this.entrypoint = entrypoint;
        this.enabled = enabled;
        this.status = status;
    }

    AddonSnapshot snapshot() {
        return new AddonSnapshot(
                descriptor.id(),
                descriptor.name(),
                descriptor.version(),
                descriptor.description(),
                descriptor.authors(),
                descriptor.iconPath(),
                descriptor.apiVersion(),
                status,
                enabled,
                restartRequired,
                modules.size(),
                draggableHudElements.size(),
                staticHudElements.size(),
                commands.size(),
                clickGuiSections.size(),
                moduleExtensions.size(),
                irisPatchManifests.size(),
                List.copyOf(issues)
        );
    }

    void issue(AddonIssue.Severity severity, String message, String detail) {
        issues.add(new AddonIssue(descriptor.id(), severity, message, detail));
    }
}
