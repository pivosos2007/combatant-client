/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.iris;

import com.mojang.blaze3d.vertex.PoseStack;

public enum IrisHandMaskState {
    ;

    public static void renderBothPhases(Runnable renderer) {
        renderBothPhases(null, renderer);
    }

    public static void renderBothPhases(PoseStack matrices, Runnable renderer) {
        if (matrices != null) {
            matrices.pushPose();
        }
        try {
            renderer.run();
        } finally {
            if (matrices != null) {
                matrices.popPose();
            }
        }
    }
}
