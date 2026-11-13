/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.common.impl;

import combatant.client.config.common.CommonBooleanGroupSchema;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.function.Predicate;

import combatant.client.features.relations.CategoryType;

public final class TargetFilters implements CommonBooleanGroupSchema {

    public static final String PLAYERS_ONLY = "players_only";
    public static final String IGNORE_FRIENDS = "ignore_friends";
    public static final String IGNORE_STAFF = "ignore_staff";
    public static final String IGNORE_ENEMIES = "ignore_enemies";
    public static final String IGNORE_NAKED = "ignore_naked";
    public static final String IGNORE_MARKED = "ignore_entities";
    public static final String VISIBLE_ONLY = "visible_only";
    public static final String MOBS_ONLY = "mobs_only";
    public static final String PLAYERS_ENABLED = "player_filters";
    public static final String MOBS_ENABLED = "mob_filters";
    public static final String MARKED_ENABLED = "marked_filters";
    public static final String ONLY_FRIENDS = "only_friends";
    public static final String ONLY_STAFF = "only_staff";
    public static final String ONLY_ENEMIES = "only_enemies";
    public static final String ONLY_TEAMMATES = "only_teammates";
    public static final String ONLY_OTHER_PLAYERS = "only_other_players";
    public static final String IGNORE_TEAMMATES = "ignore_teammates";
    public static final String IGNORE_OTHER_PLAYERS = "ignore_other_players";
    public static final String ONLY_NAKED = "only_naked";
    public static final String ONLY_HOSTILE = "only_hostile";
    public static final String ONLY_NEUTRAL = "only_neutral";
    public static final String ONLY_FRIENDLY = "only_friendly";
    public static final String IGNORE_HOSTILE = "ignore_hostile";
    public static final String IGNORE_NEUTRAL = "ignore_neutral";
    public static final String IGNORE_FRIENDLY = "ignore_friendly";
    public static final String ONLY_MARKED = "only_marked";
    public static final String IGNORE_INVISIBLE = "ignore_invisible";

    private static final List<String> PLAYER_ONLY = List.of(ONLY_FRIENDS, ONLY_STAFF, ONLY_ENEMIES,
            ONLY_TEAMMATES, ONLY_OTHER_PLAYERS);
    private static final List<String> PLAYER_IGNORE = List.of(IGNORE_FRIENDS, IGNORE_STAFF, IGNORE_ENEMIES,
            IGNORE_TEAMMATES, IGNORE_OTHER_PLAYERS);
    private static final List<String> MOB_ONLY = List.of(ONLY_HOSTILE, ONLY_NEUTRAL, ONLY_FRIENDLY);
    private static final List<String> MOB_IGNORE = List.of(IGNORE_HOSTILE, IGNORE_NEUTRAL, IGNORE_FRIENDLY);

    public enum Disposition { HOSTILE, NEUTRAL, FRIENDLY }

    public enum Section {
        GENERAL("target", null, List.of(PLAYERS_ONLY, MOBS_ONLY, VISIBLE_ONLY, IGNORE_INVISIBLE)),
        PLAYERS("target_players", PLAYERS_ENABLED, List.of(PLAYERS_ENABLED, ONLY_FRIENDS, ONLY_STAFF,
                ONLY_ENEMIES, ONLY_TEAMMATES, ONLY_OTHER_PLAYERS, IGNORE_FRIENDS, IGNORE_STAFF,
                IGNORE_ENEMIES, IGNORE_TEAMMATES, IGNORE_OTHER_PLAYERS, ONLY_NAKED, IGNORE_NAKED)),
        MOBS("target_mobs", MOBS_ENABLED, List.of(MOBS_ENABLED, ONLY_HOSTILE, ONLY_NEUTRAL,
                ONLY_FRIENDLY, IGNORE_HOSTILE, IGNORE_NEUTRAL, IGNORE_FRIENDLY)),
        MARKED("target_marked", MARKED_ENABLED, List.of(MARKED_ENABLED, ONLY_MARKED, IGNORE_MARKED));

        public final String i18nKey;
        public final String enabledKey;
        public final List<String> keys;

        Section(String i18nKey, String enabledKey, List<String> keys) {
            this.i18nKey = i18nKey;
            this.enabledKey = enabledKey;
            this.keys = keys;
        }
    }

    public static boolean selectsPlayers(Predicate<String> flags) {
        return (!flags.test(MOBS_ONLY) || flags.test(PLAYERS_ONLY))
                && flags.test(PLAYERS_ENABLED) && PLAYER_ONLY.stream().anyMatch(flags);
    }

    private static boolean selectsMobs(Predicate<String> flags) {
        return !flags.test(PLAYERS_ONLY) && flags.test(MOBS_ENABLED) && MOB_ONLY.stream().anyMatch(flags);
    }

    public static boolean optionVisible(Section section, String key, Predicate<String> flags,
                                        boolean fixedPlayersOnly) {
        if (!section.keys.contains(key)) return false;
        if (key.equals(section.enabledKey)) return true;
        if (section.enabledKey != null && !flags.test(section.enabledKey)) return false;
        if (key.equals(PLAYERS_ONLY)) return !fixedPlayersOnly && (flags.test(PLAYERS_ONLY) || !flags.test(MOBS_ONLY));
        if (key.equals(MOBS_ONLY)) return !fixedPlayersOnly && !flags.test(PLAYERS_ONLY);
        if (PLAYER_IGNORE.contains(key)) return !selectsPlayers(flags);
        if (MOB_IGNORE.contains(key)) return MOB_ONLY.stream().noneMatch(flags);
        if (key.equals(IGNORE_NAKED)) return !flags.test(ONLY_NAKED);
        if (key.equals(IGNORE_MARKED)) return !flags.test(ONLY_MARKED);
        return true;
    }

    public static boolean sectionVisible(Section section, Predicate<String> flags, boolean fixedPlayersOnly) {
        return switch (section) {
            case PLAYERS -> !flags.test(MOBS_ONLY) || fixedPlayersOnly || flags.test(PLAYERS_ONLY);
            case MOBS -> !fixedPlayersOnly && !flags.test(PLAYERS_ONLY);
            default -> true;
        };
    }

    public static boolean accepts(Predicate<String> flags, boolean player, CategoryType category,
                                  boolean naked, Disposition disposition, boolean marked, boolean invisible) {
        boolean selectedPlayers = selectsPlayers(flags);
        boolean selectedMobs = selectsMobs(flags);
        if (!player && flags.test(PLAYERS_ONLY)) return false;
        if (player && flags.test(MOBS_ONLY) && !flags.test(PLAYERS_ONLY)) return false;
        // Category selections form one union; armor, marked types and visibility constrain it independently.
        if ((selectedPlayers || selectedMobs) && !(player ? selectedPlayers : selectedMobs)) return false;
        if (flags.test(IGNORE_INVISIBLE) && invisible) return false;
        if (flags.test(MARKED_ENABLED)) {
            if (flags.test(ONLY_MARKED) ? !marked : flags.test(IGNORE_MARKED) && marked) return false;
        }
        if (player && flags.test(PLAYERS_ENABLED)) {
            int index = switch (category) {
                case FRIEND -> 0;
                case STAFF -> 1;
                case ENEMY, BEDWARS_ENEMY -> 2;
                case BEDWARS_SELF -> 3;
                case DEFAULT -> 4;
            };
            if (selectedPlayers ? !flags.test(PLAYER_ONLY.get(index)) : flags.test(PLAYER_IGNORE.get(index))) return false;
            if (flags.test(ONLY_NAKED) ? !naked : flags.test(IGNORE_NAKED) && naked) return false;
        }
        if (!player && flags.test(MOBS_ENABLED)) {
            int index = disposition.ordinal();
            if (MOB_ONLY.stream().anyMatch(flags)) return flags.test(MOB_ONLY.get(index));
            return !flags.test(MOB_IGNORE.get(index));
        }
        return true;
    }

    @Override
    public String commonI18nKey() {
        return "target";
    }

    @Override
    public Map<String, Boolean> defaults() {
        Map<String, Boolean> defaults = new LinkedHashMap<>();
        for (Section section : Section.values()) {
            for (String key : section.keys) defaults.put(key, false);
        }
        defaults.put(PLAYERS_ONLY, true);
        defaults.put(IGNORE_FRIENDS, true);
        defaults.put(IGNORE_STAFF, true);
        defaults.put(PLAYERS_ENABLED, true);
        defaults.put(MOBS_ENABLED, true);
        defaults.put(MARKED_ENABLED, true);
        defaults.put(IGNORE_TEAMMATES, true);
        return defaults;
    }
}
