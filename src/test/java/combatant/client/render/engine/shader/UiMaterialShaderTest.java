package combatant.client.render.engine.shader;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;
import combatant.client.render.engine.renderer.ui.UiMaterialTextureData;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.util.shaderc.Shaderc.*;

final class UiMaterialShaderTest {
    static final String[] MATERIALS = {"graphite", "soft_touch", "satin_titanium", "brushed_metal", "ceramic", "ice"};
    static final String[] FRAGMENTS = {"ui_material_primitive_batch", "ui_material_textured_primitive_batch",
            "ui_material_scene_primitive_batch", "ui_material_scene_textured_primitive_batch"};
    static final Path ROOT = Path.of("src/main/resources/assets/combatant/textures/ui/pbr");

    static String source(String name, boolean clip) {
        ResourceManager resources = (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),
                new Class<?>[]{ResourceManager.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("getResource")) throw new UnsupportedOperationException(method.getName());
                    Identifier id = (Identifier) args[0];
                    Path path = Path.of("src/main/resources/assets", id.getNamespace(), id.getPath());
                    return Files.isRegularFile(path) ? Optional.of(new Resource(null, () -> Files.newInputStream(path))) : Optional.empty();
                });
        Identifier id = Identifier.fromNamespaceAndPath("combatant", "shaders/" + name);
        return CombatantShaderSources.load(resources, clip ? CombatantShaderSources.analyticClipVariantId(id) : id);
    }

    @Test void compileAllMaterialVariantsForOpenGlAndVulkan() {
        long compiler = shaderc_compiler_initialize(), options = shaderc_compile_options_initialize();
        assertNotEquals(0, compiler); assertNotEquals(0, options);
        try {
            shaderc_compile_options_set_auto_bind_uniforms(options, true);
            shaderc_compile_options_set_auto_map_locations(options, true);
            for (boolean vulkan : new boolean[]{false, true}) {
                shaderc_compile_options_set_target_env(options, vulkan ? shaderc_target_env_vulkan : shaderc_target_env_opengl,
                        vulkan ? shaderc_env_version_vulkan_1_2 : shaderc_env_version_opengl_4_5);
                compile(compiler, options, "ui_material_primitive_textured_fast.vert", false, vulkan);
                for (String fragment : FRAGMENTS) for (boolean clip : new boolean[]{false, true})
                    compile(compiler, options, fragment + ".frag", clip, vulkan);
            }
        } finally { shaderc_compile_options_release(options); shaderc_compiler_release(compiler); }
    }

    private static void compile(long compiler, long options, String name, boolean clip, boolean vulkan) {
        String shader = source(name, clip);
        assertFalse(shader.contains("#include"));
        if (vulkan) shader = shader.replace("#version 330 core", "#version 450 core");
        long result = shaderc_compile_into_spv(compiler, shader,
                name.endsWith(".vert") ? shaderc_vertex_shader : shaderc_fragment_shader, name, "main", options);
        assertNotEquals(0, result);
        try {
            assertEquals(shaderc_compilation_status_success, shaderc_result_get_compilation_status(result),
                    name + " clip=" + clip + " vulkan=" + vulkan + ": " + shaderc_result_get_error_message(result));
        } finally { shaderc_result_release(result); }
    }

    @Test void allRuntimeMapsHaveCompleteMipChainsAndValidIndependentChannels() throws Exception {
        for (String material : MATERIALS) {
            for (String kind : new String[]{material.equals("ice") ? "color_ao" : "color_metal", "normal_rough_height"}) {
                try (var input = Files.newInputStream(ROOT.resolve(material).resolve(kind + ".ktx"))) {
                    var data = UiMaterialTextureData.read(input);
                    int size = material.equals("ice") ? 512 : 256;
                    int levels = material.equals("ice") ? 10 : 9;
                    assertEquals(size, data.size());
                    assertEquals(levels, data.mipLevels());
                    assertEquals(kind.startsWith("color_") ? 1 : 2, data.componentBytes());
                    for (int mip = 0; mip < levels; mip++) assertEquals((size >> mip) * (size >> mip) * 4 * data.componentBytes(), data.mip(mip).remaining());
                    var pixel = data.mip(levels - 1);
                    if (kind.startsWith("color_")) {
                        if (material.equals("ceramic")) {
                            assertTrue(Byte.toUnsignedInt(pixel.get(0)) > 240, "Indexed porcelain palette must decode to white RGB");
                            assertEquals(0, pixel.get(3), "Porcelain is dielectric; alpha is data, not coverage");
                        }
                        if (material.equals("satin_titanium")) assertEquals(255, Byte.toUnsignedInt(pixel.get(3)));
                    } else {
                        assertEquals(32768, Short.toUnsignedInt(pixel.getShort(0)), material.equals("ice") ? 16 : 2);
                        assertEquals(32768, Short.toUnsignedInt(pixel.getShort(2)), material.equals("ice") ? 16 : 2);
                    }
                }
            }
        }
    }

    @Test void rejectsCorruptOrIncompleteMipChains() throws Exception {
        byte[] source = Files.readAllBytes(ROOT.resolve("ceramic/normal_rough_height.ktx"));
        assertThrows(java.io.IOException.class, () -> UiMaterialTextureData.read(new java.io.ByteArrayInputStream(java.util.Arrays.copyOf(source, 100))));
        source[0] = 0;
        assertThrows(java.io.IOException.class, () -> UiMaterialTextureData.read(new java.io.ByteArrayInputStream(source)));
    }

    @Test void rejectsUnsupportedDimensionsAndMipCounts() throws Exception {
        byte[] source = Files.readAllBytes(ROOT.resolve("ceramic/normal_rough_height.ktx"));
        for (int[] field : new int[][]{{36, 0}, {36, -256}, {36, 255}, {36, 4096}, {40, 128},
                {44, 1}, {48, 1}, {52, 6}, {56, 0}, {56, 8}, {56, 10}}) {
            byte[] invalid = source.clone();
            java.nio.ByteBuffer.wrap(invalid).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(field[0], field[1]);
            assertThrows(java.io.IOException.class,
                    () -> UiMaterialTextureData.read(new java.io.ByteArrayInputStream(invalid)));
        }
    }
}
