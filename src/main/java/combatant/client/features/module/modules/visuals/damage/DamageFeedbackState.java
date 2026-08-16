/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals.damage;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

/**
 * Runtime state for player damage feedback.
 *
 * <p>The network damage packet is treated as the reliable hit/source signal. Actual feedback magnitude is
 * derived from observed health and absorption loss, so packet delivery itself does not invent damage. A health
 * delta without a recent damage packet is only accepted as a fallback while vanilla hurt state is active; this
 * keeps ordinary health synchronization from being promoted to a hit event.</p>
 */
public final class DamageFeedbackState {
    private static final long DAMAGE_SIGNAL_WINDOW_NS = 350_000_000L;
    private static final long ATTACK_NS = 45_000_000L;
    private static final long HOLD_NS = 55_000_000L;
    private static final long RELEASE_NS = 430_000_000L;
    private static final long TOTAL_IMPACT_NS = ATTACK_NS + HOLD_NS + RELEASE_NS;
    private static final float EPSILON = 0.001f;

    private float lastHealth = Float.NaN;
    private float lastAbsorption = Float.NaN;
    private LocalPlayer trackedPlayer;

    private long pendingDamageUntilNs;
    private float pendingDirectionX;
    private float pendingDirectionY;
    private boolean pendingDirectional;

    private long impactStartedNs;
    private float impactMagnitude;
    private float impactDirectionX;
    private float impactDirectionY;
    private boolean impactDirectional;

    public void reset(LocalPlayer player) {
        pendingDamageUntilNs = 0L;
        pendingDirectionX = 0.0f;
        pendingDirectionY = 0.0f;
        pendingDirectional = false;
        impactStartedNs = 0L;
        impactMagnitude = 0.0f;
        impactDirectionX = 0.0f;
        impactDirectionY = 0.0f;
        impactDirectional = false;

        if (player == null) {
            trackedPlayer = null;
            lastHealth = Float.NaN;
            lastAbsorption = Float.NaN;
            return;
        }

        trackedPlayer = player;
        lastHealth = player.getHealth();
        lastAbsorption = player.getAbsorptionAmount();
    }

    public void onDamageSignal(ClientboundDamageEventPacket packet,
                               LocalPlayer player,
                               ClientLevel level,
                               Camera camera) {
        if (packet == null || player == null || level == null || packet.entityId() != player.getId()) return;
        ensurePlayer(player);

        long now = System.nanoTime();
        pendingDamageUntilNs = now + DAMAGE_SIGNAL_WINDOW_NS;

        Vec3 sourcePosition = resolveSourcePosition(packet, level);
        Direction direction = projectDirection(sourcePosition, camera);
        pendingDirectionX = direction.x();
        pendingDirectionY = direction.y();
        pendingDirectional = direction.directional();
    }

    /**
     * Samples effective-health deltas and starts an impact only when the loss is backed by a damage signal or
     * vanilla hurt state. Absorption is tracked independently and then folded into the nonlinear hit magnitude.
     */
    public void sample(LocalPlayer player) {
        if (player == null) {
            reset(null);
            return;
        }
        ensurePlayer(player);

        float health = player.getHealth();
        float absorption = player.getAbsorptionAmount();
        if (Float.isNaN(lastHealth) || Float.isNaN(lastAbsorption)) {
            lastHealth = health;
            lastAbsorption = absorption;
            return;
        }

        float healthLoss = Math.max(0.0f, lastHealth - health);
        float absorptionLoss = Math.max(0.0f, lastAbsorption - absorption);
        lastHealth = health;
        lastAbsorption = absorption;

        if (healthLoss <= EPSILON && absorptionLoss <= EPSILON) return;

        long now = System.nanoTime();
        boolean packetBacked = now <= pendingDamageUntilNs;
        boolean hurtFallback = player.hurtTime > 0;
        if (!packetBacked && !hurtFallback) {
            return;
        }

        float magnitude = healthLoss + absorptionLoss * 0.9f;
        if (magnitude <= EPSILON) return;

        float dirX = 0.0f;
        float dirY = 0.0f;
        boolean directional = false;
        if (packetBacked && pendingDirectional) {
            dirX = pendingDirectionX;
            dirY = pendingDirectionY;
            directional = true;
        }

        triggerImpact(magnitude, dirX, dirY, directional, now);
        pendingDamageUntilNs = 0L;
        pendingDirectional = false;
    }

    public ImpactSnapshot snapshot() {
        return snapshot(System.nanoTime());
    }

    private ImpactSnapshot snapshot(long now) {
        if (impactStartedNs == 0L || impactMagnitude <= EPSILON) return ImpactSnapshot.NONE;

        long age = now - impactStartedNs;
        if (age < 0L || age >= TOTAL_IMPACT_NS) {
            impactStartedNs = 0L;
            impactMagnitude = 0.0f;
            return ImpactSnapshot.NONE;
        }

        float envelope;
        if (age < ATTACK_NS) {
            envelope = smoothStep(age / (float) ATTACK_NS);
        } else if (age < ATTACK_NS + HOLD_NS) {
            envelope = 1.0f;
        } else {
            float releaseT = (age - ATTACK_NS - HOLD_NS) / (float) RELEASE_NS;
            envelope = (float) Math.pow(1.0f - smoothStep(releaseT), 1.25);
        }

        float ageSeconds = age / 1_000_000_000.0f;
        float edgeSequence = 1.0f - smoothStepRange(0.075f, 0.20f, ageSeconds);
        float chromaSequence = smoothStepRange(0.025f, 0.075f, ageSeconds)
                * (1.0f - smoothStepRange(0.16f, 0.285f, ageSeconds));
        float redSequence = smoothStepRange(0.055f, 0.12f, ageSeconds);

        float strength = clamp01(impactMagnitude * envelope);
        float edgeFlash = clamp01(strength * edgeSequence);
        float chromatic = clamp01(impactMagnitude * chromaSequence);
        float redPressure = clamp01(strength * redSequence);
        float distortion = clamp01(chromatic * 0.55f);

        return new ImpactSnapshot(
                strength,
                edgeFlash,
                chromatic,
                redPressure,
                distortion,
                impactDirectionX,
                impactDirectionY,
                impactDirectional
        );
    }

    private void triggerImpact(float rawMagnitude,
                               float directionX,
                               float directionY,
                               boolean directional,
                               long now) {
        float normalized = normalizeMagnitude(rawMagnitude);
        if (normalized <= EPSILON) return;

        float existing = snapshot(now).strength();
        impactMagnitude = clamp01(Math.max(normalized, existing * 0.65f + normalized * 0.55f));
        impactStartedNs = now;
        impactDirectionX = directionX;
        impactDirectionY = directionY;
        impactDirectional = directional;
    }

    private void ensurePlayer(LocalPlayer player) {
        if (trackedPlayer == player && !Float.isNaN(lastHealth) && !Float.isNaN(lastAbsorption)) return;
        trackedPlayer = player;
        lastHealth = player.getHealth();
        lastAbsorption = player.getAbsorptionAmount();
        pendingDamageUntilNs = 0L;
        pendingDirectional = false;
        impactStartedNs = 0L;
        impactMagnitude = 0.0f;
    }

    private static float normalizeMagnitude(float damage) {
        float d = Math.max(0.0f, damage);
        // Saturating exponential: small hits stay visible, heavy hits grow strongly, huge hits never hard-clip.
        return clamp01((float) (1.0 - Math.exp(-0.33 * d)));
    }

    private static Vec3 resolveSourcePosition(ClientboundDamageEventPacket packet, ClientLevel level) {
        Vec3 explicit = packet.sourcePosition().orElse(null);
        if (explicit != null) return explicit;

        try {
            DamageSource source = packet.getSource(level);
            if (source == null) return null;
            Vec3 sourcePosition = source.getSourcePosition();
            if (sourcePosition != null) return sourcePosition;
            if (source.getEntity() != null) return source.getEntity().position();
            if (source.getDirectEntity() != null) return source.getDirectEntity().position();
        } catch (RuntimeException ignored) {
            // Broken/late entity references are allowed to fall back to the symmetric response.
        }
        return null;
    }

    private static Direction projectDirection(Vec3 sourcePosition, Camera camera) {
        if (sourcePosition == null || camera == null || !camera.isInitialized()) return Direction.NONE;

        Vec3 delta = sourcePosition.subtract(camera.position());
        if (delta.lengthSqr() < 1.0e-6) return Direction.NONE;
        delta = delta.normalize();

        Vector3fc left = camera.leftVector();
        Vector3fc up = camera.upVector();
        float screenX = (float) -(delta.x * left.x() + delta.y * left.y() + delta.z * left.z());
        float screenY = (float) (delta.x * up.x() + delta.y * up.y() + delta.z * up.z());
        float length = (float) Math.sqrt(screenX * screenX + screenY * screenY);
        if (length < 0.08f) return Direction.NONE;

        return new Direction(screenX / length, screenY / length, true);
    }

    private static float smoothStep(float t) {
        float c = clamp01(t);
        return c * c * (3.0f - 2.0f * c);
    }

    private static float smoothStepRange(float edge0, float edge1, float x) {
        if (edge1 <= edge0) return x >= edge1 ? 1.0f : 0.0f;
        return smoothStep((x - edge0) / (edge1 - edge0));
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private record Direction(float x, float y, boolean directional) {
        private static final Direction NONE = new Direction(0.0f, 0.0f, false);
    }

    public record ImpactSnapshot(float strength,
                                 float edgeFlash,
                                 float chromatic,
                                 float redPressure,
                                 float distortion,
                                 float directionX,
                                 float directionY,
                                 boolean directional) {
        public static final ImpactSnapshot NONE =
                new ImpactSnapshot(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false);
    }
}
