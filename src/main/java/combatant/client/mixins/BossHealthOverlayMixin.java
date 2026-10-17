/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import combatant.client.events.Events;
import combatant.client.events.impl.CombatProtocolBossbarEvent;
import combatant.client.mixins.accessors.BossHealthOverlayAccessor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(BossHealthOverlay.class)
public class BossHealthOverlayMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void onRender(GuiGraphicsExtractor ctx, CallbackInfo ci) {
        if (Events.BUS.hasListeners(CombatProtocolBossbarEvent.class)) {
            List<String> names = new ArrayList<>();
            for (LerpingBossEvent bar : ((BossHealthOverlayAccessor) this).getBars().values()) {
                if (bar == null) continue;
                Component name = bar.getName();
                if (name != null && !name.getString().isBlank()) {
                    names.add(name.getString());
                }
            }
            if (!names.isEmpty()) {
                Events.BUS.post(new CombatProtocolBossbarEvent(names));
            }
        }
    }
}





