/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import combatant.client.config.values.*;
import combatant.client.util.time.TimerController;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.util.Mth;
import combatant.client.config.values.*;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.EventSync;
import combatant.client.events.impl.PacketEvent;
import combatant.client.events.impl.PostPlayerUpdateEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.input.KeyManager;
import combatant.client.util.player.MovementUtil;

//todo Description
@ModuleInfo(
        id = "timer",
        displayName = "Timer",
        category = ModuleCategory.MOVEMENT
)
public class Timer extends Module {

    // ── Vulcan TimerA sliding-window model ───────────────────────────────────────
    //
    // TimerA uses a 50-sample sliding FIFO window (EvictingQueue).
    // Each add() evicts the OLDEST entry. The oldest entries are the SLOW packets
    // from before the boost, not the newly added fast packets:
    //
    //   After N fast packets, window = [S×(50-N), F×N]  (old slow | new fast)
    //   Slow send: evict the oldest slow entry, not a fast entry.
    //   Fast packets only leave after (50-N) slow packets flush the old slow first.
    //   REAL tail = 50 slow packets (not N or 14 as the broken counter model said).
    //
    // Correct approach: track the exact window state with a circular boolean buffer.
    //   canBoost = fastCount < kMax   where kMax = floor(2.5S / (1.05*(S-1)))
    //
    // For S=2.64: kMax=3.  With fastCount ≤ 2 (boost condition fastCount < kMax):
    //   avgDelay = (2×18.9 + 48×50)/50 = 48.76ms → speed = 1.025 << 1.05. SAFE.
    // For S=2.0:  kMax=4, fastCount ≤ 3. speed ≈ 1.031. SAFE.
    //
    // Energy bar = 1 - fastCount/kMax.  Full = all window slots slow. Empty = at kMax.
    private static final int VULCAN_WINDOW_SIZE = 50;
    private static final boolean[] vulcanWindow = new boolean[VULCAN_WINDOW_SIZE]; // true=fast
    @SuppressWarnings("unused")
    private static final float timerABuffer = 0f;
    @SuppressWarnings("unused")
    private static final float fastInWindow = 0f;
    @SuppressWarnings("unused")
    private static final boolean vulcanBoostPhase = false;
    public static float energy = 0f;
    public static float yaw;
    public static float pitch;
    private static int vulcanWindowHead = 0;   // next write position (circular)
    private static int vulcanWindowCount = 0;   // filled slots 0..50
    private static int vulcanFastCount = 0;   // # fast entries currently in window
    private static double prevPosX;
    private static double prevPosY;
    private static double prevPosZ;
    private static float tickTimer = 1f;
    private static float externalTickTimer = 1f;
    private final EnumValue<Mode> mode =
            enumMode("mode", Mode.NORMAL, Mode.values());
    private final BooleanValue old =
            visibleWhen(bool("old", false), () -> mode.get() == Mode.MATRIX);
    private final NumberValue<Double> speed =
            visibleWhen(num("speed", 2.0, 0.1, 10.0), () -> mode.get() != Mode.SHIFT);
    private final NumberValue<Integer> shiftTicks =
            visibleWhen(num("shift_ticks", 10, 1, 40), () -> mode.get() == Mode.SHIFT);
    private final KeyBindValue boostKey =
            visibleWhen(bind("boost_key", "NONE", BindMode.HOLD),
                    () -> mode.get() == Mode.GRIM || mode.get() == Mode.GRIM_CONCEPT || mode.get() == Mode.VULCAN);
    private final NumberValue<Double> grimFillTime =
            visibleWhen(num("grim_fill_time", 20.0, 1.0, 120.0), () -> mode.get() == Mode.GRIM_CONCEPT);
    private final NumberValue<Double> grimConsumeMult =
            visibleWhen(num("grim_consume_mult", 10.0, 1.0, 20.0), () -> mode.get() == Mode.GRIM_CONCEPT);
    private final EnumValue<OnFlag> onFlag =
            enumMode("on_flag", OnFlag.RESET, OnFlag.values());
    private long cancelTime;
    private long lastSetbackTime = 0L;
    private String lastBoostCombo = "NONE";
    private long lastPingTime = 0L;

    public static float getEnergy01() {
        return Mth.clamp(energy, 0f, 1f);
    }

    public static float getTickTimer() {
        float controllerTimer = TimerController.getTimerSpeed();
        if (tickTimer != 1f) return tickTimer;
        return externalTickTimer == 1f ? controllerTimer : externalTickTimer;
    }

    public static void setExternalTickTimer(float timer) {
        if (Float.isNaN(timer) || Float.isInfinite(timer)) {
            clearExternalTickTimer();
            return;
        }
        externalTickTimer = Mth.clamp(timer, 0.1f, 10.0f);
        TimerController.requestTimerSpeed(
                externalTickTimer,
                TimerController.IMPORTANT_FOR_USAGE_1,
                Timer.class
        );
    }

    public static void clearExternalTickTimer() {
        externalTickTimer = 1f;
        TimerController.clear(Timer.class);
    }

    @Override
    public void onEnable() {
        tickTimer = 1f;
        externalTickTimer = 1f;
        if (mode.get() == Mode.VULCAN) {
            vulcanWindowHead = 0;
            vulcanWindowCount = 0;
            vulcanFastCount = 0;
            java.util.Arrays.fill(vulcanWindow, false);
            energy = 1f;
        } else if (mode.get() != Mode.MATRIX) {
            energy = 0f;
        }
        if (mode.get() == Mode.GRIM || mode.get() == Mode.GRIM_CONCEPT) {
            cancelTime = System.currentTimeMillis();
            lastPingTime = 0L;
        }
        ensureBoostBind();
    }

    @Override
    public void onDisable() {
        tickTimer = 1f;
        externalTickTimer = 1f;
        vulcanWindowHead = 0;
        vulcanWindowCount = 0;
        vulcanFastCount = 0;
        java.util.Arrays.fill(vulcanWindow, false);
    }

    @Override
    public void onTick() {
        ensureBoostBind();

        switch (mode.get()) {
            case NORMAL -> tickTimer = speed.get().floatValue();
            case MATRIX -> {
                if (!MovementUtil.isMoving()) {
                    tickTimer = 1f;
                    return;
                }

                tickTimer = Math.max(speed.get().floatValue(), 1f);

                if (energy > 0f) {
                    float drain = (float) ((0.1f * speed.get()) - 0.1f);
                    energy = Mth.clamp(energy - drain, 0f, 1f);
                } else {
                    setEnabled(false);
                }
            }
            case GRIM -> {
                if (energy <= 0f || !isBoostHeld() || getSetbackAge() < 2000L) {
                    tickTimer = 1f;
                    return;
                }

                tickTimer = Math.max(speed.get().floatValue(), 1f);
                float drain = (float) ((0.0025f * speed.get()) - 0.0025f);
                energy = Mth.clamp(energy - drain, 0f, 1f);
            }
            case GRIM_CONCEPT -> {
                if (energy <= 0f || !isBoostHeld() || getSetbackAge() < 2000L) {
                    tickTimer = 1f;
                    return;
                }

                tickTimer = Math.max(speed.get().floatValue(), 1f);
                float drain = (float) ((0.0025f * speed.get()) - 0.0025f);
                float mult = grimConsumeMult.get().floatValue();
                if (mult < 0f) mult = 0f;
                energy = Mth.clamp(energy - drain * mult, 0f, 1f);
            }
            case SHIFT -> tickTimer = 1f;
            case VULCAN -> {
                // Correct model: circular buffer tracks exactly which of the last 50
                // packets were fast. Vulcan's EvictingQueue evicts the OLDEST entry —
                // after N fast packets the window is [S×(50-N), F×N], so slow packets
                // evict old SLOW first; fast packets only leave after (50-N) slow packets.
                //
                // Safe k_max = floor(2.5*S / (1.05*(S-1))):
                //   S=2.64 → kMax=3, avgDelay at k=2: 48.76ms, speed=1.025 < 1.05 ✓
                //   S=2.0  → kMax=4, avgDelay at k=3: 48.5ms,  speed=1.031 < 1.05 ✓
                //
                // Boost while fastCount < kMax. After boosting, fastCount rises
                // to kMax and boosting stops. Fast entries leave only after (50-kMax)
                // subsequent slow packets flush the initial slow buffer out.

                boolean held = isBoostHeld();
                boolean setbackRecent = getSetbackAge() < 500L;
                float s = speed.get().floatValue();

                // kMax: max fast packets allowed in 50-sample window at speed S
                int kMax = (s <= 1.001f) ? 0 : (int) (2.5f * s / (1.05f * (s - 1f)));
                if (kMax <= 0) kMax = 0;

                boolean canBoost = held && !setbackRecent && kMax > 0 && vulcanFastCount < kMax;

                if (canBoost) {
                    tickTimer = Math.max(s, 1f);
                    vulcanEvalWindow(true);
                } else {
                    tickTimer = 1f;
                    vulcanEvalWindow(false);
                }

                // Energy: 1.0 = window clean (fastCount=0), 0.0 = budget used (fastCount=kMax)
                energy = (kMax > 0)
                        ? Mth.clamp(1f - (float) vulcanFastCount / kMax, 0f, 1f)
                        : 0f;
            }
        }
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive e) {
        if (mode.get() == Mode.GRIM) {
            if (e.getPacket() instanceof ClientboundPingPacket && getSetbackAge() > 2000L) {
                if (System.currentTimeMillis() - cancelTime > 25000L) {
                    cancelTime = System.currentTimeMillis();
                    energy = 0f;
                    return;
                }

                if (!MovementUtil.isMoving()) {
                    energy = Mth.clamp(energy + 0.005f, 0f, 1f);
                }

                e.cancel();
            }
        }
        if (mode.get() == Mode.GRIM_CONCEPT) {
            if (e.getPacket() instanceof ClientboundPingPacket && getSetbackAge() > 2000L) {
                long now = System.currentTimeMillis();
                if (now - cancelTime > 25000L) {
                    cancelTime = now;
                    energy = 0f;
                    lastPingTime = now;
                    return;
                }

                if (lastPingTime <= 0L) {
                    lastPingTime = now;
                }

                if (!MovementUtil.isMoving()) {
                    double fillSeconds = grimFillTime.get();
                    if (fillSeconds < 1.0) fillSeconds = 1.0;
                    double delta = (now - lastPingTime) / (fillSeconds * 1000.0);
                    if (delta > 0.0) {
                        energy = Mth.clamp(energy + (float) delta, 0f, 1f);
                    }
                }

                lastPingTime = now;
                e.cancel();
            }
        }

        if (e.getPacket() instanceof ClientboundPlayerPositionPacket) {
            lastSetbackTime = System.currentTimeMillis();
            if (mode.get() == Mode.VULCAN) {
                // Server correction inserts a 150ms sample into Vulcan's window — helps.
                // Reset our circular buffer: assume server state is now clean.
                tickTimer = 1f;
                vulcanWindowHead = 0;
                vulcanWindowCount = 0;
                vulcanFastCount = 0;
                java.util.Arrays.fill(vulcanWindow, false);
                energy = 1f;
                if (onFlag.get() == OnFlag.DISABLE) {
                    setEnabled(false);
                }
            } else {
                switch (onFlag.get()) {
                    case RESET -> {
                        tickTimer = 1f;
                        energy = 0f;
                    }
                    case DISABLE -> {
                        energy = 0f;
                        setEnabled(false);
                    }
                    case NONE -> {
                    }
                }
            }
        }

        if (e.getPacket() instanceof ClientboundSetEntityMotionPacket velo
                && (mode.get() == Mode.GRIM || mode.get() == Mode.GRIM_CONCEPT)) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.player != null && velo.id() == mc.player.getId()) {
                tickTimer = 1f;
                energy = 0f;
                lastSetbackTime = System.currentTimeMillis();
            }
        }
    }

    @EventHandler
    public void onPostPlayerUpdate(PostPlayerUpdateEvent event) {
        if (mode.get() != Mode.SHIFT) return;

        if (energy < 0.9f) {
            setEnabled(false);
            return;
        }

        event.cancel();
        event.setIterations(shiftTicks.get());
        setEnabled(false);
    }

    @EventHandler
    public void onSync(EventSync e) {
        if (mode.get() == Mode.MATRIX) {
            float delta = notMoving() ? 0.025f : (old.get() ? -0.005f : 0f);
            energy = Mth.clamp(energy + delta, 0f, 1f);
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        prevPosX = mc.player.getX();
        prevPosY = mc.player.getY();
        prevPosZ = mc.player.getZ();
        yaw = mc.player.getYRot();
        pitch = mc.player.getXRot();
    }

    /**
     * Update the circular window buffer with one packet (fast or slow).
     * Evicts the OLDEST entry, exactly mirroring Vulcan's EvictingQueue.add().
     * fastCount stays accurate: only changes when a fast entry is evicted or added.
     */
    private void vulcanEvalWindow(boolean fast) {
        if (vulcanWindowCount == VULCAN_WINDOW_SIZE) {
            // evict oldest
            if (vulcanWindow[vulcanWindowHead]) vulcanFastCount--;
        } else {
            vulcanWindowCount++;
        }
        vulcanWindow[vulcanWindowHead] = fast;
        if (fast) vulcanFastCount++;
        vulcanWindowHead = (vulcanWindowHead + 1) % VULCAN_WINDOW_SIZE;
    }

    public Mode getMode() {
        return mode.get();
    }

    private long getSetbackAge() {
        if (lastSetbackTime <= 0L) return Long.MAX_VALUE;
        return System.currentTimeMillis() - lastSetbackTime;
    }

    private boolean notMoving() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return false;
        return prevPosX == mc.player.getX()
                && prevPosY == mc.player.getY()
                && prevPosZ == mc.player.getZ()
                && yaw == mc.player.getYRot()
                && pitch == mc.player.getXRot();
    }

    private String boostBindingName() {
        return name() + ":" + boostKey.getName();
    }

    private void ensureBoostBind() {
        String combo = boostKey.get();
        if (combo == null) combo = "NONE";
        if (combo.equalsIgnoreCase(lastBoostCombo)) return;

        String name = boostBindingName();
        KeyManager.unregisterAll(name);
        if (!boostKey.isNone()) {
            KeyManager.registerCombo(name, combo);
        }
        lastBoostCombo = combo;
    }

    private boolean isBoostHeld() {
        String combo = boostKey.get();
        if (combo == null || combo.equalsIgnoreCase("NONE")) return false;
        return KeyManager.isHeld(boostBindingName());
    }

    public enum Mode {
        NORMAL,
        MATRIX,
        SHIFT,
        GRIM,
        GRIM_CONCEPT,
        VULCAN
    }

    public enum OnFlag {
        DISABLE,
        NONE,
        RESET
    }
}
