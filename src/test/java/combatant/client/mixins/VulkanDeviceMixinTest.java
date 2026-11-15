/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins;

import com.mojang.renderpearl.backend.vulkan.VulkanFeatureSets;
import com.mojang.renderpearl.backend.vulkan.init.FeatureSet;
import com.mojang.renderpearl.backend.vulkan.init.VulkanFeature;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VulkanDeviceMixinTest {
    @Test
    void minecraftRequiredFeaturesDoNotImplyOptionalShaderStages() throws ReflectiveOperationException {
        var features = VulkanFeatureSets.REQUIRED_FEATURESET;
        assertFalse(featureEnabled(features, "tessellationShader"));
        assertFalse(featureEnabled(features, "geometryShader"));
        assertFalse(featureEnabled(null, "geometryShader"));
    }

    @Test
    void optionalShaderStagesRequireIndividualDeviceEnablement() throws ReflectiveOperationException {
        var tessellation = new VulkanFeature(VulkanFeatureSets.VK10_FEATURES_STRUCT, "tessellationShader");
        var geometry = new VulkanFeature(VulkanFeatureSets.VK10_FEATURES_STRUCT, "geometryShader");
        var tessellationOnly = new FeatureSet("tessellation", Set.of(), Set.of(tessellation));
        var both = new FeatureSet("both", Set.of(), Set.of(tessellation, geometry));

        assertTrue(featureEnabled(tessellationOnly, "tessellationShader"));
        assertFalse(featureEnabled(tessellationOnly, "geometryShader"));
        assertTrue(featureEnabled(both, "tessellationShader"));
        assertTrue(featureEnabled(both, "geometryShader"));
    }

    private static boolean featureEnabled(FeatureSet features, String name) throws ReflectiveOperationException {
        var method = VulkanDeviceMixin.class.getDeclaredMethod("combatant$featureEnabled", FeatureSet.class, String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, features, name);
    }
}
