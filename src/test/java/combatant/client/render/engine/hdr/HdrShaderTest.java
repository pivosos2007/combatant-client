/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.hdr;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.util.shaderc.Shaderc.*;

final class HdrShaderTest {
    @Test
    void transferRoundTripAndLinearBlend() {
        for (int i = 0; i <= 255; i++) {
            float encoded = i / 255f;
            assertEquals(encoded, SceneColor.linearToSrgb(SceneColor.srgbToLinear(encoded)), 2e-6f);
        }
        assertEquals(0.21404114f, SceneColor.srgbToLinear(.5f), 1e-6f);
        assertEquals(.7353569f, SceneColor.linearToSrgb(.5f), 1e-6f);
        assertTrue(SceneColor.linearToSrgb(4f) > 1f);
    }

    @Test
    void compilesSceneVariantsForOpenGlAndVulkan() throws Exception {
        String functions = resource("assets/combatant/shaders/include/scene_color.glsl");
        String oitDefines = RenderPipelines.OIT_ACCUMULATE_SNIPPET.shaderDefines().orElseThrow().asSourceDirectives();
        List<String> shaders = List.of("sky", "stars", "clouds", "entity", "block", "terrain",
                "particle", "world_border", "rendertype_end_portal", "rendertype_lines", "position",
                "position_color", "position_tex", "position_tex_color", "rendertype_beacon_beam",
                "rendertype_crumbling", "rendertype_entity_shadow", "rendertype_leash",
                "rendertype_lightning", "text", "glint", "item");
        long compiler = shaderc_compiler_initialize();
        long options = shaderc_compile_options_initialize();
        try {
            shaderc_compile_options_set_auto_bind_uniforms(options, true);
            shaderc_compile_options_set_auto_map_locations(options, true);
            for (boolean vulkan : new boolean[]{false, true}) {
                shaderc_compile_options_set_target_env(options,
                        vulkan ? shaderc_target_env_vulkan : shaderc_target_env_opengl,
                        vulkan ? shaderc_env_version_vulkan_1_2 : shaderc_env_version_opengl_4_5);
                for (String name : shaders) {
                    String source = expand("assets/minecraft/shaders/core/" + name + ".fsh", new HashSet<>());
                    String defines = name.equals("rendertype_end_portal")
                            ? RenderPipelines.END_PORTAL.getShaderDefines().asSourceDirectives() : "";
                    compile(compiler, options, name, HdrShaderSources.linearize(source, functions), defines);
                    if (source.contains("sampleColorForAccumulation")) {
                        compile(compiler, options, name + " OIT", HdrShaderSources.linearize(source, functions),
                                oitDefines);
                        compile(compiler, options, name + " additive OIT", HdrShaderSources.linearize(source, functions),
                                oitDefines + "#define OIT_ADDITIVE\n");
                    }
                }
                for (String path : List.of("assets/sodium/shaders/blocks/block_layer_opaque.fsh",
                        "assets/combatant/shaders/core/entity_dither.fsh", "assets/combatant/shaders/sodium/block_layer_opaque.fsh", "assets/combatant/shaders/pos_color.frag")) {
                    String source = HdrShaderSources.linearize(expand(path, new HashSet<>()), functions);
                    compile(compiler, options, path, source, "#define USE_FOG\n");
                    if (path.contains("combatant/shaders/sodium")) compile(compiler, options, path + " MRT", source,
                            "#define USE_FOG\n#define COMBATANT_DEFERRED_GBUFFER\n");
                    if (!path.endsWith("pos_color.frag")) compile(compiler, options, path + " OIT", source,
                            "#define USE_FOG\n" + oitDefines);
                }
                compileItemPermutations(compiler, options, functions);
                for (String name : List.of("scene_present.frag", "dream_sky_present.frag",
                        "dream_cloud_composite.frag", "dream_cloud_composite_hdr.frag", "reimagined_skybox.frag")) {
                    compile(compiler, options, name, expand("assets/combatant/shaders/" + name, new HashSet<>()), "");
                }
                try (var files = Files.list(Path.of("src/main/resources/assets/combatant/shaders/hdr"))) {
                    for (Path file : files.toList()) {
                        compile(compiler, options, file.toString(), Files.readString(file), "");
                    }
                }
            }
        } finally {
            shaderc_compile_options_release(options);
            shaderc_compiler_release(compiler);
        }
    }

    private static void compileItemPermutations(long compiler, long options, String functions) throws Exception {
        List<RenderPipeline> pipelines = new ArrayList<>(List.of(RenderPipelines.ITEM_CUTOUT,
                RenderPipelines.ITEM_CUTOUT_GLINT, RenderPipelines.ITEM_CUTOUT_GLINT_SPECIAL,
                RenderPipelines.ITEM_TRANSLUCENT, RenderPipelines.ITEM_TRANSLUCENT_GLINT,
                RenderPipelines.ITEM_TRANSLUCENT_GLINT_SPECIAL));
        for (var set : List.of(RenderPipelines.OIT_ITEM, RenderPipelines.OIT_ITEM_GLINT,
                RenderPipelines.OIT_ITEM_GLINT_SPECIAL)) {
            pipelines.add(set.depthBoundsPipeline());
            pipelines.add(set.transmittancePipeline());
            pipelines.add(set.accumulatePipeline());
        }
        String vertex = expand("assets/minecraft/shaders/core/item.vsh", new HashSet<>());
        String fragment = expand("assets/minecraft/shaders/core/item.fsh", new HashSet<>());
        for (var pipeline : pipelines) {
            boolean oit = pipeline.getShaderDefines().flags().contains("OIT");
            var formats = oit ? pipeline.getColorTargetStates().stream().map(state -> state.format()).toList()
                    : List.of(SceneColor.FORMAT);
            var variant = HdrPipelineVariants.variantFor(pipeline, formats, !oit);
            String defines = variant.getShaderDefines().asSourceDirectives();
            String converted = variant.getShaderDefines().flags().contains(HdrPipelineVariants.LEGACY_COLOR)
                    ? HdrShaderSources.linearize(fragment, functions) : fragment;
            compile(compiler, options, variant.getLocation() + ".vsh", vertex, defines);
            compile(compiler, options, variant.getLocation() + ".fsh", converted, defines);
        }
    }

    private static void compile(long compiler, long options, String name, String source, String defines) {
        source = source.replaceFirst("#version [^\\n]+", "#version 460 core\n" + defines);
        long result = shaderc_compile_into_spv(compiler, source,
                name.endsWith(".comp") ? shaderc_compute_shader
                        : name.endsWith(".vsh") ? shaderc_vertex_shader : shaderc_fragment_shader, name, "main", options);
        assertNotEquals(0, result);
        try {
            assertEquals(shaderc_compilation_status_success, shaderc_result_get_compilation_status(result),
                    name + ": " + shaderc_result_get_error_message(result));
        } finally {
            shaderc_result_release(result);
        }
    }

    private static String expand(String path, Set<String> included) throws Exception {
        String source = resource(path);
        var matcher = Pattern.compile("(?m)^\\s*#include\\s+([<\"])([^>\"]+)[>\"]").matcher(source);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(2);
            String include;
            if (matcher.group(1).equals("\"")) {
                include = Path.of(path).getParent().resolve(name).normalize().toString().replace('\\', '/');
            } else {
                String[] parts = name.split(":", 2);
                include = "assets/" + parts[0] + "/shaders/include/" + parts[1];
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(included.add(include) ? expand(include, included) : ""));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String resource(String path) throws Exception {
        Path local = Path.of("src/main/resources", path);
        if (Files.isRegularFile(local)) return Files.readString(local);
        try (var stream = HdrShaderTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
