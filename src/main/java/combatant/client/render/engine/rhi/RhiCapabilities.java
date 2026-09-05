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
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import combatant.client.mixininterface.IGlBackendInfo;
import combatant.client.render.engine.rhi.backend.gl.GlBackendAccess;

import java.util.Set;

/**
 * Backend capability snapshot whose primary source of truth is Blaze3D's active {@link DeviceInfo}.
 *
 * <p>Combatant deliberately does not maintain a second copy of Mojang's feature/limit resolver.
 * Existing capabilities are delegated to {@link DeviceFeatures}, {@link DeviceLimits} and
 * {@link DeviceInfo#underlyingExtensions()}. Only backend facts that Blaze3D resolved internally but
 * does not expose in {@code DeviceFeatures} are appended here through the backend bridge.</p>
 */
public final class RhiCapabilities {
    private final DeviceInfo deviceInfo;
    private final boolean nativeDirectStateAccess;
    private final boolean khrDebug;

    private RhiCapabilities(DeviceInfo deviceInfo,
                            boolean nativeDirectStateAccess,
                            boolean khrDebug) {
        this.deviceInfo = deviceInfo;
        this.nativeDirectStateAccess = nativeDirectStateAccess;
        this.khrDebug = khrDebug;
    }

    public static RhiCapabilities current() {
        GpuDevice device = RenderSystem.tryGetDevice();
        if (device == null) {
            throw new IllegalStateException("Cannot resolve RHI capabilities before Blaze3D device initialization");
        }

        DeviceInfo info = device.getDeviceInfo();
        IGlBackendInfo gl = GlBackendAccess.current();
        return new RhiCapabilities(
                info,
                gl != null && gl.combatant$nativeDirectStateAccess(),
                gl != null && gl.combatant$khrDebug()
        );
    }

    public DeviceInfo deviceInfo() {
        return deviceInfo;
    }

    public DeviceFeatures features() {
        return deviceInfo.features();
    }

    public DeviceLimits limits() {
        return deviceInfo.limits();
    }

    public Set<String> underlyingExtensions() {
        return deviceInfo.underlyingExtensions();
    }

    public String backendName() {
        return deviceInfo.backendName();
    }

    public boolean persistentMapping() {
        return features().persistentMapping();
    }

    public boolean multiDrawDirectInterleaved() {
        return features().multiDrawDirectInterleaved();
    }

    public boolean multiDrawDirectSeparate() {
        return features().multiDrawDirectSeparate();
    }

    public boolean multiDrawIndirect() {
        return features().multiDrawIndirect();
    }

    public boolean drawIndirect() {
        return features().drawIndirect();
    }

    public boolean shaderDrawParameters() {
        return features().shaderDrawParameters();
    }

    public boolean nonZeroFirstInstance() {
        return features().nonZeroFirstInstance();
    }

    public int maxMultiDrawDirectInterleavedDrawCount() {
        return limits().maxMultiDrawDirectInterleavedDrawCount();
    }

    public int maxColorAttachments() {
        return limits().maxColorAttachments();
    }

    public int minUniformOffsetAlignment() {
        return limits().minUniformOffsetAlignment();
    }

    /** Mojang-resolved native ARB_direct_state_access policy. **/
    public boolean nativeDirectStateAccess() {
        return nativeDirectStateAccess;
    }

    /** Mojang-resolved KHR_debug policy, not a Combatant extension re-probe. **/
    public boolean khrDebug() {
        return khrDebug;
    }
}
