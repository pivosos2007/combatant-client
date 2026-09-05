/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.platform.BlendFactor;
import combatant.client.render.engine.pipeline.BlendFunctions;

import java.util.Locale;

public record UiBlendSpec(BlendFunction function) {
    public static final UiBlendSpec NONE = new UiBlendSpec(null);
    public static final UiBlendSpec TRANSLUCENT = new UiBlendSpec(BlendFunctions.ALPHA);
    public static final UiBlendSpec PREMULTIPLIED_ALPHA = new UiBlendSpec(BlendFunctions.PREMULTIPLIED_ALPHA);
    public static final UiBlendSpec ADDITIVE = new UiBlendSpec(BlendFunctions.ADDITIVE);
    public static final UiBlendSpec ALPHA_ADDITIVE = new UiBlendSpec(BlendFunctions.ALPHA_ADDITIVE);
    public static final UiBlendSpec OVERLAY = new UiBlendSpec(BlendFunctions.OVERLAY);
    public static final UiBlendSpec INVERT = new UiBlendSpec(BlendFunctions.INVERT);

    public static UiBlendSpec of(BlendFactor source, BlendFactor destination) {
        return new UiBlendSpec(BlendFunctions.of(source, destination));
    }

    public static UiBlendSpec separate(BlendFactor sourceColor,
                                       BlendFactor destinationColor,
                                       BlendFactor sourceAlpha,
                                       BlendFactor destinationAlpha) {
        return new UiBlendSpec(BlendFunctions.separate(
                sourceColor,
                destinationColor,
                sourceAlpha,
                destinationAlpha
        ));
    }

    public static UiBlendSpec parse(String value) {
        if (value == null || value.isBlank()) return TRANSLUCENT;
        String raw = value.trim();
        String normalized = raw.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "none", "off", "opaque" -> NONE;
            case "translucent", "normal", "alpha" -> TRANSLUCENT;
            case "premultiplied", "premultiplied-alpha", "translucent-premultiplied-alpha" -> PREMULTIPLIED_ALPHA;
            case "add", "additive" -> ADDITIVE;
            case "overlay" -> OVERLAY;
            case "invert" -> INVERT;
            default -> parseFactors(raw);
        };
    }

    private static UiBlendSpec parseFactors(String raw) {
        String[] parts = raw.split("[,;/]");
        if (parts.length == 2) {
            BlendFactor source = source(parts[0], BlendFactor.SRC_ALPHA);
            BlendFactor dest = dest(parts[1], BlendFactor.ONE_MINUS_SRC_ALPHA);
            return of(source, dest);
        }
        if (parts.length == 4) {
            BlendFactor sourceColor = source(parts[0], BlendFactor.SRC_ALPHA);
            BlendFactor destColor = dest(parts[1], BlendFactor.ONE_MINUS_SRC_ALPHA);
            BlendFactor sourceAlpha = source(parts[2], BlendFactor.ONE);
            BlendFactor destAlpha = dest(parts[3], BlendFactor.ONE_MINUS_SRC_ALPHA);
            return separate(sourceColor, destColor, sourceAlpha, destAlpha);
        }
        return TRANSLUCENT;
    }

    private static BlendFactor source(String value, BlendFactor fallback) {
        try {
            return BlendFactor.valueOf(normalizeFactor(value));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static BlendFactor dest(String value, BlendFactor fallback) {
        try {
            return BlendFactor.valueOf(normalizeFactor(value));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static String normalizeFactor(String value) {
        return value == null
                ? ""
                : value.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }

    public boolean enabled() {
        return function != null;
    }
}
