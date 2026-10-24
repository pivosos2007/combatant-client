package combatant.client.render.engine.shader;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.util.shaderc.Shaderc.*;

final class UiGlassShaderCompileTest {
    @Test
    void compilesDefaultAndCustomVisualsWithEveryClipAndUnderlayVariant() {
        ResourceManager resources = (ResourceManager) Proxy.newProxyInstance(
                ResourceManager.class.getClassLoader(), new Class<?>[]{ResourceManager.class},
                (proxy, method, args) -> {
                    if (!method.getName().equals("getResource")) throw new UnsupportedOperationException(method.getName());
                    Identifier id = (Identifier) args[0];
                    Path path = Path.of("src/main/resources/assets", id.getNamespace(), id.getPath());
                    return Files.isRegularFile(path)
                            ? Optional.of(new Resource(null, () -> Files.newInputStream(path))) : Optional.empty();
                });
        long compiler = shaderc_compiler_initialize();
        long options = shaderc_compile_options_initialize();
        assertNotEquals(0L, compiler);
        assertNotEquals(0L, options);
        try {
            shaderc_compile_options_set_auto_bind_uniforms(options, true);
            shaderc_compile_options_set_auto_map_locations(options, true);
            for (boolean vulkan : new boolean[]{false, true}) {
                shaderc_compile_options_set_target_env(options,
                        vulkan ? shaderc_target_env_vulkan : shaderc_target_env_opengl,
                        vulkan ? shaderc_env_version_vulkan_1_2 : shaderc_env_version_opengl_4_5);
                for (String name : new String[]{"ui_liquid_glass_batch", "ui_glass_flow", "ui_glass_impact", "ui_glass_ripple", "ui_glass_frozen"}) {
                    for (int variant = 0; variant < 4; variant++) {
                        Identifier id = Identifier.fromNamespaceAndPath("combatant", "shaders/" + name + ".frag");
                        if ((variant & 1) != 0) id = CombatantShaderSources.analyticClipVariantId(id);
                        if ((variant & 2) != 0) id = CombatantShaderSources.uiUnderlayVariantId(id);
                        String source = CombatantShaderSources.load(resources, id);
                        assertFalse(source.contains("#include"));
                        if (vulkan) source = source.replace("#version 330 core", "#version 450 core");
                        long result = shaderc_compile_into_spv(compiler, source, shaderc_fragment_shader,
                                id.toString(), "main", options);
                        assertNotEquals(0L, result);
                        try {
                            assertEquals(shaderc_compilation_status_success, shaderc_result_get_compilation_status(result),
                                    id + " (vulkan=" + vulkan + "): " + shaderc_result_get_error_message(result));
                        } finally {
                            shaderc_result_release(result);
                        }
                    }
                }
            }
        } finally {
            shaderc_compile_options_release(options);
            shaderc_compiler_release(compiler);
        }
    }
}
