/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module;

import com.mojang.blaze3d.vertex.PoseStack;
import combatant.client.addon.ModuleExtensionManager;
import combatant.client.util.screen.ClientScreen;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import combatant.client.events.Events;
import combatant.client.render.engine.profiler.ProfilerPhase;
import combatant.client.render.engine.profiler.RenderProfiler2D;
import combatant.client.render.engine.profiler.RenderProfiler3D;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.renderer.ui.ItemBatchRenderer;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.runtime.RuntimeGate;
import combatant.client.runtime.RuntimeShutdownContext;
import combatant.client.util.input.KeyManager;
import combatant.client.util.logging.DebugLog;

import java.util.*;

public enum ModuleManager {
    ;

    private static final List<Module> modules = new ArrayList<>();
    private static final List<Module> modulesView = Collections.unmodifiableList(modules);
    private static final Map<String, Module> byId = new LinkedHashMap<>();
    private static final Map<Class<? extends Module>, Module> byType = new IdentityHashMap<>();
    private static final EnumMap<HudPhase, List<Module>> HUD_PHASE_MODULES = new EnumMap<>(HudPhase.class);
    private static final EnumMap<HudPhase, Module[]> HUD_PHASE_SNAPSHOTS = new EnumMap<>(HudPhase.class);
    private static final EnumMap<WorldPhase, List<Module>> WORLD_PHASE_MODULES = new EnumMap<>(WorldPhase.class);
    private static final EnumMap<WorldPhase, Module[]> WORLD_PHASE_SNAPSHOTS = new EnumMap<>(WorldPhase.class);
    private static final List<ModuleStateListener> listeners = new ArrayList<>();
    private static Module[] modulesSnapshot = new Module[0];
    private static ModuleStateListener[] listenerSnapshot = new ModuleStateListener[0];
    @Setter
    private static boolean suppressToggleSound = false;
    @Setter
    private static boolean suppressToggleNotifications = false;
    private static List<String> softPanicEnabledModules = Collections.emptyList();
    private static boolean runtimeReleased = false;

    static {
        for (HudPhase phase : HudPhase.values()) {
            HUD_PHASE_MODULES.put(phase, new ArrayList<>());
            HUD_PHASE_SNAPSHOTS.put(phase, new Module[0]);
        }
        for (WorldPhase phase : WorldPhase.values()) {
            WORLD_PHASE_MODULES.put(phase, new ArrayList<>());
            WORLD_PHASE_SNAPSHOTS.put(phase, new Module[0]);
        }
    }

    public static void register(Module module) {
        if (runtimeReleased || RuntimeGate.isJarReplacementMode()) return;
        if (module == null) {
            DebugLog.error("Skipping null module registration");
            return;
        }

        String id = module.name();
        if (id == null || id.isBlank()) {
            DebugLog.error("Skipping module with blank id: %s", null, module.getClass().getName());
            return;
        }

        Class<? extends Module> type = module.getClass();
        if (!hasModuleMetadata(type)) {
            DebugLog.error("Skipping module without metadata annotation: %s", null, type.getName());
            return;
        }

        String normalizedId = normalizeName(id);
        Module existingById = byId.get(normalizedId);
        if (existingById != null) {
            DebugLog.error("Skipping duplicate module id '%s': existing=%s duplicate=%s",
                    null, normalizedId, existingById.getClass().getName(), type.getName());
            return;
        }

        Module existingByType = byType.get(type);
        if (existingByType != null) {
            DebugLog.error("Skipping duplicate module class: %s", null, type.getName());
            return;
        }

        if (modules.contains(module)) {
            DebugLog.error("Skipping duplicate module instance: %s", null, type.getName());
            return;
        }

        modules.add(module);
        byId.put(normalizedId, module);
        byType.put(type, module);
        cacheHudPhase(module);
        cacheWorldPhase(module);
        rebuildSnapshots();
        Events.BUS.register(module);
    }

    private static boolean hasModuleMetadata(Class<? extends Module> type) {
        return type.isAnnotationPresent(ModuleInfo.class);
    }

    private static void cacheHudPhase(Module module) {
        if (module == null) return;
        HudPhase phase = module.getHudPhase();
        if (phase == null || phase == HudPhase.NONE) return;
        HUD_PHASE_MODULES.get(phase).add(module);
    }

    private static void cacheWorldPhase(Module module) {
        if (module == null) return;
        WorldPhase phase = module.getWorldPhase();
        if (phase == null || phase == WorldPhase.NONE) return;
        WORLD_PHASE_MODULES.get(phase).add(module);
    }

    private static void rebuildSnapshots() {
        modulesSnapshot = modules.toArray(Module[]::new);

        for (HudPhase phase : HudPhase.values()) {
            List<Module> list = HUD_PHASE_MODULES.get(phase);
            HUD_PHASE_SNAPSHOTS.put(phase, list == null || list.isEmpty() ? new Module[0] : list.toArray(Module[]::new));
        }

        for (WorldPhase phase : WorldPhase.values()) {
            List<Module> list = WORLD_PHASE_MODULES.get(phase);
            WORLD_PHASE_SNAPSHOTS.put(phase, list == null || list.isEmpty() ? new Module[0] : list.toArray(Module[]::new));
        }
    }

    public static Module get(String name) {
        if (runtimeReleased || RuntimeGate.isJarReplacementMode()) return null;
        if (name == null || name.isBlank()) return null;
        Module module = byId.get(normalizeName(name));
        if (module != null && !RuntimeGate.canRunModules() && !(module instanceof RuntimeControlModule)) return null;
        return module;
    }

    public static <T extends Module> T get(Class<T> type) {
        if (runtimeReleased || RuntimeGate.isJarReplacementMode()) return null;
        if (type == null) return null;
        Module module = byType.get(type);
        if (module != null && !RuntimeGate.canRunModules() && !(module instanceof RuntimeControlModule)) return null;
        return module == null ? null : type.cast(module);
    }

    public static <T extends Module> T require(Class<T> type) {
        T module = get(type);
        if (module == null) {
            String typeName = type == null ? "<null>" : type.getName();
            throw new NoSuchElementException("Module is not registered: " + typeName);
        }
        return module;
    }

    public static void toggle(String name) {
        Module m = get(name);
        if (m != null) m.toggle();
    }

    public static void toggle(String name, ModuleActivationSource source) {
        Module m = get(name);
        if (m != null) m.setEnabled(!m.isEnabled(), source);
    }

    public static void tickAll() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!RuntimeGate.canRunModules()) return;

        Module[] snapshot = modulesSnapshot;
        for (Module m : snapshot) {
            if (m.isEnabled() && ModuleExtensionManager.beforeTick(m)) {
                m.onTick();
                ModuleExtensionManager.afterTick(m);
            }
        }
    }

    public static void frameAll(float frameDeltaTicks) {
        if (!RuntimeGate.canRunModules()) return;
        Module[] snapshot = modulesSnapshot;
        for (Module m : snapshot) {
            if (m.isEnabled() && ModuleExtensionManager.beforeFrame(m, frameDeltaTicks)) {
                m.onFrame(frameDeltaTicks);
                ModuleExtensionManager.afterFrame(m, frameDeltaTicks);
            }
        }
    }

    public static void renderHud(HudPhase phase,
                                 GuiGraphicsExtractor ctx,
                                 float tickDelta) {

        if (!RuntimeGate.canRunHud()) return;

        Module[] phaseModules = HUD_PHASE_SNAPSHOTS.get(phase);
        if (phaseModules == null) return;

        for (Module m : phaseModules) {
            if (m.isEnabled()) {
                m.onRender2D(ctx, tickDelta);
            }
        }
    }

    public static void renderHudEngine(HudPhase phase, Renderer2D renderer, TextRenderer textRenderer, GuiGraphicsExtractor ctx, float tickDelta) {
        if (!RuntimeGate.canRunHud()) return;

        Module[] phaseModules = HUD_PHASE_SNAPSHOTS.get(phase);
        if (phaseModules == null) return;

        for (Module m : phaseModules) {
            if (m.isEnabled()) {
                try (ProfilerPhase.Scope _ = ProfilerPhase.scope("2d:module:" + m.name());
                     RenderProfiler2D.Section ignored = RenderProfiler2D.section("module:" + m.name())) {
                    if (ModuleExtensionManager.beforeHudRender(m, renderer, textRenderer, ctx, tickDelta)) {
                        m.onRenderHudEngine(renderer, textRenderer, ctx, tickDelta);
                        ModuleExtensionManager.afterHudRender(m, renderer, textRenderer, ctx, tickDelta);
                    }
                }
            }
        }
    }

    public static boolean hasHudEngineWork(HudPhase phase) {
        return hasEnabledHudPhaseModule(phase);
    }

    public static void renderHudEngineForeground(HudPhase phase, Renderer2D renderer, TextRenderer textRenderer, GuiGraphicsExtractor ctx, float tickDelta) {
        if (!RuntimeGate.canRunHud()) return;

        Module[] phaseModules = HUD_PHASE_SNAPSHOTS.get(phase);
        if (phaseModules == null) return;

        for (Module m : phaseModules) {
            if (m.isEnabled()) {
                try (ProfilerPhase.Scope phaseScope = ProfilerPhase.scope("2d:module_fg:" + m.name());
                     RenderProfiler2D.Section ignored = RenderProfiler2D.section("module_fg:" + m.name())) {
                    m.onRenderHudEngineForeground(renderer, textRenderer, ctx, tickDelta);
                }
            }
        }
    }

    public static boolean hasHudEngineForegroundWork(HudPhase phase) {
        return hasEnabledHudPhaseModule(phase);
    }

    private static boolean hasEnabledHudPhaseModule(HudPhase phase) {
        if (!RuntimeGate.canRunHud()) return false;
        Module[] phaseModules = HUD_PHASE_SNAPSHOTS.get(phase);
        if (phaseModules == null) return false;

        for (Module m : phaseModules) {
            if (m.isEnabled()) return true;
        }
        return false;
    }

    // ---------------------------
    // 3D WORLD рендер
    // ---------------------------

    public static void renderWorld(WorldPhase phase, PoseStack matrices, SubmitNodeCollector consumers, float tickDelta) {

        if (!RuntimeGate.canRunRender()) return;

        Module[] phaseModules = WORLD_PHASE_SNAPSHOTS.get(phase);
        if (phaseModules == null) return;

        for (Module m : phaseModules) {
            if (m.isEnabled()) {
                m.onRenderWorld(matrices, consumers, tickDelta);
            }
        }
    }

    public static void renderWorldEngine(WorldPhase phase, Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!RuntimeGate.canRunRender()) return;

        Module[] phaseModules = WORLD_PHASE_SNAPSHOTS.get(phase);
        if (phaseModules == null) return;

        try {
            for (Module m : phaseModules) {
                if (m.isEnabled()) {
                    try (ProfilerPhase.Scope phaseScope = ProfilerPhase.scope("3d:module:" + m.name());
                         RenderProfiler3D.Section ignored = RenderProfiler3D.section("module:" + m.name())) {
                        if (ModuleExtensionManager.beforeWorldRender(m, renderer, depthRenderer, tickDelta)) {
                            m.onRenderWorldEngine(renderer, depthRenderer, tickDelta);
                            ModuleExtensionManager.afterWorldRender(m, renderer, depthRenderer, tickDelta);
                        }
                    }
                }
            }
        } finally {
            ItemBatchRenderer.finishWorldItemFrame();
        }
    }

    // ---------------------------
    // KEY HANDLING
    // ---------------------------

    public static void handleModuleKeybinds() {
        if (!RuntimeGate.canRunModules()) return;
        boolean soundSuppressedThisPass = false;
        Module[] snapshot = modulesSnapshot;
        for (Module m : snapshot) {
            if (m instanceof RuntimeControlModule) continue;
            if (KeyManager.wasPressed(m.name())) {
                setSuppressToggleSound(soundSuppressedThisPass);
                m.toggleFromKeybind();
                soundSuppressedThisPass = true;
            }
        }
        setSuppressToggleSound(false);
    }

    public static void tickRuntimeControllers() {
        Module[] snapshot = modulesSnapshot;
        for (Module module : snapshot) {
            if (module instanceof RuntimeControlModule controller) {
                controller.onRuntimeControlTick();
            }
        }
    }

    public static List<String> getEnabledModulesNames() {
        List<String> list = new ArrayList<>();
        Module[] snapshot = modulesSnapshot;
        for (int i = 0; i < snapshot.length; i++) {
            Module m = snapshot[i];
            if (m.isEnabled()) list.add(m.name());
        }
        return list;
    }

    public static List<Module> getKeybindEnabledModules() {
        List<Module> list = new ArrayList<>();
        Module[] snapshot = modulesSnapshot;
        for (int i = 0; i < snapshot.length; i++) {
            Module m = snapshot[i];
            if (m.isEnabledFromKeybind()) list.add(m);
        }
        return list;
    }

    /**
     * View of all registered modules (do not modify).
     */
    public static List<Module> getModules() {
        if (runtimeReleased || RuntimeGate.isJarReplacementMode()) return List.of();
        return modulesView;
    }

    public static void setEnabledModules(List<String> names) {
        if (!RuntimeGate.canRunModules() && !RuntimeGate.canRunRuntimeController()) return;
        boolean prev = suppressToggleNotifications;
        suppressToggleNotifications = true;
        try {
            ObjectOpenHashSet<String> enabledNames = new ObjectOpenHashSet<>();
            if (names != null) {
                for (String name : names) {
                    if (name != null && !name.isBlank()) {
                        enabledNames.add(normalizeName(name));
                    }
                }
            }

            Module[] snapshot = modulesSnapshot;
            for (int i = 0; i < snapshot.length; i++) {
                Module m = snapshot[i];
                boolean shouldEnable = enabledNames.contains(normalizeName(m.name()));
                if (m.isEnabled() != shouldEnable) {
                    m.setEnabled(shouldEnable, ModuleActivationSource.INTERNAL);
                }
            }
        } finally {
            suppressToggleNotifications = prev;
        }
    }

    public static boolean isEnabled(String name) {
        Module m = get(name);
        return m != null && m.isEnabled() && (RuntimeGate.canRunModules() || m instanceof RuntimeControlModule);
    }

    public static void setEnabled(String name, boolean enabled) {
        setEnabled(name, enabled, ModuleActivationSource.MANUAL);
    }

    public static void setEnabled(String name, boolean enabled, ModuleActivationSource source) {
        Module m = get(name);
        if (m == null) return;
        if (enabled && !RuntimeGate.canRunModules() && !(m instanceof RuntimeControlModule)) return;

        if (enabled && name.equals("clickgui")) {
            if (ClientScreen.current(Minecraft.getInstance()) != null) {
                return;
            }
        }

        m.setEnabled(enabled, source);
    }

    public static boolean setRuntimeEnabledTransient(String name, boolean enabled) {
        Module m = get(name);
        if (m == null) return false;
        m.setRuntimeEnabledTransient(enabled);
        return true;
    }

    public static ModuleCategory getCategory(String name) {
        Module m = get(name);
        return m != null ? m.getCategory() : null;
    }

    /**
     * ClickGUI expects list of module names
     */
    public static List<String> getAllModules() {
        List<String> out = new ArrayList<>();
        Module[] snapshot = modulesSnapshot;
        for (int i = 0; i < snapshot.length; i++) {
            out.add(snapshot[i].name());
        }
        return out;
    }

    public static void addListener(ModuleStateListener listener) {
        if (listener == null) return;
        listeners.add(listener);
        listenerSnapshot = listeners.toArray(ModuleStateListener[]::new);
    }

    public static void notifyListeners(String name, boolean enabled) {
        if (suppressToggleNotifications) {
            // DebugLog.config("[ModuleManager] notifyListeners suppressed: %s -> %s (listeners=%d)", name, enabled, listeners.size());
            return;
        }
        ModuleStateListener[] snapshot = listenerSnapshot;
        //DebugLog.config("[ModuleManager] notifyListeners: %s -> %s (listeners=%d)", name, enabled, snapshot.length);
        for (int i = 0; i < snapshot.length; i++) {
            snapshot[i].onModuleStateChanged(name, enabled);
        }
    }

    // ====================================
    //   Sound suppression for batched toggles
    // ====================================
    public static boolean isToggleSoundSuppressed() {
        return suppressToggleSound;
    }

    public static boolean isToggleNotificationSuppressed() {
        return suppressToggleNotifications;
    }

    /**
     * Runs config profile application without toggle sounds or module-state notifications.
     */
    public static void runSilentConfigApply(Runnable action) {
        if (action == null) return;
        boolean prevSound = suppressToggleSound;
        boolean prevNotifications = suppressToggleNotifications;
        suppressToggleSound = true;
        suppressToggleNotifications = true;
        try {
            action.run();
        } finally {
            suppressToggleSound = prevSound;
            suppressToggleNotifications = prevNotifications;
        }
    }

    // ============================
    //   CONFIG ORCHESTRATION
    // ============================

    /**
     * Loads and applies config for all modules (enabled state + settings).
     */
    public static void loadAllModuleConfigs() {
        Module[] snapshot = modulesSnapshot;
        DebugLog.config("loadAllModuleConfigs: %d modules", snapshot.length);
        for (int i = 0; i < snapshot.length; i++) {
            Module m = snapshot[i];
            try {
                DebugLog.config("Loading module config -> %s", m.name());
                m.loadAndApplyConfig();
            } catch (Throwable ignored) {
                DebugLog.error("Failed to load config for module %s", ignored, m.name());
            }
        }
    }

    public static void saveAllModuleConfigs() {
        Module[] snapshot = modulesSnapshot;
        for (int i = 0; i < snapshot.length; i++) {
            Module m = snapshot[i];
            try {
                m.saveConfig();
            } catch (Throwable ignored) {
            }
        }
    }

    public static int registeredCount() {
        return modulesSnapshot.length;
    }

    public static void shutdownForRuntime(RuntimeShutdownContext context) {
        if (context == null) return;
        Module[] snapshot = modulesSnapshot;
        List<String> enabled = new ArrayList<>();
        int disabled = 0;
        boolean prevNotifications = suppressToggleNotifications;
        suppressToggleNotifications = true;
        try {
            for (Module module : snapshot) {
                if (module == null) continue;
                if (module.isEnabled()) {
                    enabled.add(module.name());
                    if (!(module instanceof RuntimeControlModule)) {
                        module.setRuntimeEnabledTransient(false);
                        disabled++;
                    }
                }
            }
        } finally {
            suppressToggleNotifications = prevNotifications;
        }
        if (!context.jarReplacement()) {
            softPanicEnabledModules = enabled;
        }
        context.increment("modules_disabled", disabled);
        if (context.jarReplacement()) {
            releaseRuntimeReferences(context);
        }
    }

    public static void restoreAfterSoftPanic(String reason) {
        if (runtimeReleased || RuntimeGate.isJarReplacementMode()) return;
        List<String> restore = softPanicEnabledModules;
        softPanicEnabledModules = Collections.emptyList();
        if (restore == null || restore.isEmpty()) return;
        ObjectOpenHashSet<String> enabledNames = new ObjectOpenHashSet<>();
        for (String name : restore) {
            if (name != null && !name.isBlank()) {
                enabledNames.add(normalizeName(name));
            }
        }
        boolean prevNotifications = suppressToggleNotifications;
        suppressToggleNotifications = true;
        try {
            for (Module module : modulesSnapshot) {
                if (module == null || module instanceof RuntimeControlModule) continue;
                if (enabledNames.contains(normalizeName(module.name()))) {
                    module.setRuntimeEnabledTransient(true);
                }
            }
        } finally {
            suppressToggleNotifications = prevNotifications;
        }
    }

    private static void releaseRuntimeReferences(RuntimeShutdownContext context) {
        runtimeReleased = true;
        int moduleCount = modules.size();
        Events.BUS.clear();
        modules.clear();
        byId.clear();
        byType.clear();
        for (HudPhase phase : HudPhase.values()) {
            HUD_PHASE_MODULES.get(phase).clear();
            HUD_PHASE_SNAPSHOTS.put(phase, new Module[0]);
        }
        for (WorldPhase phase : WorldPhase.values()) {
            WORLD_PHASE_MODULES.get(phase).clear();
            WORLD_PHASE_SNAPSHOTS.put(phase, new Module[0]);
        }
        listeners.clear();
        listenerSnapshot = new ModuleStateListener[0];
        modulesSnapshot = new Module[0];
        context.increment("module_references_released", moduleCount);
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }
}
