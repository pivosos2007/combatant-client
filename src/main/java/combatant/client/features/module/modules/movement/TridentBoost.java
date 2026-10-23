/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PlayerMoveEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(
        id = "tridentboost",
        displayName = "TridentBoost",
        aliases = {"RiptideBoost"},
        category = ModuleCategory.MOVEMENT,
        subcategory = ModuleSubcategory.RAGE,
        description = "module.tridentboost.description")
public final class TridentBoost extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Float> multiplier = num("tridentBoostMultiplier", "multiplier", 1.5f, 1.0f, 5.0f);
    private final BooleanValue vertical = bool("tridentBoostVertical", "vertical", true);

    @EventHandler
    private void onPlayerMove(PlayerMoveEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || event.getType() != MoverType.SELF || !player.isAutoSpinAttack()) return;

        double factor = multiplier.get();
        Vec3 movement = event.getMovement();
        event.setMovement(new Vec3(
                movement.x * factor,
                vertical.get() ? movement.y * factor : movement.y,
                movement.z * factor));
    }
}
