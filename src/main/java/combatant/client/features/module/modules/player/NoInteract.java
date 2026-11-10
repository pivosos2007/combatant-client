/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.CrosshairTargetUpdateEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.combat.KillAura;

@ModuleInfo(
        id = "nointeract",
        displayName = "NoInteract",
        category = ModuleCategory.PLAYER, subcategory = ModuleSubcategory.AUTOMATION,
        description = "module.nointeract.description")
public final class NoInteract extends Module {
    private final BooleanValue onlyKillAura = bool("only_kill_aura", true);

    @EventHandler(priority = -20000)
    private void onCrosshairTarget(CrosshairTargetUpdateEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !shouldBlockBlockInteraction()
                || event.getHitResult() instanceof EntityHitResult) return;

        Vec3 cameraPosition = mc.player.getEyePosition(event.getTickDelta());
        event.setHitResult(new BlockHitResult(cameraPosition, Direction.UP, BlockPos.ZERO, false));
        event.setTargetedEntity(null);
    }

    public boolean shouldBlockBlockInteraction() {
        KillAura killAura = Modules.get(KillAura.class);
        return shouldBlockBlockInteraction(
                isEnabled(),
                onlyKillAura.get(),
                killAura != null && killAura.isEnabled()
        );
    }

    static boolean shouldBlockBlockInteraction(
            boolean noInteractEnabled,
            boolean onlyKillAura,
            boolean killAuraEnabled
    ) {
        return noInteractEnabled && (!onlyKillAura || killAuraEnabled);
    }
}
