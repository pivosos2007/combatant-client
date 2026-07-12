/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.relations;

import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * Provides sorted staff entries based on PlayerRelations and StaffTracker.
 */
public enum StaffData {
    ;

    public static List<StaffEntry> getEntries() {
        Set<String> staffList = PlayerRelations.get().getStaff();
        Map<UUID, StaffTracker.StaffInfo> map = StaffTracker.all();
        List<StaffEntry> out = new ArrayList<>();

        for (String rawName : staffList) {
            StaffTracker.StaffInfo info = findByName(map, rawName);

            if (info == null) {
                out.add(new StaffEntry(
                        rawName,
                        Component.literal(rawName),
                        StaffTracker.Status.OFFLINE,
                        -1L,
                        0L
                ));
                continue;
            }

            out.add(new StaffEntry(
                    rawName,
                    info.displayName != null
                            ? info.displayName
                            : Component.literal(rawName),
                    info.status,
                    info.offlineSince,
                    info.lastEntityGone
            ));
        }

        for (StaffTracker.StaffInfo info : map.values()) {
            if (info == null || info.name == null || info.name.isBlank()) continue;
            if (containsIgnoreCase(staffList, info.name)) continue;
            if (!StaffHeuristicsConfig.get().matches(info.name, info.displayName)) continue;

            out.add(new StaffEntry(
                    info.name,
                    info.displayName != null
                            ? info.displayName
                            : Component.literal(info.name),
                    info.status,
                    info.offlineSince,
                    info.lastEntityGone
            ));
        }


        out.sort(Comparator.comparing((StaffEntry e) -> sortPriority(e.status))
                .thenComparing(e -> e.name.toLowerCase(Locale.ROOT)));
        return out;
    }

    private static int sortPriority(StaffTracker.Status st) {
        return switch (st) {
            case VANISH -> 0;
            case GM3 -> 1;
            case GM2 -> 2;
            case GM1 -> 3;
            case GM0 -> 4;
            case OFFLINE -> 5;
            case WAITING -> 6;
            default -> 7;
        };
    }

    private static StaffTracker.StaffInfo findByName(Map<UUID, StaffTracker.StaffInfo> map, String name) {
        for (StaffTracker.StaffInfo info : map.values()) {
            if (info.name.equalsIgnoreCase(name)) {
                return info;
            }
        }
        return null;
    }

    private static boolean containsIgnoreCase(Set<String> set, String value) {
        if (set == null || value == null) return false;
        for (String entry : set) {
            if (entry != null && entry.equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    public record StaffEntry(
            String name,
            Component displayName,
            StaffTracker.Status status,
            long offlineSince,
            long lastGone
    ) {
    }

}
