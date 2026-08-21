/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.addon;

import combatant.client.api.v0.module.CombatantModuleExtension;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleManager;
import combatant.client.render.engine.profiler.ProfilerPhase;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public enum ModuleExtensionManager {
    ;

    private static final Map<String, List<RegisteredExtension>> EXTENSIONS = new LinkedHashMap<>();

    public static boolean register(String addonId, String moduleId, CombatantModuleExtension extension) {
        if (extension == null || moduleId == null || moduleId.isBlank()) return false;
        Module module = ModuleManager.get(moduleId);
        if (module == null) {
            DebugLog.warn("[Addons] Module extension target missing: addon=%s module=%s", addonId, moduleId);
            return false;
        }
        String normalized = normalize(moduleId);
        RegisteredExtension registered = new RegisteredExtension(addonId, extension);
        EXTENSIONS.computeIfAbsent(normalized, ignored -> new ArrayList<>()).add(registered);
        try {
            extension.onRegister(new ModuleExtensionContextImpl(addonId, module));
        } catch (Throwable t) {
            DebugLog.error("[Addons] Module extension registration failed: %s", t, normalized);
            return false;
        }
        return true;
    }

    public static int count() {
        int count = 0;
        for (List<RegisteredExtension> list : EXTENSIONS.values()) {
            count += list.size();
        }
        return count;
    }

    public static boolean beforeEnable(Module module) {
        return all("before_enable", module, extension -> extension.beforeEnable(module));
    }

    public static void afterEnable(Module module) {
        each("after_enable", module, extension -> extension.afterEnable(module));
    }

    public static boolean beforeDisable(Module module) {
        return all("before_disable", module, extension -> extension.beforeDisable(module));
    }

    public static void afterDisable(Module module) {
        each("after_disable", module, extension -> extension.afterDisable(module));
    }

    public static boolean beforeTick(Module module) {
        return all("before_tick", module, extension -> extension.beforeTick(module));
    }

    public static void afterTick(Module module) {
        each("after_tick", module, extension -> extension.afterTick(module));
    }

    public static boolean beforeFrame(Module module, float frameDeltaTicks) {
        return all("before_frame", module, extension -> extension.beforeFrame(module, frameDeltaTicks));
    }

    public static void afterFrame(Module module, float frameDeltaTicks) {
        each("after_frame", module, extension -> extension.afterFrame(module, frameDeltaTicks));
    }

    public static boolean beforeHudRender(Module module,
                                          Renderer2D renderer,
                                          TextRenderer textRenderer,
                                          GuiGraphicsExtractor ctx,
                                          float tickDelta) {
        return all("before_hud", module, extension -> extension.beforeHudRender(module, renderer, textRenderer, ctx, tickDelta));
    }

    public static void afterHudRender(Module module,
                                      Renderer2D renderer,
                                      TextRenderer textRenderer,
                                      GuiGraphicsExtractor ctx,
                                      float tickDelta) {
        each("after_hud", module, extension -> extension.afterHudRender(module, renderer, textRenderer, ctx, tickDelta));
    }

    public static boolean beforeWorldRender(Module module,
                                            Renderer3D renderer,
                                            Renderer3D depthRenderer,
                                            float tickDelta) {
        return all("before_world", module, extension -> extension.beforeWorldRender(module, renderer, depthRenderer, tickDelta));
    }

    public static void afterWorldRender(Module module,
                                        Renderer3D renderer,
                                        Renderer3D depthRenderer,
                                        float tickDelta) {
        each("after_world", module, extension -> extension.afterWorldRender(module, renderer, depthRenderer, tickDelta));
    }

    private static boolean all(String hook, Module module, ExtensionBooleanCall call) {
        List<RegisteredExtension> extensions = extensions(module);
        if (extensions.isEmpty()) return true;
        boolean proceed = true;
        boolean profile = ProfilerPhase.isActive();
        for (RegisteredExtension registered : extensions) {
            if (!AddonManager.isActive(registered.addonId())) continue;
            try {
                if (profile) {
                    try (ProfilerPhase.Scope ignored = ProfilerPhase.scope(profileLabel(hook, module, registered))) {
                        proceed &= call.invoke(registered.extension());
                    }
                } else {
                    proceed &= call.invoke(registered.extension());
                }
            } catch (Throwable t) {
                DebugLog.error("[Addons] Module extension hook failed: addon=%s module=%s",
                        t, registered.addonId(), module == null ? "" : module.name());
            }
        }
        return proceed;
    }

    private static void each(String hook, Module module, ExtensionVoidCall call) {
        List<RegisteredExtension> extensions = extensions(module);
        if (extensions.isEmpty()) return;
        boolean profile = ProfilerPhase.isActive();
        for (RegisteredExtension registered : extensions) {
            if (!AddonManager.isActive(registered.addonId())) continue;
            try {
                if (profile) {
                    try (ProfilerPhase.Scope ignored = ProfilerPhase.scope(profileLabel(hook, module, registered))) {
                        call.invoke(registered.extension());
                    }
                } else {
                    call.invoke(registered.extension());
                }
            } catch (Throwable t) {
                DebugLog.error("[Addons] Module extension hook failed: addon=%s module=%s",
                        t, registered.addonId(), module == null ? "" : module.name());
            }
        }
    }

    private static String profileLabel(String hook, Module module, RegisteredExtension registered) {
        String moduleId = module == null ? "unknown" : module.name();
        return "addon_extension:" + registered.addonId() + ":" + moduleId + ":" + hook;
    }

    private static List<RegisteredExtension> extensions(Module module) {
        if (module == null) return List.of();
        List<RegisteredExtension> list = EXTENSIONS.get(normalize(module.name()));
        return list == null ? List.of() : List.copyOf(list);
    }

    private static String normalize(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    @FunctionalInterface
    private interface ExtensionBooleanCall {
        boolean invoke(CombatantModuleExtension extension);
    }

    @FunctionalInterface
    private interface ExtensionVoidCall {
        void invoke(CombatantModuleExtension extension);
    }

    private record RegisteredExtension(String addonId, CombatantModuleExtension extension) {
    }
}
