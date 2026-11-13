/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

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
import combatant.client.features.module.ModuleListInfoProvider;
import combatant.client.features.gui.hud.gauges.GaugeRegistry;
import combatant.client.features.gui.hud.gauges.GaugeConnect;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.input.CombatantInput;
import combatant.client.input.InputScope;
import combatant.client.util.player.MovementUtil;

@ModuleInfo(
        id = "timer",
        displayName = "Timer",
        category = ModuleCategory.MOVEMENT, subcategory = ModuleSubcategory.RAGE,
        description = "module.timer.description")
public class Timer extends Module {

    // Tracks fast/normal ticks in insertion order. Overwriting the oldest entry
    // must adjust the fast count before inserting the new sample; merely counting
    // recent normal ticks would not recover the same window occupancy.
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
                    () -> mode.get() == Mode.GRIM || mode.get() == Mode.VULCAN);
    private final EnumValue<OnFlag> onFlag =
            enumMode("on_flag", OnFlag.RESET, OnFlag.values());
    private long cancelTime;
    private float lastGaugeFill = Float.NaN;
    private long lastGaugeChangeNanos;
    private Mode lastGaugeMode;
    private long lastSetbackTime = 0L;
    private String lastBoostCombo = "NONE";

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
        lastGaugeFill = Float.NaN;
        lastGaugeMode = null;
        lastGaugeChangeNanos = 0L;
        tickTimer = 1f;
        if (mode.get() == Mode.VULCAN) {
            vulcanWindowHead = 0;
            vulcanWindowCount = 0;
            vulcanFastCount = 0;
            java.util.Arrays.fill(vulcanWindow, false);
            energy = 1f;
        } else if (mode.get() != Mode.MATRIX) {
            energy = 0f;
        }
        if (mode.get() == Mode.GRIM) {
            cancelTime = System.currentTimeMillis();
        }
        ensureBoostBind();
    }

    @Override
    public void onDisable() {
        GaugeRegistry.removeOwner("timer");
        lastGaugeFill = Float.NaN;
        lastGaugeMode = null;
        lastGaugeChangeNanos = 0L;
        tickTimer = 1f;
        vulcanWindowHead = 0;
        vulcanWindowCount = 0;
        vulcanFastCount = 0;
        java.util.Arrays.fill(vulcanWindow, false);
    }

    @Override
    public void onTick() {
        tickModes();
        GaugeRegistry.updateAnnotated("timer", this);
    }

    @GaugeConnect(id = "energy", labelKey = "hud.gauges.timer",
            readiness = GaugeRegistry.Readiness.THRESHOLD, hideAfterMillis = 1100)
    private GaugeRegistry.Sample energyGauge() {
        Mode current = mode.get();
        if (current == Mode.NORMAL || current == Mode.SHIFT) return null;
        float fill = getEnergy01();
        float threshold = timerConsumptionThreshold(current);
        boolean canUse = switch (current) {
            case VULCAN -> vulcanMaxFastEntries() > 0 && vulcanFastCount < vulcanMaxFastEntries();
            case MATRIX, GRIM -> fill > 0f && fill >= threshold;
            default -> false;
        };
        if (current == Mode.GRIM && getSetbackAge() < 2000L) canUse = false;
        boolean draining = tickTimer > 1.001f;
        long now = System.nanoTime();
        if (lastGaugeMode != current || !Float.isFinite(lastGaugeFill)
                || Math.abs(fill - lastGaugeFill) > .0001f || draining) {
            lastGaugeChangeNanos = now;
            lastGaugeFill = fill;
            lastGaugeMode = current;
        }
        boolean recentlyChanged = now - lastGaugeChangeNanos < 900_000_000L;
        boolean active = switch (current) {
            case VULCAN -> vulcanFastCount > 0 || draining;
            case MATRIX, GRIM -> draining || (fill > .001f && fill < .995f && recentlyChanged);
            default -> false;
        };
        return new GaugeRegistry.Sample(fill, canUse, active,
                draining ? GaugeRegistry.Flow.DRAIN : GaugeRegistry.Flow.FILL, "", threshold);
    }

    private float timerEnergyCost(Mode current) {
        float requestedSpeed = speed.get().floatValue();
        return switch (current) {
            case MATRIX -> Math.max(0f, 0.1f * requestedSpeed - 0.1f);
            case GRIM -> Math.max(0f, 0.0025f * requestedSpeed - 0.0025f);
            default -> 0f;
        };
    }

    private int vulcanMaxFastEntries() {
        float s = speed.get().floatValue();
        return s <= 1.001f ? 0 : Math.max(0, (int) (2.5f * s / (1.05f * (s - 1f))));
    }

    private float timerConsumptionThreshold(Mode current) {
        if (current != Mode.VULCAN) return timerEnergyCost(current);
        int limit = vulcanMaxFastEntries();
        return limit > 0 ? 1f / limit : 1f;
    }

    private void tickModes() {
        ensureBoostBind();

        switch (mode.get()) {
            case NORMAL -> tickTimer = speed.get().floatValue();
            case MATRIX -> {
                if (!MovementUtil.isMoving()) {
                    tickTimer = 1f;
                    return;
                }

                tickTimer = Math.max(speed.get().floatValue(), 1f);

                float drain = timerEnergyCost(Mode.MATRIX);
                if (energy > 0f && energy >= drain) {
                    energy = Mth.clamp(energy - drain, 0f, 1f);
                } else {
                    setEnabled(false);
                }
            }
            case GRIM -> {
                float drain = timerEnergyCost(Mode.GRIM);
                if (energy <= 0f || energy < drain || !isBoostHeld() || getSetbackAge() < 2000L) {
                    tickTimer = 1f;
                    return;
                }

                tickTimer = Math.max(speed.get().floatValue(), 1f);
                energy = Mth.clamp(energy - drain, 0f, 1f);
            }
            case SHIFT -> tickTimer = 1f;
            case VULCAN -> {
                // The boost limit depends on the configured speed and the count of
                // fast entries still present in the rolling window.

                boolean held = isBoostHeld();
                boolean setbackRecent = getSetbackAge() < 500L;
                float s = speed.get().floatValue();
                int kMax = vulcanMaxFastEntries();

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
        if (e.getPacket() instanceof ClientboundPlayerPositionPacket) {
            lastSetbackTime = System.currentTimeMillis();
            if (mode.get() == Mode.VULCAN) {
                // A server correction invalidates the locally tracked sample window.
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
                && (mode.get() == Mode.GRIM)) {
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

        // the shifted ticks are tick packets counted against one 50 ms of wall clock
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
     * Advances the fixed-size sample window and updates the number of fast entries.
     * Once full, the overwritten entry is removed from the count before insertion.
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

    @ModuleListInfoProvider
    private String moduleListDetail() {
        return switch (mode.get()) {
            case NORMAL -> "Normal";
            case MATRIX -> "Matrix";
            case SHIFT -> "Shift";
            case GRIM -> "Grim";
            case VULCAN -> "Vulcan";
        };
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
        CombatantInput.bindings().unbind(name);
        if (!boostKey.isNone()) {
            CombatantInput.bindings().bind(name, combo);
        }
        lastBoostCombo = combo;
    }

    private boolean isBoostHeld() {
        String combo = boostKey.get();
        if (combo == null || combo.equalsIgnoreCase("NONE")) return false;
        return CombatantInput.bindings().isHeld(boostBindingName(), InputScope.GAME);
    }

    public enum Mode {
        NORMAL,
        MATRIX,
        SHIFT,
        GRIM,
        VULCAN
    }

    public enum OnFlag {
        DISABLE,
        NONE,
        RESET
    }
}
