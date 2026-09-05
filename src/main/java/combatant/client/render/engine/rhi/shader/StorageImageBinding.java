/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.shader;

/** Native image load/store binding. */
public record StorageImageBinding(int binding,
                                  RhiStorageImage image,
                                  StorageAccess access) {
    public StorageImageBinding {
        if (binding < 0) throw new IllegalArgumentException("binding");
        if (image == null) throw new IllegalArgumentException("image");
        access = access == null ? image.descriptor().access() : access;
    }
}
