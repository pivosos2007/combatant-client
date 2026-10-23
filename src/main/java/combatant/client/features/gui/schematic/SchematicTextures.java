/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.schematic;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import combatant.client.util.schematic.Schematic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Block-atlas sprites for the schematic previews. Every block state is resolved once through the vanilla block
 * model (the quads on each side of the block, with the particle sprite as fallback) into atlas UVs plus a fixed
 * tint, and cached; the 3D view and the 2D grid then only read flat arrays. The cache drops itself when the model
 * set is rebuilt (resource reload). Render thread only.
 *
 * <p>A face can carry two layers (grass sides are a dirt quad plus a tinted overlay quad); layer 0 is the first
 * quad on that side, layer 1 the last one when it uses a different sprite.
 */
final class SchematicTextures {

    static final int FACES = 6;
    static final int LAYERS = 2;
    static final int WHITE = 0xFFFFFFFF;

    /** Face directions in {@code FaceList} order: UP, DOWN, EAST, WEST, SOUTH, NORTH. */
    private static final Direction[] DIRECTIONS = {
            Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH
    };
    private static final int WATER_TINT = 0xB43F76E4;
    private static final int GRASS_TINT = 0xFF79C05A;
    private static final int FOLIAGE_TINT = 0xFF48B518;
    private static final Identifier WATER_SPRITE = Identifier.withDefaultNamespace("block/water_still");
    private static final Identifier LAVA_SPRITE = Identifier.withDefaultNamespace("block/lava_still");
    /** Slot of the up face's base layer (see {@link Entry}); the 2D grid draws blocks from it. */
    static final int UP_SLOT = 0;

    /** Resolved look of one block state; slot is {@code direction * LAYERS + layer}. */
    static final class Entry {
        final float[] uv = new float[FACES * LAYERS * 4];
        final int[] tint = new int[FACES * LAYERS];
        final boolean[] has = new boolean[FACES * LAYERS];
        /** True when at least the base layer of every face has a sprite. */
        boolean textured;
        /** Atlas-averaged colour of the top face with its tint applied, or 0 when unknown. */
        int average;
    }

    private static final Entry NONE = new Entry();
    private static final Map<BlockState, Entry> CACHE = new HashMap<>();
    private static final Map<Identifier, Integer> AVERAGES = new HashMap<>();
    private static final List<BlockStateModelPart> PARTS = new ArrayList<>();
    private static final List<BakedQuad> SIDE = new ArrayList<>();
    private static Object modelSetOwner;
    private static AbstractTexture atlasOwner;
    private static int generation;
    /** Sprite of the top face base layer of the entry being resolved; feeds its average colour. */
    private static TextureAtlasSprite topSprite;
    private static Field imageField;
    private static boolean imageFieldTried;

    private SchematicTextures() {
    }

    /** Changes whenever cached UVs became invalid; compare against the value an array was built under. */
    static int generation() {
        validate();
        return generation;
    }

    /** The block atlas view to draw with, or null while it is not loaded. */
    static GpuTextureView view() {
        return atlasOwner == null ? null : atlasOwner.getTextureView();
    }

    static GpuSampler sampler() {
        return atlasOwner == null ? null : atlasOwner.getSampler();
    }

    private static void validate() {
        Minecraft mc = Minecraft.getInstance();
        Object models = mc == null || mc.getModelManager() == null ? null : mc.getModelManager().getBlockStateModelSet();
        AbstractTexture atlas = mc == null || mc.getTextureManager() == null ? null
                : mc.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        if (models == modelSetOwner && atlas == atlasOwner) return;
        modelSetOwner = models;
        atlasOwner = atlas;
        CACHE.clear();
        AVERAGES.clear();
        generation++;
    }

    /** One entry per palette index of the schematic (index 0, air, and unresolved states share an untextured one). */
    static Entry[] resolvePalette(Schematic schematic) {
        validate();
        Entry[] entries = new Entry[schematic.paletteSize()];
        entries[0] = NONE;
        for (int i = 1; i < entries.length; i++) entries[i] = of(schematic.paletteState(i));
        return entries;
    }

    private static Entry of(BlockState state) {
        Entry cached = CACHE.get(state);
        if (cached != null) return cached;
        Entry resolved = resolve(state);
        CACHE.put(state, resolved);
        return resolved;
    }

    private static Entry resolve(BlockState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getModelManager() == null || !(atlasOwner instanceof TextureAtlas atlas)) return NONE;
        try {
            Entry entry = new Entry();
            topSprite = null;
            BlockStateModelSet set = mc.getModelManager().getBlockStateModelSet();
            BlockStateModel model = set.get(state);
            PARTS.clear();
            if (model != null) model.collectParts(RandomSource.create(42L), PARTS);
            boolean any = false;
            for (int d = 0; d < FACES; d++) any |= fillFace(entry, state, d);
            TextureAtlasSprite particle = null;
            if (!any) {
                particle = fluidSprite(atlas, state);
                if (particle != null) {
                    int tint = state.getFluidState().is(FluidTags.WATER) ? WATER_TINT : WHITE;
                    for (int d = 0; d < FACES; d++) set(entry, d, 0, particle, tint);
                } else if (model != null) {
                    particle = model.particleMaterial().sprite();
                    if (particle == null || particle == atlas.missingSprite()) particle = null;
                }
            } else if (model != null) {
                particle = model.particleMaterial().sprite();
            }
            // Sides the model has no quad for (slab tops, stair backs...) borrow the particle sprite.
            if (particle != null) {
                for (int d = 0; d < FACES; d++) {
                    if (!entry.has[d * LAYERS]) set(entry, d, 0, particle, WHITE);
                }
            }
            entry.textured = true;
            for (int d = 0; d < FACES; d++) entry.textured &= entry.has[d * LAYERS];
            if (entry.has[0]) entry.average = multiply(average(topSprite), entry.tint[0]);
            return entry;
        } catch (RuntimeException e) {
            return NONE;
        } finally {
            PARTS.clear();
            SIDE.clear();
        }
    }

    private static boolean fillFace(Entry entry, BlockState state, int d) {
        Direction side = DIRECTIONS[d];
        SIDE.clear();
        for (BlockStateModelPart part : PARTS) {
            SIDE.addAll(part.getQuads(side));
            for (BakedQuad quad : part.getQuads(null)) {
                if (quad.direction() == side) SIDE.add(quad);
            }
        }
        if (SIDE.isEmpty()) return false;
        BakedQuad base = SIDE.get(0);
        set(entry, d, 0, base.materialInfo().sprite(), tintOf(state, base));
        BakedQuad top = SIDE.get(SIDE.size() - 1);
        if (top != base && top.materialInfo().sprite() != base.materialInfo().sprite()) {
            set(entry, d, 1, top.materialInfo().sprite(), tintOf(state, top));
        }
        return true;
    }

    private static void set(Entry entry, int d, int layer, TextureAtlasSprite sprite, int tint) {
        int slot = d * LAYERS + layer;
        // Pull the UVs in a hair so linear filtering and mips never bleed the neighbouring sprite in.
        float inset = 0.02f;
        float du = (sprite.getU1() - sprite.getU0()) * inset, dv = (sprite.getV1() - sprite.getV0()) * inset;
        entry.uv[slot * 4] = sprite.getU0() + du;
        entry.uv[slot * 4 + 1] = sprite.getV0() + dv;
        entry.uv[slot * 4 + 2] = sprite.getU1() - du;
        entry.uv[slot * 4 + 3] = sprite.getV1() - dv;
        entry.tint[slot] = tint;
        entry.has[slot] = true;
        if (slot == 0) topSprite = sprite;
    }


    private static TextureAtlasSprite fluidSprite(TextureAtlas atlas, BlockState state) {
        if (state.getFluidState().isEmpty()) return null;
        Identifier id = state.getFluidState().is(FluidTags.WATER) ? WATER_SPRITE
                : state.getFluidState().is(FluidTags.LAVA) ? LAVA_SPRITE : null;
        if (id == null) return null;
        TextureAtlasSprite sprite = atlas.getSprite(id);
        return sprite == null || sprite == atlas.missingSprite() ? null : sprite;
    }

    /** A fixed, world-independent tint: the block's own tint source for the state, or a biome-neutral green. */
    private static int tintOf(BlockState state, BakedQuad quad) {
        if (!quad.materialInfo().isTinted()) return WHITE;
        Minecraft mc = Minecraft.getInstance();
        int colour = 0;
        try {
            BlockTintSource source = mc.getBlockColors().getTintSource(state, quad.materialInfo().tintIndex());
            if (source != null) colour = source.color(state);
        } catch (RuntimeException ignored) {
            // World-bound tint sources cannot answer without a level.
        }
        if (colour == 0 || colour == -1) {
            String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
            return path.contains("leaves") || path.contains("vine") ? FOLIAGE_TINT : GRASS_TINT;
        }
        return 0xFF000000 | colour;
    }

    // ---------------------------------------------------------------- colour maths

    /** Channel-wise product of two ARGB colours. */
    static int multiply(int a, int b) {
        int al = ((a >>> 24) & 0xFF) * ((b >>> 24) & 0xFF) / 255;
        int r = ((a >>> 16) & 0xFF) * ((b >>> 16) & 0xFF) / 255;
        int g = ((a >>> 8) & 0xFF) * ((b >>> 8) & 0xFF) / 255;
        int bl = (a & 0xFF) * (b & 0xFF) / 255;
        return (al << 24) | (r << 16) | (g << 8) | bl;
    }

    // ---------------------------------------------------------------- averaging

    /** Alpha-weighted mean colour of the sprite's first frame, cached per sprite; 0 when the pixels are unreachable. */
    private static int average(TextureAtlasSprite sprite) {
        if (sprite == null) return 0;
        SpriteContents contents = sprite.contents();
        Integer known = AVERAGES.get(contents.name());
        if (known != null) return known;
        int mean = 0;
        NativeImage image = imageOf(contents);
        if (image != null) {
            int w = Math.min(contents.width(), image.getWidth()), h = Math.min(contents.height(), image.getHeight());
            long a = 0, r = 0, g = 0, b = 0;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int px = image.getPixel(x, y);
                    int alpha = (px >>> 24) & 0xFF;
                    a += alpha;
                    r += (long) ((px >>> 16) & 0xFF) * alpha;
                    g += (long) ((px >>> 8) & 0xFF) * alpha;
                    b += (long) (px & 0xFF) * alpha;
                }
            }
            if (a > 0) {
                long n = (long) w * h;
                mean = ((int) (a / n) << 24) | ((int) (r / a) << 16) | ((int) (g / a) << 8) | (int) (b / a);
            }
        }
        AVERAGES.put(contents.name(), mean);
        return mean;
    }

    private static NativeImage imageOf(SpriteContents contents) {
        if (!imageFieldTried) {
            imageFieldTried = true;
            try {
                Field field = SpriteContents.class.getDeclaredField("originalImage");
                field.setAccessible(true);
                imageField = field;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                imageField = null;
            }
        }
        if (imageField == null) return null;
        try {
            return (NativeImage) imageField.get(contents);
        } catch (IllegalAccessException | RuntimeException e) {
            return null;
        }
    }
}
