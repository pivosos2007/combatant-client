/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.effects;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bounded CPU lifecycle backend used before the compute/SSBO implementation exists.
 * It owns lifetime/budget/priority only; rendering stays in registered current-renderer adapters.
 */
public class CpuTransientWorldEffectSystem implements TransientWorldEffectSystem {
    private final Map<Long, TransientEffectDescriptor> active = new LinkedHashMap<>();
    private EffectBudget budget;
    private long currentTick = Long.MIN_VALUE;
    private int spawnedThisTick;

    public CpuTransientWorldEffectSystem() {
        this(EffectBudget.DEFAULT);
    }

    public CpuTransientWorldEffectSystem(EffectBudget budget) {
        this.budget = budget == null ? EffectBudget.DEFAULT : budget;
    }

    @Override
    public synchronized void beginTick(long tickSequence) {
        if (tickSequence == currentTick) return;
        currentTick = tickSequence;
        spawnedThisTick = 0;
    }

    @Override
    public synchronized boolean spawn(TransientEffectDescriptor descriptor) {
        if (descriptor == null || descriptor.priority() < budget.minimumPriority()) {
            return false;
        }
        if (active.containsKey(descriptor.id())) {
            active.put(descriptor.id(), descriptor);
            return true;
        }
        if (spawnedThisTick >= budget.maxSpawnPerTick()) {
            return false;
        }
        if (active.size() >= budget.maxActive() && !evictFor(descriptor.priority())) {
            return false;
        }
        active.put(descriptor.id(), descriptor);
        spawnedThisTick++;
        return true;
    }

    private boolean evictFor(int incomingPriority) {
        Map.Entry<Long, TransientEffectDescriptor> candidate = active.entrySet().stream()
                .min(Comparator
                        .comparingInt((Map.Entry<Long, TransientEffectDescriptor> e) -> e.getValue().priority())
                        .thenComparingLong(e -> e.getValue().spawnTimeMs()))
                .orElse(null);
        if (candidate == null || candidate.getValue().priority() > incomingPriority) {
            return false;
        }
        active.remove(candidate.getKey());
        return true;
    }

    @Override
    public synchronized boolean cancel(long effectId) {
        return active.remove(effectId) != null;
    }

    @Override
    public synchronized void update(long nowMs) {
        active.values().removeIf(effect -> effect.isExpired(nowMs));
    }

    @Override
    public synchronized List<TransientEffectDescriptor> snapshot() {
        return List.copyOf(active.values());
    }

    public synchronized List<TransientEffectDescriptor> snapshot(EffectDomain domain) {
        if (domain == null) return snapshot();
        List<TransientEffectDescriptor> out = new ArrayList<>();
        for (TransientEffectDescriptor descriptor : active.values()) {
            if (descriptor.domain() == domain) {
                out.add(descriptor);
            }
        }
        return List.copyOf(out);
    }

    @Override
    public synchronized int activeCount() {
        return active.size();
    }

    @Override
    public synchronized EffectBudget budget() {
        return budget;
    }

    @Override
    public synchronized void setBudget(EffectBudget budget) {
        this.budget = budget == null ? EffectBudget.DEFAULT : budget;
        trimToBudget();
    }

    private void trimToBudget() {
        while (active.size() > budget.maxActive()) {
            Map.Entry<Long, TransientEffectDescriptor> candidate = active.entrySet().stream()
                    .min(Comparator
                            .comparingInt((Map.Entry<Long, TransientEffectDescriptor> e) -> e.getValue().priority())
                            .thenComparingLong(e -> e.getValue().spawnTimeMs()))
                    .orElse(null);
            if (candidate == null) break;
            active.remove(candidate.getKey());
        }
    }

    @Override
    public synchronized void clear() {
        active.clear();
        spawnedThisTick = 0;
        currentTick = Long.MIN_VALUE;
    }
}
