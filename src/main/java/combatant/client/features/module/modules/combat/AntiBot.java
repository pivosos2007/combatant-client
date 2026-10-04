/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import combatant.client.config.values.BooleanValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Modules;
import combatant.client.util.combat.AntiBotTracker;

import java.util.ArrayList;
import java.util.List;

/**
 * Global switch for {@link AntiBotTracker}. The tracker always scores players in the
 * background; this module decides whether shared targeting treats high scorers as bots.
 * KillAura keeps its own per-module "ignore bots" toggle, which works with or without this.
 */
@ModuleInfo(
        id = "antibot",
        displayName = "AntiBot",
        category = ModuleCategory.COMBAT, subcategory = ModuleSubcategory.ATTACK,
        description = "module.antibot.description")
public final class AntiBot extends Module {

    private final BooleanValue strict = bool("strict", false);
    private final BooleanValue removeFromWorld = bool("remove_from_world", false);

    private final Minecraft mc = Minecraft.getInstance();

    /**
     * True when the AntiBot module is on and the tracker flags this entity as a bot.
     * Called from shared targeting code, so it must stay cheap for non-players.
     */
    public static boolean shouldIgnore(Entity entity) {
        if (!(entity instanceof Player player)) return false;
        AntiBot module = Modules.get(AntiBot.class);
        return module != null && module.isEnabled() && AntiBotTracker.INSTANCE.isBot(player, module.strict.get());
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        if (!removeFromWorld.get() || mc.level == null || mc.player == null) return;

        // Collect first: removeEntity mutates the list players() is backed by.
        List<Player> bots = null;
        for (Player player : mc.level.players()) {
            if (player != mc.player && AntiBotTracker.INSTANCE.isBot(player, strict.get())) {
                if (bots == null) bots = new ArrayList<>();
                bots.add(player);
            }
        }
        if (bots == null) return;
        for (Player bot : bots) {
            mc.level.removeEntity(bot.getId(), Entity.RemovalReason.DISCARDED);
        }
    }
}
