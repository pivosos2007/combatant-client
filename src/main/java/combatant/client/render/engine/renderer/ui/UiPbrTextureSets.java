/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.*;
import combatant.client.render.engine.renderer.MeshRenderer;
import combatant.client.render.engine.renderer.ui.draw.UiSurfaceMaterialSpec.Mode;
import combatant.client.util.resources.asset.AssetLoad;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.util.EnumMap;
import java.util.EnumSet;

/** Lazily loads the independent, repeat-wrapped PBR texture set for each UI surface. */
public final class UiPbrTextureSets {
    private static final EnumMap<Mode, Binding> LOADED = new EnumMap<>(Mode.class);
    private UiPbrTextureSets() {}

    public record Binding(GpuTexture normal, GpuTexture color, GpuTextureView view,
                          GpuTextureView colorView, GpuSampler sampler) implements AutoCloseable {
        public void bind(MeshRenderer draw) {
            draw.sampler("u_MaterialMicro", view, sampler)
                    .sampler("u_MaterialColor", colorView, sampler);
        }
        @Override public void close() {
            view.close(); colorView.close(); normal.close(); color.close();
        }
    }

    public static @Nullable String directory(Mode mode) {
        if (mode == null) return null;
        return switch (mode) {
            case GRAPHITE -> "graphite";
            case SOFT_TOUCH -> "soft_touch";
            case SATIN_TITANIUM -> "satin_titanium";
            case BRUSHED_METAL -> "brushed_metal";
            case CERAMIC -> "ceramic";
            case ICE -> "ice";
            default -> null;
        };
    }

    public static Binding resolve(Mode mode) {
        if (directory(mode) == null) throw new IllegalArgumentException("No PBR set for " + mode);
        Binding cached = LOADED.get(mode);
        if (cached != null) return cached;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getResourceManager() == null)
            throw new IllegalStateException("UI PBR resources are not initialized");
        return load(mc.getResourceManager(), mode);
    }

    private static Binding load(ResourceManager resources, Mode mode) {
        RenderSystem.assertOnRenderThread();
        GpuTexture normal = null, color = null;
        GpuTextureView normalView = null, colorView = null;
        try {
            normal = upload(resources, directory(mode), "normal_rough_height");
            color = upload(resources, directory(mode), mode == Mode.ICE ? "color_ao" : "color_metal");
            normalView = RenderSystem.getDevice().createTextureView(normal);
            colorView = RenderSystem.getDevice().createTextureView(color);
            GpuSampler sampler = RenderSystem.getSamplerCache().getSampler(
                    AddressMode.REPEAT, AddressMode.REPEAT, FilterMode.LINEAR, FilterMode.LINEAR, true);
            Binding binding = new Binding(normal, color, normalView, colorView, sampler);
            LOADED.put(mode, binding);
            return binding;
        } catch (IOException | RuntimeException exception) {
            if (normalView != null) normalView.close();
            if (colorView != null) colorView.close();
            if (normal != null) normal.close();
            if (color != null) color.close();
            throw new IllegalStateException("Cannot load UI PBR material " + mode, exception);
        }
    }

    private static GpuTexture upload(ResourceManager resources, String directory, String map) throws IOException {
        boolean data16 = map.equals("normal_rough_height");
        Identifier id = Identifier.fromNamespaceAndPath("combatant",
                "textures/ui/pbr/" + directory + "/" + map + ".ktx");
        var resource = resources.getResource(id).orElseThrow(() -> new IOException("Missing " + id));
        UiMaterialTextureData data;
        try (var input = resource.open()) { data = UiMaterialTextureData.read(input); }
        if (data.componentBytes() != (data16 ? 2 : 1)) throw new IOException("Unexpected component precision: " + id);
        GpuTexture texture = RenderSystem.getDevice().createTexture("UI PBR " + directory + "/" + map,
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                data16 ? GpuFormat.RGBA16_UNORM : GpuFormat.RGBA8_UNORM, data.size(), data.size(), 1, data.mipLevels());
        boolean complete = false;
        try {
            for (int mip = 0; mip < data.mipLevels(); mip++) {
                var pixels = MemoryUtil.memAlloc(data.mip(mip).remaining());
                try {
                    int size = data.size() >> mip;
                    pixels.put(data.mip(mip)).flip();
                    RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, pixels, mip, 0, 0, 0, size, size);
                } finally { MemoryUtil.memFree(pixels); }
            }
            complete = true;
            return texture;
        } finally {
            if (!complete) texture.close();
        }
    }

    @AssetLoad(order = 210)
    public static void reload(ResourceManager resources) {
        EnumSet<Mode> requested = EnumSet.noneOf(Mode.class);
        requested.addAll(LOADED.keySet());
        close();
        for (Mode mode : requested) load(resources, mode);
    }

    public static void close() {
        LOADED.values().forEach(Binding::close);
        LOADED.clear();
    }
}
