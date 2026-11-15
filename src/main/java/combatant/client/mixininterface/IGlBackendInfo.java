/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixininterface;

import com.mojang.renderpearl.backend.opengl.DirectStateAccess;
import com.mojang.renderpearl.backend.opengl.FrameBufferCache;

public interface IGlBackendInfo {
    DirectStateAccess combatant$directStateAccess();

    FrameBufferCache combatant$frameBufferCache();

    /** Native DSA selected by Mojang after capability and driver checks. */
    boolean combatant$nativeDirectStateAccess();

    /** KHR_debug selected by Mojang for this device. */
    boolean combatant$khrDebug();

    /** Raw support flags captured from the exact GLCapabilities instance Mojang creates for GlDevice. */
    boolean combatant$computeShaders();

    boolean combatant$tessellationShaders();

    boolean combatant$geometryShaders();

    boolean combatant$shaderStorageBuffers();

    boolean combatant$imageLoadStore();

    boolean combatant$multiBind();

    boolean combatant$copyImage();

    boolean combatant$attachmentInvalidation();

    /** Driver identity captured from the active Mojang GL context; informational, not a capability re-probe. */
    String combatant$vendor();

    String combatant$renderer();

    /** Driver-reported GL_VERSION, captured from the active context at GlDevice initialization. */
    String combatant$glVersion();

    /** Driver-reported GL_SHADING_LANGUAGE_VERSION; distinct from GL context version. */
    String combatant$glslVersion();

    /** Actual context profile (Core / Compatibility), not the game's requested minimum. */
    String combatant$glProfile();

    /** Core OpenGL version plus any independently supported newer feature groups. */
    String combatant$availableCapabilitiesLevel();
}
