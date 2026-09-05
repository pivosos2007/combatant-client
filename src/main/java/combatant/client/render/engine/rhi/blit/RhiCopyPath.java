/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.blit;

public enum RhiCopyPath {
    NO_OP,
    BACKEND_COPY,
    GL_COPY_IMAGE,
    FRAMEBUFFER_BLIT,
    FRAMEBUFFER_RESOLVE,
    SHADER_CONVERSION
}
