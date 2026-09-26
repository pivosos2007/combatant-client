/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.material;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/**
 * Exact producer-side material classification. Custom/addon content should use Combatant tags or
 * an explicit material descriptor. Vanilla class/identity checks are a final built-in fallback.
 */
public final class MaterialClassifier {
    private static final TagKey<Block> TAG_GLASS = blockTag("glass");
    private static final TagKey<Block> TAG_FOLIAGE = blockTag("foliage");
    private static final TagKey<Block> TAG_PORTAL = blockTag("portal");
    private static final TagKey<Block> TAG_EMISSIVE = blockTag("emissive");
    private static final TagKey<Block> TAG_CUTOUT = blockTag("cutout");
    private static final TagKey<Block> TAG_TRANSLUCENT = blockTag("translucent");
    private static final TagKey<Block> TAG_OPAQUE = blockTag("opaque");
    private static final TagKey<Fluid> TAG_WATER = fluidTag("water");
    private static final TagKey<Fluid> TAG_LAVA = fluidTag("lava");

    private MaterialClassifier() {
    }

    public static MaterialClassification classifyBlock(BlockState state, MaterialDomain passFallback) {
        if (state == null) return fallback(passFallback, MaterialResolutionSource.UNKNOWN);

        FluidState fluid = state.getFluidState();
        if (fluid != null && !fluid.isEmpty()) {
            MaterialClassification fluidClassification = classifyFluid(fluid);
            if (fluidClassification.domain() != MaterialDomain.UNKNOWN) return fluidClassification;
        }

        if (state.is(TAG_GLASS)) return glass(MaterialResolutionSource.TAG);
        if (state.is(TAG_PORTAL)) return portal(MaterialResolutionSource.TAG);
        if (state.is(TAG_FOLIAGE)) return foliage(MaterialResolutionSource.TAG);
        if (state.is(TAG_EMISSIVE)) return emissive(passFallback, MaterialResolutionSource.TAG);
        if (state.is(TAG_CUTOUT)) return cutout(MaterialResolutionSource.TAG);
        if (state.is(TAG_TRANSLUCENT)) return translucentSurface(MaterialResolutionSource.TAG);
        if (state.is(TAG_OPAQUE)) return opaque(MaterialResolutionSource.TAG);

        // Existing vanilla tags are exact producer metadata, not visual inference.
        if (state.is(BlockTags.PORTALS)) return portal(MaterialResolutionSource.TAG);
        if (state.is(BlockTags.LEAVES) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.CROPS)) {
            return foliage(MaterialResolutionSource.TAG);
        }

        Block block = state.getBlock();
        // Built-in fallback only. Modded glass should register combatant:material/glass.
        if (block == Blocks.GLASS || block == Blocks.GLASS_PANE || block == Blocks.TINTED_GLASS
                || block instanceof StainedGlassBlock || block instanceof StainedGlassPaneBlock
                || block instanceof TransparentBlock) {
            return glass(MaterialResolutionSource.VANILLA_FALLBACK);
        }

        int extraTraits = state.getLightEmission() > 0 ? MaterialTrait.EMISSIVE.bit() : 0;
        MaterialClassification base = fallback(passFallback, MaterialResolutionSource.VANILLA_FALLBACK);
        if (extraTraits == 0) return base;
        return new MaterialClassification(base.domain(), base.traitMask() | extraTraits, base.route(), base.source());
    }

    public static MaterialClassification classifyFluid(FluidState state) {
        if (state == null || state.isEmpty()) return MaterialClassification.UNKNOWN;
        Fluid fluid = state.getType();
        if (fluid.is(TAG_WATER) || fluid.is(FluidTags.WATER)) {
            return new MaterialClassification(
                    MaterialDomain.WATER,
                    MaterialTrait.mask(MaterialTrait.FLUID, MaterialTrait.TRANSMISSIVE, MaterialTrait.REFRACTIVE),
                    MaterialSubmissionRoute.FORWARD_SPECIAL,
                    fluid.is(TAG_WATER) ? MaterialResolutionSource.TAG : MaterialResolutionSource.VANILLA_FALLBACK
            );
        }
        if (fluid.is(TAG_LAVA) || fluid.is(FluidTags.LAVA)) {
            return new MaterialClassification(
                    MaterialDomain.LAVA,
                    MaterialTrait.mask(MaterialTrait.FLUID, MaterialTrait.EMISSIVE),
                    MaterialSubmissionRoute.FORWARD_SPECIAL,
                    fluid.is(TAG_LAVA) ? MaterialResolutionSource.TAG : MaterialResolutionSource.VANILLA_FALLBACK
            );
        }
        return new MaterialClassification(
                MaterialDomain.TRANSLUCENT,
                MaterialTrait.FLUID.bit(),
                MaterialSubmissionRoute.FORWARD_TRANSLUCENT,
                MaterialResolutionSource.UNKNOWN
        );
    }

    public static MaterialClassification fallback(MaterialDomain domain, MaterialResolutionSource source) {
        MaterialDomain resolved = domain == null ? MaterialDomain.UNKNOWN : domain;
        return switch (resolved) {
            case OPAQUE -> opaque(source);
            case CUTOUT -> cutout(source);
            case TRANSLUCENT -> translucentSurface(source);
            case WATER -> new MaterialClassification(MaterialDomain.WATER,
                    MaterialTrait.mask(MaterialTrait.FLUID, MaterialTrait.TRANSMISSIVE, MaterialTrait.REFRACTIVE),
                    MaterialSubmissionRoute.FORWARD_SPECIAL, source);
            case LAVA -> new MaterialClassification(MaterialDomain.LAVA,
                    MaterialTrait.mask(MaterialTrait.FLUID, MaterialTrait.EMISSIVE),
                    MaterialSubmissionRoute.FORWARD_SPECIAL, source);
            case GLASS -> glass(source);
            case PORTAL -> portal(source);
            case UNKNOWN -> MaterialClassification.UNKNOWN;
        };
    }

    private static MaterialClassification opaque(MaterialResolutionSource source) {
        return new MaterialClassification(MaterialDomain.OPAQUE, 0, MaterialSubmissionRoute.DEFERRED_GBUFFER, source);
    }

    private static MaterialClassification cutout(MaterialResolutionSource source) {
        return new MaterialClassification(MaterialDomain.CUTOUT, 0, MaterialSubmissionRoute.DEFERRED_GBUFFER, source);
    }

    private static MaterialClassification translucentSurface(MaterialResolutionSource source) {
        return new MaterialClassification(MaterialDomain.TRANSLUCENT,
                MaterialTrait.TRANSMISSIVE.bit(), MaterialSubmissionRoute.FORWARD_TRANSLUCENT, source);
    }

    private static MaterialClassification glass(MaterialResolutionSource source) {
        return new MaterialClassification(MaterialDomain.GLASS,
                MaterialTrait.mask(MaterialTrait.GLASS, MaterialTrait.TRANSMISSIVE, MaterialTrait.REFRACTIVE),
                MaterialSubmissionRoute.FORWARD_SPECIAL, source);
    }

    private static MaterialClassification foliage(MaterialResolutionSource source) {
        return new MaterialClassification(MaterialDomain.CUTOUT,
                MaterialTrait.mask(MaterialTrait.FOLIAGE, MaterialTrait.DOUBLE_SIDED),
                MaterialSubmissionRoute.DEFERRED_GBUFFER, source);
    }

    private static MaterialClassification portal(MaterialResolutionSource source) {
        return new MaterialClassification(MaterialDomain.PORTAL,
                MaterialTrait.mask(MaterialTrait.PORTAL, MaterialTrait.EMISSIVE),
                MaterialSubmissionRoute.FORWARD_SPECIAL, source);
    }

    private static MaterialClassification emissive(MaterialDomain fallback, MaterialResolutionSource source) {
        MaterialClassification base = fallback(fallback, source);
        return new MaterialClassification(base.domain(), base.traitMask() | MaterialTrait.EMISSIVE.bit(), base.route(), source);
    }

    private static TagKey<Block> blockTag(String path) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("combatant", "material/" + path));
    }

    private static TagKey<Fluid> fluidTag(String path) {
        return TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath("combatant", "material/" + path));
    }
}
