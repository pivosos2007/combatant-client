/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.shader;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class CombatantShaderSourcesTest {
    @Test
    void analyticClipVariantIsAReversibleSingleAxisKey() {
        Identifier base = Identifier.fromNamespaceAndPath("combatant", "shaders/ui_shape_batch.frag");
        Identifier variant = CombatantShaderSources.analyticClipVariantId(base);

        assertEquals("shaders/ui_shape_batch__analytic_clip.frag", variant.getPath());
        assertTrue(CombatantShaderSources.isAnalyticClipVariant(variant));
        assertEquals(base, CombatantShaderSources.analyticClipBaseId(variant));
    }

    @Test
    void defineIsInjectedAfterRequiredVersionDirective() {
        String source = "#version 330 core\nvoid main() {}\n";
        String result = CombatantShaderSources.injectDefineAfterVersion(source, "#define TEST_VARIANT 1\n");

        assertTrue(result.startsWith("#version 330 core\n#define TEST_VARIANT 1\n"));
        assertEquals(1, occurrences(result, "#version"));
        assertEquals(1, occurrences(result, "TEST_VARIANT"));
    }

    @Test
    void underlayAndAnalyticClipAxesComposeAndRemainReversible() {
        Identifier base = Identifier.fromNamespaceAndPath("combatant", "shaders/ui_liquid_glass_batch.frag");
        Identifier combined = CombatantShaderSources.uiUnderlayVariantId(
                CombatantShaderSources.analyticClipVariantId(base));

        assertEquals("shaders/ui_liquid_glass_batch__analytic_clip__ui_underlay.frag", combined.getPath());
        assertTrue(CombatantShaderSources.isAnalyticClipVariant(combined));
        assertTrue(CombatantShaderSources.isUiUnderlayVariant(combined));
        assertEquals(base, CombatantShaderSources.uiUnderlayBaseId(
                CombatantShaderSources.analyticClipBaseId(combined)));
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }
}
