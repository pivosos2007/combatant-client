/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import combatant.client.features.gui.hud.nondraggable.impl.BetterTooltips;
import combatant.client.util.item.IllegalItemUtil;
import combatant.client.util.item.RarityColorUtil;
import combatant.client.util.item.TopEnchantUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mixin(ItemStack.class)
public class ItemStackMixin {

    @Unique
    private static Component recolor(Component text, int argb) {
        int rgb = argb & 0x00FFFFFF;
        return text.copy().setStyle(text.getStyle().withColor(rgb));
    }

    @Unique
    private static int argbFrom(float[] rgba) {
        if (rgba == null || rgba.length < 3) return 0xFFFFFFFF;
        float r = rgba[0], g = rgba[1], b = rgba[2];
        float a = rgba.length > 3 ? rgba[3] : 1f;
        int ai = (int) (a * 255) & 0xFF;
        int ri = (int) (r * 255) & 0xFF;
        int gi = (int) (g * 255) & 0xFF;
        int bi = (int) (b * 255) & 0xFF;
        return (ai << 24) | (ri << 16) | (gi << 8) | bi;
    }

    @Inject(method = "getTooltipLines", at = @At("RETURN"), cancellable = true)
    private void combatant$colorizeTooltip(Item.TooltipContext tooltipContext, @Nullable Player player, TooltipFlag type, CallbackInfoReturnable<List<Component>> cir) {
        ItemStack self = (ItemStack) (Object) this;
        // Item tooltip scheduling often reaches GuiGraphicsExtractor through the generic
        // lines+optional-image overload. Capture the producing stack independently from text
        // colorization so the central tooltip hook can still select the item renderer.
        BetterTooltips.setLastTooltipStack(self);

        BetterTooltips tooltips = BetterTooltips.get();
        if (tooltips == null || !tooltips.isItemInfoColorizeEnabled()) return;

        List<Component> original = cir.getReturnValue();
        if (original == null || original.isEmpty()) return;

        List<Component> modified = new ArrayList<>(original);

        int illegalColor = IllegalItemUtil.illegalColor();
        int topColor = TopEnchantUtil.topColor();
        int rarityColor = argbFrom(RarityColorUtil.INSTANCE.getRarityColor(self));

        Set<String> illegalLabels = new HashSet<>();
        Set<String> topLabels = new HashSet<>();
        IllegalItemUtil.collectEnchants(self, (label, overMax) -> {
            if (overMax) illegalLabels.add(label.trim());
        });
        TopEnchantUtil.collectTopEnchants(self, (label, isTop) -> {
            if (isTop) topLabels.add(label.trim());
        });

        boolean hasIllegal = !illegalLabels.isEmpty();
        boolean hasTop = !topLabels.isEmpty();

        if (!modified.isEmpty()) {
            int nameColor = hasIllegal ? illegalColor : (hasTop ? topColor : rarityColor);
            modified.set(0, recolor(modified.get(0), nameColor));
        }

        for (int i = 1; i < modified.size(); i++) {
            Component t = modified.get(i);
            String trimmed = t.getString().trim();
            if (illegalLabels.contains(trimmed)) {
                modified.set(i, recolor(t, illegalColor));
                continue;
            }
            if (topLabels.contains(trimmed)) {
                modified.set(i, recolor(t, topColor));
                continue;
            }
            if (hasIllegal && t.getStyle().getColor() == null) {
                modified.set(i, recolor(t, illegalColor));
            }
        }

        cir.setReturnValue(modified);
    }
}




