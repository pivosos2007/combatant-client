/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.effects.particle;

/**
 * Pure layout data. It intentionally has no module, texture, pipeline or renderer ownership.
 * A future compute backend can evaluate the same descriptors without changing consumers.
 */
public sealed interface ParticleLayoutDescriptor permits
        ParticleLayoutDescriptor.Helix,
        ParticleLayoutDescriptor.Orbit,
        ParticleLayoutDescriptor.Runes,
        ParticleLayoutDescriptor.Pulse,
        ParticleLayoutDescriptor.LightningPath {

    float speed();

    record Helix(
            float radius,
            float height,
            float turns,
            int strands,
            float speed,
            float verticalWave
    ) implements ParticleLayoutDescriptor {
        public Helix {
            radius = Math.max(0.0f, radius);
            height = Math.max(0.0f, height);
            turns = Math.max(0.05f, turns);
            strands = Math.max(1, strands);
            verticalWave = Math.max(0.0f, verticalWave);
        }
    }

    record Orbit(
            float radius,
            float verticalRadius,
            int planes,
            float tiltRadians,
            float speed
    ) implements ParticleLayoutDescriptor {
        public Orbit {
            radius = Math.max(0.0f, radius);
            verticalRadius = Math.max(0.0f, verticalRadius);
            planes = Math.max(1, planes);
        }
    }

    record Runes(
            float radius,
            float height,
            int runeCount,
            float runeSize,
            float speed
    ) implements ParticleLayoutDescriptor {
        public Runes {
            radius = Math.max(0.0f, radius);
            height = Math.max(0.0f, height);
            runeCount = Math.max(1, runeCount);
            runeSize = Math.max(0.0f, runeSize);
        }
    }

    record Pulse(
            float baseRadius,
            float radiusAmplitude,
            float height,
            int rings,
            float speed
    ) implements ParticleLayoutDescriptor {
        public Pulse {
            baseRadius = Math.max(0.0f, baseRadius);
            radiusAmplitude = Math.max(0.0f, radiusAmplitude);
            height = Math.max(0.0f, height);
            rings = Math.max(1, rings);
        }
    }

    record LightningPath(
            float radius,
            float height,
            int branches,
            float jitter,
            float twist,
            float speed
    ) implements ParticleLayoutDescriptor {
        public LightningPath {
            radius = Math.max(0.0f, radius);
            height = Math.max(0.0f, height);
            branches = Math.max(1, branches);
            jitter = Math.max(0.0f, jitter);
        }
    }
}
