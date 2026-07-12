/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import combatant.client.mixininterface.IItemEntityRenderState;

@Mixin(ItemEntityRenderState.class)
public class ItemEntityRenderStateMixin implements IItemEntityRenderState {
    @Unique
    private boolean combatant$onGround;

    @Override
    public boolean combatant$isOnGround() {
        return combatant$onGround;
    }

    @Override
    public void combatant$setOnGround(boolean onGround) {
        combatant$onGround = onGround;
    }
}
