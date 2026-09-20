/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.xaero.minimap;

import combatant.client.config.subsystem.MapUiConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.common.minimap.mcworld.MinimapClientWorldData;
import xaero.hud.packet.basic.ClientboundRulesPacket;

@Pseudo
@Mixin(MinimapClientWorldData.class)
public abstract class MinimapClientWorldDataMixin {

    @Unique
    private static final ClientboundRulesPacket COMBATANT$PERMISSIVE_RULES =
            new ClientboundRulesPacket(true, true, true);

    /**
     * Older servers use this packet instead of XaeroLib's synced config channel. Gate the read,
     * not just packet receipt, so turning server configuration permission off takes effect
     * immediately even when restrictive rules were received earlier in the session.
     */
    @Inject(method = "getSyncedRules()Lxaero/hud/packet/basic/ClientboundRulesPacket;", at = @At("HEAD"), cancellable = true)
    private void combatant$ignoreLegacyServerRules(CallbackInfoReturnable<ClientboundRulesPacket> cir) {
        if (!MapUiConfig.get().allowServerConfiguration()) {
            cir.setReturnValue(COMBATANT$PERMISSIVE_RULES);
        }
    }
}
