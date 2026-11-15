/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins;

import com.mojang.renderpearl.api.device.DeviceInfo;
import com.mojang.renderpearl.backend.opengl.DirectStateAccess;
import com.mojang.renderpearl.backend.opengl.FrameBufferCache;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlDeviceBackendMixinTest {
    @Test
    void openLtwDeviceDoesNotEnableNativeDsaFromOtherExtensions() {
        var device = device("3.3 OpenLTW", Set.of(
                "GL_ARB_buffer_storage", "GL_KHR_debug", "GL_EXT_texture_filter_anisotropic"));

        assertFalse(device.combatant$nativeDirectStateAccess());
        assertTrue(device.combatant$khrDebug());
    }

    @Test
    void nativeDsaSelectionDoesNotLeakBetweenDevices() {
        var nativeDevice = device("4.6", Set.of("GL_ARB_direct_state_access"));
        var emulatedDevice = device("4.6", Set.of());

        assertTrue(nativeDevice.combatant$nativeDirectStateAccess());
        assertFalse(emulatedDevice.combatant$nativeDirectStateAccess());
        assertFalse(nativeDevice.combatant$khrDebug());
        assertFalse(emulatedDevice.combatant$khrDebug());
        assertTrue(nativeDevice.combatant$nativeDirectStateAccess());
    }

    private static GlDeviceBackendMixin device(String version, Set<String> extensions) {
        DeviceInfo info = new DeviceInfo("test", "test", version, false, "OpenGL", 1.0f,
                null, null, extensions, null, null);
        return new GlDeviceBackendMixin() {
            @Override
            public DeviceInfo getDeviceInfo() {
                return info;
            }

            @Override
            public DirectStateAccess combatant$directStateAccess() {
                throw new AssertionError("Native GL access is not required for capability resolution");
            }

            @Override
            public FrameBufferCache combatant$frameBufferCache() {
                throw new AssertionError("Framebuffer access is not required for capability resolution");
            }
        };
    }
}
