/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi;

import com.mojang.blaze3d.systems.DeviceFeatures;
import com.mojang.blaze3d.systems.DeviceInfo;
import com.mojang.blaze3d.systems.DeviceLimits;
import com.mojang.blaze3d.systems.RenderSystem;

/**
 * Backend-neutral capability snapshot sourced from Blaze3D's active device.
 *
 * <p>Do not re-probe capabilities here that Mojang already resolved. Combatant-only native GL/Vulkan
 * capabilities live below the corresponding backend and may be layered on top of this snapshot.</p>
 */
public record RhiCapabilities(String backendName,
                              boolean persistentMapping,
                              boolean multiDrawDirectInterleaved,
                              boolean multiDrawDirectSeparate,
                              boolean multiDrawIndirect,
                              boolean drawIndirect,
                              boolean shaderDrawParameters,
                              boolean nonZeroFirstInstance,
                              int maxMultiDrawDirectInterleavedDrawCount,
                              int maxColorAttachments,
                              int minUniformOffsetAlignment) {

    public static RhiCapabilities current() {
        DeviceInfo info = RenderSystem.getDevice().getDeviceInfo();
        DeviceFeatures features = info.features();
        DeviceLimits limits = info.limits();
        return new RhiCapabilities(
                info.backendName(),
                features.persistentMapping(),
                features.multiDrawDirectInterleaved(),
                features.multiDrawDirectSeparate(),
                features.multiDrawIndirect(),
                features.drawIndirect(),
                features.shaderDrawParameters(),
                features.nonZeroFirstInstance(),
                limits.maxMultiDrawDirectInterleavedDrawCount(),
                limits.maxColorAttachments(),
                limits.minUniformOffsetAlignment()
        );
    }
}
