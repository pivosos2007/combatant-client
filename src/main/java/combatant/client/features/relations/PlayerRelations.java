/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.relations;

import combatant.client.util.logging.DebugLog;
import net.minecraft.util.Util;
import combatant.client.config.ConfigNameProvider;
import combatant.client.config.ConfigObject;
import combatant.client.config.ConfigSerializer;
import combatant.client.config.values.ConfigValue;
import combatant.client.config.values.RGBColorValue;
import combatant.client.config.values.SetValue;
import combatant.client.util.pvp.PvpState;
import combatant.client.util.text.ChatNameUtil;
import combatant.client.util.text.LegacyTextUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service storing player relations (friends/enemies/staff) and shared category colors
 */
public final class PlayerRelations implements ConfigObject, ConfigNameProvider {

    public static final PlayerRelations INSTANCE = new PlayerRelations();
    private static final int DYNAMIC_ENEMY_MAX_SECONDS = 600;
    private static volatile boolean dynamicEnemyEnabled = true;
    private static volatile int dynamicEnemySeconds = 60;
    private final SetValue friends = new SetValue("friends");
    private final SetValue enemies = new SetValue("enemies");
    private final SetValue staff = new SetValue("staff");
    private final SetValue bedwarsSelf = new SetValue("bedwars_self");
    private final SetValue bedwarsEnemies = new SetValue("bedwars_enemies");
    private final RGBColorValue defaultColor = new RGBColorValue("color_default", "#78C8FF");
    private final RGBColorValue friendColor = new RGBColorValue("color_friend", "#00FF00");
    private final RGBColorValue enemyColor = new RGBColorValue("color_enemy", "#FF0000");
    private final RGBColorValue staffColor = new RGBColorValue("color_staff", "#FFA500");
    private final RGBColorValue bedwarsSelfColor = new RGBColorValue("color_bedwars_self", "#00D7FF");
    private final RGBColorValue bedwarsEnemyColor = new RGBColorValue("color_bedwars_enemy", "#FF5CC8");
    private final ConcurrentHashMap<String, DynamicEnemy> dynamicEnemies = new ConcurrentHashMap<>();

    private PlayerRelations() {
        ConfigSerializer.load(this);
    }

    public static PlayerRelations get() {
        return INSTANCE;
    }

    public static boolean isDynamicEnemyEnabled() {
        return dynamicEnemyEnabled;
    }

    public static void setDynamicEnemyEnabled(boolean enabled) {
        dynamicEnemyEnabled = enabled;
        if (!enabled) {
            PlayerRelations.get().dynamicEnemies.clear();
        }
    }

    public static int getDynamicEnemyDurationSeconds() {
        return dynamicEnemySeconds;
    }

    public static void setDynamicEnemyDurationSeconds(int seconds) {
        dynamicEnemySeconds = clampSeconds(seconds);
    }

    private static String normalizeKey(String name) {
        if (name == null || name.isBlank()) return "";
        String raw = LegacyTextUtil.stripLegacy(name);
        String cleaned = ChatNameUtil.normalizeNickCandidate(raw);
        if (cleaned.isEmpty()) return "";
        return cleaned.toLowerCase(Locale.ROOT);
    }

    private static boolean containsIgnoreCase(Set<String> set, String name) {
        if (set == null || name == null) return false;
        for (String s : set) {
            if (s != null && s.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private static int clampSeconds(int seconds) {
        if (seconds < 0) return 0;
        return Math.min(DYNAMIC_ENEMY_MAX_SECONDS, seconds);
    }

    @Override
    public String getConfigName() {
        return "playerrelations";
    }

    // ---- API ----
    public boolean isFriend(String name) {
        return friends.get().contains(name);
    }

    public boolean isEnemy(String name) {
        if (name == null || name.isBlank()) return false;
        if (enemies.get().contains(name)) return true;
        return isDynamicEnemy(name);
    }

    public boolean isStaff(String name) {
        return staff.get().contains(name);
    }

    public boolean isBedwarsSelf(String name) {
        return bedwarsSelf.get().contains(name);
    }

    public boolean isBedwarsEnemy(String name) {
        return bedwarsEnemies.get().contains(name);
    }

    public boolean isEnemyPersisted(String name) {
        return enemies.get().contains(name);
    }

    public boolean isFriendOrStaff(String name) {
        if (name == null || name.isBlank()) return false;
        return containsIgnoreCase(friends.get(), name)
                || containsIgnoreCase(staff.get(), name)
                || containsIgnoreCase(bedwarsSelf.get(), name);
    }

    public void markDynamicEnemy(String name, long timeMs) {
        if (!dynamicEnemyEnabled) return;
        int seconds = clampSeconds(dynamicEnemySeconds);
        if (seconds <= 0) return;
        String key = normalizeKey(name);
        if (key.isEmpty()) return;
        long now = timeMs > 0 ? timeMs : Util.getMillis();
        long durationMs = seconds * 1000L;
        boolean paused = PvpState.isActive();
        dynamicEnemies.put(key, new DynamicEnemy(durationMs, now, paused));
        if (DebugLog.serverOnly()) {
            DebugLog.server(
                    "Dynamic enemy set: name=%s key=%s seconds=%d paused=%s",
                    name, key, seconds, paused
            );
        }
    }

    public boolean addFriend(String name) {
        return friends.get().add(name);
    }

    public boolean removeFriend(String name) {
        return friends.get().remove(name);
    }

    public boolean addEnemy(String name) {
        return enemies.get().add(name);
    }

    public boolean removeEnemy(String name) {
        return enemies.get().remove(name);
    }

    public boolean addStaff(String name) {
        return staff.get().add(name);
    }

    public boolean removeStaff(String name) {
        return staff.get().remove(name);
    }

    public boolean addBedwarsSelf(String name) {
        return bedwarsSelf.get().add(name);
    }

    public boolean removeBedwarsSelf(String name) {
        return bedwarsSelf.get().remove(name);
    }

    public boolean addBedwarsEnemy(String name) {
        return bedwarsEnemies.get().add(name);
    }

    public boolean removeBedwarsEnemy(String name) {
        return bedwarsEnemies.get().remove(name);
    }

    public Set<String> getFriends() {
        return friends.get();
    }

    public Set<String> getEnemies() {
        return enemies.get();
    }

    public Set<String> getStaff() {
        return staff.get();
    }

    public Set<String> getBedwarsSelf() {
        return bedwarsSelf.get();
    }

    public Set<String> getBedwarsEnemies() {
        return bedwarsEnemies.get();
    }

    public int colorDefault() {
        return defaultColor.getArgb();
    }

    public int colorFriend() {
        return friendColor.getArgb();
    }

    public int colorEnemy() {
        return enemyColor.getArgb();
    }

    public int colorStaff() {
        return staffColor.getArgb();
    }

    public int colorBedwarsSelf() {
        return bedwarsSelfColor.getArgb();
    }

    public int colorBedwarsEnemy() {
        return bedwarsEnemyColor.getArgb();
    }

    public RGBColorValue colorDefaultValue() {
        return defaultColor;
    }

    public RGBColorValue colorFriendValue() {
        return friendColor;
    }

    public RGBColorValue colorEnemyValue() {
        return enemyColor;
    }

    public RGBColorValue colorStaffValue() {
        return staffColor;
    }

    public RGBColorValue colorBedwarsSelfValue() {
        return bedwarsSelfColor;
    }

    public RGBColorValue colorBedwarsEnemyValue() {
        return bedwarsEnemyColor;
    }

    public SetValue getFriendsValue() {
        return friends;
    }

    public SetValue getEnemiesValue() {
        return enemies;
    }

    public SetValue getStaffValue() {
        return staff;
    }

    public SetValue getBedwarsSelfValue() {
        return bedwarsSelf;
    }

    public SetValue getBedwarsEnemiesValue() {
        return bedwarsEnemies;
    }

    public void save() {
        ConfigSerializer.requestSave(this);
    }

    private boolean isDynamicEnemy(String name) {
        if (!dynamicEnemyEnabled) return false;
        String key = normalizeKey(name);
        if (key.isEmpty()) return false;
        DynamicEnemy entry = dynamicEnemies.get(key);
        if (entry == null) return false;
        long now = Util.getMillis();
        boolean pvpActive = PvpState.isActive();

        synchronized (entry) {
            if (pvpActive) {
                entry.paused = true;
                entry.lastTickMs = now;
                return true;
            }

            if (entry.paused) {
                entry.paused = false;
                entry.lastTickMs = now;
                return true;
            }

            long dt = now - entry.lastTickMs;
            if (dt > 0L) {
                entry.remainingMs -= dt;
                entry.lastTickMs = now;
            }

            if (entry.remainingMs <= 0L) {
                dynamicEnemies.remove(key);
                return false;
            }
            return true;
        }
    }

    @Override
    public List<ConfigValue<?>> getConfigValues() {
        List<ConfigValue<?>> list = new ArrayList<>();
        list.add(friends);
        list.add(enemies);
        list.add(staff);
        list.add(bedwarsSelf);
        list.add(bedwarsEnemies);
        list.add(defaultColor);
        list.add(friendColor);
        list.add(enemyColor);
        list.add(staffColor);
        list.add(bedwarsSelfColor);
        list.add(bedwarsEnemyColor);
        return list;
    }

    private static final class DynamicEnemy {
        private long remainingMs;
        private long lastTickMs;
        private boolean paused;

        private DynamicEnemy(long remainingMs, long lastTickMs, boolean paused) {
            this.remainingMs = remainingMs;
            this.lastTickMs = lastTickMs;
            this.paused = paused;
        }
    }
}
