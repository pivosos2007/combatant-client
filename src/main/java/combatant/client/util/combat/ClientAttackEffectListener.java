/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.combat;

import combatant.client.events.EventHandler;
import combatant.client.events.impl.ClientAttackEffectsEvent;
import combatant.client.features.gui.hud.draggable.impl.TargetHud;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.combat.Hitbox;
import combatant.client.features.module.modules.misc.HitSounds;
import combatant.client.features.module.modules.visuals.HitEffect;
import combatant.client.util.target.TargetManager;

/** Applies client-attack notifications after the regular attack event has been posted. */
public enum ClientAttackEffectListener {
    INSTANCE;

    @EventHandler
    private void onAttackEffects(ClientAttackEffectsEvent event) {
        var target = event.getTarget();
        Hitbox hitbox = Modules.get(Hitbox.class);
        if (hitbox != null) hitbox.markHit(target);

        HitSounds hitSounds = Modules.get(HitSounds.class);
        if (hitSounds != null) hitSounds.handleHit(target);

        HitEffect hitEffect = Modules.get(HitEffect.class);
        if (hitEffect != null) hitEffect.handleHit(target);

        TargetHud.notifyHit(target);
        TargetManager.onAttack(target);
    }
}
