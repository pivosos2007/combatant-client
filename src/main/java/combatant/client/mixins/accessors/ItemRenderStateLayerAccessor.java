/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.accessors;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface ItemRenderStateLayerAccessor {

    @Accessor("renderType")
    RenderType pvs$getRenderLayer();

    @Accessor("quads")
    List<BakedQuad> pvs$getQuads();

    @Accessor("tintLayers")
    int[] pvs$getTints();

    @Accessor("foilType")
    ItemStackRenderState.FoilType pvs$getGlint();

    @Accessor("transform")
    ItemTransform pvs$getTransform();

    @Accessor("specialRenderer")
    SpecialModelRenderer<Object> pvs$getSpecialModelType();

    @Accessor("argumentForSpecialRendering")
    Object pvs$getData();
}


