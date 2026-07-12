/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import net.minecraft.client.Minecraft;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.player.NetworkStatsUtil;

//todo Description
@ModuleInfo(
        id = "tpssync",
        displayName = "TPSSync",
        category = ModuleCategory.COMBAT
)
public class TPSSync extends Module {

    private static final String SETTING_START_TPS = "start_tps";
    private final Minecraft mc = Minecraft.getInstance();
    private final BooleanValue enabled =
            new BooleanValue("tpssync_enabled", true);
    private final NumberValue<Float> startTps =
            num("tpssync_start_tps", SETTING_START_TPS, 19.0f, 1.0f, 20.0f);

    /** сколько серверных тиков прошло за клиентский */
    public float getServerTickDelta() {
        if (!isEnabled() || !enabled.get()) return 1.0f;
        if (mc == null || mc.level == null) return 1.0f;

        float tps = NetworkStatsUtil.getTps(mc);
        if (tps >= startTps.get()) return 1.0f;

        return Math.max(0.0f, tps / 20.0f);
    }
}
