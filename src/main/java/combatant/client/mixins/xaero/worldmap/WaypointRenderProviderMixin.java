/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.xaero.worldmap;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import combatant.client.compat.xaero.TriangulatorXaeroWorldMapCompat;
import xaero.map.element.render.ElementRenderLocation;
import xaero.map.mods.SupportXaeroMinimap;
import xaero.map.mods.gui.Waypoint;
import xaero.map.mods.gui.WaypointRenderContext;
import xaero.map.mods.gui.WaypointRenderProvider;

import java.util.ArrayDeque;
import java.util.Queue;

@Pseudo
@Mixin(WaypointRenderProvider.class)
public final class WaypointRenderProviderMixin {

    @Shadow
    @Final
    private SupportXaeroMinimap minimap;

    @Unique
    private Queue<Waypoint> combatant$pendingWaypoints;

    @Unique
    private boolean combatant$originalHadNext;

    @Inject(method = "begin*", at = @At("HEAD"))
    private void combatant$begin(ElementRenderLocation location,
                                 WaypointRenderContext context,
                                 CallbackInfo ci) {
        combatant$pendingWaypoints = new ArrayDeque<>();
        Waypoint triangulatorWaypoint = TriangulatorXaeroWorldMapCompat.getWaypoint(minimap);
        if (triangulatorWaypoint != null) {
            combatant$pendingWaypoints.add(triangulatorWaypoint);
        }
        combatant$originalHadNext = false;
    }

    @Inject(method = "hasNext*", at = @At("RETURN"), cancellable = true)
    private void combatant$hasNext(ElementRenderLocation location,
                                   WaypointRenderContext context,
                                   CallbackInfoReturnable<Boolean> cir) {
        combatant$originalHadNext = cir.getReturnValue();
        cir.setReturnValue(combatant$originalHadNext
                || (combatant$pendingWaypoints != null && !combatant$pendingWaypoints.isEmpty()));
    }

    @Inject(method = "getNext*", at = @At("HEAD"), cancellable = true)
    private void combatant$getNext(ElementRenderLocation location,
                                   WaypointRenderContext context,
                                   CallbackInfoReturnable<Waypoint> cir) {
        if (combatant$originalHadNext) {
            combatant$originalHadNext = false;
            return;
        }
        if (combatant$pendingWaypoints != null && !combatant$pendingWaypoints.isEmpty()) {
            cir.setReturnValue(combatant$pendingWaypoints.poll());
        }
    }

    @Inject(method = "end*", at = @At("HEAD"))
    private void combatant$end(ElementRenderLocation location,
                               WaypointRenderContext context,
                               CallbackInfo ci) {
        combatant$pendingWaypoints = null;
        combatant$originalHadNext = false;
    }
}
