/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemCooldowns;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.combat.TPSSync;
import combatant.client.features.module.modules.movement.Timer;
import combatant.client.mixins.accessors.ItemCooldownEntryAccessor;

import java.util.Iterator;
import java.util.Map;

@Mixin(ItemCooldowns.class)
public abstract class ItemCooldownsMixin {

    @Shadow
    private int tickCount;
    @Shadow
    @Final
    private Map<Identifier, ?> cooldowns;
    @Unique
    private float combatant$timerRemainder = 0f;

    @Shadow
    protected abstract void onCooldownEnded(Identifier groupId);

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void combatant$timerCompensate(CallbackInfo ci) {
        float mult = Timer.getTickTimer();
        float serverDelta = 1.0f;
        TPSSync tps = Modules.get(TPSSync.class);
        if (tps != null && tps.isEnabled()) {
            serverDelta = tps.getServerTickDelta();
        }

        float scale = (mult <= 0.0001f) ? 1.0f : (serverDelta / mult);
        if (scale > 1.0f) scale = 1.0f;
        if (scale < 0.0f) scale = 0.0f;

        if (Math.abs(scale - 1.0f) < 0.0001f) {
            return;
        }

        float step = scale;
        combatant$timerRemainder += step;
        int inc = (int) combatant$timerRemainder;
        if (inc <= 0) {
            ci.cancel();
            return;
        }
        combatant$timerRemainder -= inc;

        tickCount += inc;
        if (!cooldowns.isEmpty()) {
            @SuppressWarnings("unchecked")
            Iterator<Map.Entry<Identifier, Object>> iterator =
                    ((Map<Identifier, Object>) cooldowns).entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Identifier, Object> entry = iterator.next();
                Object value = entry.getValue();
                if (value instanceof ItemCooldownEntryAccessor accessor && accessor.getEndTick() <= tickCount) {
                    iterator.remove();
                    onCooldownEnded(entry.getKey());
                }
            }
        }

        ci.cancel();
    }
}
