/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.playermodel;

import com.google.gson.Gson;
import combatant.client.features.playeranimator.PlayerRigBone;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import combatant.client.render.engine.asset.model.io.ModelResourceResolver;

/** Optional source-space fitting data; original OBJ geometry is never rewritten. */
public record PlayerModelProfile(Map<String, String> bones, List<Blend> blends) {
    private static final Map<String, String> DEFAULTS = Map.of(
            "headModel", "HEAD", "bodyModel", "CHEST",
            "leftArmModel", "LEFT_UPPER_ARM", "rightArmModel", "RIGHT_UPPER_ARM",
            "leftLegModel", "LEFT_THIGH", "rightLegModel", "RIGHT_THIGH");

    public PlayerModelProfile {
        bones = bones == null ? Map.of() : Map.copyOf(bones);
        blends = blends == null ? List.of() : List.copyOf(blends);
        if (bones.size() > 256 || blends.size() > 64) throw new IllegalArgumentException("Fitting profile is too large");
        bones.values().forEach(PlayerRigBone::valueOf);
        blends.forEach(Blend::validate);
    }

    public static PlayerModelProfile load(ModelResourceResolver source, String path) throws IOException {
        if (path == null || path.isBlank()) return new PlayerModelProfile(Map.of(), List.of());
        try {
            PlayerModelProfile profile = new Gson().fromJson(
                    new String(ModelCatalog.read(source, path), StandardCharsets.UTF_8), PlayerModelProfile.class);
            if (profile == null) throw new IOException("Empty fitting profile");
            return profile;
        } catch (RuntimeException failure) {
            throw new IOException("Invalid fitting profile: " + path, failure);
        }
    }

    public PlayerRigBone bone(String group) {
        String name = bones.getOrDefault(group, DEFAULTS.get(group));
        if (name == null) throw new IllegalArgumentException("Unmapped model group: " + group);
        return PlayerRigBone.valueOf(name);
    }

    public record Blend(String group, String fromBone, String toBone,
                        float[] min, float[] max, float[] from, float[] to) {
        private void validate() {
            if (group == null || group.isBlank()) throw new IllegalArgumentException("Blend group is required");
            PlayerRigBone.valueOf(fromBone);
            PlayerRigBone.valueOf(toBone);
            for (float[] vector : List.of(min, max, from, to)) {
                if (vector.length != 3) throw new IllegalArgumentException("Expected three profile coordinates");
                for (float value : vector) if (!Float.isFinite(value)) throw new IllegalArgumentException("Nonfinite profile coordinate");
            }
            for (int i = 0; i < 3; i++) if (min[i] > max[i]) throw new IllegalArgumentException("Inverted blend bounds");
            if (new Vector3f(from).distanceSquared(new Vector3f(to)) < 1e-6f) {
                throw new IllegalArgumentException("Blend endpoints must differ");
            }
        }

        public boolean contains(String name, Vector3f point) {
            return group.equals(name) && point.x >= min[0] && point.x <= max[0]
                    && point.y >= min[1] && point.y <= max[1] && point.z >= min[2] && point.z <= max[2];
        }

        public float weight(Vector3f point) {
            Vector3f direction = new Vector3f(to).sub(new Vector3f(from));
            float t = Math.clamp(new Vector3f(point).sub(new Vector3f(from)).dot(direction)
                    / direction.lengthSquared(), 0f, 1f);
            return t * t * (3f - 2f * t);
        }
    }
}
