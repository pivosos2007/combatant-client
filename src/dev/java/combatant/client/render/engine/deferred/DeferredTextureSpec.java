/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.GpuTexture;
import combatant.client.render.engine.framegraph.FrameGraphPhysicalResourceDescriptor;
import combatant.client.render.engine.framegraph.FrameGraphTextureDescriptor;
import combatant.client.render.engine.rhi.shader.RhiTextureUsage;
import combatant.client.render.engine.rhi.shader.StorageAccess;

/** Deferred logical texture requirements lowered into a FrameGraphTextureDescriptor before planning. */
public record DeferredTextureSpec(
        GpuFormat format,
        ResolutionClass resolution,
        int fixedWidth,
        int fixedHeight,
        SamplePolicy samples,
        boolean mipChain,
        boolean storageImage,
        boolean renderAttachment
) implements DeferredPhysicalResourceSpec {
    /**
     * Semantic resolution classes. The class is stable graph ABI; the actual scale is runtime
     * policy and can change without replacing resource keys or pass contracts.
     */
    public enum ResolutionClass {
        FULL,
        /** Final presentation/output resolution; may differ from the world render resolution. */
        OUTPUT,
        /** HDR bloom working resolution, scaled from final output rather than render resolution. */
        BLOOM,
        /** Tile grid used by output-resolution velocity dilation for camera motion blur. */
        MOTION_TILE,
        SHADOW_OUTPUT,
        CONTACT_SHADOW_TRACE,
        AMBIENT_OCCLUSION,
        INDIRECT_LIGHT,
        REFLECTION_TRACE,
        REFLECTION_OUTPUT,
        REFLECTION_HISTORY,
        CLOUD_RENDER;

        public float scale(DeferredRuntimeConfig.Snapshot settings) {
            if (settings == null) settings = DeferredRuntimeConfig.current();
            return switch (this) {
                case FULL, OUTPUT -> 1.0f;
                case BLOOM -> DeferredPostConfig.current().bloomInitialScale();
                case MOTION_TILE -> 1.0f / DeferredCameraPostConfig.MOTION_TILE_SIZE;
                case SHADOW_OUTPUT -> settings.shadowOutputScale();
                case CONTACT_SHADOW_TRACE -> settings.contactShadowScale();
                case AMBIENT_OCCLUSION -> settings.ambientOcclusionScale();
                case INDIRECT_LIGHT -> settings.indirectLightScale();
                case REFLECTION_TRACE -> settings.reflectionTraceScale();
                case REFLECTION_OUTPUT -> settings.reflectionOutputScale();
                case REFLECTION_HISTORY -> settings.reflectionHistoryScale();
                case CLOUD_RENDER -> DeferredCloudConfig.current().renderScale();
            };
        }

        public int width(int renderWidth, int outputWidth, DeferredRuntimeConfig.Snapshot settings) {
            if (this == OUTPUT) return Math.max(1, outputWidth > 0 ? outputWidth : renderWidth);
            if (this == BLOOM || this == MOTION_TILE) {
                int base = outputWidth > 0 ? outputWidth : renderWidth;
                return scaledExtent(base, scale(settings));
            }
            return scaledExtent(renderWidth, scale(settings));
        }

        public int width(int fullWidth, DeferredRuntimeConfig.Snapshot settings) {
            return width(fullWidth, fullWidth, settings);
        }

        public int width(int fullWidth) {
            return width(fullWidth, DeferredRuntimeConfig.current());
        }

        public int height(int renderHeight, int outputHeight, DeferredRuntimeConfig.Snapshot settings) {
            if (this == OUTPUT) return Math.max(1, outputHeight > 0 ? outputHeight : renderHeight);
            if (this == BLOOM || this == MOTION_TILE) {
                int base = outputHeight > 0 ? outputHeight : renderHeight;
                return scaledExtent(base, scale(settings));
            }
            return scaledExtent(renderHeight, scale(settings));
        }

        public int height(int fullHeight, DeferredRuntimeConfig.Snapshot settings) {
            return height(fullHeight, fullHeight, settings);
        }

        public int height(int fullHeight) {
            return height(fullHeight, DeferredRuntimeConfig.current());
        }

        private static int scaledExtent(int fullExtent, float scale) {
            int extent = Math.max(1, fullExtent);
            float safeScale = Float.isFinite(scale) ? Math.max(0.01f, scale) : 1.0f;
            return Math.max(1, Math.round(extent * safeScale));
        }
    }

    public enum SamplePolicy {
        MATCH_SCENE,
        SINGLE_SAMPLE
    }

    public DeferredTextureSpec {
        if (format == null) throw new IllegalArgumentException("format");
        if (resolution == null) resolution = ResolutionClass.FULL;
        if (samples == null) samples = SamplePolicy.SINGLE_SAMPLE;
        boolean fixed = fixedWidth > 0 || fixedHeight > 0;
        if (fixed && (fixedWidth <= 0 || fixedHeight <= 0)) {
            throw new IllegalArgumentException("Fixed texture extent requires both width and height");
        }
        if (!fixed) {
            fixedWidth = 0;
            fixedHeight = 0;
        }
        if (storageImage && !format.hasColorAspect()) {
            throw new IllegalArgumentException("Storage image requires a color format: " + format);
        }
    }

    @Override
    public FrameGraphPhysicalResourceDescriptor descriptor(DeferredResource resource,
                                                           int renderWidth,
                                                           int renderHeight,
                                                           int outputWidth,
                                                           int outputHeight,
                                                           int sceneSamples,
                                                           DeferredRuntimeConfig.Snapshot settings) {
        int width = width(renderWidth, outputWidth, settings);
        int height = height(renderHeight, outputHeight, settings);
        int samples = this.samples == SamplePolicy.MATCH_SCENE ? Math.max(1, sceneSamples) : 1;
        int mipLevels = mipChain
                ? 32 - Integer.numberOfLeadingZeros(Math.max(width, height)) : 1;
        if (resource == DeferredResource.DEPTH_PYRAMID && settings != null
                && settings.depthPyramidMaxMipLevels() > 0) {
            mipLevels = Math.min(mipLevels, settings.depthPyramidMaxMipLevels());
        }
        if (samples > 1 && mipLevels > 1) {
            throw new IllegalStateException("Multisampled deferred resources cannot have mip chains: " + resource);
        }

        int usage = GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST;
        if (storageImage) usage |= RhiTextureUsage.STORAGE_IMAGE;
        if (renderAttachment) usage |= GpuTexture.USAGE_RENDER_ATTACHMENT;
        return new FrameGraphTextureDescriptor(
                width, height, samples, mipLevels, format, usage, StorageAccess.READ_WRITE);
    }

    public int width(int renderWidth, int outputWidth, DeferredRuntimeConfig.Snapshot settings) {
        return fixedWidth > 0 ? fixedWidth : resolution.width(renderWidth, outputWidth, settings);
    }

    public int width(int fullWidth, DeferredRuntimeConfig.Snapshot settings) {
        return width(fullWidth, fullWidth, settings);
    }

    public int height(int renderHeight, int outputHeight, DeferredRuntimeConfig.Snapshot settings) {
        return fixedHeight > 0 ? fixedHeight : resolution.height(renderHeight, outputHeight, settings);
    }

    public int height(int fullHeight, DeferredRuntimeConfig.Snapshot settings) {
        return height(fullHeight, fullHeight, settings);
    }

    public boolean fixedExtent() {
        return fixedWidth > 0;
    }

    public static DeferredTextureSpec attachment(GpuFormat format, SamplePolicy samples) {
        return new DeferredTextureSpec(format, ResolutionClass.FULL, 0, 0, samples, false, false, true);
    }

    public static DeferredTextureSpec compute(GpuFormat format, ResolutionClass resolution, boolean mipChain) {
        return new DeferredTextureSpec(
                format, resolution, 0, 0, SamplePolicy.SINGLE_SAMPLE, mipChain, true, false
        );
    }

    public static DeferredTextureSpec computeAttachment(GpuFormat format, ResolutionClass resolution) {
        return new DeferredTextureSpec(
                format, resolution, 0, 0, SamplePolicy.SINGLE_SAMPLE, false, true, true
        );
    }

    public static DeferredTextureSpec fixedCompute(GpuFormat format, int width, int height, boolean mipChain) {
        return new DeferredTextureSpec(
                format, ResolutionClass.FULL, Math.max(1, width), Math.max(1, height),
                SamplePolicy.SINGLE_SAMPLE, mipChain, true, false
        );
    }
}
