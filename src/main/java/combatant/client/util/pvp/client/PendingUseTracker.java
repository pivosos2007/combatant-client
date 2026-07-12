/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.pvp.client;

import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.Map;

/**
 * Трекер попыток использования предметов до подтверждения от сервера.
 * Хранит данные о слоте, количестве и времени клика.
 */
public class PendingUseTracker {

    private final Map<Integer, Pending> pending = new HashMap<>();

    /**
     * Регистрирует новую попытку использования предмета (по номеру слота).
     */
    public void start(Item item, int slot, int count, long now) {
        if (slot >= 0 && slot <= 8) {
            slot += 36;
        }
        pending.put(slot, new Pending(item, slot, count, now));
        //System.out.println("[Combatant][DEBUG]   Pending started for " + item + " (server slot=" + slot + ")");
    }

    /**
     * Проверяет, изменился ли стак на сервере (уменьшилось количество или не откатилось).
     */
    public Pending consumeIfConfirmed(int slot, int newCount) {
        Pending p = pending.get(slot);
        if (p == null) return null;

        if (newCount <= p.count) {
            pending.remove(slot);
            //System.out.println("[Combatant][DEBUG] Confirmed use (slot=" + slot +", old=" + p.count + ", new=" + newCount + ")");
            return p;
        }

        //  сервер восстановил стак (откат)
        if (newCount > p.count) {
            //System.out.println("[Combatant][DEBUG] Rejected (slot=" + slot +", old=" + p.count + ", new=" + newCount + ")");
            pending.remove(slot);
        }

        return null;
    }

    public void clear() {
        pending.clear();
    }

    public record Pending(Item item, int slot, int count, long startedAtMs) {
    }
}
