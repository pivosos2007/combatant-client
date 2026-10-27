/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.modules;

import combatant.client.render.engine.renderer.ui.draw.UiGlassVisual;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

final class ModulesMenuStyleTest {
    @Test
    void menuUsesPlainGlassWithoutIceSpecialization() {
        var glass = ModulesMenuStyle.glass();
        assertSame(UiGlassVisual.DEFAULT, glass.visual());
        assertEquals(0.012f, glass.optics().refraction(), 1e-6f);
        assertEquals(0.085f, glass.optics().bevelWidth(), 1e-6f);
        assertEquals(0.042f, glass.optics().bevelDepth(), 1e-6f);
        assertEquals(0.16f, glass.tintStrength(), 1e-6f);
    }

    @Test
    void oldIceProfilesArePreservedButInactive() {
        var panel = ModulesMenuStyle.futureIceProfile(1, false);
        var control = ModulesMenuStyle.futureIceProfile(1, true);
        var search = ModulesMenuStyle.futureIceProfile(17, true);
        assertEquals(1f, panel.seed(), 1e-6f);
        assertEquals(0.55f, panel.frost(), 1e-6f);
        assertEquals(0.38f, panel.cracks(), 1e-6f);
        assertEquals(0.34f, control.frost(), 1e-6f);
        assertEquals(0.16f, control.cracks(), 1e-6f);
        assertEquals(17f, search.seed(), 1e-6f);
    }
}
