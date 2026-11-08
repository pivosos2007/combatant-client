/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.backend.gl;

import org.lwjgl.opengl.GLCapabilities;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes only the capabilities of Mojang's *current* OpenGL context.
 *
 * <p>The core version is derived from LWJGL's OpenGLxx flags. Independently resolved
 * ARB extensions are reported as feature groups, never promoted to a higher core version:
 * e.g. compute+SSBO do not make a 3.3 context fully OpenGL 4.3 compliant.</p>
 */
public final class GlCapabilityLevel {
    private GlCapabilityLevel() {
    }

    public static String describe(GLCapabilities caps) {
        if (caps == null) return "unavailable";
        int core = coreLevel(caps);
        List<String> extensions = new ArrayList<>(8);
        int extensionEra = 0;

        // Mention only functionality beyond the corresponding core level. These are
        // representative supported feature groups, NOT a synthetic OpenGL version.
        if (core < 40 && caps.GL_ARB_tessellation_shader) {
            extensions.add("tessellation"); extensionEra = 40;
        }
        if (core < 42 && caps.GL_ARB_shader_image_load_store) {
            extensions.add("image load/store"); extensionEra = 42;
        }
        if (core < 43 && caps.GL_ARB_compute_shader) {
            extensions.add("compute"); extensionEra = 43;
        }
        if (core < 43 && caps.GL_ARB_shader_storage_buffer_object) {
            extensions.add("SSBO"); extensionEra = 43;
        }
        if (core < 43 && caps.GL_ARB_multi_draw_indirect) {
            extensions.add("MDI"); extensionEra = 43;
        }
        if (core < 44 && caps.GL_ARB_multi_bind) {
            extensions.add("multi-bind"); extensionEra = 44;
        }
        if (core < 45 && caps.GL_ARB_direct_state_access) {
            extensions.add("DSA"); extensionEra = 45;
        }
        if (core < 46 && caps.GL_ARB_gl_spirv) {
            extensions.add("SPIR-V"); extensionEra = 46;
        }

        return extensions.isEmpty()
                ? "OpenGL " + version(core) + " core"
                : "OpenGL " + version(core) + " core; " + version(extensionEra)
                        + "-era ARB subset (" + String.join(", ", extensions) + ")";
    }

    private static int coreLevel(GLCapabilities caps) {
        if (caps.OpenGL46) return 46;
        if (caps.OpenGL45) return 45;
        if (caps.OpenGL44) return 44;
        if (caps.OpenGL43) return 43;
        if (caps.OpenGL42) return 42;
        if (caps.OpenGL41) return 41;
        if (caps.OpenGL40) return 40;
        if (caps.OpenGL33) return 33;
        if (caps.OpenGL32) return 32;
        if (caps.OpenGL31) return 31;
        if (caps.OpenGL30) return 30;
        return 0;
    }

    private static String version(int level) {
        return level == 0 ? "unknown" : level / 10 + "." + level % 10;
    }
}
