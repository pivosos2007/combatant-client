/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui;

import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.clip.UiClipSnapshot;
import combatant.client.render.engine.renderer.ui.clip.UiScissorSnapshot;
import combatant.client.render.engine.renderer.ui.draw.UiBackdropRequest;
import combatant.client.render.engine.renderer.ui.blend.UiBackdropBlendSpec;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;

public final class DrawBatch {
    public final UiBatchType type;
    public final MeshBuilder mesh;
    public GpuTextureView view;
    public GpuSampler sampler;
    public float msdfPxRange;
    public int msdfAtlasWidth;
    public int msdfAtlasHeight;
    public Renderer2D.BlurQuality blurQuality = Renderer2D.DEFAULT_BLUR_QUALITY;
    public float blurOffsetPx = Renderer2D.DEFAULT_KAWASE_OFFSET_PX;
    public UiBackdropRequest backdropRequest = UiBackdropRequest.NONE;
    public UiBackdropBlendSpec backdropBlend = UiBackdropBlendSpec.NORMAL;
    /** Tight logical bounds of GLASS geometry, not the inflated blur capture rectangle. */
    public combatant.client.render.engine.renderer.ui.draw.UiRect glassBounds;
    /** Exact submitted primitive bounds, preserved even when adjacent glass quads are batched. */
    public final List<combatant.client.render.engine.renderer.ui.draw.UiRect> glassRegions = new ArrayList<>();
    public UiClipSnapshot clipSnapshot = UiClipSnapshot.NONE;
    public UiScissorSnapshot scissorSnapshot = UiScissorSnapshot.NONE;

    public DrawBatch(UiBatchType type) {
        this.type = type;
        this.mesh = new MeshBuilder(type.pipeline);
    }

    public void begin(GpuTextureView view, GpuSampler sampler,
                      UiScissorSnapshot scissorSnapshot, UiClipSnapshot clipSnapshot) {
        this.view = view;
        this.sampler = sampler;
        this.msdfPxRange = 0.0f;
        this.msdfAtlasWidth = 0;
        this.msdfAtlasHeight = 0;
        this.blurQuality = Renderer2D.DEFAULT_BLUR_QUALITY;
        this.blurOffsetPx = Renderer2D.DEFAULT_KAWASE_OFFSET_PX;
        this.backdropRequest = UiBackdropRequest.NONE;
        this.glassBounds = null;
        this.glassRegions.clear();
        this.backdropBlend = UiBackdropBlendSpec.NORMAL;
        this.scissorSnapshot = scissorSnapshot != null ? scissorSnapshot : UiScissorSnapshot.NONE;
        this.clipSnapshot = clipSnapshot != null ? clipSnapshot : UiClipSnapshot.NONE;
        if (mesh.isBuilding()) {
            mesh.end();
        }
        mesh.begin();
    }

    public void beginMsdf(GpuTextureView view, GpuSampler sampler, float pxRange, int atlasWidth, int atlasHeight,
                          UiScissorSnapshot scissorSnapshot, UiClipSnapshot clipSnapshot) {
        begin(view, sampler, scissorSnapshot, clipSnapshot);
        this.msdfPxRange = pxRange;
        this.msdfAtlasWidth = atlasWidth;
        this.msdfAtlasHeight = atlasHeight;
    }

    public void beginBlur(GpuTextureView view, GpuSampler sampler, Renderer2D.BlurQuality quality, float offsetPx,
                          UiBackdropRequest backdropRequest,
                          UiScissorSnapshot scissorSnapshot, UiClipSnapshot clipSnapshot) {
        beginBlur(view, sampler, quality, offsetPx, backdropRequest, UiBackdropBlendSpec.NORMAL,
                scissorSnapshot, clipSnapshot);
    }

    public void beginBlur(GpuTextureView view, GpuSampler sampler, Renderer2D.BlurQuality quality, float offsetPx,
                          UiBackdropRequest backdropRequest,
                          UiBackdropBlendSpec backdropBlend,
                          UiScissorSnapshot scissorSnapshot, UiClipSnapshot clipSnapshot) {
        begin(view, sampler, scissorSnapshot, clipSnapshot);
        this.blurQuality = quality != null ? quality : Renderer2D.DEFAULT_BLUR_QUALITY;
        this.blurOffsetPx = Float.isFinite(offsetPx) ? Math.max(0.0f, offsetPx) : Renderer2D.DEFAULT_KAWASE_OFFSET_PX;
        this.backdropRequest = backdropRequest != null ? backdropRequest : UiBackdropRequest.NONE;
        this.backdropBlend = backdropBlend != null ? backdropBlend : UiBackdropBlendSpec.NORMAL;
    }

    public void includeGlassBounds(combatant.client.render.engine.renderer.ui.draw.UiRect bounds) {
        if (bounds == null || bounds.empty()) return;
        glassRegions.add(bounds);
        if (glassBounds == null) { glassBounds = bounds; return; }
        float x0 = Math.min(glassBounds.x(), bounds.x());
        float y0 = Math.min(glassBounds.y(), bounds.y());
        float x1 = Math.max(glassBounds.x() + glassBounds.width(), bounds.x() + bounds.width());
        float y1 = Math.max(glassBounds.y() + glassBounds.height(), bounds.y() + bounds.height());
        glassBounds = new combatant.client.render.engine.renderer.ui.draw.UiRect(x0, y0, x1 - x0, y1 - y0);
    }

    public boolean canMerge(UiBatchType type, GpuTextureView view, GpuSampler sampler,
                            UiScissorSnapshot scissorSnapshot, UiClipSnapshot clipSnapshot) {
        if (this.type != type || !sameState(this.scissorSnapshot, scissorSnapshot)
                || !sameState(this.clipSnapshot, clipSnapshot)) return false;
        if (!type.usesSampler) return true;
        return this.view == view && this.sampler == sampler;
    }

    public boolean canMergeMsdf(GpuTextureView view, GpuSampler sampler, float pxRange, int atlasWidth, int atlasHeight,
                                UiScissorSnapshot scissorSnapshot, UiClipSnapshot clipSnapshot) {
        return canMerge(UiBatchType.SVG_MSDF, view, sampler, scissorSnapshot, clipSnapshot)
                && Float.compare(this.msdfPxRange, pxRange) == 0
                && this.msdfAtlasWidth == atlasWidth
                && this.msdfAtlasHeight == atlasHeight;
    }

    public boolean canMergeBlur(UiBatchType type, GpuTextureView view, GpuSampler sampler,
                                Renderer2D.BlurQuality quality, float offsetPx,
                                UiBackdropRequest backdropRequest,
                                UiScissorSnapshot scissorSnapshot, UiClipSnapshot clipSnapshot) {
        return canMergeBlur(type, view, sampler, quality, offsetPx, backdropRequest, UiBackdropBlendSpec.NORMAL,
                scissorSnapshot, clipSnapshot);
    }

    public boolean canMergeBlur(UiBatchType type, GpuTextureView view, GpuSampler sampler,
                                Renderer2D.BlurQuality quality, float offsetPx,
                                UiBackdropRequest backdropRequest,
                                UiBackdropBlendSpec backdropBlend,
                                UiScissorSnapshot scissorSnapshot, UiClipSnapshot clipSnapshot) {
        // Stacked lenses depend on prior writes to the same target. Merging two
        // stacked lenses would discard their required ordered snapshot boundary.
        if (type.usesPreparedGlass() && backdropRequest != null
                && backdropRequest.sceneSource() == UiBackdropRequest.SceneSource.STACKED_LENSES) {
            return false;
        }
        Renderer2D.BlurQuality normalizedQuality = quality != null ? quality : Renderer2D.DEFAULT_BLUR_QUALITY;
        float normalizedOffset = Float.isFinite(offsetPx) ? Math.max(0.0f, offsetPx) : Renderer2D.DEFAULT_KAWASE_OFFSET_PX;
        return canMerge(type, view, sampler, scissorSnapshot, clipSnapshot)
                && this.blurQuality == normalizedQuality
                && Float.compare(this.blurOffsetPx, normalizedOffset) == 0
                && Objects.equals(this.backdropRequest,
                backdropRequest != null ? backdropRequest : UiBackdropRequest.NONE)
                && Objects.equals(this.backdropBlend,
                backdropBlend != null ? backdropBlend : UiBackdropBlendSpec.NORMAL);
    }

    private static boolean sameState(UiScissorSnapshot a, UiScissorSnapshot b) {
        return a != null && b != null && a.id() == b.id();
    }

    private static boolean sameState(UiClipSnapshot a, UiClipSnapshot b) {
        return a != null && b != null && a.id() == b.id();
    }
}
