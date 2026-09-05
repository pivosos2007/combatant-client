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
import combatant.client.render.engine.rhi.backend.vulkan.util.VulkanRenderStateBridge;

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
    private final boolean computeShaders;
    private final boolean tessellationShaders;
    private final boolean geometryShaders;
    private final boolean shaderStorageBuffers;
    private final boolean multiBind;
    private final boolean copyImage;
    private final boolean attachmentInvalidation;
    private final String glVendor;
    private final String glRenderer;

    private RhiCapabilities(DeviceInfo deviceInfo,
                            boolean nativeDirectStateAccess,
                            boolean khrDebug,
                            boolean computeShaders,
                            boolean tessellationShaders,
                            boolean geometryShaders,
                            boolean shaderStorageBuffers,
                            boolean multiBind,
                            boolean copyImage,
                            boolean attachmentInvalidation,
                            String glVendor,
                            String glRenderer) {
        this.deviceInfo = deviceInfo;
        this.nativeDirectStateAccess = nativeDirectStateAccess;
        this.khrDebug = khrDebug;
        this.computeShaders = computeShaders;
        this.tessellationShaders = tessellationShaders;
        this.geometryShaders = geometryShaders;
        this.shaderStorageBuffers = shaderStorageBuffers;
        this.multiBind = multiBind;
        this.copyImage = copyImage;
        this.attachmentInvalidation = attachmentInvalidation;
        this.glVendor = glVendor == null ? "" : glVendor;
        this.glRenderer = glRenderer == null ? "" : glRenderer;
    }

    public static RhiCapabilities current() {
        GpuDevice device = RenderSystem.tryGetDevice();
        if (device == null) {
            throw new IllegalStateException("Cannot resolve RHI capabilities before Blaze3D device initialization");
        }

        DeviceInfo info = device.getDeviceInfo();
        IGlBackendInfo gl = GlBackendAccess.current();
        boolean vulkan = info.backendName() != null
                && info.backendName().toLowerCase(java.util.Locale.ROOT).contains("vulkan");
        return new RhiCapabilities(
                info,
                gl != null && gl.combatant$nativeDirectStateAccess(),
                gl != null && gl.combatant$khrDebug(),
                vulkan ? VulkanRenderStateBridge.computeShadersSupported()
                        : gl != null && gl.combatant$computeShaders(),
                vulkan ? VulkanRenderStateBridge.tessellationShadersSupported()
                        : gl != null && gl.combatant$tessellationShaders(),
                vulkan ? VulkanRenderStateBridge.geometryShadersSupported()
                        : gl != null && gl.combatant$geometryShaders(),
                vulkan ? VulkanRenderStateBridge.shaderStorageBuffersSupported()
                        : gl != null && gl.combatant$shaderStorageBuffers(),
                !vulkan && gl != null && gl.combatant$multiBind(),
                vulkan || gl != null && gl.combatant$copyImage(),
                vulkan || gl != null && gl.combatant$attachmentInvalidation(),
                gl != null ? gl.combatant$vendor() : "",
                gl != null ? gl.combatant$renderer() : ""
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

    public boolean computeShaders() {
        return computeShaders;
    }

    public boolean tessellationShaders() {
        return tessellationShaders;
    }

    public boolean geometryShaders() {
        return geometryShaders;
    }

    public boolean shaderStorageBuffers() {
        return shaderStorageBuffers;
    }

    public boolean multiBind() {
        return multiBind;
    }

    public boolean copyImage() {
        return copyImage;
    }

    public boolean attachmentInvalidation() {
        return attachmentInvalidation;
    }

    public String glVendor() {
        return glVendor;
    }

    public String glRenderer() {
        return glRenderer;
    }

    /**
     * Conservative AUTO policy for raw image copies.
     *
     * <p>ARB_copy_image is a direct image-memory copy and is usually a good fit for exact desktop
     * texture-to-texture transfers. Known mobile/tile-based families have shipped pathological
     * stalls/hangs in this path, so AUTO keeps Mojang's FBO blit there. FORCE still overrides this
     * policy for diagnostics.</p>
     */
    public boolean copyImageAutoSafe() {
        if (!copyImage || GlBackendAccess.current() == null) return false;
        String identity = (glVendor + " " + glRenderer).toLowerCase(java.util.Locale.ROOT);
        if (identity.isBlank()) return true;
        return !(identity.contains("mali")
                || identity.contains("powervr")
                || identity.contains("imagination")
                || identity.contains("vivante")
                || identity.contains("swiftshader")
                || identity.contains("llvmpipe")
                || identity.contains("softpipe"));
    }

    /** Blaze3D 26.2 exposes no compute dispatch; Combatant supplies it only on the native GL tier today. */
    public boolean nativeComputeSubmission() {
        return GlBackendAccess.current() != null && computeShaders && shaderStorageBuffers;
    }

    /** Blaze3D 26.2 exposes no tessellation stages; native pipeline lowering is separate. */
    public boolean nativeTessellationSubmission() {
        return false;
    }

    public boolean nativeGeometrySubmission() {
        return false;
    }
}
