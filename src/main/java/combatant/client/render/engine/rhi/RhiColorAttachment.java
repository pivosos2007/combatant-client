/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi;

import com.mojang.blaze3d.textures.GpuTextureView;
import org.jetbrains.annotations.Nullable;

import java.util.OptionalInt;

/** One logical color slot in an ordinary graphics render pass. */
public record RhiColorAttachment(@Nullable GpuTextureView view, OptionalInt clearColor) {
    public RhiColorAttachment {
        clearColor = clearColor == null ? OptionalInt.empty() : clearColor;
        if (view == null && clearColor.isPresent()) {
            throw new IllegalArgumentException("Unused color attachment cannot be cleared");
        }
    }

    public static RhiColorAttachment load(GpuTextureView view) {
        if (view == null) throw new IllegalArgumentException("view");
        return new RhiColorAttachment(view, OptionalInt.empty());
    }

    public static RhiColorAttachment clear(GpuTextureView view, int argb) {
        if (view == null) throw new IllegalArgumentException("view");
        return new RhiColorAttachment(view, OptionalInt.of(argb));
    }

    public static RhiColorAttachment unused() {
        return new RhiColorAttachment(null, OptionalInt.empty());
    }

    public boolean isUnused() {
        return view == null;
    }
}
