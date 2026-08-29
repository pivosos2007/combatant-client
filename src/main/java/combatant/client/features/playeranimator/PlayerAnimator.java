/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.playeranimator;

import combatant.client.features.playeranimator.render.PlayerRigCpuRenderer;
import combatant.client.features.playeranimator.script.PlayerRigScriptCommand;
import combatant.client.features.playeranimator.script.PlayerRigScriptContext;
import combatant.client.features.playeranimator.script.PlayerRigScriptRuntime;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;

import java.util.Map;
import java.util.WeakHashMap;

/** Central owner of per-player anatomical rig state and the shared JavaScript runtime. */
public enum PlayerAnimator {
    ;

    private static final Map<AbstractClientPlayer, InstancePool> INSTANCES = new WeakHashMap<>();
    private static final Map<AbstractClientPlayer, MotionState> MOTION = new WeakHashMap<>();
    private static final PlayerRigScriptRuntime SCRIPTS = new PlayerRigScriptRuntime();

    public static synchronized PlayerRigInstance instance(AbstractClientPlayer player) {
        if (player == null) throw new IllegalArgumentException("Animated player must not be null");
        return INSTANCES.computeIfAbsent(player, ignored -> new InstancePool()).current();
    }

    /**
     * Starts from bind pose, applies the vanilla render-state head orientation, evaluates one
     * interpolated JS animation graph and solves the hierarchy once. AvatarRenderState is required:
     * it already contains Minecraft's interpolated walk/swim/body/head values for this render frame.
     */
    public static synchronized PlayerRigInstance animate(
            AbstractClientPlayer player,
            AvatarRenderState state,
            float tickDelta,
            float deltaSeconds,
            String style,
            float strength
    ) {
        if (player == null || state == null) {
            throw new IllegalArgumentException("Animated player and AvatarRenderState must not be null");
        }

        float frameSeconds = Float.isFinite(deltaSeconds) ? Mth.clamp(deltaSeconds, 0f, 0.1f) : 0f;
        // AvatarRenderState.attackTime is already interpolated for this render submission.
        float attack = state.attackTime;
        MotionState motion = MOTION.computeIfAbsent(player, ignored -> new MotionState());
        float continuousSeconds = state.ageInTicks / 20.0f;
        float attackCooldownSeconds = Mth.clamp(player.getCurrentItemAttackStrengthDelay() / 20.0f, 0.28f, 1.35f);
        motion.updateAttack(attack, continuousSeconds, attackCooldownSeconds);

        PlayerRigInstance instance = INSTANCES.computeIfAbsent(player, ignored -> new InstancePool())
                .acquire().resetFrame();
        applyVanillaLook(instance, state);

        Object[] context = PlayerRigScriptContext.pack(
                player,
                state,
                tickDelta,
                frameSeconds,
                style,
                strength,
                motion.swingIndex,
                attack,
                motion.attackTimeSeconds,
                motion.attackDurationSeconds,
                motion.attackActive
        );
        for (PlayerRigScriptCommand command : SCRIPTS.execute(context)) {
            command.apply(instance);
        }
        instance.solve();
        return instance;
    }

    /**
     * Mirrors HumanoidModel's interpolated head target, distributed over the anatomical neck chain.
     * All three joints share the vanilla head pivot, so this adds no forward neck translation/orbit.
     */
    private static void applyVanillaLook(PlayerRigInstance instance, AvatarRenderState state) {
        float xRot = state.xRot * Mth.DEG_TO_RAD;
        if (state.isFallFlying) {
            xRot = -((float) Math.PI * 0.25f);
        } else if (state.swimAmount > 0f) {
            xRot = Mth.rotLerpRad(state.swimAmount, xRot, -((float) Math.PI * 0.25f));
        }
        float yRot = state.yRot * Mth.DEG_TO_RAD;

        instance.setRotation(PlayerRigDefinition.index(PlayerRigBone.NECK_LOWER), xRot * 0.12f, yRot * 0.16f, 0f);
        instance.setRotation(PlayerRigDefinition.index(PlayerRigBone.NECK_UPPER), xRot * 0.18f, yRot * 0.20f, 0f);
        instance.setRotation(PlayerRigDefinition.index(PlayerRigBone.HEAD),       xRot * 0.70f, yRot * 0.64f, 0f);
    }

    public static synchronized void remove(AbstractClientPlayer player) {
        if (player != null) {
            INSTANCES.remove(player);
            MOTION.remove(player);
        }
    }

    public static synchronized void clearInstances() {
        INSTANCES.clear();
        MOTION.clear();
    }

    public static synchronized void invalidateScripts() {
        SCRIPTS.invalidate();
    }

    public static synchronized void close() {
        INSTANCES.clear();
        MOTION.clear();
        SCRIPTS.close();
        PlayerRigCpuRenderer.clearCaches();
    }

    /**
     * A swing owns a continuous render-time clock instead of being sampled only from vanilla's
     * short attackProgress window. The clock survives the raw swing returning to zero just long
     * enough for a smooth procedural recovery, without stretching combat clips over 1.5 seconds.
     */
    private static final class MotionState {
        private static final float DEFAULT_ATTACK_WINDOW_SECONDS = 0.62f;
        private float previousAttack;
        private float attackTimeSeconds = DEFAULT_ATTACK_WINDOW_SECONDS;
        private float attackDurationSeconds = DEFAULT_ATTACK_WINDOW_SECONDS;
        private int swingIndex;
        private boolean attackActive;

        private float attackStartSeconds = Float.NaN;

        private void updateAttack(float rawAttack, float continuousSeconds, float requestedDurationSeconds) {
            boolean rising = rawAttack > 0.001f && previousAttack <= 0.001f;
            if (rising || (!Float.isFinite(attackStartSeconds) && rawAttack > 0.001f)) {
                swingIndex++;
                attackStartSeconds = continuousSeconds;
                attackTimeSeconds = 0f;
                attackDurationSeconds = Float.isFinite(requestedDurationSeconds)
                        ? Mth.clamp(requestedDurationSeconds, 0.28f, 1.35f)
                        : DEFAULT_ATTACK_WINDOW_SECONDS;
                attackActive = true;
            } else if (attackActive) {
                float elapsed = continuousSeconds - attackStartSeconds;
                if (!Float.isFinite(elapsed) || elapsed < 0f) {
                    attackStartSeconds = continuousSeconds;
                    elapsed = 0f;
                }
                attackTimeSeconds = Mth.clamp(elapsed, 0f, attackDurationSeconds);
                if (elapsed >= attackDurationSeconds) {
                    attackActive = false;
                }
            }
            previousAttack = rawAttack;
        }
    }

    /** Separate mutable poses for deferred world, outline and preview submissions in one frame. */
    private static final class InstancePool {
        private static final int CAPACITY = 8;
        private final PlayerRigInstance[] instances = new PlayerRigInstance[CAPACITY];
        private int cursor;
        private PlayerRigInstance current;

        private PlayerRigInstance acquire() {
            int index = cursor++ & (CAPACITY - 1);
            PlayerRigInstance instance = instances[index];
            if (instance == null) instances[index] = instance = new PlayerRigInstance();
            current = instance;
            return instance;
        }

        private PlayerRigInstance current() {
            return current != null ? current : acquire();
        }
    }
}
