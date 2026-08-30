/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.preview;

import combatant.client.features.gui.clickgui.settings.Setting;
import combatant.client.render.engine.renderer.Renderer2D;

import java.util.List;

public interface VisualPreviewProvider {
    String id();

    String title();

    VisualPreviewControlMode controlMode();

    default VisualPreviewInteractionProfile interactionProfile() {
        return VisualPreviewInteractionProfile.fromLegacy(controlMode());
    }

    default List<Setting> settings() {
        return List.of();
    }

    default float initialZoom() {
        return 1.0f;
    }

    default boolean showSceneTitle() {
        return true;
    }

    void renderSubject(VisualPreviewSceneContext context);

    default void renderOverlay(VisualPreviewSceneContext context, Renderer2D renderer) {
    }
}
