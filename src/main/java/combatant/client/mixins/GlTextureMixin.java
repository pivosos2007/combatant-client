/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.mojang.blaze3d.opengl.GlTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.mixininterface.IMsaaTexture;
import combatant.client.render.engine.core.CombatantRenderSystem;

@Mixin(GlTexture.class)
public abstract class GlTextureMixin implements IMsaaTexture {
    @Final
    @Shadow
    protected int id;

    @Unique
    private int combatant$samples = 1;

    @Override
    public void combatant$setSamples(int samples) {
        combatant$samples = samples;
    }

    @Override
    public int combatant$getSamples() {
        return combatant$samples;
    }

    @Override
    public boolean combatant$isMsaa() {
        return combatant$samples > 1;
    }

    @Override
    public int combatant$getGlId() {
        return id;
    }

    @Inject(method = "destroyImmediately", at = @At("HEAD"))
    private void combatant$onFree(CallbackInfo ci) {
        CombatantRenderSystem.rhi().msaa().unregisterTexture(id);
    }
}




