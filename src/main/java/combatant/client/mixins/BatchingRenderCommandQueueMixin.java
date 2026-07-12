/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import combatant.client.render.ViewObstructionFadeContext;
import combatant.client.render.engine.msaa.MsaaWorldTarget;

import java.util.Arrays;

@Mixin(SubmitNodeCollection.class)
public abstract class BatchingRenderCommandQueueMixin {
    @Unique
    private static final int COMBATANT_VIEW_FADE_DITHER_MARK = 0x00010001;
    @Unique
    private static final int COMBATANT_VIEW_FADE_A2C_MARK = 0x00010100;

    @Unique
    private static int combatant$markViewObstructionFade(int argb, float alpha, boolean msaaActive) {
        int rgb = (argb & 0x00FEFEFE) | (msaaActive ? COMBATANT_VIEW_FADE_A2C_MARK : COMBATANT_VIEW_FADE_DITHER_MARK);
        int alphaByte = Math.round(255.0f * Mth.clamp(alpha, 0.0f, 1.0f));
        return rgb | ((alphaByte & 0xFF) << 24);
    }

    @Unique
    private static boolean combatant$shouldApplyViewObstructionFade() {
        return ViewObstructionFadeContext.isActive() && ViewObstructionFadeContext.alpha() < 0.99f;
    }

    @Unique
    private static int combatant$applyFadeToArgb(int color) {
        int baseColor = color == -1 ? 0xFFFFFFFF : ((color & 0x00FFFFFF) | 0xFF000000);
        return combatant$markViewObstructionFade(baseColor, ViewObstructionFadeContext.alpha(), MsaaWorldTarget.isActive());
    }

    @ModifyVariable(method = "submitModel", at = @At("HEAD"), argsOnly = true, index = 7)
    private int combatant$applyViewObstructionFadeAlpha(int tintedColor) {
        if (!combatant$shouldApplyViewObstructionFade()) {
            return tintedColor;
        }
        return combatant$applyFadeToArgb(tintedColor);
    }

    @ModifyVariable(method = "submitItem", at = @At("HEAD"), argsOnly = true, index = 6)
    private int[] combatant$applyViewObstructionFadeAlphaToItemTints(int[] tints) {
        if (!combatant$shouldApplyViewObstructionFade()) {
            return tints;
        }

        int sentinel = combatant$applyFadeToArgb(-1);
        if (tints == null || tints.length == 0) {
            return new int[]{sentinel};
        }

        int[] faded = Arrays.copyOf(tints, tints.length);
        faded[0] = combatant$applyFadeToArgb(faded[0]);
        for (int i = 1; i < faded.length; i++) {
            faded[i] = combatant$applyFadeToArgb(faded[i]);
        }
        return faded;
    }

}
