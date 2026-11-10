/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.material;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Immutable map from canonical material semantics to backend-neutral texture sources. */
public final class MaterialTextureSet {
    private final Map<MaterialTextureSemantic, MaterialTextureSource> textures;
    private final int presenceMask;

    /** Compatibility constructor for ordinary Minecraft/resource-pack textures. */
    public MaterialTextureSet(Map<MaterialTextureSemantic, Identifier> textures) {
        EnumMap<MaterialTextureSemantic, MaterialTextureSource> copy = new EnumMap<>(MaterialTextureSemantic.class);
        if (textures != null) {
            for (Map.Entry<MaterialTextureSemantic, Identifier> entry : textures.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    copy.put(entry.getKey(), new MaterialTextureSource.Resource(entry.getValue()));
                }
            }
        }
        this.textures = Collections.unmodifiableMap(copy);
        this.presenceMask = presenceMask(copy);
    }

    private MaterialTextureSet(Map<MaterialTextureSemantic, MaterialTextureSource> textures, boolean trustedSources) {
        EnumMap<MaterialTextureSemantic, MaterialTextureSource> copy = new EnumMap<>(MaterialTextureSemantic.class);
        if (textures != null) {
            for (Map.Entry<MaterialTextureSemantic, MaterialTextureSource> entry : textures.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) copy.put(entry.getKey(), entry.getValue());
            }
        }
        this.textures = Collections.unmodifiableMap(copy);
        this.presenceMask = presenceMask(copy);
    }

    public static MaterialTextureSet fromSources(Map<MaterialTextureSemantic, MaterialTextureSource> textures) {
        return new MaterialTextureSet(textures, true);
    }

    /** Returns an Identifier only when this semantic is backed by a Minecraft resource texture. */
    public Identifier texture(MaterialTextureSemantic semantic) {
        MaterialTextureSource source = textures.get(semantic);
        return source instanceof MaterialTextureSource.Resource resource ? resource.id() : null;
    }

    public MaterialTextureSource source(MaterialTextureSemantic semantic) {
        return textures.get(semantic);
    }

    public boolean has(MaterialTextureSemantic semantic) {
        return textures.containsKey(semantic);
    }

    public int presenceMask() {
        return presenceMask;
    }

    /** Returns resource-backed texture identifiers, excluding imported texture sources. */
    public Map<MaterialTextureSemantic, Identifier> asMap() {
        EnumMap<MaterialTextureSemantic, Identifier> resources = new EnumMap<>(MaterialTextureSemantic.class);
        for (Map.Entry<MaterialTextureSemantic, MaterialTextureSource> entry : textures.entrySet()) {
            if (entry.getValue() instanceof MaterialTextureSource.Resource resource) {
                resources.put(entry.getKey(), resource.id());
            }
        }
        return Collections.unmodifiableMap(resources);
    }

    public Map<MaterialTextureSemantic, MaterialTextureSource> sources() {
        return textures;
    }

    private static int presenceMask(Map<MaterialTextureSemantic, MaterialTextureSource> textures) {
        int mask = 0;
        for (MaterialTextureSemantic semantic : textures.keySet()) mask |= 1 << semantic.ordinal();
        return mask;
    }
}
