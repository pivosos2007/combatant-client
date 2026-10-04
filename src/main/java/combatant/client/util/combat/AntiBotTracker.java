/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.combat;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.PacketEvent;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AntiBotTracker {
    public static final AntiBotTracker INSTANCE = new AntiBotTracker();

    private static final long SUSPECT_TTL_MS = 8_000L;
    private static final long TAB_TTL_MS = 30_000L;
    private static final int BOT_SCORE = 4;
    // Strict mode acts one point earlier, but only as a live check: a player that scores 3 is
    // not remembered as a bot, so gaining a tab entry or armor clears them again.
    private static final int STRICT_BOT_SCORE = 3;
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    private final Minecraft mc = Minecraft.getInstance();
    private final ConcurrentHashMap<UUID, TabInfo> tab = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, SuspectInfo> suspects = new ConcurrentHashMap<>();
    private final ConcurrentHashMap.KeySetView<UUID, Boolean> bots = ConcurrentHashMap.newKeySet();
    // Targeting asks about every player in range every tick and assess() walks the whole tab
    // list, so on a 200-player server scores are computed at most once per player per tick.
    private final ConcurrentHashMap<UUID, Integer> scoreCache = new ConcurrentHashMap<>();

    private AntiBotTracker() {
    }

    @EventHandler
    private void onPacketReceivePost(PacketEvent.ReceivePost event) {
        if (event.getPacket() instanceof ClientboundPlayerInfoUpdatePacket packet) {
            handlePlayerList(packet);
        } else if (event.getPacket() instanceof ClientboundPlayerInfoRemovePacket packet) {
            handlePlayerRemove(packet);
        }
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        if (mc.level == null || mc.player == null) {
            reset();
            return;
        }

        scoreCache.clear();
        long now = System.currentTimeMillis();
        suspects.entrySet().removeIf(entry -> now - entry.getValue().seenAtMs > SUSPECT_TTL_MS);
        tab.entrySet().removeIf(entry -> {
            UUID uuid = entry.getKey();
            return now - entry.getValue().updatedAtMs > TAB_TTL_MS
                    && mc.getConnection() != null
                    && mc.getConnection().getPlayerInfo(uuid) == null
                    && mc.level.getPlayerByUUID(uuid) == null;
        });
        bots.removeIf(uuid -> mc.level.getPlayerByUUID(uuid) == null && !isKnownInTab(uuid));

        for (UUID uuid : suspects.keySet()) {
            Player player = mc.level.getPlayerByUUID(uuid);
            if (player != null && score(player) >= BOT_SCORE) {
                bots.add(uuid);
            }
        }
    }

    public boolean isBot(Player player) {
        return isBot(player, false);
    }

    public boolean isBot(Player player, boolean strict) {
        if (player == null || mc.player == null || player == mc.player) {
            return false;
        }

        UUID uuid = player.getUUID();
        if (bots.contains(uuid)) {
            return true;
        }

        int score = score(player);
        if (score >= BOT_SCORE) {
            bots.add(uuid);
            return true;
        }
        return strict && score >= STRICT_BOT_SCORE;
    }

    private int score(Player player) {
        return scoreCache.computeIfAbsent(player.getUUID(), uuid -> assess(player).score());
    }

    public void reset() {
        tab.clear();
        suspects.clear();
        bots.clear();
        scoreCache.clear();
    }

    private void handlePlayerList(ClientboundPlayerInfoUpdatePacket packet) {
        long now = System.currentTimeMillis();
        boolean add = packet.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER);
        boolean listedAffects = add || packet.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED);
        boolean latencyAffects = add || packet.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY);

        for (ClientboundPlayerInfoUpdatePacket.Entry entry : packet.entries()) {
            UUID uuid = entry.profileId();
            TabInfo info = tab.computeIfAbsent(uuid, TabInfo::new);
            info.updatedAtMs = now;

            GameProfile profile = entry.profile();
            if (profile != null) {
                info.name = profile.name();
                info.nameLower = profile.name() == null ? null : profile.name().toLowerCase(Locale.ROOT);
                info.propertiesMissing = profile.properties() == null || profile.properties().isEmpty();
            }
            if (listedAffects) {
                info.listed = entry.listed();
                info.listedKnown = true;
            }
            if (latencyAffects) {
                info.latency = entry.latency();
                info.latencyKnown = true;
            }

            if (add && profile != null) {
                if (hasDuplicateProfile(profile.name(), profile.id())) {
                    bots.add(uuid);
                } else if (isSuspiciousAddition(entry, profile)) {
                    suspects.put(uuid, new SuspectInfo(now));
                }
            }
        }
    }

    private void handlePlayerRemove(ClientboundPlayerInfoRemovePacket packet) {
        for (UUID uuid : packet.profileIds()) {
            tab.remove(uuid);
            suspects.remove(uuid);
            bots.remove(uuid);
            scoreCache.remove(uuid);
        }
    }

    private BotAssessment assess(Player player) {
        String name = player.getGameProfile().name();
        UUID uuid = player.getUUID();
        TabInfo info = tab.get(uuid);
        PlayerInfo networkEntry = mc.getConnection() != null
                ? mc.getConnection().getPlayerInfo(uuid)
                : null;

        boolean knownInTab = info != null || networkEntry != null;
        boolean listed = info == null || !info.listedKnown || info.listed;
        boolean suspect = suspects.containsKey(uuid);
        boolean profilePropertiesMissing = info != null && info.propertiesMissing;
        boolean npcName = looksLikeNpcName(name);

        int score = 0;
        if (isMatrixNameBot(name)) {
            score += 4;
        }
        if (hasDuplicateProfile(name, uuid)) {
            score += 4;
        }
        if (!knownInTab && player.tickCount > 20) {
            score += 4;
        } else if (!listed && player.tickCount > 40) {
            score += 3;
        }
        if (suspect) {
            score += 2;
        }
        if (profilePropertiesMissing && suspect) {
            score += 1;
        }
        if (player.isInvisible() && (!knownInTab || !listed) && !npcName) {
            score += 3;
        }
        if (suspect && isFullyCleanArmored(player)) {
            score += 2;
        }
        if (isNonOfflineUuid(player) && player.isInvisible() && !npcName && (!knownInTab || !listed)) {
            score += 2;
        }

        return new BotAssessment(score);
    }

    private boolean isSuspiciousAddition(ClientboundPlayerInfoUpdatePacket.Entry entry, GameProfile profile) {
        if (profile == null || profile.name() == null || profile.name().isBlank()) {
            return true;
        }
        if (looksLikeNpcName(profile.name())) {
            return false;
        }
        boolean propertiesMissing = profile.properties() == null || profile.properties().isEmpty();
        return propertiesMissing && entry.latency() >= 2;
    }

    private boolean hasDuplicateProfile(String name, UUID self) {
        if (name == null || name.isBlank() || self == null) {
            return false;
        }

        String normalized = name.toLowerCase(Locale.ROOT);
        for (TabInfo info : tab.values()) {
            if (info.nameLower != null
                    && info.nameLower.equals(normalized)
                    && !self.equals(info.uuid)) {
                return true;
            }
        }

        if (mc.getConnection() == null) {
            return false;
        }
        for (PlayerInfo entry : mc.getConnection().getOnlinePlayers()) {
            GameProfile profile = entry.getProfile();
            if (profile != null
                    && profile.name() != null
                    && profile.name().equalsIgnoreCase(name)
                    && !self.equals(profile.id())) {
                return true;
            }
        }
        return false;
    }

    private boolean isKnownInTab(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        if (tab.containsKey(uuid)) {
            return true;
        }
        return mc.getConnection() != null && mc.getConnection().getPlayerInfo(uuid) != null;
    }

    private boolean isFullyCleanArmored(Player player) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
            if (stack.isEmpty() || equippable == null || equippable.slot() != slot || stack.isEnchanted()) {
                return false;
            }
        }
        return true;
    }

    private boolean isMatrixNameBot(String name) {
        return name != null
                && name.startsWith("CIT-")
                && !name.contains("NPC")
                && !name.contains("[ZNPC]");
    }

    private boolean looksLikeNpcName(String name) {
        if (name == null) {
            return false;
        }
        String upper = name.toUpperCase(Locale.ROOT);
        return upper.contains("NPC") || upper.startsWith("[ZNPC]");
    }

    private boolean isNonOfflineUuid(Player player) {
        String name = player.getGameProfile().name();
        if (name == null || name.isBlank()) {
            return false;
        }
        UUID offline = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        return !player.getUUID().equals(offline);
    }

    private static final class TabInfo {
        private final UUID uuid;
        private String name;
        private String nameLower;
        private boolean listed = true;
        private boolean listedKnown;
        private int latency;
        private boolean latencyKnown;
        private boolean propertiesMissing;
        private long updatedAtMs;

        private TabInfo(UUID uuid) {
            this.uuid = uuid;
        }
    }

    private record SuspectInfo(long seenAtMs) {
    }

    private record BotAssessment(int score) {
    }
}
