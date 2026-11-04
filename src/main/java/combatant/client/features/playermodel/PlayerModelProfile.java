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
public record PlayerModelProfile(Map<String, String> bones, List<Blend> blends,
                                 List<Link> links, List<Attachment> attachments) {
    private static final Map<String, String> DEFAULTS = Map.of(
            "headModel", "HEAD", "bodyModel", "CHEST",
            "leftArmModel", "LEFT_UPPER_ARM", "rightArmModel", "RIGHT_UPPER_ARM",
            "leftLegModel", "LEFT_THIGH", "rightLegModel", "RIGHT_THIGH");

    // Older resource addons and callers use the original two-field fitting format.
    public PlayerModelProfile(Map<String, String> bones, List<Blend> blends) {
        this(bones, blends, List.of(), List.of());
    }

    public PlayerModelProfile(Map<String, String> bones, List<Blend> blends, List<Link> links) {
        this(bones, blends, links, List.of());
    }

    public PlayerModelProfile {
        bones = bones == null ? Map.of() : Map.copyOf(bones);
        blends = blends == null ? List.of() : List.copyOf(blends);
        links = links == null ? List.of() : List.copyOf(links);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        if (bones.size() > 256 || blends.size() > 64 || links.size() > 64 || attachments.size() > 128) throw new IllegalArgumentException("Fitting profile is too large");
        bones.values().forEach(PlayerRigBone::valueOf);
        blends.forEach(Blend::validate);
        links.forEach(Link::validate);
        attachments.forEach(Attachment::validate);
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

    /**
     * Binds a disconnected OBJ island (e.g. a breathing hose) between two animated bones.
     * Island bounds select the part, not a broad vertex AABB that would deform the helmet or
     * backpack behind it. Both endpoints are in source OBJ model pixels (+Y down).
     */
    public record Link(String group, String fromBone, String toBone,
                       float[] componentMin, float[] componentMax, float[] from, float[] to) {
        private void validate() {
            if (group == null || group.isBlank()) throw new IllegalArgumentException("Link group is required");
            PlayerRigBone.valueOf(fromBone);
            PlayerRigBone.valueOf(toBone);
            for (float[] v : java.util.Arrays.asList(componentMin, componentMax, from, to)) {
                if (v == null || v.length != 3) throw new IllegalArgumentException("Link coordinates must have 3 values");
                for (float n : v) if (!Float.isFinite(n)) throw new IllegalArgumentException("Nonfinite link coordinate");
            }
            validateIsland(componentMin, componentMax);
            if (new Vector3f(from).distanceSquared(new Vector3f(to)) < 1e-6f)
                throw new IllegalArgumentException("Link endpoints must differ");
        }

        public boolean matches(String sourceGroup, float[] islandMin, float[] islandMax) {
            return matchIsland(group, componentMin, componentMax, sourceGroup, islandMin, islandMax);
        }

        public float weight(Vector3f p) {
            Vector3f axis = new Vector3f(to).sub(new Vector3f(from));
            float t = Math.clamp(new Vector3f(p).sub(new Vector3f(from)).dot(axis)
                    / axis.lengthSquared(), 0f, 1f);
            return t * t * (3f - 2f * t);
        }
    }

    /** Select one rigid OBJ island and anchor it to a single skeletal bone.
     *  Rigid backpack, tank and armour plates must not be rubber-skin blended with torso cloth. */
    public record Attachment(String group, String bone, float[] componentMin, float[] componentMax) {
        private void validate() {
            if (group == null || group.isBlank()) throw new IllegalArgumentException("Attachment group is required");
            PlayerRigBone.valueOf(bone);
            validateIsland(componentMin, componentMax);
        }
        public boolean matches(String sourceGroup, float[] islandMin, float[] islandMax) {
            return matchIsland(group, componentMin, componentMax, sourceGroup, islandMin, islandMax);
        }
    }

    private static void validateIsland(float[] min, float[] max) {
        for (float[] bounds : java.util.Arrays.asList(min, max)) {
            if (bounds == null || bounds.length != 3) throw new IllegalArgumentException("Island bounds need 3 coordinates");
            for (float n : bounds) if (!Float.isFinite(n)) throw new IllegalArgumentException("Nonfinite island bound");
        }
        for (int i = 0; i < 3; i++)
            if (min[i] > max[i]) throw new IllegalArgumentException("Inverted island bounds");
    }

    private static boolean matchIsland(String requiredGroup, float[] requiredMin, float[] requiredMax,
                                       String sourceGroup, float[] actualMin, float[] actualMax) {
        if (!requiredGroup.equals(sourceGroup)) return false;
        // The importer retains OBJ-space coordinates, but float parsing may differ in the last bit.
        for (int i = 0; i < 3; i++) {
            if (Math.abs(requiredMin[i] - actualMin[i]) > .025f
                    || Math.abs(requiredMax[i] - actualMax[i]) > .025f) return false;
        }
        return true;
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
