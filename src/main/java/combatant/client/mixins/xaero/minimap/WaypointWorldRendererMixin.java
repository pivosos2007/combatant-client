/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.xaero.minimap;

import combatant.client.compat.xaero.XaeroWaypointHudOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.element.render.MinimapElementGraphics;
import xaero.hud.minimap.element.render.MinimapElementRenderInfo;
import xaero.hud.minimap.waypoint.render.world.WaypointWorldRenderer;
import xaero.lib.client.graphics.XaeroBufferProvider;

@Pseudo
@Mixin(WaypointWorldRenderer.class)
public abstract class WaypointWorldRendererMixin {
    @Inject(
            method = "renderElement(Lxaero/common/minimap/waypoints/Waypoint;ZZDFDDLxaero/hud/minimap/element/render/MinimapElementRenderInfo;Lxaero/hud/minimap/element/render/MinimapElementGraphics;Lxaero/lib/client/graphics/XaeroBufferProvider;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void combatant$replaceXaeroWorldWaypoint(Waypoint waypoint,
                                                      boolean renderDistance,
                                                      boolean renderName,
                                                      double optionalDepth,
                                                      float optionalScale,
                                                      double optionalX,
                                                      double optionalY,
                                                      MinimapElementRenderInfo renderInfo,
                                                      MinimapElementGraphics graphics,
                                                      XaeroBufferProvider bufferProvider,
                                                      CallbackInfoReturnable<Boolean> cir) {
        if (XaeroWaypointHudOverlay.ownsXaeroWorldWaypoints()) {
            cir.setReturnValue(false);
        }
    }
}
