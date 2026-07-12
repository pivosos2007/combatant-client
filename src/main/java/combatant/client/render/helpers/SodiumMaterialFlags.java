/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.helpers;

public enum SodiumMaterialFlags {
    ;
    public static final int SURFACE_FLAG_SOFT_FADE = 1 << 0;
    public static final int SURFACE_FLAG_WAVY_VEGETATION = 1 << 1;
    public static final int SURFACE_FLAG_WAVY_VEGETATION_FREE = 1 << 2;
    public static final int WAVE_LOCAL_Y_SHIFT = 8;
    public static final int WAVE_LOCAL_Y_BITS = 4;
    public static final int WAVE_LOCAL_Y_MASK = (1 << WAVE_LOCAL_Y_BITS) - 1;
    public static final int WAVE_ROOTED_HORIZONTAL_SHIFT = 12;
    public static final int WAVE_ROOTED_VERTICAL_SHIFT = 16;
    public static final int WAVE_FREE_HORIZONTAL_SHIFT = 20;
    public static final int WAVE_FREE_VERTICAL_SHIFT = 24;
    public static final int WAVE_SPEED_SHIFT = 28;
    public static final int WAVE_SETTING_BITS = 4;
    public static final int WAVE_SETTING_MASK = (1 << WAVE_SETTING_BITS) - 1;
    public static final float WAVE_SETTING_MAX = 3.0f;

}
