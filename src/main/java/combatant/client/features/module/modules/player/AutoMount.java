/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;

import java.util.LinkedHashMap;

@ModuleInfo(
        id = "automount",
        displayName = "AutoMount",
        aliases = {"Mount", "HorseMount"},
        category = ModuleCategory.PLAYER,
        subcategory = ModuleSubcategory.AUTOMATION,
        description = "module.automount.description")
public final class AutoMount extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Double> range = num("autoMountRange", "range", 4.0D, 1.0D, 6.0D);
    private final BooleanMapValue types = group("autoMountTypes", "types", new LinkedHashMap<>() {{
        put("boats", true);
        put("minecarts", true);
        put("horses", true);
    }});
    private final NumberValue<Integer> delayTicks = num("autoMountDelayTicks", "delay_ticks", 5, 0, 20);

    private int cooldown;

    @Override
    public void onEnable() {
        cooldown = 0;
    }

    @Override
    public void onDisable() {
        cooldown = 0;
    }

    /**
     * Mounts from the start-of-tick hook: the interact packet has to precede the tick's movement packet, which a
     * vanilla client's click does and {@code onTick} (end of tick) does not; Grim's Post check flags the latter.
     */
    @EventHandler
    private void onGameTick(GameTickEvent event) {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) {
            cooldown = 0;
            return;
        }
        if (player.isPassenger()) {
            cooldown = 0;
            return;
        }
        if (ClientScreen.current() != null) return;
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        double rangeValue = range.get();
        double rangeSq = rangeValue * rangeValue;
        AABB box = player.getBoundingBox().inflate(rangeValue);

        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : mc.level.getEntities(player, box)) {
            if (!isAllowed(entity) || !entity.getPassengers().isEmpty() || entity.isPassenger()) continue;
            double distance = player.distanceToSqr(entity);
            if (distance > rangeSq || distance >= bestDistance) continue;
            best = entity;
            bestDistance = distance;
        }
        if (best == null) return;

        InteractionResult result = mc.gameMode.interact(player, best, new EntityHitResult(best), InteractionHand.MAIN_HAND);
        if (result != null && result.consumesAction()) {
            player.swing(InteractionHand.MAIN_HAND, player.getItemInHand(InteractionHand.MAIN_HAND).getInteractAnimation(), false);
            cooldown = delayTicks.get();
        }
    }

    private boolean isAllowed(Entity entity) {
        if (entity instanceof AbstractBoat) return types.get("boats");
        if (entity instanceof AbstractMinecart) return types.get("minecarts");
        return entity instanceof AbstractHorse && types.get("horses");
    }
}
