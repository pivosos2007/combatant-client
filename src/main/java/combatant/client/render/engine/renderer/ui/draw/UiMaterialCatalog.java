/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer.ui.draw;

import java.util.Locale;

/** Canonical names/default profiles used by Java callers and the script-facing runtime. */
public final class UiMaterialCatalog {
    private UiMaterialCatalog() {}

    public static UiSurfaceMaterialSpec solid(String name) {
        return switch (normalize(name)) {
            case "graphite" -> UiSurfaceMaterialSpec.GRAPHITE;
            case "ceramic" -> UiSurfaceMaterialSpec.CERAMIC;
            case "brushed-metal", "brushed", "brushedmetal" -> UiSurfaceMaterialSpec.BRUSHED_METAL;
            case "satin-titanium", "satin", "titanium", "satin-metal" -> UiSurfaceMaterialSpec.SATIN_TITANIUM;
            case "soft-touch", "softtouch", "polymer" -> UiSurfaceMaterialSpec.SOFT_TOUCH;
            case "phosphor-led", "led", "dot-matrix", "dotmatrix" -> UiSurfaceMaterialSpec.PHOSPHOR_LED;
            default -> null;
        };
    }

    public static UiGlassOpticsSpec glass(String name) {
        return switch (normalize(name)) {
            case "mirror-frosted", "fresnel", "fresnel-glass", "fresnel-frosted", "legacy-glass" ->
                    UiGlassOpticsSpec.FRESNEL_GLASS;
            case "reactive-smoked", "smoked-glass" -> UiGlassOpticsSpec.LIQUID
                    .withMode(UiGlassOpticsSpec.Mode.REACTIVE_SMOKED).withCleanReveal(0.42f)
                    .withChromaticAberration(0.075f);
            case "soft-lens" -> UiGlassOpticsSpec.LIQUID
                    .withMode(UiGlassOpticsSpec.Mode.SOFT_LENS).withCleanReveal(0.34f)
                    .withMagnification(1.055f).withChromaticAberration(0.14f);
            case "etched-glass" -> UiGlassOpticsSpec.LIQUID
                    .withMode(UiGlassOpticsSpec.Mode.ETCHED_GLASS).withCleanReveal(0.20f)
                    .withChromaticAberration(0.018f);
            default -> UiGlassOpticsSpec.LIQUID;
        };
    }

    public static UiHoneycombMaterialSpec honeycomb(String name) {
        return switch (normalize(name)) {
            case "ceramic-cells" -> UiHoneycombMaterialSpec.CERAMIC_CELLS;
            case "legacy-glass", "legacy" -> UiHoneycombMaterialSpec.LEGACY_GLASS;
            default -> UiHoneycombMaterialSpec.SMOKED_MACHINED;
        };
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
