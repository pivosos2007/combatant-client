/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.hud.actions;

import combatant.client.features.gui.clickgui.settings.FunctionBindSetting;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleManager;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class HudActionRegistry {
    private static final Map<Class<?>, List<AnnotatedAction>> CACHE = new ConcurrentHashMap<>();

    private HudActionRegistry() {}

    public record State(ItemStack stack, boolean available, boolean active, boolean aware, float cooldown,
                        boolean inProgress, float progress) {
        public State {
            stack = stack == null ? ItemStack.EMPTY : stack.copy();
            cooldown = Math.max(0.0f, Math.min(1.0f, cooldown));
            progress = Math.max(0.0f, Math.min(1.0f, progress));
        }

        public State(ItemStack stack, boolean available, boolean active, boolean aware, float cooldown) {
            this(stack, available, active, aware, cooldown, false, 0.0f);
        }
    }

    public record Snapshot(String id, String label, String description, String bind, String icon,
                           ItemStack stack, boolean moduleEnabled, boolean available,
                           boolean active, boolean aware, float cooldown, boolean inProgress, float progress) {
        public Snapshot(String id, String label, String description, String bind, String icon,
                        ItemStack stack, boolean moduleEnabled, boolean available,
                        boolean active, boolean aware, float cooldown) {
            this(id, label, description, bind, icon, stack, moduleEnabled,
                    available, active, aware, cooldown, false, 0.0f);
        }
    }

    private record AnnotatedAction(HudAction annotation, Method method) {}

    public static List<String> actionIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (Module module : ModuleManager.getModules()) {
            String prefix = module.name() + ":";
            for (AnnotatedAction action : CACHE.computeIfAbsent(module.getClass(), HudActionRegistry::discover)) {
                ids.add(prefix + action.annotation().id());
            }
        }
        return List.copyOf(ids);
    }

    public static List<Snapshot> snapshots() {
        ArrayList<Snapshot> results = new ArrayList<>();
        for (Module module : ModuleManager.getModules()) {
            Map<String, FunctionBindSetting> bindings = new LinkedHashMap<>();
            for (FunctionBindSetting bind : module.getActionSettings()) {
                if (isAnnotated(module, bind.getActionId())) bindings.put(bind.getActionId(), bind);
            }
            Map<String, AnnotatedAction> descriptions = new LinkedHashMap<>();
            for (AnnotatedAction action : CACHE.computeIfAbsent(module.getClass(), HudActionRegistry::discover)) {
                descriptions.put(action.annotation().id(), action);
            }
            for (Map.Entry<String, FunctionBindSetting> entry : bindings.entrySet()) {
                String actionId = entry.getKey();
                FunctionBindSetting bind = entry.getValue();
                AnnotatedAction described = descriptions.remove(actionId);
                State state = state(module, described);
                if (state == null && described != null) continue;
                if (state == null) state = new State(ItemStack.EMPTY, true,
                        bind.isHeldForHud() || bind.isHudToggleActive(), false, 0.0f);
                HudAction info = described == null ? null : described.annotation();
                String label = info != null ? info.label()
                        : bind.getHudLabel() != null ? bind.getHudLabel() : actionId.replace('_', ' ');
                String icon = info != null && !info.icon().isBlank() ? info.icon()
                        : bind.getHudIcon() != null ? bind.getHudIcon() : "";
                results.add(new Snapshot(module.name() + ":" + actionId, label,
                        info != null ? info.description() : module.getDisplayName(),
                        bind.get(), icon, state.stack(), module.isEnabled(),
                        state.available(), state.active(), state.aware(), state.cooldown(),
                        state.inProgress(), state.progress()));
            }
            for (AnnotatedAction annotated : descriptions.values()) {
                State state = state(module, annotated);
                if (state == null) continue;
                HudAction info = annotated.annotation();
                results.add(new Snapshot(module.name() + ":" + info.id(), info.label(),
                        info.description(), "", info.icon(), state.stack(), module.isEnabled(),
                        state.available(), state.active(), state.aware(), state.cooldown(),
                        state.inProgress(), state.progress()));
            }
        }
        results.sort(Comparator.comparing(Snapshot::id));
        return List.copyOf(results);
    }

    private static boolean isAnnotated(Module module, String id) {
        for (AnnotatedAction action : CACHE.computeIfAbsent(module.getClass(), HudActionRegistry::discover)) {
            if (action.annotation().id().equals(id)) return true;
        }
        return false;
    }

    private static List<AnnotatedAction> discover(Class<?> type) {
        ArrayList<AnnotatedAction> result = new ArrayList<>();
        for (Method method : type.getDeclaredMethods()) {
            HudAction meta = method.getAnnotation(HudAction.class);
            if (meta == null || method.getParameterCount() != 0 || method.getReturnType() != State.class) continue;
            method.setAccessible(true);
            result.add(new AnnotatedAction(meta, method));
        }
        result.sort(Comparator.comparing(item -> item.annotation().id()));
        return List.copyOf(result);
    }

    private static State state(Module module, AnnotatedAction action) {
        if (action == null) return null;
        try {
            return (State) action.method().invoke(module);
        } catch (ReflectiveOperationException | RuntimeException error) {
            return null;
        }
    }
}
