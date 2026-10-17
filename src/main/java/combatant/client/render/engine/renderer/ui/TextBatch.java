/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui;

import java.util.Objects;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import combatant.client.render.engine.text.GlyphFont;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.backend.TextPlacementMode;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.engine.renderer.ui.clip.UiClipSnapshot;
import combatant.client.render.engine.renderer.ui.clip.UiScissorSnapshot;
import combatant.client.render.engine.renderer.ui.draw.UiRect;
import combatant.client.render.engine.renderer.ui.draw.UiBackdropRequest;
import combatant.client.render.engine.renderer.ui.draw.UiBlurQuality;
import combatant.client.render.engine.renderer.ui.blend.UiBackdropBlendSpec;

public final class TextBatch {
    public String label;
    public GlyphFont font;
    public RenderPipeline pipeline;
    public TextPlacementMode placement;
    public UiClipSnapshot clipSnapshot = UiClipSnapshot.NONE;
    public UiScissorSnapshot scissorSnapshot = UiScissorSnapshot.NONE;
    public MeshBuilder mesh;
    public boolean liquidGlass;
    public UiRect glassBounds;
    public UiBackdropBlendSpec backdropBlend;
    public UiBackdropRequest backdropRequest;

    public void begin(String label, GlyphFont font, RenderPipeline pipeline,
               TextPlacementMode placement, UiScissorSnapshot scissorSnapshot, UiClipSnapshot clipSnapshot) {
        this.label = label != null ? label : "Combatant UI Text Batch";
        this.font = font;
        this.pipeline = pipeline;
        this.placement = placement != null ? placement : TextPlacementMode.UI;
        this.scissorSnapshot = scissorSnapshot != null ? scissorSnapshot : UiScissorSnapshot.NONE;
        this.clipSnapshot = clipSnapshot != null ? clipSnapshot : UiClipSnapshot.NONE;
        this.liquidGlass = false;
        this.glassBounds = null;
        this.backdropBlend = null;
        this.backdropRequest = UiBackdropRequest.NONE;
        if (mesh == null) {
            mesh = new MeshBuilder(pipeline);
        } else if (mesh.isBuilding()) {
            mesh.end();
        }
        mesh.begin();
    }

    public boolean canMerge(GlyphFont font, RenderPipeline pipeline, TextPlacementMode placement,
                            UiScissorSnapshot scissorSnapshot, UiClipSnapshot clipSnapshot) {
        return !liquidGlass
                && this.font == font
                && this.pipeline == pipeline
                && this.placement == (placement != null ? placement : TextPlacementMode.UI)
                && this.scissorSnapshot.id() == scissorSnapshot.id()
                && this.clipSnapshot.id() == clipSnapshot.id();
    }

    public void beginLiquidGlass(String label,
                                 GlyphFont font,
                                 RenderPipeline pipeline,
                                 TextPlacementMode placement,
                                 UiRect bounds,
                                 UiScissorSnapshot scissorSnapshot,
                                 UiClipSnapshot clipSnapshot) {
        beginLiquidGlass(label, font, pipeline, placement, bounds, null,
                UiBackdropRequest.capturedSceneGlass(bounds, UiBlurQuality.LIQUID_GLASS,
                        Renderer2D.LIQUID_GLASS_KAWASE_OFFSET_PX),
                scissorSnapshot, clipSnapshot);
    }

    public void beginLiquidGlass(String label,
                                 GlyphFont font,
                                 RenderPipeline pipeline,
                                 TextPlacementMode placement,
                                 UiRect bounds,
                                 UiBackdropBlendSpec backdropBlend,
                                 UiBackdropRequest backdropRequest,
                                 UiScissorSnapshot scissorSnapshot,
                                 UiClipSnapshot clipSnapshot) {
        begin(label, font, pipeline, placement, scissorSnapshot, clipSnapshot);
        this.liquidGlass = true;
        this.glassBounds = bounds;
        this.backdropBlend = backdropBlend;
        this.backdropRequest = backdropRequest != null
                ? backdropRequest.withCaptureBounds(bounds)
                : UiBackdropRequest.capturedSceneGlass(bounds, UiBlurQuality.LIQUID_GLASS,
                Renderer2D.LIQUID_GLASS_KAWASE_OFFSET_PX);
    }

    public boolean canMergeLiquidGlass(GlyphFont font,
                                       RenderPipeline pipeline,
                                       TextPlacementMode placement,
                                       UiScissorSnapshot scissorSnapshot,
                                       UiClipSnapshot clipSnapshot) {
        return canMergeLiquidGlass(font, pipeline, placement, null, null, scissorSnapshot, clipSnapshot);
    }

    public boolean canMergeLiquidGlass(GlyphFont font,
                                       RenderPipeline pipeline,
                                       TextPlacementMode placement,
                                       UiBackdropBlendSpec backdropBlend,
                                       UiBackdropRequest backdropRequest,
                                       UiScissorSnapshot scissorSnapshot,
                                       UiClipSnapshot clipSnapshot) {
        return liquidGlass
                && this.font == font
                && this.pipeline == pipeline
                && this.placement == (placement != null ? placement : TextPlacementMode.UI)
                && Objects.equals(this.backdropBlend, backdropBlend)
                && compatibleBackdrop(this.backdropRequest, backdropRequest)
                && this.scissorSnapshot.id() == scissorSnapshot.id()
                && this.clipSnapshot.id() == clipSnapshot.id();
    }

    private static boolean compatibleBackdrop(UiBackdropRequest current, UiBackdropRequest requested) {
        if (requested == null) return current == null || current.requiresCapturedScene();
        if (current == null) return false;
        return current.compatibleInputs(requested)
                && Float.compare(current.sceneMix(), requested.sceneMix()) == 0
                && Float.compare(current.uiMix(), requested.uiMix()) == 0;
    }

    public void expandGlassBounds(UiRect bounds) {
        if (bounds == null) return;
        if (glassBounds == null) {
            glassBounds = bounds;
            return;
        }
        float x0 = Math.min(glassBounds.x(), bounds.x());
        float y0 = Math.min(glassBounds.y(), bounds.y());
        float x1 = Math.max(glassBounds.x() + glassBounds.width(), bounds.x() + bounds.width());
        float y1 = Math.max(glassBounds.y() + glassBounds.height(), bounds.y() + bounds.height());
        glassBounds = new UiRect(x0, y0, Math.max(0f, x1 - x0), Math.max(0f, y1 - y0));
        if (backdropRequest != null) backdropRequest = backdropRequest.withCaptureBounds(glassBounds);
    }

    public void append(MeshBuilder source) {
        if (source == null) return;
        if (source.isBuilding()) source.end();
        if (source.getIndicesCount() <= 0) return;
        if (mesh == null || !mesh.isBuilding()) {
            begin(label, font, pipeline, placement, scissorSnapshot, clipSnapshot);
        }
        mesh.appendMesh(source);
    }

    public boolean isEmpty() {
        if (mesh == null) return true;
        if (mesh.isBuilding()) mesh.end();
        return mesh.getIndicesCount() <= 0;
    }
}
