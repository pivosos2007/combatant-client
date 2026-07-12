/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.relations;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public enum StaffTracker {
    ;

    private static final long TIMEOUT = 1500;       // время ожидания TAB после исчезновения entity
    private static final Map<UUID, StaffInfo> map = new HashMap<>();

    private static StaffInfo get(UUID id) {
        return map.computeIfAbsent(id, k -> {
            StaffInfo i = new StaffInfo();
            i.uuid = k;
            i.offlineSince = System.currentTimeMillis();
            return i;
        });
    }

    public static void onTabUpdate(UUID id, String name, Component displayName, Boolean listed, GameType gm) {
        StaffInfo i = get(id);
        if (displayName != null) i.displayName = displayName;

        if (name != null && !name.isBlank()) i.name = name;
        if (listed != null) i.listed = listed;
        if (gm != null) i.gm = gm;

        recalc(i);
    }

    public static void onEntityAppear(UUID id, String name) {
        StaffInfo i = get(id);

        i.entityPresent = true;
        if (name != null && !name.isBlank()) i.name = name;

        recalc(i);
    }

    // ======================
    // PUBLIC EVENTS
    // ======================

    public static void onEntityDisappear(UUID id) {
        StaffInfo i = get(id);

        i.entityPresent = false;
        i.lastEntityGone = System.currentTimeMillis();

        recalc(i);
    }

    private static void recalc(StaffInfo i) {
        long now = System.currentTimeMillis();

        // entity исчезла → показываем таймер ожидания TAB (WAITING)
        if (!i.entityPresent && now - i.lastEntityGone < TIMEOUT) {

            // пока WAITING – это таймер с lastEntityGone
            i.status = Status.WAITING;
            return;
        }

        // есть в TAB → GM
        if (i.listed) {
            switch (i.gm) {
                case SURVIVAL -> i.status = Status.GM0;
                case CREATIVE -> i.status = Status.GM1;
                case ADVENTURE -> i.status = Status.GM2;
                case SPECTATOR -> i.status = Status.GM3;
                default -> i.status = Status.UNKNOWN;
            }
            return;
        }

        // entity есть, но нет TAB → VANISH
        if (i.entityPresent) {
            i.status = Status.VANISH;
            return;
        }

        // OFFLINE — запись времени
        if (i.status != Status.OFFLINE) {
            i.offlineSince = now;
        }
        i.status = Status.OFFLINE;
    }

    public static Map<UUID, StaffInfo> all() {
        return map;
    }

    // ======================
    // LOGIC
    // ======================

    public static void resetAll() {
        map.clear();
    }

    // ======================
    // API
    // ======================

    public enum Status {
        GM0, GM1, GM2, GM3,
        VANISH,
        OFFLINE,
        WAITING,
        UNKNOWN
    }

    public static class StaffInfo {
        public UUID uuid;
        public String name = "?";      // логика
        public Component displayName;       // ПОЛНЫЙ серверный Text

        public boolean listed = false;
        public boolean entityPresent = false;

        public long lastEntityGone = 0;
        public long offlineSince = 0;

        public GameType gm = GameType.DEFAULT_MODE;
        public Status status = Status.UNKNOWN;
    }
}
