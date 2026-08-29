/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.preview;

import net.minecraft.client.Minecraft;

public record VisualPreviewSceneContext(
        Minecraft minecraft,
        VisualPreviewScreen screen,
        float width,
        float height,
        float subjectX,
        float subjectY,
        float subjectWidth,
        float subjectHeight,
        float tickDelta
) {
}
