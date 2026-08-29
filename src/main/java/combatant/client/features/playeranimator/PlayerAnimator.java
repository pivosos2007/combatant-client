/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.playeranimator;

import combatant.client.features.playeranimator.script.PlayerRigScriptCommand;
import combatant.client.features.playeranimator.script.PlayerRigScriptContext;
import combatant.client.features.playeranimator.script.PlayerRigScriptRuntime;
import combatant.client.features.playeranimator.render.PlayerRigCpuRenderer;
import net.minecraft.client.player.AbstractClientPlayer;

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
     * Starts from the bind pose, applies every registered JS layer in one V8 call and solves the
     * complete hierarchy once. The returned instance is ready for body, armor and attachment draws.
     */
    public static synchronized PlayerRigInstance animate(
            AbstractClientPlayer player,
            float tickDelta,
            float deltaSeconds,
            String style,
            float strength
    ) {
        float age = player.tickCount + tickDelta;
        float rawAttack = player.getAttackAnim(tickDelta);
        MotionState motion = MOTION.computeIfAbsent(player, ignored -> new MotionState());
        float attack = motion.updateAttack(rawAttack, age);
        PlayerRigInstance instance = INSTANCES.computeIfAbsent(player, ignored -> new InstancePool())
                .acquire().resetFrame();
        Object[] context = PlayerRigScriptContext.pack(
                player, tickDelta, deltaSeconds, style, strength, motion.swingIndex, attack
        );
        for (PlayerRigScriptCommand command : SCRIPTS.execute(context)) {
            command.apply(instance);
        }
        instance.solve();
        return instance;
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

    private static final class MotionState {
        private float previousAttack;
        private int swingIndex;
        private float attackStartedAt = Float.NEGATIVE_INFINITY;

        private float updateAttack(float rawAttack, float age) {
            boolean started = rawAttack > 0.001f && previousAttack <= 0.001f;
            boolean restarted = rawAttack > 0.001f && rawAttack + 0.35f < previousAttack;
            if (started || restarted) {
                swingIndex++;
                attackStartedAt = age;
            }
            previousAttack = rawAttack;
            // Some combat modules/servers leave attackAnim latched. Never let one sampled swing
            // hold an anatomical arm pose indefinitely.
            return age - attackStartedAt <= 12f ? rawAttack : 0f;
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
