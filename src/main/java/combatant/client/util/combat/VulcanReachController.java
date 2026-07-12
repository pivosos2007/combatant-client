/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.util.network.BacktrackController;
import combatant.client.util.player.NetworkStatsUtil;

import java.util.*;

/**
 * Local model for Vulcan 2.9.7 Reach A/B.
 * This does not change combat by itself; Reach/Hitbox should query planReach()
 * and actual sent attacks are observed through the same attack -> movement split.
 */
public final class VulcanReachController {

    public static final VulcanReachController INSTANCE = new VulcanReachController();

    private static final double REACH_A_CONFIG_MAX = 3.03;
    private static final double REACH_A_BODY_SUBTRACT = 0.52;
    private static final double REACH_A_BUFFER_MAX = 3.0;
    private static final double REACH_A_LATCH_RELEASE = REACH_A_BUFFER_MAX - 1.0;
    private static final double REACH_A_DECAY = 0.035;
    private static final double REACH_A_NO_HISTORY_DECAY = 0.025;
    private static final double REACH_A_RECENT_SERVER_ALLOWANCE = 1.15;
    private static final double REACH_A_LIQUID_ALLOWANCE = 0.1;
    private static final double REACH_A_SANITY_CAP = 8.0;

    private static final double REACH_B_CONFIG_MAX = 3.15;
    private static final double REACH_B_BODY_SUBTRACT = 0.565;
    private static final double REACH_B_BUFFER_MAX = 6.0;
    private static final double REACH_B_LATCH_RELEASE = REACH_B_BUFFER_MAX - 1.0;
    private static final double REACH_B_DECAY = 0.225;
    private static final double REACH_B_BASE_EXTRA = 0.05;
    private static final double REACH_B_HORIZONTAL_ALLOWANCE = 0.9;
    private static final double REACH_B_VERTICAL_ALLOWANCE = 0.66;
    private static final double REACH_B_MOVING_STATE_ALLOWANCE = 0.32;
    private static final double REACH_B_FRESH_STATE_ALLOWANCE = 0.35;
    private static final double REACH_B_STATE_COUNTER_SCALE = 0.125;
    private static final double REACH_B_HIGH_LATENCY_ALLOWANCE = 1.25;
    private static final double REACH_B_SANITY_CAP = 6.0;

    private static final long SERVER_POSITION_WINDOW_MS = 1500L;
    private static final long JOIN_WORLD_WINDOW_MS = 1500L;
    private static final long HISTORY_MAX_AGE_MS = 700L;
    private static final long HISTORY_STRICT_WINDOW_MS = 250L;
    private static final int HISTORY_MAX_SAMPLES = 20;
    private static final double BUFFER_EPSILON = 1.0E-6;
    private static final double FAST_POST_HIT_MOVE_THRESHOLD = 0.18;
    private static final double FAST_POST_HIT_APPROACH_ALLOWANCE = 0.45;

    private final Minecraft mc = Minecraft.getInstance();
    private final Map<UUID, ArrayDeque<HistorySample>> targetHistory = new HashMap<>();

    private double reachABuffer;
    private double reachBBuffer;
    private int pendingAttackEntityId = -1;
    private long pendingAttackMs;
    private Vec3 pendingAttackPosition;
    private long lastServerPositionMs = Long.MIN_VALUE;
    private long lastJoinOrWorldMs = Long.MIN_VALUE;
    private Vec3 lastSentPosition;
    private double lastSentHorizontalDelta;
    private double lastSentVerticalDelta;
    private int movementStateTicks;
    private boolean reachAClampLatched;
    private boolean reachBClampLatched;

    private VulcanReachController() {
    }

    public ReachPlan planReach(Entity target, double requestedReach) {
        LocalPlayer player = mc.player;
        if (player == null || target == null) {
            return ReachPlan.empty(requestedReach);
        }

        CheckState state = buildState(player, target, false);
        updateClampLatches();

        boolean forceReachAClamp = reachAClampLatched && !state.reachAExempt;
        boolean forceReachBClamp = reachBClampLatched && !state.reachBExempt;

        double strictReachALimit = state.reachAExempt
                ? requestedReach
                : reachAEdgeLimit(player, target, state.reachAAllowed);
        double reachALimit = state.reachAExempt || (state.noReachAHistory && !reachAClampLatched)
                ? requestedReach
                : strictReachALimit;
        if (state.fastPostHitApproach) {
            reachALimit = Math.min(requestedReach, reachALimit + FAST_POST_HIT_APPROACH_ALLOWANCE);
        }
        double reachBLimit = state.reachBExempt
                ? requestedReach
                : reachBEdgeLimit(player, target, state.reachBAllowed);

        boolean overB = !state.reachBExempt
                && requestedReach > reachBLimit;
        boolean spendsA = !state.reachAExempt
                && !state.noReachAHistory
                && requestedReach > strictReachALimit
                && state.reachAMeasured < REACH_A_SANITY_CAP;
        boolean spendsB = overB && state.reachBMeasured < REACH_B_SANITY_CAP;

        double clamped = requestedReach;
        if (forceReachAClamp) {
            clamped = Math.min(clamped, strictReachALimit);
        }
        if (forceReachBClamp) {
            clamped = Math.min(clamped, reachBLimit);
        }

        return new ReachPlan(
                requestedReach,
                Math.max(0.0, clamped),
                Math.max(0.0, reachALimit),
                Math.max(0.0, reachBLimit),
                reachABuffer,
                reachBBuffer,
                state.reachAExempt,
                state.reachBExempt,
                state.noReachAHistory,
                spendsA,
                spendsB,
                state.reason
        );
    }

    public double clampReach(Entity target, double requestedReach) {
        return planReach(target, requestedReach).allowedReach();
    }

    public HudSnapshot hudSnapshot(Entity target, double requestedReach) {
        if (!(target instanceof Player)) {
            return null;
        }

        ReachPlan plan = planReach(target, requestedReach);
        boolean dirty = reachABuffer > 1.0E-4
                || reachBBuffer > 1.0E-4
                || reachAClampLatched
                || reachBClampLatched;

        double displayReach = plan.allowedReach();
        boolean clamped = displayReach + BUFFER_EPSILON < requestedReach;

        int rawRemaining = Math.min(
                longHitsRemaining(reachABuffer, REACH_A_BUFFER_MAX, reachAClampLatched),
                longHitsRemaining(reachBBuffer, REACH_B_BUFFER_MAX, reachBClampLatched)
        );
        int remaining = clamped ? 0 : Math.max(1, rawRemaining);

        return new HudSnapshot(displayReach, Math.max(0, remaining), dirty, plan.reason());
    }

    public HudSnapshot hudSnapshot(double requestedReach) {
        boolean dirty = reachABuffer > 1.0E-4
                || reachBBuffer > 1.0E-4
                || reachAClampLatched
                || reachBClampLatched;

        int rawRemaining = Math.min(
                longHitsRemaining(reachABuffer, REACH_A_BUFFER_MAX, reachAClampLatched),
                longHitsRemaining(reachBBuffer, REACH_B_BUFFER_MAX, reachBClampLatched)
        );
        int remaining = reachAClampLatched || reachBClampLatched ? 0 : Math.max(1, rawRemaining);

        return new HudSnapshot(requestedReach, Math.max(0, remaining), dirty, "no_target");
    }

    public double getReachABuffer() {
        return reachABuffer;
    }

    public double getReachBBuffer() {
        return reachBBuffer;
    }

    public void clear() {
        reachABuffer = 0.0;
        reachBBuffer = 0.0;
        pendingAttackEntityId = -1;
        pendingAttackMs = 0L;
        pendingAttackPosition = null;
        lastSentPosition = null;
        lastSentHorizontalDelta = 0.0;
        lastSentVerticalDelta = 0.0;
        movementStateTicks = 0;
        reachAClampLatched = false;
        reachBClampLatched = false;
        targetHistory.clear();
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            clear();
            return;
        }

        long now = System.currentTimeMillis();
        for (Player other : mc.level.players()) {
            if (other == player || !other.isAlive()) {
                continue;
            }
            addHistorySample(other, now);
        }
        pruneHistory(now);
    }

    @EventHandler
    private void onPacketSendPost(PacketEvent.SendPost event) {
        if (event.getPacket() instanceof ServerboundInteractPacket packet) {
            onAttackPacket(packet);
            return;
        }

        if (event.getPacket() instanceof ServerboundMovePlayerPacket packet) {
            updateSentMovement(packet);
            evaluatePendingAttack();
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            lastServerPositionMs = System.currentTimeMillis();
            return;
        }

        if (event.getPacket() instanceof ClientboundLoginPacket) {
            clear();
            lastJoinOrWorldMs = System.currentTimeMillis();
            return;
        }

        if (event.getPacket() instanceof ClientboundRespawnPacket) {
            pendingAttackEntityId = -1;
        }
    }

    private void onAttackPacket(ServerboundInteractPacket packet) {
        if (!isAttack(packet) || mc.level == null) {
            return;
        }

        int entityId = packet.entityId();
        Entity target = mc.level.getEntity(entityId);
        if (!(target instanceof Player)) {
            pendingAttackEntityId = -1;
            return;
        }

        pendingAttackEntityId = entityId;
        pendingAttackMs = System.currentTimeMillis();
        pendingAttackPosition = mc.player != null ? mc.player.position() : null;
    }

    private void evaluatePendingAttack() {
        if (pendingAttackEntityId < 0 || mc.level == null || mc.player == null) {
            return;
        }

        Entity target = mc.level.getEntity(pendingAttackEntityId);
        pendingAttackEntityId = -1;

        if (!(target instanceof Player)) {
            return;
        }

        CheckState state = buildState(mc.player, target, true);
        applyReachA(state);
        applyReachB(state);
        pendingAttackPosition = null;
    }

    private void applyReachA(CheckState state) {
        if (state.reachAExempt) {
            decayABuffer(REACH_A_DECAY);
            return;
        }

        if (state.noReachAHistory) {
            decayABuffer(REACH_A_NO_HISTORY_DECAY);
            return;
        }

        if (state.reachAMeasured > state.reachAAllowed && state.reachAMeasured < REACH_A_SANITY_CAP) {
            reachABuffer = increaseLocalBuffer(reachABuffer, REACH_A_BUFFER_MAX);
        } else {
            decayABuffer(REACH_A_DECAY);
        }
    }

    private void applyReachB(CheckState state) {
        if (state.reachBExempt) {
            decayBBuffer(REACH_B_DECAY);
            return;
        }

        if (state.reachBMeasured > state.reachBAllowed && state.reachBMeasured < REACH_B_SANITY_CAP) {
            reachBBuffer = increaseLocalBuffer(reachBBuffer, REACH_B_BUFFER_MAX);
        } else {
            decayBBuffer(REACH_B_DECAY);
        }
    }

    private CheckState buildState(LocalPlayer player, Entity target, boolean evaluatingPendingAttack) {
        if (!(target instanceof Player targetPlayer)) {
            return CheckState.exempt("non_player");
        }

        boolean attackerVehicle = player.isPassenger();
        boolean targetVehicle = targetPlayer.isPassenger();
        boolean attackerBoat = attackerVehicle && isBoatLike(player.getVehicle());
        boolean targetBoat = targetVehicle && isBoatLike(targetPlayer.getVehicle());
        boolean creative = player.isCreative();
        boolean liquid = player.isInWater() || player.isUnderWater() || player.isInLava();
        boolean recentServer = isRecent(lastServerPositionMs, SERVER_POSITION_WINDOW_MS);
        boolean recentJoinWorld = isRecent(lastJoinOrWorldMs, JOIN_WORLD_WINDOW_MS);
        boolean knockback = hasKnockback(player.getMainHandItem());
        boolean fastPostHitApproach = evaluatingPendingAttack && isFastPostHitApproach(player, targetPlayer);

        boolean reachAExempt = creative || attackerVehicle || attackerBoat || targetVehicle || targetBoat || knockback;
        boolean reachBExempt = creative || attackerVehicle || attackerBoat || targetVehicle || targetBoat || recentServer || recentJoinWorld;

        double baseReach = getInteractionRange(player);
        double reachAAllowed = baseReach + (REACH_A_CONFIG_MAX - 3.0);
        if (recentServer) {
            reachAAllowed += REACH_A_RECENT_SERVER_ALLOWANCE;
        }
        if (liquid) {
            reachAAllowed += REACH_A_LIQUID_ALLOWANCE;
        }

        double reachBAllowed = baseReach + (REACH_B_CONFIG_MAX - 3.0) + REACH_B_BASE_EXTRA;
        reachBAllowed += lastSentHorizontalDelta * REACH_B_HORIZONTAL_ALLOWANCE;
        reachBAllowed += lastSentVerticalDelta * REACH_B_VERTICAL_ALLOWANCE;
        if (lastSentHorizontalDelta > 1.0E-4 || lastSentVerticalDelta > 1.0E-4) {
            reachBAllowed += REACH_B_MOVING_STATE_ALLOWANCE;
        }
        if (movementStateTicks < 3) {
            reachBAllowed += REACH_B_FRESH_STATE_ALLOWANCE;
        }
        reachBAllowed += Math.min(4, movementStateTicks) * REACH_B_STATE_COUNTER_SCALE;
        if (NetworkStatsUtil.getPing(mc) > 100) {
            reachBAllowed += REACH_B_HIGH_LATENCY_ALLOWANCE;
        }

        double reachAMeasured = reachAMeasureDistance(player, targetPlayer);
        double reachBMeasured = Math.max(0.0, player.position().distanceTo(targetPlayer.position()) - REACH_B_BODY_SUBTRACT);
        boolean noHistory = reachAMeasured < 0.0;

        String reason = resolveReason(reachAExempt, reachBExempt, noHistory, recentServer, recentJoinWorld, liquid, knockback);
        return new CheckState(
                reachAExempt,
                reachBExempt,
                noHistory,
                reachAAllowed,
                reachBAllowed,
                reachAMeasured,
                reachBMeasured,
                fastPostHitApproach,
                reason
        );
    }

    private double reachAMeasureDistance(LocalPlayer player, Player target) {
        if (shouldUseBacktrackHistory(target)) {
            return reachAHistoryDistance(player, target);
        }

        Vec3 playerPos = player.position();
        Vec3 targetPos = target.position();
        double dx = playerPos.x - targetPos.x;
        double dz = playerPos.z - targetPos.z;
        return Math.max(0.0, Math.sqrt(dx * dx + dz * dz) - REACH_A_BODY_SUBTRACT);
    }

    private boolean shouldUseBacktrackHistory(Entity target) {
        BacktrackController backtrack = BacktrackController.INSTANCE;
        Entity backtrackTarget = backtrack.getTarget();
        return backtrack.isEnabled()
                && backtrack.isLagging()
                && backtrackTarget != null
                && target != null
                && backtrackTarget.getId() == target.getId();
    }

    private double reachAHistoryDistance(LocalPlayer player, Player target) {
        ArrayDeque<HistorySample> samples = targetHistory.get(target.getUUID());
        if (samples == null || samples.isEmpty()) {
            return -1.0;
        }

        long now = pendingAttackMs > 0L ? pendingAttackMs : System.currentTimeMillis();
        Vec3 playerPos = player.position();
        double best = Double.MAX_VALUE;

        for (HistorySample sample : samples) {
            long age = Math.abs(now - sample.timeMs());
            if (age > HISTORY_STRICT_WINDOW_MS) {
                continue;
            }
            double dx = playerPos.x - sample.pos().x;
            double dz = playerPos.z - sample.pos().z;
            best = Math.min(best, Math.sqrt(dx * dx + dz * dz) - REACH_A_BODY_SUBTRACT);
        }

        return best == Double.MAX_VALUE ? -1.0 : Math.max(0.0, best);
    }

    private double reachAEdgeLimit(LocalPlayer player, Entity target, double allowed) {
        Vec3 from = player.getEyePosition(1.0F);
        AABB box = target.getBoundingBox();
        Vec3 center = box.getCenter();

        double centerDistance = Math.sqrt(squaredHorizontalDistance(from.x, from.z, center.x, center.z));
        double edgeDistance = horizontalBoxDistance(box, from);
        double centerToEdgeReduction = Math.max(0.0, centerDistance - edgeDistance);

        return Math.max(0.0, allowed + REACH_A_BODY_SUBTRACT - centerToEdgeReduction);
    }

    private double reachBEdgeLimit(LocalPlayer player, Entity target, double allowed) {
        Vec3 from = player.getEyePosition(1.0F);
        AABB box = target.getBoundingBox();
        Vec3 center = box.getCenter();

        double centerDistance = from.distanceTo(center);
        double edgeDistance = Math.sqrt(boxDistanceSq(box, from));
        double centerToEdgeReduction = Math.max(0.0, centerDistance - edgeDistance);

        return Math.max(0.0, allowed + REACH_B_BODY_SUBTRACT - centerToEdgeReduction);
    }

    private double horizontalBoxDistance(AABB box, Vec3 point) {
        double dx = 0.0;
        if (point.x < box.minX) dx = box.minX - point.x;
        else if (point.x > box.maxX) dx = point.x - box.maxX;

        double dz = 0.0;
        if (point.z < box.minZ) dz = box.minZ - point.z;
        else if (point.z > box.maxZ) dz = point.z - box.maxZ;

        return Math.sqrt(dx * dx + dz * dz);
    }

    private double boxDistanceSq(AABB box, Vec3 point) {
        double dx = 0.0;
        if (point.x < box.minX) dx = box.minX - point.x;
        else if (point.x > box.maxX) dx = point.x - box.maxX;

        double dy = 0.0;
        if (point.y < box.minY) dy = box.minY - point.y;
        else if (point.y > box.maxY) dy = point.y - box.maxY;

        double dz = 0.0;
        if (point.z < box.minZ) dz = box.minZ - point.z;
        else if (point.z > box.maxZ) dz = point.z - box.maxZ;

        return dx * dx + dy * dy + dz * dz;
    }

    private double squaredHorizontalDistance(double x1, double z1, double x2, double z2) {
        double dx = x1 - x2;
        double dz = z1 - z2;
        return dx * dx + dz * dz;
    }

    private void updateSentMovement(ServerboundMovePlayerPacket packet) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }

        Vec3 current = new Vec3(
                packet.getX(player.getX()),
                packet.getY(player.getY()),
                packet.getZ(player.getZ())
        );

        if (lastSentPosition == null) {
            lastSentPosition = current;
            lastSentHorizontalDelta = 0.0;
            lastSentVerticalDelta = 0.0;
            movementStateTicks = 0;
            return;
        }

        double dx = current.x - lastSentPosition.x;
        double dy = current.y - lastSentPosition.y;
        double dz = current.z - lastSentPosition.z;
        lastSentHorizontalDelta = Math.sqrt(dx * dx + dz * dz);
        lastSentVerticalDelta = Math.abs(dy);
        lastSentPosition = current;

        if (lastSentHorizontalDelta > 1.0E-4 || lastSentVerticalDelta > 1.0E-4) {
            movementStateTicks = Mth.clamp(movementStateTicks + 1, 0, 20);
        } else {
            movementStateTicks = 0;
        }
    }

    private boolean isFastPostHitApproach(LocalPlayer player, Player target) {
        if (pendingAttackPosition == null) {
            return false;
        }

        Vec3 current = player.position();
        double moved = Math.sqrt(squaredHorizontalDistance(
                pendingAttackPosition.x,
                pendingAttackPosition.z,
                current.x,
                current.z
        ));
        if (moved < FAST_POST_HIT_MOVE_THRESHOLD) {
            return false;
        }

        Vec3 targetCenter = target.getBoundingBox().getCenter();
        double then = Math.sqrt(squaredHorizontalDistance(
                pendingAttackPosition.x,
                pendingAttackPosition.z,
                targetCenter.x,
                targetCenter.z
        ));
        double now = Math.sqrt(squaredHorizontalDistance(
                current.x,
                current.z,
                targetCenter.x,
                targetCenter.z
        ));

        return now + 0.03 < then;
    }

    private void addHistorySample(Player player, long now) {
        ArrayDeque<HistorySample> samples = targetHistory.computeIfAbsent(player.getUUID(), ignored -> new ArrayDeque<>());
        samples.addLast(new HistorySample(player.position(), now));
        while (samples.size() > HISTORY_MAX_SAMPLES) {
            samples.removeFirst();
        }
    }

    private void pruneHistory(long now) {
        Iterator<Map.Entry<UUID, ArrayDeque<HistorySample>>> iterator = targetHistory.entrySet().iterator();
        while (iterator.hasNext()) {
            ArrayDeque<HistorySample> samples = iterator.next().getValue();
            while (!samples.isEmpty() && now - samples.peekFirst().timeMs() > HISTORY_MAX_AGE_MS) {
                samples.removeFirst();
            }
            if (samples.isEmpty()) {
                iterator.remove();
            }
        }
    }

    private double getInteractionRange(LocalPlayer player) {
        try {
            return player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        } catch (Throwable ignored) {
            return 3.0;
        }
    }

    private boolean hasKnockback(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Holder<Enchantment> entry = getEnchantmentEntry(Enchantments.KNOCKBACK);
        return entry != null && EnchantmentHelper.getItemEnchantmentLevel(entry, stack) > 0;
    }

    private Holder<Enchantment> getEnchantmentEntry(ResourceKey<Enchantment> key) {
        if (mc.level == null) {
            return null;
        }
        Registry<Enchantment> registry = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Enchantment enchantment = registry.getValue(key);
        return enchantment != null ? registry.wrapAsHolder(enchantment) : null;
    }

    private boolean isBoatLike(Entity entity) {
        return entity != null && entity.getType().toString().toLowerCase().contains("boat");
    }

    private boolean isAttack(ServerboundInteractPacket packet) {
        return packet.hand() == null && packet.location() == null;
    }

    private boolean isAtPreAlert(double buffer, double max) {
        return buffer + 1.0 > max + BUFFER_EPSILON;
    }

    private void updateClampLatches() {
        if (isAtPreAlert(reachABuffer, REACH_A_BUFFER_MAX)) {
            reachAClampLatched = true;
        } else if (reachABuffer <= REACH_A_LATCH_RELEASE) {
            reachAClampLatched = false;
        }

        if (isAtPreAlert(reachBBuffer, REACH_B_BUFFER_MAX)) {
            reachBClampLatched = true;
        } else if (reachBBuffer <= REACH_B_LATCH_RELEASE) {
            reachBClampLatched = false;
        }
    }

    private boolean isRecent(long timestamp, long windowMs) {
        return timestamp != Long.MIN_VALUE && System.currentTimeMillis() - timestamp <= windowMs;
    }

    private void decayABuffer(double amount) {
        reachABuffer = Math.max(0.0, reachABuffer - amount);
    }

    private void decayBBuffer(double amount) {
        reachBBuffer = Math.max(0.0, reachBBuffer - amount);
    }

    private double increaseLocalBuffer(double buffer, double max) {
        return Math.min(max, buffer + 1.0);
    }

    private int longHitsRemaining(double buffer, double max, boolean latched) {
        if (latched || isAtPreAlert(buffer, max)) {
            return 0;
        }
        return Math.max(0, (int) Math.floor(max - buffer + BUFFER_EPSILON));
    }

    private String resolveReason(boolean reachAExempt,
                                 boolean reachBExempt,
                                 boolean noHistory,
                                 boolean recentServer,
                                 boolean recentJoinWorld,
                                 boolean liquid,
                                 boolean knockback) {
        if (recentServer) return "server_position";
        if (recentJoinWorld) return "join_world";
        if (noHistory) return "no_history";
        if (liquid) return "liquid";
        if (knockback) return "knockback";
        if (reachAExempt || reachBExempt) return "exempt";
        return "normal";
    }

    public record ReachPlan(
            double requestedReach,
            double allowedReach,
            double reachALimit,
            double reachBLimit,
            double reachABuffer,
            double reachBBuffer,
            boolean reachAExempt,
            boolean reachBExempt,
            boolean noReachAHistory,
            boolean usesReachABuffer,
            boolean usesReachBBuffer,
            String reason
    ) {
        private static ReachPlan empty(double requestedReach) {
            return new ReachPlan(requestedReach, requestedReach, requestedReach, requestedReach,
                    0.0, 0.0, true, true, true, false, false, "empty");
        }
    }

    public record HudSnapshot(
            double displayReach,
            int longHitsRemaining,
            boolean dirty,
            String reason
    ) {
    }

    private record HistorySample(Vec3 pos, long timeMs) {
    }

    private record CheckState(
            boolean reachAExempt,
            boolean reachBExempt,
            boolean noReachAHistory,
            double reachAAllowed,
            double reachBAllowed,
            double reachAMeasured,
            double reachBMeasured,
            boolean fastPostHitApproach,
            String reason
    ) {
        private static CheckState exempt(String reason) {
            return new CheckState(true, true, true, 0.0, 0.0, -1.0, -1.0, false, reason);
        }
    }
}
