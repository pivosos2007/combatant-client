/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import net.minecraft.client.Minecraft;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.MovementInputEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.modules.movement.rules.MovementRules;

@ModuleInfo(
        id = "parkour",
        displayName = "Parkour",
        aliases = {"EdgeJump", "AutoEdgeJump"},
        category = ModuleCategory.MOVEMENT, subcategory = ModuleSubcategory.BASIC,
        description = "module.parkour.description")
public final class Parkour extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Float> jumpFactor = num("jump_factor", 0.01f, 0.001f, 0.3f);

    @EventHandler
    private void onMovementInput(MovementInputEvent event) {
        if (!isEnabled() || mc.player == null || mc.level == null) {
            return;
        }

        boolean moving = event.isForward() || event.isBackward() || event.isLeft() || event.isRight();
        boolean supportBelow = mc.level.getBlockCollisions(
                mc.player,
                mc.player.getBoundingBox()
                        .inflate(-jumpFactor.get(), 0.0, -jumpFactor.get())
                        .move(0.0, -0.99, 0.0)
        ).iterator().hasNext();

        if (MovementRules.edgeJump(mc.player.onGround(), event.isSneak(), event.isJump(), moving,
                supportBelow, mc.player.getDeltaMovement().y)) {
            event.setJump(true);
        }
    }
}
