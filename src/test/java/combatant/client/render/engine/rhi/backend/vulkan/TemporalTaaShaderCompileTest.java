/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.backend.vulkan;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compute_shader;

final class TemporalTaaShaderCompileTest {
    @Test
    void compilesForVulkan() throws Exception {
        try (VulkanNativeShaderCompiler compiler = new VulkanNativeShaderCompiler()) {
            assertCompiles(compiler, "taa_resolve");
            assertCompiles(compiler, "taa_present");
        }
    }

    private static void assertCompiles(VulkanNativeShaderCompiler compiler, String name) throws Exception {
        String source = Files.readString(Path.of(
                "src/main/resources/assets/combatant/shaders/temporal/" + name + ".comp"));
        assertTrue(compiler.compile("temporal/" + name, source, shaderc_compute_shader).hasRemaining());
    }
}
