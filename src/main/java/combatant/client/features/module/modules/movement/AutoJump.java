/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import combatant.client.automation.ActionPriority;
import combatant.client.automation.AutomationRuntime;
import combatant.client.automation.AutomationTask;
import combatant.client.automation.actions.HoldJumpAction;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.modules.movement.rules.MovementRules;
import combatant.client.util.player.MovementUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Jumps on every grounded tick you are walking. The jump goes through the automation runtime's jump lease as a
 * normal jump input, so the movement that follows is vanilla physics.
 */
@ModuleInfo(
        id = "autojump",
        displayName = "AutoJump",
        aliases = {"Bhop", "AutoHop"},
        category = ModuleCategory.MOVEMENT,
        subcategory = ModuleSubcategory.BASIC,
        description = "module.autojump.description")
public final class AutoJump extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    @Override
    public void onEnable() {
        submit();
    }

    @Override
    public void onTick() {
        if (!AutomationRuntime.INSTANCE.hasTasks(this)) submit();
    }

    private void submit() {
        AutomationRuntime.INSTANCE.submit(AutomationTask.builder(this, "AutoJump",
                        new HoldJumpAction("AutoJump", this::shouldJump))
                .priority(ActionPriority.BACKGROUND)
                .yieldToManualInput(java.util.Set.of())
                .build());
    }

    private boolean shouldJump() {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return false;
        boolean allowed = !player.isInWater() && !player.isInLava() && !player.onClimbable()
                && !player.isPassenger() && !player.isFallFlying() && !player.isSpectator();
        return MovementRules.autoJump(allowed, player.onGround(), MovementUtil.isMoving(), player.isShiftKeyDown());
    }
}
