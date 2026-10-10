/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

public record UiHoneycombMaterialSpec(Mode mode,
                                      float cellBlurMix,
                                      float sceneDetail,
                                      float tintAbsorption,
                                      float highlightCompression) implements UiMaterialSpec {
    @Override public Family family() { return Family.HONEYCOMB; }

    public static final UiHoneycombMaterialSpec SMOKED_MACHINED = new UiHoneycombMaterialSpec(
            Mode.SMOKED_MACHINED, 0.32f, 0.20f, 0.36f, 0.72f);
    public static final UiHoneycombMaterialSpec CERAMIC_CELLS = new UiHoneycombMaterialSpec(
            Mode.CERAMIC_CELLS, 0.22f, 0.10f, 0.72f, 0.82f);
    public static final UiHoneycombMaterialSpec LEGACY_GLASS = new UiHoneycombMaterialSpec(
            Mode.LEGACY_GLASS, 0.985f, 0.035f, 0.012f, 0.0f);

    public UiHoneycombMaterialSpec {
        mode = mode != null ? mode : Mode.SMOKED_MACHINED;
        cellBlurMix = clamp(cellBlurMix);
        sceneDetail = clamp(sceneDetail);
        tintAbsorption = clamp(tintAbsorption);
        highlightCompression = clamp(highlightCompression);
    }

    public enum Mode {
        SMOKED_MACHINED(0),
        CERAMIC_CELLS(1),
        LEGACY_GLASS(2);

        private final int shaderId;

        Mode(int shaderId) {
            this.shaderId = shaderId;
        }

        public int shaderId() {
            return shaderId;
        }
    }

    private static float clamp(float value) {
        if (!Float.isFinite(value)) return 0.0f;
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
