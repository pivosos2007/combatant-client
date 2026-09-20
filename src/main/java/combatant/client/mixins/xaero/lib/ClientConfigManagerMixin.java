/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.xaero.lib;

import combatant.client.config.subsystem.MapUiConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.lib.client.config.ClientConfigManager;
import xaero.lib.common.config.option.ConfigOption;
import xaero.lib.common.config.profile.ConfigProfile;

@Pseudo
@Mixin(ClientConfigManager.class)
public abstract class ClientConfigManagerMixin {

    /**
     * Central client ownership boundary for XaeroLib server-enforced configuration. The default is
     * deny, so receiving a synced config channel alone never gives the server authority over local
     * map/radar settings.
     */
    @Inject(
            method = "shouldIgnoreServerEnforcement(Lxaero/lib/common/config/profile/ConfigProfile;Lxaero/lib/common/config/option/ConfigOption;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private <T> void combatant$denyServerEnforcementByDefault(ConfigProfile profile,
                                                               ConfigOption<T> option,
                                                               CallbackInfoReturnable<Boolean> cir) {
        if (!MapUiConfig.get().allowServerConfiguration()) {
            cir.setReturnValue(true);
        }
    }
}
