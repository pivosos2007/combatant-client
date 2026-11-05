/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

layout (std140) uniform UIBatch {
    vec4 uScreen; // xy = framebuffer size, zw = logical size
    vec4 uLayer;  // xy = logical origin, zw = logical extent of the active render target
};
