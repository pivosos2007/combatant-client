/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui;

import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.engine.renderer.Renderer2D;

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
    public boolean shapeClipActive;

    public DrawBatch(UiBatchType type) {
        this.type = type;
        this.mesh = new MeshBuilder(type.pipeline);
    }

    public void begin(GpuTextureView view, GpuSampler sampler, boolean shapeClipActive) {
        this.view = view;
        this.sampler = sampler;
        this.msdfPxRange = 0.0f;
        this.msdfAtlasWidth = 0;
        this.msdfAtlasHeight = 0;
        this.blurQuality = Renderer2D.DEFAULT_BLUR_QUALITY;
        this.blurOffsetPx = Renderer2D.DEFAULT_KAWASE_OFFSET_PX;
        this.shapeClipActive = shapeClipActive;
        if (mesh.isBuilding()) {
            mesh.end();
        }
        mesh.begin();
    }

    public void beginMsdf(GpuTextureView view, GpuSampler sampler, float pxRange, int atlasWidth, int atlasHeight, boolean shapeClipActive) {
        begin(view, sampler, shapeClipActive);
        this.msdfPxRange = pxRange;
        this.msdfAtlasWidth = atlasWidth;
        this.msdfAtlasHeight = atlasHeight;
    }

    public void beginBlur(GpuTextureView view, GpuSampler sampler, Renderer2D.BlurQuality quality, float offsetPx, boolean shapeClipActive) {
        begin(view, sampler, shapeClipActive);
        this.blurQuality = quality != null ? quality : Renderer2D.DEFAULT_BLUR_QUALITY;
        this.blurOffsetPx = Float.isFinite(offsetPx) ? Math.max(0.0f, offsetPx) : Renderer2D.DEFAULT_KAWASE_OFFSET_PX;
    }

    public boolean canMerge(UiBatchType type, GpuTextureView view, GpuSampler sampler, boolean shapeClipActive) {
        if (this.type != type || this.shapeClipActive != shapeClipActive) return false;
        if (!type.usesSampler) return true;
        return this.view == view && this.sampler == sampler;
    }

    public boolean canMergeMsdf(GpuTextureView view, GpuSampler sampler, float pxRange, int atlasWidth, int atlasHeight, boolean shapeClipActive) {
        return canMerge(UiBatchType.SVG_MSDF, view, sampler, shapeClipActive)
                && Float.compare(this.msdfPxRange, pxRange) == 0
                && this.msdfAtlasWidth == atlasWidth
                && this.msdfAtlasHeight == atlasHeight;
    }

    public boolean canMergeBlur(UiBatchType type, GpuTextureView view, GpuSampler sampler,
                                Renderer2D.BlurQuality quality, float offsetPx, boolean shapeClipActive) {
        Renderer2D.BlurQuality normalizedQuality = quality != null ? quality : Renderer2D.DEFAULT_BLUR_QUALITY;
        float normalizedOffset = Float.isFinite(offsetPx) ? Math.max(0.0f, offsetPx) : Renderer2D.DEFAULT_KAWASE_OFFSET_PX;
        return canMerge(type, view, sampler, shapeClipActive)
                && this.blurQuality == normalizedQuality
                && Float.compare(this.blurOffsetPx, normalizedOffset) == 0;
    }
}
