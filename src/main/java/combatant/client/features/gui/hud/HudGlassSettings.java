/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.hud;

import combatant.client.config.SettingDef;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.PrecisionNumberValue;
import combatant.client.render.engine.renderer.ui.draw.UiGlassOpticsSpec;

import java.text.DecimalFormat;
import java.util.List;

/** Independent, persisted optical controls for one HUD element's glass surface. */
public final class HudGlassSettings {
    private final UiGlassOpticsSpec defaults;
    private final BooleanValue customize;
    private final NumberValue<Float> refraction;
    private final NumberValue<Float> chromatic;
    private final NumberValue<Float> magnification;
    private final NumberValue<Float> bevelWidth;
    private final NumberValue<Float> bevelDepth;
    private final NumberValue<Float> cleanReveal;
    private final NumberValue<Float> interaction;
    private final NumberValue<Float> interactionRadius;
    private final NumberValue<Float> viscosity;
    private final NumberValue<Float> thickness;
    private final NumberValue<Float> refractiveIndex;
    private final NumberValue<Float> roughness;
    private final NumberValue<Float> frost;
    private final NumberValue<Float> filmThickness;
    private final NumberValue<Float> filmStrength;
    private final NumberValue<Float> coating;
    private final BooleanValue specular;

    public HudGlassSettings(String owner, UiGlassOpticsSpec defaults) {
        this.defaults = defaults;
        String prefix = owner + "_glass_";
        customize = new BooleanValue(prefix + "customize", false);
        refraction = number(prefix, "refraction", defaults.refraction(), -0.08f, 0.08f);
        chromatic = number(prefix, "chromatic", defaults.chromaticAberration(), 0f, 0.95f);
        magnification = number(prefix, "magnification", defaults.magnification(), 0.65f, 1.8f);
        bevelWidth = number(prefix, "bevel_width", defaults.bevelWidth(), 0.001f, 1f);
        bevelDepth = number(prefix, "bevel_depth", defaults.bevelDepth(), -0.15f, 0.15f);
        cleanReveal = number(prefix, "clean_reveal", defaults.cleanReveal(), 0f, 1f);
        interaction = number(prefix, "interaction", defaults.interactionStrength(), 0f, 5f);
        interactionRadius = number(prefix, "interaction_radius", defaults.interactionRadius(), 0f, 2f);
        viscosity = number(prefix, "viscosity", defaults.interactionViscosity(), 0f, 1f);
        thickness = number(prefix, "optical_thickness", defaults.opticalThickness(), 0f, 2.5f);
        refractiveIndex = number(prefix, "ior", defaults.refractiveIndex(), 1f, 2f);
        roughness = number(prefix, "roughness", defaults.surfaceRoughness(), 0f, 1f);
        frost = number(prefix, "frost", defaults.frost(), 0f, 1f);
        filmThickness = number(prefix, "film_thickness", defaults.filmThicknessNm(), 0f, 1200f);
        filmStrength = number(prefix, "film_strength", defaults.filmStrength(), 0f, 1f);
        coating = number(prefix, "reactive_coating", defaults.reactiveCoating(), 0f, 1f);
        specular = new BooleanValue(prefix + "specular", defaults.specular());
    }

    private static NumberValue<Float> number(String prefix, String name, float initial, float min, float max) {
        return new PreciseFloatValue(prefix + name, initial, min, max);
    }

    // Standard NumberValue truncates floating-point config to 2 decimal places;
    // 0.0045 refraction and 0.023 bevel depth must survive save/load intact.
    private static final class PreciseFloatValue extends NumberValue<Float> implements PrecisionNumberValue {
        private PreciseFloatValue(String name, float initial, float min, float max) {
            super(name, initial, min, max);
        }

        @Override
        public int decimalPlaces() {
            return 4;
        }

        @Override
        public Object toJson() {
            return Math.round(get() * 10000.0d) / 10000.0d;
        }

        @Override
        public String toDisplay() {
            return new DecimalFormat("0.####").format(get());
        }
    }

    /** Append controls to the owning element, not a shared/global settings page. */
    public void addSettings(List<SettingDef> defs) {
        defs.add(SettingDef.bool(customize).common("setting.common.hud_glass.customize"));
        add(defs, refraction, "refraction");
        add(defs, chromatic, "chromatic");
        add(defs, magnification, "magnification");
        add(defs, bevelWidth, "bevel_width");
        add(defs, bevelDepth, "bevel_depth");
        add(defs, cleanReveal, "clean_reveal");
        add(defs, interaction, "interaction");
        add(defs, interactionRadius, "interaction_radius");
        add(defs, viscosity, "viscosity");
        add(defs, thickness, "optical_thickness");
        add(defs, refractiveIndex, "ior");
        add(defs, roughness, "roughness");
        add(defs, frost, "frost");
        add(defs, filmThickness, "film_thickness");
        add(defs, filmStrength, "film_strength");
        add(defs, coating, "reactive_coating");
        defs.add(SettingDef.bool(specular)
                .common("setting.common.hud_glass.specular").visibleWhen(customize::get));
    }

    private void add(List<SettingDef> defs, NumberValue<Float> value, String key) {
        defs.add(SettingDef.number(value)
                .common("setting.common.hud_glass." + key).visibleWhen(customize::get));
    }

    public boolean customized() {
        return customize.get();
    }

    /**
     * Retains internal sub-surface profiles (button lenses, hit reactions) at defaults.
     * Relative adjustments preserve their differences; setting a multiplier to zero
     * reliably disables that property on every sub-surface.
     */
    public UiGlassOpticsSpec apply(UiGlassOpticsSpec original) {
        if (original == null) original = defaults;
        if (!customize.get()) return original;
        // Build one immutable spec, not a chain of temporary specs on the render thread.
        return new UiGlassOpticsSpec(
                original.mode(),
                adjust(original.refraction(), defaults.refraction(), refraction.get()),
                adjust(original.bevelWidth(), defaults.bevelWidth(), bevelWidth.get()),
                adjust(original.bevelDepth(), defaults.bevelDepth(), bevelDepth.get()),
                adjust(original.interactionStrength(), defaults.interactionStrength(), interaction.get()),
                adjust(original.interactionRadius(), defaults.interactionRadius(), interactionRadius.get()),
                adjust(original.interactionViscosity(), defaults.interactionViscosity(), viscosity.get()),
                adjust(original.cleanReveal(), defaults.cleanReveal(), cleanReveal.get()),
                adjust(original.chromaticAberration(), defaults.chromaticAberration(), chromatic.get()),
                adjust(original.magnification(), defaults.magnification(), magnification.get()),
                original.shapeDeformation(),
                original.tiltXDegrees(), original.tiltYDegrees(), specular.get(), original.chromaticMotion(),
                adjust(original.opticalThickness(), defaults.opticalThickness(), thickness.get()),
                adjust(original.refractiveIndex(), defaults.refractiveIndex(), refractiveIndex.get()),
                adjust(original.surfaceRoughness(), defaults.surfaceRoughness(), roughness.get()),
                adjust(original.frost(), defaults.frost(), frost.get()),
                adjust(original.filmThicknessNm(), defaults.filmThicknessNm(), filmThickness.get()),
                filmStrength.get(),
                adjust(original.reactiveCoating(), defaults.reactiveCoating(), coating.get()));
    }

    private static float adjust(float original, float base, float selected) {
        if (Float.compare(selected, base) == 0) return original;
        if (Float.compare(selected, 0f) == 0) return 0f;
        // Zero-default properties can still be enabled on every HUD surface.
        if (Math.abs(base) < 0.00001f) return original + selected;
        return original * (selected / base);
    }
}
