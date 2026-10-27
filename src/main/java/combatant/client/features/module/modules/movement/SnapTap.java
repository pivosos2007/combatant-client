/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.module.modules.movement;

import combatant.client.config.values.BooleanValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.MovementInputEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;

/**
 * SOCD "last input wins" cleaning: when opposite keys are held together the most recently pressed one stays active
 * instead of both cancelling out.
 */
@ModuleInfo(
        id = "snaptap",
        displayName = "SnapTap",
        aliases = {"SOCD", "LastInputPriority"},
        category = ModuleCategory.MOVEMENT,
        subcategory = ModuleSubcategory.BASIC,
        description = "module.snaptap.description")
public final class SnapTap extends Module {

    private final BooleanValue horizontal = bool("snapTapHorizontal", "horizontal", true);
    private final BooleanValue vertical = bool("snapTapVertical", "vertical", true);

    private boolean prevLeft;
    private boolean prevRight;
    private boolean prevForward;
    private boolean prevBackward;
    /** True when the second key of the pair (right / backward) was pressed last. */
    private boolean rightLast;
    private boolean backwardLast;

    @Override
    public void onDisable() {
        prevLeft = prevRight = prevForward = prevBackward = false;
        rightLast = backwardLast = false;
    }

    @EventHandler
    private void onMovementInput(MovementInputEvent event) {
        if (!isEnabled()) return;

        boolean left = event.isLeft();
        boolean right = event.isRight();
        boolean forward = event.isForward();
        boolean backward = event.isBackward();

        if (right && !prevRight) rightLast = true;
        if (left && !prevLeft) rightLast = false;
        if (backward && !prevBackward) backwardLast = true;
        if (forward && !prevForward) backwardLast = false;

        prevLeft = left;
        prevRight = right;
        prevForward = forward;
        prevBackward = backward;

        if (horizontal.get() && left && right) {
            if (rightLast) event.setLeft(false);
            else event.setRight(false);
        }
        if (vertical.get() && forward && backward) {
            if (backwardLast) event.setForward(false);
            else event.setBackward(false);
        }
    }
}
