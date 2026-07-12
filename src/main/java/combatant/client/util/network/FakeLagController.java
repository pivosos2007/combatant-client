/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on LiquidBounce
 * (https://github.com/CCBlueX/LiquidBounce).
 * Copyright (c) 2015-2026 CCBlueX.
 *
 * LiquidBounce portions are licensed under GPLv3-or-later.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.util.network;

import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.EnumValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.BlinkPacketEvent;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.util.item.FoodUtil;
import combatant.client.util.target.TargetingUtil;

import java.util.EnumSet;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Headless fake-lag policy controller adapted from LiquidBounce's FakeLag module.
 */
public final class FakeLagController {
    public static final FakeLagController INSTANCE = new FakeLagController();

    private volatile boolean enabled;
    private Config config = Config.defaults();
    private long nextDelayMs = randomDelay(config);
    private long recoilUntilMs;
    private boolean enemyNearby;

    private FakeLagController() {
    }

    private static boolean shouldPassOnSafetyPacket(Packet<?> packet, LocalPlayer player) {
        if (packet instanceof ClientboundPlayerPositionPacket || packet instanceof ServerboundResourcePackPacket) {
            return true;
        }
        if (packet instanceof ClientboundSetEntityMotionPacket(int id, Vec3 movement)) {
            return id == player.getId() && movement != Vec3.ZERO;
        }
        if (packet instanceof ClientboundExplodePacket explosion) {
            return explosion.playerKnockback().isPresent() && explosion.playerKnockback().get() != Vec3.ZERO;
        }
        return packet instanceof ClientboundSetHealthPacket;
    }

    private static boolean isConsumable(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && (FoodUtil.isFood(stack) || stack.get(DataComponents.CONSUMABLE) != null);
    }

    private static LivingEntity findEnemy(Minecraft mc, double minRange, double maxRange) {
        if (mc == null || mc.level == null || mc.player == null) {
            return null;
        }
        double safeMin = Math.max(0.0, Math.min(minRange, maxRange));
        double safeMax = Math.max(safeMin, Math.max(minRange, maxRange));
        AABB box = mc.player.getBoundingBox().inflate(safeMax);
        double minRangeSq = safeMin * safeMin;
        double maxRangeSq = safeMax * safeMax;
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity entity : mc.level.getEntities(mc.player, box, e -> e instanceof LivingEntity)) {
            LivingEntity living = (LivingEntity) entity;
            if (!TargetingUtil.isValidCombatTarget(living)) {
                continue;
            }
            double dist = TargetingUtil.distanceToEntityBoxSq(mc.player.getEyePosition(), living);
            if (dist >= minRangeSq && dist <= maxRangeSq && dist < bestDist) {
                best = living;
                bestDist = dist;
            }
        }
        return best;
    }

    private static long randomDelay(Config config) {
        int min = Math.max(0, Math.min(config.minDelayMs(), config.maxDelayMs()));
        int max = Math.max(min, Math.max(config.minDelayMs(), config.maxDelayMs()));
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            enemyNearby = false;
            BlinkManager.INSTANCE.flush(TransferOrigin.OUTGOING);
        } else {
            nextDelayMs = randomDelay(config);
            recoilUntilMs = 0L;
        }
    }

    public Config getConfig() {
        return config;
    }

    public void configure(Config config) {
        this.config = config != null ? config : Config.defaults();
        this.nextDelayMs = randomDelay(this.config);
    }

    @EventHandler
    public void onTick(GameTickEvent event) {
        if (!enabled) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc != null ? mc.player : null;
        if (mc == null || mc.level == null || player == null) {
            enemyNearby = false;
            return;
        }

        enemyNearby = findEnemy(mc, config.minRange(), config.maxRange()) != null;
    }

    @EventHandler
    public void onBlinkPacket(BlinkPacketEvent event) {
        if (!enabled || event.getOrigin() != TransferOrigin.OUTGOING) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc != null ? mc.player : null;
        if (mc == null || mc.level == null || player == null || player.isDeadOrDying() || player.isInWater() || ClientScreen.current() != null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now < recoilUntilMs) {
            return;
        }

        if (BlinkManager.INSTANCE.isAboveTime(nextDelayMs)) {
            nextDelayMs = randomDelay(config);
            return;
        }

        Packet<?> packet = event.getPacket();
        if (config.flushOn().stream().anyMatch(flushOn -> flushOn.test(packet)) || shouldPassOnSafetyPacket(packet, player)) {
            recoilUntilMs = now + config.recoilTimeMs();
            return;
        }

        if (player.isUsingItem() && isConsumable(player.getUseItem())) {
            return;
        }

        if (config.mode() == Mode.CONSTANT) {
            event.setAction(BlinkManager.Action.QUEUE);
            return;
        }

        if (!enemyNearby) {
            return;
        }

        Vec3 serverPosition = BlinkManager.INSTANCE.getQueuedMovePositions().stream().findFirst().orElse(player.position());
        AABB serverBox = player.getDimensions(player.getPose()).makeBoundingBox(serverPosition);
        LivingEntity enemy = findEnemy(mc, config.minRange(), config.maxRange());
        if (enemy == null) {
            return;
        }

        double serverDistance = enemy.position().distanceTo(serverPosition);
        double clientDistance = enemy.position().distanceTo(player.position());
        if (serverDistance < clientDistance || enemy.getBoundingBox().intersects(serverBox)) {
            return;
        }

        event.setAction(BlinkManager.Action.QUEUE);
    }

    public enum Mode implements EnumValue.IdProvider {
        CONSTANT("constant"),
        DYNAMIC("dynamic");

        private final String id;

        Mode(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    public enum FlushOn {
        ENTITY_INTERACT,
        BLOCK_INTERACT,
        ACTION;

        boolean test(Packet<?> packet) {
            return switch (this) {
                case ENTITY_INTERACT -> packet instanceof ServerboundInteractPacket
                        || packet instanceof ServerboundSwingPacket;
                case BLOCK_INTERACT -> packet instanceof ServerboundUseItemOnPacket
                        || packet instanceof ServerboundSignUpdatePacket;
                case ACTION -> packet instanceof ServerboundPlayerActionPacket;
            };
        }
    }

    public record Config(
            double minRange,
            double maxRange,
            int minDelayMs,
            int maxDelayMs,
            int recoilTimeMs,
            Mode mode,
            EnumSet<FlushOn> flushOn
    ) {
        public Config {
            if (mode == null) mode = Mode.DYNAMIC;
            flushOn = flushOn == null ? EnumSet.noneOf(FlushOn.class) : EnumSet.copyOf(flushOn);
        }

        public static Config defaults() {
            return new Config(2.0, 5.0, 300, 600, 250, Mode.DYNAMIC, EnumSet.noneOf(FlushOn.class));
        }
    }
}
