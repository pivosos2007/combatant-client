/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.text.backend;

import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.text.TextBackendPreference;
import combatant.client.render.engine.text.TextClipSpec;
import combatant.client.render.engine.text.TextEffectSpec;
import combatant.client.render.engine.text.TextRenderer;

public record TextDrawCommand(TextRenderer renderer,
                              String text,
                              float x,
                              float y,
                              float z,
                              int argb,
                              float size,
                              TextPlacementMode placement,
                              boolean shadow,
                              boolean big,
                              boolean customEffects,
                              TextBackendPreference preference,
                              TextEffectSpec effect,
                              TextClipSpec clip) {
    public TextDrawCommand(String text,
                           float x,
                           float y,
                           float z,
                           int argb,
                           float size,
                           TextRenderDomain domain,
                           boolean customEffects) {
        this(TextRenderer.get(), text, x, y, z, argb, size, TextPlacementMode.fromDomain(domain), false, false, customEffects,
                TextBackendPreference.AUTO, TextEffectSpec.NONE, TextClipSpec.NONE);
    }

    public static Builder builder(String text) {
        return new Builder(text);
    }

    /**
     * Compatibility accessor for old code. Placement is the source of truth.
     */
    @Deprecated
    public TextRenderDomain domain() {
        return placement != null ? placement.legacyDomain() : TextRenderDomain.UI;
    }

    public RenderColor color() {
        return new RenderColor(argb);
    }

    public boolean wants(TextBackendPreference backend) {
        return preference == backend;
    }

    public boolean auto() {
        return preference == null || preference == TextBackendPreference.AUTO;
    }

    public boolean worldPlaced() {
        return placement != null && placement.world();
    }

    public boolean screenLike() {
        return placement == null || placement.screenLike();
    }

    public static final class Builder {
        private final String text;
        private TextRenderer renderer = TextRenderer.get();
        private float x;
        private float y;
        private float z;
        private int argb = 0xFFFFFFFF;
        private float size = 1.0f;
        private TextPlacementMode placement = TextPlacementMode.UI;
        private boolean shadow;
        private boolean big;
        private boolean customEffects;
        private TextBackendPreference preference = TextBackendPreference.AUTO;
        private TextEffectSpec effect = TextEffectSpec.NONE;
        private TextClipSpec clip = TextClipSpec.NONE;

        private Builder(String text) {
            this.text = text;
        }

        public Builder renderer(TextRenderer value) {
            this.renderer = value != null ? value : TextRenderer.get();
            return this;
        }

        public Builder position(float x, float y) {
            this.x = x;
            this.y = y;
            return this;
        }

        public Builder position(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        public Builder z(float z) {
            this.z = z;
            return this;
        }

        public Builder color(int argb) {
            this.argb = argb;
            return this;
        }

        public Builder size(float size) {
            this.size = Math.max(0.01f, size);
            return this;
        }

        public Builder placement(TextPlacementMode placement) {
            this.placement = placement != null ? placement : TextPlacementMode.UI;
            return this;
        }

        public Builder domain(TextRenderDomain domain) {
            this.placement = TextPlacementMode.fromDomain(domain);
            return this;
        }

        public Builder worldBillboard() {
            this.placement = TextPlacementMode.WORLD_BILLBOARD;
            return this;
        }

        public Builder worldAligned() {
            this.placement = TextPlacementMode.WORLD_ALIGNED;
            return this;
        }

        public Builder screenSpace() {
            this.placement = TextPlacementMode.SCREEN_SPACE;
            return this;
        }

        public Builder shadow(boolean value) {
            this.shadow = value;
            return this;
        }

        public Builder big(boolean value) {
            this.big = value;
            return this;
        }

        public Builder customEffects(boolean value) {
            this.customEffects = value;
            return this;
        }

        public Builder preference(TextBackendPreference value) {
            this.preference = value != null ? value : TextBackendPreference.AUTO;
            return this;
        }

        public Builder effect(TextEffectSpec value) {
            this.effect = value != null ? value : TextEffectSpec.NONE;
            this.customEffects |= this.effect.enabled();
            return this;
        }

        public Builder clip(TextClipSpec value) {
            this.clip = value != null ? value : TextClipSpec.NONE;
            return this;
        }

        public TextDrawCommand build() {
            return new TextDrawCommand(renderer, text, x, y, z, argb, size, placement, shadow, big, customEffects,
                    preference, effect, clip);
        }
    }
}
