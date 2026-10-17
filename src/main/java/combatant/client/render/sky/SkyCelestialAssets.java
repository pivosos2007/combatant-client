/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.sky;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import combatant.client.render.engine.TextureStorage;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.resources.asset.AssetLoad;
import combatant.client.util.resources.asset.AssetLoadPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Resource-managed lunar texture bindings for the production sky renderer.
 *
 * The Minecraft TextureManager owns the textures and uploads; this class owns
 * only references, refreshed by the generated @AssetLoad lifecycle. In
 * particular, the sky render loop does not load or register texture resources.
 */
public enum SkyCelestialAssets {
    ;

    private static volatile LunarMaps lunarMaps;

    @AssetLoad(value = {AssetLoadPhase.INITIALIZE, AssetLoadPhase.RELOAD}, order = 210)
    public static void load(ResourceManager resources) {
        // Clear references before reading new resource-pack state. No manual GPU
        // disposal here: TextureManager owns the underlying textures.
        lunarMaps = null;
        if (resources == null || RenderSystem.tryGetDevice() == null) return;
        if (resources.getResource(TextureStorage.SKY_MOON_ALBEDO).isEmpty()
                || resources.getResource(TextureStorage.SKY_MOON_NORMAL).isEmpty()) {
            DebugLog.warnOnce("reimagined-sky-moon-missing",
                    "Sky lunar albedo/normal resource missing; using vanilla sky fallback");
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;

        // The TextureStorage @TextureCatalog/@AssetLoad hook preloads these on
        // RELOAD (order 200). INITIALIZE can run before the first resource reload,
        // so resolving the cached TextureManager entries here also handles startup.
        TextureManager textures = minecraft.getTextureManager();
        AbstractTexture albedo = textures.getTexture(TextureStorage.SKY_MOON_ALBEDO);
        AbstractTexture normals = textures.getTexture(TextureStorage.SKY_MOON_NORMAL);
        if (!ready(albedo) || !ready(normals)) {
            DebugLog.warnOnce("reimagined-sky-moon-not-ready",
                    "Sky lunar albedo/normal not GPU-ready; using vanilla sky fallback");
            return;
        }
        lunarMaps = new LunarMaps(albedo, normals);
    }

    /** Cached, manager-owned textures; null when not loaded or not GPU-ready. */
    public static LunarMaps lunarMaps() {
        LunarMaps maps = lunarMaps;
        return maps != null && ready(maps.albedo()) && ready(maps.normals()) ? maps : null;
    }

    private static boolean ready(AbstractTexture texture) {
        return texture != null && texture.getTextureView() != null && texture.getSampler() != null;
    }

    public record LunarMaps(AbstractTexture albedo, AbstractTexture normals) {
        public GpuTextureView albedoView() { return albedo.getTextureView(); }
        public GpuSampler albedoSampler() { return albedo.getSampler(); }
        public GpuTextureView normalView() { return normals.getTextureView(); }
        public GpuSampler normalSampler() { return normals.getSampler(); }
    }
}
