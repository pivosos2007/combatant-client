/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.iris.geometry;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import combatant.client.render.engine.asset.gltf.GltfRuntimeAsset;
import combatant.client.render.engine.asset.gltf.material.GltfMaterialBinding;
import combatant.client.render.engine.asset.gltf.model.GltfTexture;
import combatant.client.render.engine.asset.gltf.model.GltfTextureInfo;
import combatant.client.render.engine.rhi.CombatantRhi;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Stable Combatant-to-Iris material transport. Shaderpack patches translate this canonical data
 * into their own material/G-buffer ABI; Java does not know that ABI.
 */
final class IrisCanonicalMaterialAtlasCompiler {
    static final int HEADER_TEXELS = 96;
    private static final int USAGE = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING;

    private IrisCanonicalMaterialAtlasCompiler() {}

    static IrisCanonicalMaterialAtlas upload(CombatantRhi rhi, GltfRuntimeAsset asset,
                                             int materialIndex, String label) {
        RenderSystem.assertOnRenderThread();
        GltfMaterialBinding material = asset.material(materialIndex);
        Decoded[] maps = new Decoded[5];
        try {
            maps[0] = decode(asset, material.baseColorTexture(), 0xFFFFFFFF);
            maps[1] = decode(asset, material.normalTexture(), 0xFF8080FF);
            maps[2] = decode(asset, material.metallicRoughnessTexture(), 0xFFFFFFFF);
            maps[3] = decode(asset, material.occlusionTexture(), 0xFFFFFFFF);
            maps[4] = decode(asset, material.emissiveTexture(), 0xFF000000);
            int tile = 32;
            for (Decoded map : maps) tile = Math.max(tile, Math.max(map.image.getWidth(), map.image.getHeight()));
            int width = Math.max(HEADER_TEXELS, Math.multiplyExact(tile, 3));
            int height = Math.addExact(1, Math.multiplyExact(tile, 2));
            try (NativeImage atlas = new NativeImage(width, height, false)) {
                fill(atlas, 0xFF000000);
                for (int i = 0; i < maps.length; i++) {
                    blitScaled(maps[i].image, atlas, (i % 3) * tile, 1 + (i / 3) * tile, tile);
                }
                writeMetadata(atlas, material, maps);
                GpuTexture texture = RenderSystem.getDevice().createTexture(
                        label, USAGE, GpuFormat.RGBA8_UNORM, width, height, 1, 1);
                boolean success = false;
                try {
                    RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, atlas, 0, 0, 0, 0);
                    GpuSampler sampler = rhi.resources().samplers().get(
                            AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                            FilterMode.LINEAR, FilterMode.LINEAR, false);
                    var view = rhi.resources().samplers().createTextureView(texture);
                    success = true;
                    return new IrisCanonicalMaterialAtlas(texture, view, sampler);
                } finally {
                    if (!success) try { texture.close(); } catch (Throwable ignored) { }
                }
            }
        } finally {
            for (Decoded map : maps) if (map != null) map.image.close();
        }
    }

    private static Decoded decode(GltfRuntimeAsset asset, GltfTextureInfo info, int fallback) {
        if (info == null || !info.present()) {
            NativeImage image = new NativeImage(1, 1, false);
            image.setPixel(0, 0, fallback);
            return new Decoded(image, 10497, 10497);
        }
        GltfTexture texture = asset.asset().textures().get(info.texture());
        try {
            return new Decoded(NativeImage.read(new ByteArrayInputStream(bytes(texture.encodedImage()))),
                    texture.wrapS(), texture.wrapT());
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to decode Iris canonical material source", exception);
        }
    }

    private static void writeMetadata(NativeImage atlas, GltfMaterialBinding material, Decoded[] maps) {
        int cursor = 0;
        putFloat(atlas, cursor++, 20260927.0f); // contract version marker
        for (float value : material.baseColorFactor()) putFloat(atlas, cursor++, value);
        float[] emissive = material.emissiveFactor();
        putFloat(atlas, cursor++, emissive[0]);
        putFloat(atlas, cursor++, emissive[1]);
        putFloat(atlas, cursor++, emissive[2]);
        putFloat(atlas, cursor++, material.emissiveStrength());
        putFloat(atlas, cursor++, material.normalScale());
        putFloat(atlas, cursor++, material.occlusionStrength());
        putFloat(atlas, cursor++, material.surface().roughness());
        putFloat(atlas, cursor++, material.surface().metallic());
        putFloat(atlas, cursor++, material.surface().dielectricF0());
        putFloat(atlas, cursor++, switch (material.alphaMode()) {
            case OPAQUE -> 0.0f;
            case MASK -> 1.0f;
            case BLEND -> 2.0f;
        });
        putFloat(atlas, cursor++, material.alphaCutoff());
        putFloat(atlas, cursor++, material.unlit() ? 1.0f : 0.0f);

        GltfTextureInfo[] infos = {
                material.baseColorTexture(), material.normalTexture(), material.metallicRoughnessTexture(),
                material.occlusionTexture(), material.emissiveTexture()
        };
        for (GltfTextureInfo nullable : infos) {
            GltfTextureInfo info = nullable == null ? GltfTextureInfo.absent() : nullable;
            float cosine = (float) Math.cos(info.rotation());
            float sine = (float) Math.sin(info.rotation());
            putFloat(atlas, cursor++, cosine * info.scaleU());
            putFloat(atlas, cursor++, -sine * info.scaleV());
            putFloat(atlas, cursor++, info.offsetU());
            putFloat(atlas, cursor++, sine * info.scaleU());
            putFloat(atlas, cursor++, cosine * info.scaleV());
            putFloat(atlas, cursor++, info.offsetV());
        }
        for (int i = 0; i < infos.length; i++) {
            GltfTextureInfo info = infos[i] == null ? GltfTextureInfo.absent() : infos[i];
            putFloat(atlas, cursor++, info.texCoord());
            putFloat(atlas, cursor++, wrapCode(maps[i].wrapS));
            putFloat(atlas, cursor++, wrapCode(maps[i].wrapT));
        }
        if (cursor > HEADER_TEXELS) throw new IllegalStateException("Iris material header overflow");
    }

    private static float wrapCode(int gltfWrap) {
        return switch (gltfWrap) {
            case 33071 -> 0.0f;
            case 33648 -> 1.0f;
            default -> 2.0f;
        };
    }
    private static void putFloat(NativeImage image, int x, float value) {
        int bits = Float.floatToRawIntBits(value);
        image.setPixel(x, 0, argb((bits >>> 24) & 255, bits & 255,
                (bits >>> 8) & 255, (bits >>> 16) & 255));
    }
    private static void blitScaled(NativeImage source, NativeImage target, int ox, int oy, int size) {
        for (int y = 0; y < size; y++) {
            int sy = Math.min(source.getHeight() - 1, (int) ((long) y * source.getHeight() / size));
            for (int x = 0; x < size; x++) {
                int sx = Math.min(source.getWidth() - 1, (int) ((long) x * source.getWidth() / size));
                target.setPixel(ox + x, oy + y, source.getPixel(sx, sy));
            }
        }
    }
    private static void fill(NativeImage image, int color) {
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) image.setPixel(x, y, color);
    }
    private static byte[] bytes(ByteBuffer source) {
        ByteBuffer copy = source.duplicate();
        byte[] out = new byte[copy.remaining()];
        copy.get(out);
        return out;
    }
    private static int argb(int a, int r, int g, int b) {
        return ((a & 255) << 24) | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255);
    }
    private record Decoded(NativeImage image, int wrapS, int wrapT) {}
}
