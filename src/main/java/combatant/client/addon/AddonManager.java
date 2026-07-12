/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.addon;

import combatant.client.api.v0.addon.CombatantAddon;
import combatant.client.features.gui.hud.AbstractHudElement;
import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry;
import combatant.client.features.gui.hud.nondraggable.StaticHudElementRegistry;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleAutoLoader;
import combatant.client.features.module.ModuleManager;
import combatant.client.runtime.ClientRuntime;
import combatant.client.runtime.RuntimeResumeParticipant;
import combatant.client.runtime.RuntimeShutdownContext;
import combatant.client.runtime.RuntimeShutdownParticipant;
import combatant.client.util.logging.DebugLog;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModMetadata;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public enum AddonManager {
    ;

    public static final String ADDON_METADATA_KEY = "combatant:addon";

    private static final Map<String, AddonRegistration> ADDONS = new LinkedHashMap<>();
    private static final Map<String, Boolean> ENABLED_OVERRIDES = new LinkedHashMap<>();
    private static final Map<String, String> MODULE_OWNERS = new LinkedHashMap<>();
    private static final Map<String, String> DRAGGABLE_HUD_OWNERS = new LinkedHashMap<>();
    private static final Map<String, String> STATIC_HUD_OWNERS = new LinkedHashMap<>();
    private static boolean discovered;
    private static boolean moduleLoadingPrepared;
    private static boolean initialized;
    private static boolean clientReady;

    /**
     * Discovers enabled addon entrypoints and lets them configure built-in module
     * exclusions before {@code ModuleAutoLoader} starts instantiating modules.
     */
    public static void prepareModuleLoading() {
        ensureDiscovered();
        if (moduleLoadingPrepared) return;
        moduleLoadingPrepared = true;
        configureEnabledAddons();
    }

    public static void init() {
        if (initialized) return;
        prepareModuleLoading();
        if (!ModuleAutoLoader.isDiscoveryFinished()) {
            return;
        }
        reconcileModuleExclusions();
        initialized = true;
        initializeEnabledAddons();
    }

    /**
     * Completes addon startup after module configs have been loaded and applied.
     */
    public static void notifyClientReady() {
        init();
        if (!initialized) return;
        clientReady = true;
        for (AddonRegistration registration : ADDONS.values()) {
            notifyClientReady(registration);
        }
    }

    private static void ensureDiscovered() {
        if (discovered) return;
        discovered = true;
        ENABLED_OVERRIDES.clear();
        ENABLED_OVERRIDES.putAll(AddonStateStore.loadEnabledOverrides());
        discoverEntrypoints();
    }

    public static void registerRuntimeLifecycle() {
        ClientRuntime.setAddonCounter(AddonManager::activeCount);
        ClientRuntime.registerParticipant(new RuntimeShutdownParticipant() {
            @Override
            public String id() {
                return "addons";
            }

            @Override
            public void shutdown(RuntimeShutdownContext context) {
                suspendForRuntime(context);
            }
        });
        ClientRuntime.registerResumeParticipant(new RuntimeResumeParticipant() {
            @Override
            public String id() {
                return "addons";
            }

            @Override
            public void resume(String reason) {
                resumeAfterRuntime(reason);
            }
        });
    }

    public static List<AddonSnapshot> snapshots() {
        init();
        return ADDONS.values().stream()
                .map(AddonRegistration::snapshot)
                .sorted(Comparator.comparing(AddonSnapshot::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public static AddonSnapshot snapshot(String id) {
        init();
        AddonRegistration registration = ADDONS.get(normalizeId(id));
        return registration == null ? null : registration.snapshot();
    }

    public static boolean isActive(String id) {
        init();
        AddonRegistration registration = ADDONS.get(normalizeId(id));
        return registration != null && registration.enabled && registration.status == AddonStatus.ACTIVE;
    }

    public static List<AddonIssue> scan() {
        init();
        List<AddonIssue> out = new ArrayList<>();
        for (AddonRegistration registration : ADDONS.values()) {
            validateRegistration(registration);
            out.addAll(registration.issues);
        }
        if (out.isEmpty()) {
            out.add(new AddonIssue("combatant", AddonIssue.Severity.INFO, "No addon errors found", ""));
        }
        return List.copyOf(out);
    }

    public static String moduleProfileOwnerId(String moduleId) {
        String addonId = MODULE_OWNERS.get(normalizeId(moduleId));
        return addonId == null ? "module:" + moduleId : "addon:" + addonId + ":module:" + moduleId;
    }

    public static String draggableHudProfileOwnerId(String elementId) {
        String addonId = DRAGGABLE_HUD_OWNERS.get(normalizeId(elementId));
        return addonId == null ? "hud.draggable:" + elementId : "addon:" + addonId + ":hud.draggable:" + elementId;
    }

    public static String staticHudProfileOwnerId(String elementId) {
        String addonId = STATIC_HUD_OWNERS.get(normalizeId(elementId));
        return addonId == null ? "hud.static:" + elementId : "addon:" + addonId + ":hud.static:" + elementId;
    }

    public static String profileDisplayName(String ownerId, String displayName) {
        String addonId = addonIdFromProfileOwner(ownerId);
        if (addonId == null) return displayName;
        AddonRegistration registration = ADDONS.get(addonId);
        String addonName = registration == null ? addonId : registration.descriptor.name();
        return addonName + " / " + displayName;
    }

    public static String addonIdFromProfileOwner(String ownerId) {
        if (ownerId == null || !ownerId.startsWith("addon:")) return null;
        int start = "addon:".length();
        int end = ownerId.indexOf(':', start);
        if (end <= start) return null;
        return normalizeId(ownerId.substring(start, end));
    }

    public static boolean setEnabled(String id, boolean enabled) {
        init();
        String normalizedId = normalizeId(id);
        AddonRegistration registration = ADDONS.get(normalizedId);
        if (registration == null) return false;
        if (registration.enabled == enabled) return true;

        registration.enabled = enabled;
        registration.restartRequired = false;
        ENABLED_OVERRIDES.put(normalizedId, enabled);
        AddonStateStore.saveEnabledOverrides(ENABLED_OVERRIDES);

        if (enabled) {
            if (!registration.moduleConfigurationApplied) {
                configureAddonModules(registration);
            }
            if (!registration.initialized) {
                initializeAddon(registration);
            } else {
                resumeRuntimeContributions(registration);
                resumeAddon(registration, "enabled by user");
                if (registration.status != AddonStatus.ERROR) {
                    registration.status = AddonStatus.ACTIVE;
                }
            }
            if (clientReady) {
                notifyClientReady(registration);
            }
            return true;
        }

        registration.restartRequired = !registration.disabledBuiltInModules.isEmpty();
        if (!registration.initialized) {
            registration.status = AddonStatus.DISABLED;
            return true;
        }

        suspendRuntimeContributions(registration);
        suspendAddon(registration, "disabled by user");
        registration.status = AddonStatus.DISABLED;
        return true;
    }

    public static int activeCount() {
        int count = 0;
        for (AddonRegistration registration : ADDONS.values()) {
            if (registration.status == AddonStatus.ACTIVE) count++;
        }
        return count;
    }

    private static void discoverEntrypoints() {
        for (var entrypoint : FabricLoader.getInstance()
                .getEntrypointContainers(CombatantAddon.ENTRYPOINT_KEY, CombatantAddon.class)) {
            ModMetadata metadata = entrypoint.getProvider().getMetadata();
            String id = normalizeId(metadata.getId());
            AddonDescriptor descriptor = descriptor(metadata, entrypoint.getDefinition());
            boolean enabled = ENABLED_OVERRIDES.getOrDefault(id, true);
            AddonStatus status = enabled ? AddonStatus.DISCOVERED : AddonStatus.DISABLED;
            AddonRegistration duplicate = ADDONS.get(id);
            if (duplicate != null) {
                duplicate.issue(AddonIssue.Severity.ERROR, "Duplicate addon id", metadata.getId());
                continue;
            }
            AddonRegistration registration = new AddonRegistration(descriptor, entrypoint, enabled, status);
            validateRegistration(registration);
            ADDONS.put(id, registration);
        }
    }

    private static void configureEnabledAddons() {
        for (AddonRegistration registration : ADDONS.values()) {
            if (registration.enabled) {
                configureAddonModules(registration);
            }
        }
    }

    private static void configureAddonModules(AddonRegistration registration) {
        if (registration.moduleConfigurationApplied || !registration.enabled || registration.status == AddonStatus.ERROR) {
            return;
        }
        try {
            CombatantAddon addon = addonInstance(registration);
            CombatantModuleLoadContextImpl context = new CombatantModuleLoadContextImpl(registration);
            addon.onConfigureModules(context);
            context.commit();
            registration.moduleConfigurationApplied = true;
        } catch (Throwable t) {
            registration.status = AddonStatus.ERROR;
            registration.issue(AddonIssue.Severity.ERROR, "Module configuration failed", t.toString());
            DebugLog.error("Addon module configuration failed: %s", t, registration.descriptor.id());
        }
    }

    private static void reconcileModuleExclusions() {
        for (AddonRegistration registration : ADDONS.values()) {
            if (registration.disabledBuiltInModules.isEmpty()) continue;
            registration.disabledBuiltInModules.removeIf(reference -> {
                if (ModuleAutoLoader.wasDisableRequestMatched(reference, registration.descriptor.id())) {
                    return false;
                }
                registration.issue(
                        AddonIssue.Severity.WARNING,
                        "Built-in module exclusion target was not found",
                        reference
                );
                return true;
            });
        }
    }

    private static void initializeEnabledAddons() {
        for (AddonRegistration registration : ADDONS.values()) {
            if (registration.enabled && registration.status != AddonStatus.ERROR) {
                initializeAddon(registration);
            }
        }
    }

    private static void initializeAddon(AddonRegistration registration) {
        if (registration.initialized || !registration.enabled || registration.status == AddonStatus.ERROR) return;
        if (!registration.moduleConfigurationApplied) {
            configureAddonModules(registration);
            if (registration.status == AddonStatus.ERROR) return;
        }
        try {
            CombatantAddon addon = addonInstance(registration);
            addon.onInitialize(new CombatantAddonContextImpl(registration));
            registration.initialized = true;
            registration.status = AddonStatus.ACTIVE;
            DebugLog.info("Addon initialized: %s", registration.descriptor.id());
            if (clientReady) {
                notifyClientReady(registration);
            }
        } catch (Throwable t) {
            registration.status = AddonStatus.ERROR;
            registration.issue(AddonIssue.Severity.ERROR, "Initialization failed", t.toString());
            DebugLog.error("Addon initialization failed: %s", t, registration.descriptor.id());
        }
    }

    private static void notifyClientReady(AddonRegistration registration) {
        if (registration == null
                || registration.clientReadyNotified
                || !registration.initialized
                || !registration.enabled
                || registration.status != AddonStatus.ACTIVE) {
            return;
        }
        try {
            registration.addon.onClientReady(
                    new CombatantAddonRuntimeContextImpl(registration.descriptor.id())
            );
            registration.clientReadyNotified = true;
        } catch (Throwable t) {
            registration.status = AddonStatus.ERROR;
            registration.issue(AddonIssue.Severity.ERROR, "Client ready callback failed", t.toString());
            DebugLog.error("Addon client ready callback failed: %s", t, registration.descriptor.id());
        }
    }

    private static CombatantAddon addonInstance(AddonRegistration registration) {
        CombatantAddon addon = registration.addon;
        if (addon == null) {
            addon = registration.entrypoint.getEntrypoint();
            registration.addon = addon;
        }
        return addon;
    }

    private static void suspendForRuntime(RuntimeShutdownContext context) {
        int suspended = 0;
        for (AddonRegistration registration : ADDONS.values()) {
            if (registration.addon == null || registration.status != AddonStatus.ACTIVE) continue;
            try {
                if (context.jarReplacement()) {
                    shutdownAddon(registration, context.reason());
                    registration.status = AddonStatus.SHUTDOWN;
                } else {
                    registration.addon.onRuntimeSuspended(
                            new CombatantAddonRuntimeContextImpl(registration.descriptor.id())
                    );
                    registration.status = AddonStatus.SUSPENDED;
                }
                suspended++;
            } catch (Throwable t) {
                registration.status = AddonStatus.ERROR;
                registration.issue(AddonIssue.Severity.ERROR, "Runtime suspend failed", t.toString());
                DebugLog.error("Addon runtime suspend failed: %s", t, registration.descriptor.id());
            }
        }
        context.increment("addons_suspended", suspended);
    }

    private static void resumeAfterRuntime(String reason) {
        for (AddonRegistration registration : ADDONS.values()) {
            if (registration.addon == null || registration.status != AddonStatus.SUSPENDED || !registration.enabled) {
                continue;
            }
            resumeAddon(registration, reason);
            if (registration.status != AddonStatus.ERROR) {
                registration.status = AddonStatus.ACTIVE;
            }
        }
    }

    private static void suspendAddon(AddonRegistration registration, String reason) {
        if (registration.addon == null) return;
        try {
            registration.addon.onRuntimeSuspended(new CombatantAddonRuntimeContextImpl(registration.descriptor.id()));
        } catch (Throwable t) {
            registration.status = AddonStatus.ERROR;
            registration.issue(AddonIssue.Severity.ERROR, "Runtime suspend failed", t.toString());
            DebugLog.error("Addon runtime suspend failed: %s", t, registration.descriptor.id());
        }
    }

    private static void resumeAddon(AddonRegistration registration, String reason) {
        if (registration.addon == null) return;
        try {
            registration.addon.onRuntimeResumed(new CombatantAddonRuntimeContextImpl(registration.descriptor.id()));
        } catch (Throwable t) {
            registration.status = AddonStatus.ERROR;
            registration.issue(AddonIssue.Severity.ERROR, "Runtime resume failed", t.toString());
            DebugLog.error("Addon runtime resume failed: %s", t, registration.descriptor.id());
        }
    }

    private static void shutdownAddon(AddonRegistration registration, String reason) {
        if (registration.addon == null) return;
        try {
            registration.addon.onShutdown(new CombatantAddonRuntimeContextImpl(registration.descriptor.id()));
        } catch (Throwable t) {
            registration.issue(AddonIssue.Severity.ERROR, "Shutdown failed", t.toString());
            DebugLog.error("Addon shutdown failed: %s", t, registration.descriptor.id());
        }
    }

    private static void suspendRuntimeContributions(AddonRegistration registration) {
        registration.suspendedModules.clear();
        registration.suspendedDraggableHudElements.clear();
        registration.suspendedStaticHudElements.clear();
        for (String id : registration.modules) {
            Module module = ModuleManager.get(id);
            if (module != null) {
                registration.suspendedModules.put(id, module.isEnabled());
                ModuleManager.setRuntimeEnabledTransient(id, false);
            }
        }
        for (String id : registration.draggableHudElements) {
            DraggableHudElement element = DraggableHudElementRegistry.getById(id);
            if (element != null) {
                registration.suspendedDraggableHudElements.put(id, element.isEnabled());
                element.setRuntimeEnabledTransient(false);
            }
        }
        for (String id : registration.staticHudElements) {
            AbstractHudElement element = StaticHudElementRegistry.getById(id);
            if (element != null) {
                registration.suspendedStaticHudElements.put(id, element.isEnabled());
                element.setRuntimeEnabledTransient(false);
            }
        }
    }

    private static void resumeRuntimeContributions(AddonRegistration registration) {
        for (String id : registration.modules) {
            Boolean enabled = registration.suspendedModules.get(id);
            if (enabled != null) {
                ModuleManager.setRuntimeEnabledTransient(id, enabled);
            }
        }
        for (String id : registration.draggableHudElements) {
            Boolean enabled = registration.suspendedDraggableHudElements.get(id);
            DraggableHudElement element = DraggableHudElementRegistry.getById(id);
            if (enabled != null && element != null) {
                element.setRuntimeEnabledTransient(enabled);
            }
        }
        for (String id : registration.staticHudElements) {
            Boolean enabled = registration.suspendedStaticHudElements.get(id);
            AbstractHudElement element = StaticHudElementRegistry.getById(id);
            if (enabled != null && element != null) {
                element.setRuntimeEnabledTransient(enabled);
            }
        }
        registration.suspendedModules.clear();
        registration.suspendedDraggableHudElements.clear();
        registration.suspendedStaticHudElements.clear();
    }

    static void noteModuleOwner(String addonId, String moduleId) {
        if (moduleId != null && !moduleId.isBlank()) {
            MODULE_OWNERS.put(normalizeId(moduleId), normalizeId(addonId));
        }
    }

    static void noteDraggableHudOwner(String addonId, String elementId) {
        if (elementId != null && !elementId.isBlank()) {
            DRAGGABLE_HUD_OWNERS.put(normalizeId(elementId), normalizeId(addonId));
        }
    }

    static void noteStaticHudOwner(String addonId, String elementId) {
        if (elementId != null && !elementId.isBlank()) {
            STATIC_HUD_OWNERS.put(normalizeId(elementId), normalizeId(addonId));
        }
    }

    private static void validateRegistration(AddonRegistration registration) {
        if (registration.descriptor.apiVersion() != CombatantAddon.API_VERSION) {
            registration.issue(
                    AddonIssue.Severity.WARNING,
                    "Addon API version differs from client API",
                    "addon=" + registration.descriptor.apiVersion() + ", client=" + CombatantAddon.API_VERSION
            );
        }
        if (registration.descriptor.iconPath() == null || registration.descriptor.iconPath().isBlank()) {
            registration.issue(AddonIssue.Severity.WARNING, "Addon icon is missing", "Add icon in fabric.mod.json");
        }
        if (registration.descriptor.entrypointDefinition() == null
                || registration.descriptor.entrypointDefinition().isBlank()) {
            registration.issue(AddonIssue.Severity.WARNING, "Entrypoint definition is unavailable", "");
        }
    }

    private static AddonDescriptor descriptor(ModMetadata metadata, String entrypointDefinition) {
        List<String> authors = metadata.getAuthors().stream()
                .map(person -> person.getName() == null ? "" : person.getName())
                .filter(name -> !name.isBlank())
                .toList();
        return new AddonDescriptor(
                normalizeId(metadata.getId()),
                blankFallback(metadata.getName(), metadata.getId()),
                String.valueOf(metadata.getVersion()),
                metadata.getDescription() == null ? "" : metadata.getDescription(),
                authors,
                metadata.getIconPath(64).or(() -> metadata.getIconPath(32)).orElse(""),
                apiVersion(metadata),
                entrypointDefinition
        );
    }

    private static int apiVersion(ModMetadata metadata) {
        CustomValue value = metadata.getCustomValue(ADDON_METADATA_KEY);
        if (value == null || value.getType() != CustomValue.CvType.OBJECT) return CombatantAddon.API_VERSION;
        CustomValue api = value.getAsObject().get("api");
        if (api == null || api.getType() != CustomValue.CvType.NUMBER) return CombatantAddon.API_VERSION;
        return api.getAsNumber().intValue();
    }

    private static String blankFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String normalizeId(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
