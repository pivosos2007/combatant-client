/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.draggable.impl;

import combatant.client.config.values.ModeValue;
import combatant.client.events.impl.RenderPrewarmCollectEvent;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.draw.UiMaterialCatalog;
import combatant.client.render.engine.renderer.ui.draw.UiPaint;
import combatant.client.render.engine.renderer.ui.draw.UiPrimitive;
import combatant.client.render.engine.renderer.ui.draw.UiSurfaceMaterialSpec;

/** Shared selectable surface material profile for draggable HUD panels. */
public final class HudMaterialChoice {
    public static final String DEFAULT = "Default";
    public static final String[] OPTIONS = {DEFAULT, "Graphite", "SoftTouch", "Ceramic",
            "SatinTitanium", "BrushedMetal", "Ice", "PhosphorLED", "Voronoi"};

    private HudMaterialChoice() {}

    public static ModeValue setting(String owner) {
        return new ModeValue(owner + "_surface_material", DEFAULT, OPTIONS);
    }

    public static void collectPrewarm(RenderPrewarmCollectEvent event, String option) {
        if (event == null) return;
        UiSurfaceMaterialSpec material = UiMaterialCatalog.solid(shaderMode(option));
        if (material != null && material.mode() != UiSurfaceMaterialSpec.Mode.PHOSPHOR_LED) {
            event.material(material.withMicrotexture(true));
        }
    }

    /** Draws only the base surface. Existing blur, border and content stay with the widget. */
    public static boolean draw(Renderer2D renderer, String option, double x, double y,
                        double width, double height, float radius, int top, int bottom) {
        UiSurfaceMaterialSpec material = UiMaterialCatalog.solid(shaderMode(option));
        if (width <= 0.0 || height <= 0.0) return false;
        if ("Voronoi".equals(option)) {
            renderer.roundedRectGradient(x, y, width, height, radius, top, bottom, 90f);
            renderer.cellularPrimitive(UiPrimitive.builder(x, y, width, height)
                            .roundedCorners(radius, radius, radius, radius).build(),
                    UiPaint.solid(combatant.client.features.theme.Theme.theme().accent()),
                    combatant.client.render.engine.renderer.ui.draw.UiCellularStructureSpec.CELLS
                            .withCellSize(32f).withOpacity(0.14f));
            combatant.client.features.gui.hud.HudClimateSurface.drawMaterialClimate(renderer,
                    UiPrimitive.builder(x, y, width, height)
                            .roundedCorners(radius, radius, radius, radius).build(), 0.85f);
            return true;
        }
        if (material == null) return false;
        if (material.mode() != UiSurfaceMaterialSpec.Mode.PHOSPHOR_LED) {
            material = material.withMicrotexture(material.texture().withTintStrength(1.0f));
        }
        renderer.materialPrimitive(UiPrimitive.builder(x, y, width, height)
                        .roundedCorners(radius, radius, radius, radius).build(),
                UiPaint.linear(top, bottom, 90f, 0f),
                material.withAccent(combatant.client.features.theme.Theme.theme().accent()));
        combatant.client.features.gui.hud.HudClimateSurface.drawMaterialClimate(renderer,
                UiPrimitive.builder(x, y, width, height)
                        .roundedCorners(radius, radius, radius, radius).build(), 0.85f);
        return true;
    }

    /** Keep the old Default fill, then optionally lay weather ON TOP of it. */
    public static void drawDefaultClimate(Renderer2D renderer, String option,
                                          double x, double y, double width, double height,
                                          float radius) {
        if (!DEFAULT.equals(option) || width <= 0 || height <= 0) return;
        combatant.client.features.gui.hud.HudClimateSurface.drawMaterialClimate(renderer,
                UiPrimitive.builder(x, y, width, height)
                        .roundedCorners(radius, radius, radius, radius).build(), 0.80f);
    }

    /**
     * Glass-compatible material contribution. Unlike draw(), this never replaces
     * the existing optical pass or its blur/capture. The physical surface is a
     * restrained translucent coat; ICE belongs to the glass visual pipeline.
     * Each caller passes the EXACT silhouette of its existing glass surface.
     */
    public static boolean drawGlassOverlay(Renderer2D renderer, String option, double x, double y,
                                    double width, double height,
                                    float tl, float tr, float br, float bl,
                                    int top, int bottom) {
        if (renderer == null || width <= 0.0 || height <= 0.0
                || option == null || DEFAULT.equals(option) || "Ice".equals(option)) return false;
        UiPrimitive primitive = UiPrimitive.builder(x, y, width, height)
                .roundedCorners(tl, tr, br, bl).build();
        if ("Voronoi".equals(option)) {
            renderer.cellularPrimitive(primitive,
                    UiPaint.solid(combatant.client.features.theme.Theme.theme().accent()),
                    combatant.client.render.engine.renderer.ui.draw.UiCellularStructureSpec.CELLS
                            .withCellSize(32f).withOpacity(0.14f));
            return true;
        }
        UiSurfaceMaterialSpec material = UiMaterialCatalog.solid(shaderMode(option));
        if (material == null) return false;
        if (material.mode() != UiSurfaceMaterialSpec.Mode.PHOSPHOR_LED) {
            material = material.withMicrotexture(material.texture().withTintStrength(1.0f));
        }
        // This is a coat ON TOP of Liquid Glass. Do not let an opaque PBR
        // albedo hide refraction, existing tint or the carefully tuned blur.
        final float opacity = 0.22f;
        renderer.materialPrimitive(primitive,
                UiPaint.linear(top, bottom, 90f, 0f).withAlphaScale(opacity),
                material.withAccent(combatant.client.features.theme.Theme.theme().accent()));
        return true;
    }

    public static String shaderMode(String option) {
        if (option == null) return "";
        return switch (option) {
            case "Graphite" -> "graphite";
            case "SoftTouch" -> "soft-touch";
            case "Ceramic" -> "ceramic";
            case "SatinTitanium" -> "satin-titanium";
            case "BrushedMetal" -> "brushed-metal";
            case "Ice" -> "ice";
            case "PhosphorLED" -> "phosphor-led";
            case "Voronoi" -> "voronoi";
            default -> "";
        };
    }
}
