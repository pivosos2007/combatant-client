/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.preview.provider;

import combatant.client.features.gui.clickgui.settings.Setting;
import combatant.client.features.gui.preview.VisualPreviewControlMode;
import combatant.client.features.gui.preview.VisualPreviewProvider;
import combatant.client.features.gui.preview.VisualPreviewSceneContext;
import combatant.client.features.gui.preview.VisualPreviewInteractionProfile;
import combatant.client.features.gui.preview.render.VisualPreviewHandRenderer;
import combatant.client.features.module.Module;

import java.util.List;

/** Shared true first-person scene for modules that alter held items and player arms. */
public final class HandVisualPreviewProvider implements VisualPreviewProvider {
    private final Module module;

    public HandVisualPreviewProvider(Module module) {
        if (module == null) throw new IllegalArgumentException("module");
        this.module = module;
    }

    @Override
    public String id() {
        return "hands:" + module.name();
    }

    @Override
    public String title() {
        return module.getDisplayName() + " — hands";
    }

    @Override
    public VisualPreviewControlMode controlMode() {
        return "viewmodel".equalsIgnoreCase(module.name())
                ? VisualPreviewControlMode.FIXED
                : VisualPreviewControlMode.HAND_VIEW;
    }

    @Override
    public VisualPreviewInteractionProfile interactionProfile() {
        return "viewmodel".equalsIgnoreCase(module.name())
                ? VisualPreviewInteractionProfile.FIXED
                : VisualPreviewInteractionProfile.HAND_INSPECTION;
    }

    @Override
    public List<Setting> settings() {
        return module.getSettings();
    }

    @Override
    public void renderSubject(VisualPreviewSceneContext context) {
        VisualPreviewHandRenderer.render(context);
    }
}
