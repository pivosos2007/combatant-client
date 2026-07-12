/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.projectile.FishingHook;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.EventPushOutOfBlocks;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;

import java.util.LinkedHashMap;

//todo Description
@ModuleInfo(
        id = "nopush",
        displayName = "NoPush",
        category = ModuleCategory.MOVEMENT
)
public class NoPush extends Module {

    private static final String SETTING_TOGGLES = "toggles";
    private final BooleanMapValue toggles = group(
            "nopush_toggles",
            SETTING_TOGGLES,
            new LinkedHashMap<>() {{
                put("players", true); // толкание другими энтити
                put("blocks", true);
                put("liquids", true);
                put("fishing_hook", true);  // выталкивание из блоков
                put("pearl_phase", true); // спуф позы после жемчуга
            }}
    );

    public boolean off(String key) {
        return isEnabled() && toggles.get(key);
    }

    public boolean blocksEnabled() {
        return off("blocks");
    }

    public boolean playersEnabled() {
        return off("players");
    }

    public boolean liquidsEnabled() {
        return off("liquids");
    }

    public boolean fishingHookEnabled() {
        return off("fishing_hook");
    }

    @EventHandler
    public void onPushOutOfBlocks(EventPushOutOfBlocks e) {
        if (!blocksEnabled()) return;
        e.cancel();
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive e) {
        if (!fishingHookEnabled()) return;
        if (!(e.getPacket() instanceof ClientboundEntityEventPacket pac)) return;
        if (pac.getEventId() != 31) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return;
        if (pac.getEntity(mc.level) instanceof FishingHook hook && hook.getHookedIn() == mc.player) {
            e.cancel();
        }
    }
}
