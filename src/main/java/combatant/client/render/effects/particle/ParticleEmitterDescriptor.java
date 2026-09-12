/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.effects.particle;

import net.minecraft.world.phys.Vec3;

/** Backend-neutral spawn-source description shared by current and future particle backends. */
public sealed interface ParticleEmitterDescriptor permits
        ParticleEmitterDescriptor.AabbSurface,
        ParticleEmitterDescriptor.SphereVolume,
        ParticleEmitterDescriptor.MeshSurface,
        ParticleEmitterDescriptor.RigSurface,
        ParticleEmitterDescriptor.BoneSurface,
        ParticleEmitterDescriptor.Socket {

    int count();
    long seed();

    record AabbSurface(
            Vec3 min,
            Vec3 max,
            int count,
            long seed
    ) implements ParticleEmitterDescriptor {
        public AabbSurface {
            min = min == null ? Vec3.ZERO : min;
            max = max == null ? min : max;
            count = Math.max(1, count);
        }
    }

    record SphereVolume(
            Vec3 center,
            float radius,
            boolean surfaceOnly,
            int count,
            long seed
    ) implements ParticleEmitterDescriptor {
        public SphereVolume {
            center = center == null ? Vec3.ZERO : center;
            radius = Math.max(0.0f, radius);
            count = Math.max(1, count);
        }
    }

    record MeshSurface(
            String sourceId,
            int count,
            long seed
    ) implements ParticleEmitterDescriptor {
        public MeshSurface {
            if (sourceId == null || sourceId.isBlank()) {
                throw new IllegalArgumentException("Mesh surface emitter source id must not be blank");
            }
            count = Math.max(1, count);
        }
    }

    record RigSurface(
            int sourceEntityId,
            String sourceId,
            int count,
            long seed
    ) implements ParticleEmitterDescriptor {
        public RigSurface {
            if (sourceId == null || sourceId.isBlank()) {
                throw new IllegalArgumentException("Rig surface emitter source id must not be blank");
            }
            count = Math.max(1, count);
        }
    }

    record BoneSurface(
            int sourceEntityId,
            int boneIndex,
            float radius,
            int count,
            long seed
    ) implements ParticleEmitterDescriptor {
        public BoneSurface {
            if (boneIndex < 0) throw new IllegalArgumentException("Bone emitter index must be >= 0");
            radius = Math.max(0.0f, radius);
            count = Math.max(1, count);
        }
    }

    record Socket(
            int sourceEntityId,
            String socketName,
            float radius,
            int count,
            long seed
    ) implements ParticleEmitterDescriptor {
        public Socket {
            if (socketName == null || socketName.isBlank()) {
                throw new IllegalArgumentException("Socket emitter name must not be blank");
            }
            radius = Math.max(0.0f, radius);
            count = Math.max(1, count);
        }
    }
}
