/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.playermodel;

import combatant.client.render.ViewObstructionFadeState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Temporal visual state for a replacement player. The weak keys follow the lifetime of
 * actual client player entities, rather than a UUID that could survive a world change.
 * Only the render thread updates this map (during AvatarRenderer state extraction).
 */
public final class PlayerModelVisualEffects {
    // NoRender approaches the obstructed state promptly, but returning to full
    // opacity must not switch the model to an opaque pass in a single frame.
    private static final float FADE_IN_SECONDS = 0.18f;
    private static final float FADE_OUT_SECONDS = 0.26f;
    private static final float HIT_ATTACK_SECONDS = 0.085f;
    private static final float HIT_RELEASE_SECONDS = 0.19f;
    private static final Map<AbstractClientPlayer, Animation> ANIMATIONS = new WeakHashMap<>();

    public record Values(float opacity, float hit) {}

    private static final class Animation {
        long updatedAt;
        float opacity;
        float hit;

        Animation(long now, float initialOpacity) {
            updatedAt = now;
            opacity = initialOpacity;
        }
    }

    private PlayerModelVisualEffects() {}

    public static Values sample(AbstractClientPlayer player, ViewObstructionFadeState fade) {
        float target = fade != null && fade.combatant$isViewObstructionFadeActive()
                ? Mth.clamp(fade.combatant$getViewObstructionFadeAlpha(), 0.0f, 1.0f) : 1.0f;
        long now = System.nanoTime();
        Animation animation = ANIMATIONS.get(player);
        if (animation == null) {
            // A freshly tracked model should not appear partially invisible in its
            // first visible frame, even when it spawns inside the obstruction zone.
            animation = new Animation(now, 1.0f);
            ANIMATIONS.put(player, animation);
        }
        // Clamp both ends: repeated extraction in one frame must not advance the
        // effect, and a paused client must not advance it by several seconds.
        float delta = Mth.clamp((now - animation.updatedAt) * 1.0e-9f, 0.0f, 0.1f);
        animation.updatedAt = now;
        animation.opacity = approach(animation.opacity, target, delta,
                target < animation.opacity ? FADE_IN_SECONDS : FADE_OUT_SECONDS);
        if (Math.abs(animation.opacity - target) <= 0.001f) animation.opacity = target;

        // Do not use the discontinuous vanilla overlay (which also changes the
        // renderer's submission path). Attack/release instead follows wall time.
        float hitTarget = player.hurtTime > 0 ? 1.0f : 0.0f;
        animation.hit = approach(animation.hit, hitTarget, delta,
                hitTarget > animation.hit ? HIT_ATTACK_SECONDS : HIT_RELEASE_SECONDS);
        if (Math.abs(animation.hit - hitTarget) <= 0.005f) animation.hit = hitTarget;
        return new Values(animation.opacity, animation.hit);
    }

    static float approach(float current, float target, float dt, float seconds) {
        if (dt <= 0.0f || current == target) return current;
        return Mth.lerp(1.0f - (float) Math.exp(-dt / seconds), current, target);
    }

    public static void clear() { ANIMATIONS.clear(); }
}
