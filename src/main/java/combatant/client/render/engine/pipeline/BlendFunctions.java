/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.pipeline;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.platform.BlendFactor;

import java.util.Objects;

/**
 * Typed blend helpers backed directly by Blaze3D's pipeline state.
 *
 * <p>Renderer and feature code should use Mojang {@link BlendFactor} values instead of
 * OpenGL/Vulkan constants. Blaze3D is then responsible for lowering the same
 * {@link BlendFunction} to the active graphics backend.</p>
 */
public final class BlendFunctions {
    /** Standard straight-alpha compositing: srcAlpha / oneMinusSrcAlpha. */
    public static final BlendFunction ALPHA = BlendFunction.TRANSLUCENT;

    /** Standard premultiplied-alpha compositing. */
    public static final BlendFunction PREMULTIPLIED_ALPHA = BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA;

    /** Mojang's additive blend preset. */
    public static final BlendFunction ADDITIVE = BlendFunction.ADDITIVE;

    /** Alpha-weighted additive color, useful for glows/particles. */
    public static final BlendFunction ALPHA_ADDITIVE = of(BlendFactor.SRC_ALPHA, BlendFactor.ONE);

    /** Mojang's overlay preset. */
    public static final BlendFunction OVERLAY = BlendFunction.OVERLAY;

    /** Mojang's invert preset. */
    public static final BlendFunction INVERT = BlendFunction.INVERT;

    private BlendFunctions() {
    }

    /**
     * Uses the same factors for RGB and alpha.
     */
    public static BlendFunction of(BlendFactor source, BlendFactor destination) {
        return new BlendFunction(
                Objects.requireNonNull(source, "source"),
                Objects.requireNonNull(destination, "destination")
        );
    }

    /**
     * Uses independent factors for RGB and alpha channels.
     */
    public static BlendFunction separate(BlendFactor sourceColor,
                                         BlendFactor destinationColor,
                                         BlendFactor sourceAlpha,
                                         BlendFactor destinationAlpha) {
        return new BlendFunction(
                Objects.requireNonNull(sourceColor, "sourceColor"),
                Objects.requireNonNull(destinationColor, "destinationColor"),
                Objects.requireNonNull(sourceAlpha, "sourceAlpha"),
                Objects.requireNonNull(destinationAlpha, "destinationAlpha")
        );
    }
}
