/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.sound;

import combatant.client.features.gui.clickgui.sound.GuiSound;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class SoundRegistryTest {
    @Test
    void classGraphDiscoversAnnotatedCatalog() {
        SoundRegistry registry = SoundRegistry.get();
        registry.discover("combatant.client.features.gui.clickgui.sound");

        Identifier id = Identifier.fromNamespaceAndPath("combatant", "gui/guiscroll");
        SoundDefinition definition = registry.find(id);
        assertNotNull(definition);
        assertEquals(Identifier.fromNamespaceAndPath("combatant", "sounds/gui/guiscroll.wav"),
                definition.resource());
        assertEquals(definition, GuiSound.SCROLL.soundDefinition());
    }

    @Test
    void discoversCatalogNestedInsideItsOwner() {
        SoundRegistry registry = SoundRegistry.get();
        registry.discover("combatant.client.features.gui.hud.draggable.impl");

        SoundDefinition definition = registry.find(
                Identifier.fromNamespaceAndPath("combatant", "notifications/enable_1")
        );
        assertNotNull(definition);
        assertEquals(Identifier.fromNamespaceAndPath("combatant", "sounds/enable/enable1.wav"),
                definition.resource());
    }
}
