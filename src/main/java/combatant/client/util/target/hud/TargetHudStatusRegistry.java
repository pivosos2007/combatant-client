/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.target.hud;

import combatant.client.features.module.Module;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.target.TargetManager;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Module-agnostic TargetHUD status arbitration. Cached at module registration,
 * never performs method discovery during rendering.
 */
public final class TargetHudStatusRegistry {
    private static final List<Provider> PROVIDERS = new ArrayList<>();

    private record Provider(Module owner, Method method) { }

    private TargetHudStatusRegistry() { }

    public static void register(Module owner) {
        if (owner == null) return;
        for (Method method : owner.getClass().getMethods()) {
            if (!method.isAnnotationPresent(TargetHudStatus.class)) continue;
            if (method.getParameterCount() != 1 || method.getParameterTypes()[0] != LivingEntity.class
                    || method.getReturnType() != String.class) {
                DebugLog.warn("[TargetHUD] Invalid @TargetHudStatus method: %s.%s",
                        owner.getClass().getName(), method.getName());
                continue;
            }
            PROVIDERS.add(new Provider(owner, method));
        }
    }

    public static void clear() {
        PROVIDERS.clear();
    }

    public static String resolve(LivingEntity target) {
        if (target == null) return null;
        // One snapshot from the shared manager. Only modules publishing THIS target
        // can supply a status. Highest published target priority wins; no class checks.
        Map<String, TargetManager.ModuleSnapshot> snapshots = TargetManager.moduleSnapshots();
        String best = null;
        int bestPriority = Integer.MIN_VALUE;
        for (Provider provider : PROVIDERS) {
            Module owner = provider.owner();
            if (!owner.isEnabled()) continue;
            int priority = Integer.MIN_VALUE;
            String id = owner.name();
            for (var entry : snapshots.entrySet()) {
                String key = entry.getKey();
                if (!key.equals(id) && !key.startsWith(id + "/")) continue;
                var snapshot = entry.getValue();
                if (snapshot.target() == target)
                    priority = Math.max(priority, snapshot.priority());
            }
            if (priority <= bestPriority) continue;
            try {
                String message = (String) provider.method().invoke(owner, target);
                if (message == null || message.isBlank()) continue;
                best = message;
                bestPriority = priority;
            } catch (ReflectiveOperationException | IllegalArgumentException ex) {
                DebugLog.warnOnce("targethud:status:" + owner.name(),
                        "[TargetHUD] Status provider failed: %s", ex.toString());
            }
        }
        return best;
    }
}
