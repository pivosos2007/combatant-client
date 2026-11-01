/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.map.location.PlayerLocationService;
import combatant.client.features.map.location.PlayerLocationSnapshot;
import combatant.client.features.map.location.PlayerLocationSource;
import combatant.client.features.map.location.PlayerLocationView;
import combatant.client.features.map.runtime.PlayerTrackingRangeRuntime;
import combatant.client.features.map.runtime.PlayerTrackingRangeSnapshot;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Notifier;
import combatant.client.features.relations.CategoryRules;
import combatant.client.features.relations.CategoryType;
import combatant.client.util.sound.MiscAlertSound;
import combatant.client.util.sound.SoundOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@ModuleInfo(
        id = "visualrange",
        displayName = "VisualRange",
        aliases = {"RangeAlert", "PlayerRadar"},
        category = ModuleCategory.MISC,
        subcategory = ModuleSubcategory.UTILITY,
        description = "module.visualrange.description")
public final class VisualRange extends Module {

    private static final long REMOTE_SOURCE_MAX_AGE_MS = 10_000L;
    private static final long PREWARN_FORGET_MS = 30_000L;
    private static final double PREWARN_HYSTERESIS = 24.0D;

    private final Minecraft mc = Minecraft.getInstance();

    private final BooleanMapValue events = group("visualRangeEvents", "events", new LinkedHashMap<>() {{
        put("enter", true);
        put("leave", true);
    }});
    private final BooleanMapValue targets = group("visualRangeTargets", "targets", new LinkedHashMap<>() {{
        put("friends", false);
        put("enemies", true);
        put("staff", true);
        put("others", true);
    }});
    private final BooleanValue mapPreWarning = bool("visualRangeMapPreWarning", "map_pre_warning", true);
    private final NumberValue<Double> mapWarningDistance = num("visualRangeMapWarningDistance", "map_warning_distance", 192.0D, 32.0D, 2048.0D);
    private final NumberValue<Double> preVisualLead = num("visualRangePreVisualLead", "pre_visual_lead", 32.0D, 0.0D, 256.0D);
    private final NumberValue<Double> minimumMapConfidence = num("visualRangeMinimumMapConfidence", "minimum_map_confidence", 0.35D, 0.0D, 1.0D);
    private final BooleanValue showCoords = bool("visualRangeShowCoords", "show_coords", true);
    private final BooleanValue warningSound = bool("visualRangeWarningSound", "warning_sound", false);
    private final NumberValue<Double> minimumDistance = num("visualRangeMinimumDistance", "minimum_distance", 0.0D, 0.0D, 256.0D);
    private final NumberValue<Integer> cooldownSeconds = num("visualRangeCooldownSeconds", "cooldown_seconds", 2, 0, 30);

    private final Map<UUID, TrackedPlayer> known = new HashMap<>();
    private final Map<UUID, Long> lastAlertAt = new HashMap<>();
    private final Map<UUID, Long> preWarnedSeenAt = new HashMap<>();
    private final Set<UUID> preWarned = new HashSet<>();
    private ClientLevel level;
    private boolean seeded;
    private int mapScanDivider;

    @Override
    public void onEnable() {
        clearState();
    }

    @Override
    public void onDisable() {
        clearState();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        if (mc.level == null || mc.player == null) {
            clearState();
            return;
        }
        if (level != mc.level) {
            clearState();
            level = mc.level;
        }

        Set<UUID> localVisible = new HashSet<>();
        Set<UUID> current = new HashSet<>();
        double minDistanceSq = minimumDistance.get() * minimumDistance.get();

        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;
            UUID id = player.getUUID();
            localVisible.add(id);
            if (!allows(player.getName().getString())) continue;
            if (mc.player.distanceToSqr(player) < minDistanceSq) continue;

            current.add(id);
            TrackedPlayer tracked = new TrackedPlayer(
                    player.getName().getString(),
                    player.getBlockX(), player.getBlockY(), player.getBlockZ()
            );

            if (seeded && !known.containsKey(id) && events.get("enter") && canAlert(id)) {
                notifyEnter(id, tracked);
            }
            known.put(id, tracked);
        }

        if (seeded && events.get("leave")) {
            for (Map.Entry<UUID, TrackedPlayer> entry : known.entrySet()) {
                if (!current.contains(entry.getKey()) && canAlert(entry.getKey())) {
                    notifyLeave(entry.getKey(), entry.getValue());
                }
            }
        }

        known.keySet().removeIf(id -> !current.contains(id));
        seeded = true;

        if (mapPreWarning.get() && ++mapScanDivider >= 5) {
            mapScanDivider = 0;
            scanRemoteLocations(localVisible);
        }
        pruneCooldowns();
        prunePreWarnings();
    }

    private void scanRemoteLocations(Set<UUID> localVisible) {
        PlayerLocationView view = PlayerLocationService.get().snapshot();
        if (view.sourcesByPlayer().isEmpty()) return;

        long now = System.currentTimeMillis();
        String worldKey = mc.level.dimension().identifier().toString();
        double threshold = mapWarningThreshold();
        double releaseThreshold = threshold + PREWARN_HYSTERESIS;

        for (Map.Entry<UUID, List<PlayerLocationSnapshot>> entry : view.sourcesByPlayer().entrySet()) {
            UUID id = entry.getKey();
            if (id == null || localVisible.contains(id) || id.equals(mc.player.getUUID())) continue;

            PlayerLocationSnapshot source = bestRemotePosition(entry.getValue(), worldKey, now);
            if (source == null || !allows(source.playerName())) continue;

            double centerDistance = Math.hypot(source.x() - mc.player.getX(), source.z() - mc.player.getZ());
            double uncertainty = Math.max(source.uncertaintyMajor(), source.uncertaintyMinor());
            double nearestPossible = Math.max(0.0D, centerDistance - uncertainty);
            boolean near = source.exact()
                    ? centerDistance <= threshold
                    : nearestPossible <= threshold && uncertainty <= Math.max(96.0D, threshold * 0.75D);

            if (!near) {
                if (centerDistance - uncertainty > releaseThreshold) {
                    preWarnedSeenAt.remove(id);
                    preWarned.remove(id);
                }
                continue;
            }

            preWarnedSeenAt.put(id, now);
            if (preWarned.contains(id) || !canAlert(id)) continue;
            notifyMapPreWarning(id, source, centerDistance, uncertainty);
            preWarned.add(id);
        }
    }

    private PlayerLocationSnapshot bestRemotePosition(List<PlayerLocationSnapshot> sources, String worldKey, long now) {
        if (sources == null || sources.isEmpty()) return null;
        return sources.stream()
                .filter(snapshot -> snapshot != null
                        && snapshot.source() != PlayerLocationSource.LOCAL_ENTITY_EXACT
                        && snapshot.source() != PlayerLocationSource.LOCATOR_BEARING
                        && snapshot.source() != PlayerLocationSource.HISTORICAL
                        && snapshot.hasPosition()
                        && snapshot.worldKey().equals(worldKey)
                        && snapshot.confidence() >= minimumMapConfidence.get()
                        && snapshot.ageMs(now) <= REMOTE_SOURCE_MAX_AGE_MS)
                .max(Comparator
                        .comparingInt((PlayerLocationSnapshot snapshot) -> snapshot.source().priority())
                        .thenComparingDouble(PlayerLocationSnapshot::confidence)
                        .thenComparingLong(PlayerLocationSnapshot::observedAtMs))
                .orElse(null);
    }

    private double mapWarningThreshold() {
        double configured = mapWarningDistance.get();
        PlayerTrackingRangeSnapshot tracking = PlayerTrackingRangeRuntime.get().snapshot();
        double boundary = tracking.hasEmpiricalBoundary()
                ? tracking.playerTrackingBoundaryBlocks()
                : Math.max(tracking.confirmedPlayerTrackingLowerBoundBlocks(), tracking.vanillaPlayerTrackingCapBlocks());
        if (!Double.isFinite(boundary) || boundary <= 0.0D) return configured;
        return Math.max(configured, boundary + preVisualLead.get());
    }

    private void notifyMapPreWarning(UUID id, PlayerLocationSnapshot source, double distance, double uncertainty) {
        int roundedDistance = (int) Math.round(distance);
        String name = source.playerName().isBlank() ? I18n.get("notification.visualrange.unknown_player") : source.playerName();
        String text;
        if (source.exact() && showCoords.get() && Double.isFinite(source.y())) {
            text = I18n.get("notification.visualrange.map_exact_coords", name,
                    (int) Math.floor(source.x()), (int) Math.floor(source.y()), (int) Math.floor(source.z()), roundedDistance);
        } else if (source.exact()) {
            text = I18n.get("notification.visualrange.map_exact", name, roundedDistance);
        } else {
            int roundedUncertainty = (int) Math.ceil(Math.max(0.0D, uncertainty));
            text = I18n.get("notification.visualrange.map_estimate", name, roundedDistance, roundedUncertainty);
        }
        Notifier.warning(text);
        lastAlertAt.put(id, Util.getMillis());
        if (warningSound.get()) MiscAlertSound.WARNING.play(SoundOptions.gain(0.85));
    }

    private void notifyEnter(UUID id, TrackedPlayer player) {
        String text = showCoords.get()
                ? I18n.get("notification.visualrange.enter_coords", player.name(), player.x(), player.y(), player.z())
                : I18n.get("notification.visualrange.enter", player.name());
        Notifier.warning(text);
        lastAlertAt.put(id, Util.getMillis());
        if (warningSound.get()) {
            MiscAlertSound.WARNING.play(SoundOptions.gain(0.85));
        }
    }

    private void notifyLeave(UUID id, TrackedPlayer player) {
        String text = showCoords.get()
                ? I18n.get("notification.visualrange.leave_coords", player.name(), player.x(), player.y(), player.z())
                : I18n.get("notification.visualrange.leave", player.name());
        Notifier.info(text);
        lastAlertAt.put(id, Util.getMillis());
    }

    private boolean allows(String playerName) {
        if (playerName == null || playerName.isBlank()) return false;
        CategoryType type = CategoryRules.determine(playerName);
        return switch (type) {
            case FRIEND, BEDWARS_SELF -> targets.get("friends");
            case ENEMY, BEDWARS_ENEMY -> targets.get("enemies");
            case STAFF -> targets.get("staff");
            case DEFAULT -> targets.get("others");
        };
    }

    private boolean canAlert(UUID id) {
        int seconds = cooldownSeconds.get();
        if (seconds <= 0) return true;
        long last = lastAlertAt.getOrDefault(id, 0L);
        return Util.getMillis() - last >= seconds * 1000L;
    }

    private void pruneCooldowns() {
        if (lastAlertAt.size() < 64) return;
        long now = Util.getMillis();
        long keep = Math.max(30_000L, cooldownSeconds.get() * 4000L);
        lastAlertAt.entrySet().removeIf(entry -> now - entry.getValue() > keep);
    }

    private void prunePreWarnings() {
        if (preWarnedSeenAt.isEmpty()) return;
        long now = System.currentTimeMillis();
        Set<UUID> stale = new HashSet<>();
        preWarnedSeenAt.forEach((id, seenAt) -> {
            if (now - seenAt > PREWARN_FORGET_MS) stale.add(id);
        });
        for (UUID id : stale) {
            preWarnedSeenAt.remove(id);
            preWarned.remove(id);
        }
    }

    private void clearState() {
        known.clear();
        lastAlertAt.clear();
        preWarnedSeenAt.clear();
        preWarned.clear();
        level = null;
        seeded = false;
        mapScanDivider = 0;
    }

    private record TrackedPlayer(String name, int x, int y, int z) {
    }
}
