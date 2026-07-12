/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.accessors;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ScreenEffectRenderer.class)
public interface InGameOverlayRendererAccessor {

    @Invoker("submitFire")
    static void callRenderFireOverlay(PoseStack matrices,
                                      SubmitNodeCollector vertexConsumers,
                                      TextureAtlasSprite sprite) {
        throw new AssertionError();
    }

    @Invoker("submitBlockSprite")
    static void callRenderInWallOverlay(TextureAtlasSprite sprite,
                                        PoseStack matrices,
                                        SubmitNodeCollector vertexConsumers,
                                        int color) {
        throw new AssertionError();
    }
}


