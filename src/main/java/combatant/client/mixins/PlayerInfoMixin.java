/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import combatant.client.features.gui.hud.nondraggable.impl.tab.CustomTabList;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.misc.BetterMinecraft;
import combatant.client.features.relations.CategoryService;
import combatant.client.features.relations.CategoryType;

@Mixin(PlayerInfo.class)
public abstract class PlayerInfoMixin {

    @Shadow
    public abstract GameProfile getProfile();

    @Inject(method = "getTabListDisplayName", at = @At("RETURN"), cancellable = true)
    private void injectColorIntoTab(CallbackInfoReturnable<Component> cir) {
        if (CustomTabList.shouldReplaceVanilla()) return;

        BetterMinecraft module = Modules.get(BetterMinecraft.class);
        if (module == null || !module.isTablistRelationsEnabled()) return;

        Component original = cir.getReturnValue();
        if (original == null) return;

        GameProfile profile = getProfile();
        if (profile == null) return;

        String name = profile.name();
        if (name == null) return;

        // category (friend/enemy/staff/default)
        CategoryType type = CategoryService.get(name);

        if (type == CategoryType.DEFAULT)
            return;

        int color = CategoryService.getColor(name);

        MutableComponent modified = recolorNameOnly(original, name, color);

        cir.setReturnValue(modified);
    }

    @Unique
    private MutableComponent recolorNameOnly(Component text, String name, int color) {

        ComponentContents content = text.getContents();
        MutableComponent result;

        if (content instanceof PlainTextContents plain) {

            String raw = plain.text();
            String rawLower = raw.toLowerCase();
            String nameLower = name.toLowerCase();

            int idx = rawLower.indexOf(nameLower);

            if (idx != -1) {
                // raw = before + name + after
                String before = raw.substring(0, idx);
                String mid = raw.substring(idx, idx + name.length());
                String after = raw.substring(idx + name.length());

                result = Component.empty();

                if (!before.isEmpty())
                    result.append(Component.literal(before).withStyle(text.getStyle()));

                result.append(
                        Component.literal(mid)
                                .withStyle(style -> style.withColor(color))
                );

                if (!after.isEmpty())
                    result.append(Component.literal(after).withStyle(text.getStyle()));

            } else {
                result = Component.literal(raw).withStyle(text.getStyle());
            }

        } else {
            result = MutableComponent.create(content).withStyle(text.getStyle());
        }

        for (Component child : text.getSiblings()) {
            result.append(recolorNameOnly(child, name, color));
        }

        return result;
    }
}
