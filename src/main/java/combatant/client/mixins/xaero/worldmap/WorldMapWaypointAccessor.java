/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.xaero.worldmap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;
import xaero.map.mods.gui.Waypoint;

@Pseudo
@Mixin(Waypoint.class)
public interface WorldMapWaypointAccessor {

    @Accessor
    void setX(int x);

    @Accessor
    void setY(int y);

    @Accessor
    void setZ(int z);

    @Accessor
    void setColor(int color);
}
