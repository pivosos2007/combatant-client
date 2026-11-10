/* Copyright (c) 2026 pivosos2007. GPL-3.0-only. */
package combatant.client.render.engine.shader;

import org.junit.jupiter.api.Test;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import java.lang.reflect.Proxy;
import java.util.Optional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.util.shaderc.Shaderc.*;
import static org.lwjgl.sdl.SDLInit.*;
import static org.lwjgl.sdl.SDLVideo.*;
import static org.lwjgl.sdl.SDLError.SDL_GetError;

final class DeferredGbufferShaderCompileTest {
    private static final Pattern INCLUDE = Pattern.compile("(?m)^\\s*#include <([^:>]+):([^>]+)>");

    @Test
    void compilesOpaqueAndCutoutCaptureForBothBackends() throws Exception {
        long compiler = shaderc_compiler_initialize();
        long options = shaderc_compile_options_initialize();
        assertNotEquals(0L, compiler);
        assertNotEquals(0L, options);
        try {
            shaderc_compile_options_set_auto_bind_uniforms(options, true);
            for (boolean vulkan : new boolean[] {false, true}) {
                // Legacy GL has standalone uniforms; RenderPearl's SPIR-V path requires explicit stage locations.
                shaderc_compile_options_set_auto_map_locations(options, !vulkan);
                shaderc_compile_options_set_target_env(options,
                        vulkan ? shaderc_target_env_vulkan : shaderc_target_env_opengl,
                        vulkan ? shaderc_env_version_vulkan_1_2 : shaderc_env_version_opengl_4_5);
                for (boolean capture : new boolean[] {false, true}) {
                    for (boolean cutout : new boolean[] {false, true}) {
                        for (String extension : new String[] {"vsh", "fsh"}) {
                            String source = expand(Files.readString(Path.of(
                                    "src/main/resources/assets/combatant/shaders/sodium/block_layer_opaque." + extension)));
                            source = source.replace("#version 330 core", "#version 460 core");
                            int headerEnd = source.indexOf('\n') + 1;
                            String defines = "#define USE_FOG\n#define USE_VERTEX_COMPRESSION\n";
                            if (capture) defines += "#define COMBATANT_DEFERRED_GBUFFER\n";
                            if (cutout) defines += "#define ALPHA_CUTOUT 0.1\n";
                            source = source.substring(0, headerEnd) + defines + source.substring(headerEnd);
                            String label = extension + ": vulkan=" + vulkan + ", capture=" + capture + ", cutout=" + cutout;
                            long result = shaderc_compile_into_spv(compiler, source,
                                    extension.equals("vsh") ? shaderc_vertex_shader : shaderc_fragment_shader,
                                    label, "main", options);
                            assertNotEquals(0L, result);
                            try {
                                assertEquals(shaderc_compilation_status_success, shaderc_result_get_compilation_status(result),
                                        label + ": " + shaderc_result_get_error_message(result));
                            } finally {
                                shaderc_result_release(result);
                            }
                        }
                    }
                }
            }
        } finally {
            shaderc_compile_options_release(options);
            shaderc_compiler_release(compiler);
        }
    }

    @Test
    void compilesDeferredComputeAndGraphicsForBothBackends() throws Exception {
        var paths = new java.util.ArrayList<Path>();
        try (var files = Files.walk(Path.of("src/dev/resources/assets/combatant/shaders"))) {
            files.filter(p -> p.toString().endsWith(".comp") || p.getFileName().toString().startsWith("deferred_")
                    && p.toString().endsWith(".frag")).sorted().forEach(paths::add);
        }
        assertTrue(paths.size() >= 22);
        long compiler = shaderc_compiler_initialize();
        long options = shaderc_compile_options_initialize();
        try {
            shaderc_compile_options_set_auto_bind_uniforms(options, true);
            for (boolean vulkan : new boolean[] {false, true}) {
                shaderc_compile_options_set_target_env(options,
                        vulkan ? shaderc_target_env_vulkan : shaderc_target_env_opengl,
                        vulkan ? shaderc_env_version_vulkan_1_2 : shaderc_env_version_opengl_4_5);
                for (Path path : paths) {
                    String source = loadRuntimeSource(path)
                            .replaceFirst("#version [0-9]+(?: core)?", "#version 460 core");
                    String label = path.getFileName() + ": vulkan=" + vulkan;
                    long result = shaderc_compile_into_spv(compiler, source,
                            path.toString().endsWith(".comp") ? shaderc_compute_shader : shaderc_fragment_shader,
                            label, "main", options);
                    assertNotEquals(0L, result);
                    try {
                        assertEquals(shaderc_compilation_status_success, shaderc_result_get_compilation_status(result),
                                label + ": " + shaderc_result_get_error_message(result));
                    } finally {
                        shaderc_result_release(result);
                    }
                }
            }
        } finally {
            shaderc_compile_options_release(options);
            shaderc_compiler_release(compiler);
        }
    }

    @Test
    void compilesRuntimeSourcesOnNativeOpenGl() throws Exception {
        assertTrue(SDL_InitSubSystem(SDL_INIT_VIDEO), SDL_GetError());
        long window = 0;
        long context = 0;
        try {
            SDL_GL_ResetAttributes();
            assertTrue(SDL_GL_SetAttribute(SDL_GL_CONTEXT_MAJOR_VERSION, 4), SDL_GetError());
            assertTrue(SDL_GL_SetAttribute(SDL_GL_CONTEXT_MINOR_VERSION, 6), SDL_GetError());
            window = SDL_CreateWindow("Deferred shader validation", 16, 16, SDL_WINDOW_OPENGL | SDL_WINDOW_HIDDEN);
            assertNotEquals(0L, window, SDL_GetError());
            context = SDL_GL_CreateContext(window);
            assertNotEquals(0L, context, SDL_GetError());
            assertTrue(SDL_GL_MakeCurrent(window, context), SDL_GetError());
            org.lwjgl.opengl.GL.createCapabilities();
            System.out.println("Native shader validation: " + org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER));
            try (var files = Files.walk(Path.of("src/dev/resources/assets/combatant/shaders"))) {
                for (Path path : files.filter(p -> p.toString().endsWith(".comp") || p.toString().endsWith(".frag")).toList()) {
                    boolean compute = path.toString().endsWith(".comp");
                    int shader = org.lwjgl.opengl.GL20.glCreateShader(compute
                            ? org.lwjgl.opengl.GL43.GL_COMPUTE_SHADER : org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER);
                    try {
                        org.lwjgl.opengl.GL20.glShaderSource(shader, loadRuntimeSource(path));
                        org.lwjgl.opengl.GL20.glCompileShader(shader);
                        assertEquals(org.lwjgl.opengl.GL11.GL_TRUE,
                                org.lwjgl.opengl.GL20.glGetShaderi(shader, org.lwjgl.opengl.GL20.GL_COMPILE_STATUS),
                                path + ": " + org.lwjgl.opengl.GL20.glGetShaderInfoLog(shader));
                        if (compute) {
                            int program = org.lwjgl.opengl.GL20.glCreateProgram();
                            try {
                                org.lwjgl.opengl.GL20.glAttachShader(program, shader);
                                org.lwjgl.opengl.GL20.glLinkProgram(program);
                                assertEquals(org.lwjgl.opengl.GL11.GL_TRUE,
                                        org.lwjgl.opengl.GL20.glGetProgrami(program, org.lwjgl.opengl.GL20.GL_LINK_STATUS),
                                        path + ": " + org.lwjgl.opengl.GL20.glGetProgramInfoLog(program));
                            } finally {
                                org.lwjgl.opengl.GL20.glDeleteProgram(program);
                            }
                        }
                    } finally {
                        org.lwjgl.opengl.GL20.glDeleteShader(shader);
                    }
                }
            }
        } finally {
            org.lwjgl.opengl.GL.setCapabilities(null);
            if (context != 0) SDL_GL_DestroyContext(context);
            if (window != 0) SDL_DestroyWindow(window);
            SDL_QuitSubSystem(SDL_INIT_VIDEO);
        }
    }

    @Test
    void devGraphHasOrderedLightingAndHistoryProducers() throws Exception {
        Class<?> graphClass;
        try {
            graphClass = Class.forName("combatant.client.render.engine.deferred.DeferredPassGraph");
        } catch (ClassNotFoundException unavailable) {
            org.junit.jupiter.api.Assumptions.abort("Requires Dev runtime on test classpath");
            return;
        }
        Object graph = graphClass.getConstructor().newInstance();
        var compiled = (combatant.client.render.engine.framegraph.CompiledFrameGraph)
                graphClass.getMethod("compile").invoke(graph);
        var labels = compiled.orderedPasses().stream().map(p -> p.label()).toList();
        for (String name : java.util.List.of("world.lighting.neutral", "world.lighting.publish",
                "world.local-light.shade", "world.radiance.capture", "world.indirect.temporal",
                "world.indirect.history", "world.reflection.resolve", "world.reflection.temporal",
                "world.reflection.history", "world.opaque.publish")) {
            assertTrue(labels.contains(name), name);
        }
        assertTrue(labels.indexOf("world.lighting.publish") < labels.indexOf("world.radiance.capture"));
        assertTrue(labels.indexOf("world.reflection.temporal") < labels.indexOf("world.reflection.history"));
        assertTrue(labels.contains("world.history.capture"));
        assertFalse(labels.stream().anyMatch(name -> name.startsWith("world.taa.")
                || name.startsWith("world.temporal.") || name.startsWith("world.post.")), labels.toString());
        assertFalse(compiled.dependencies().isEmpty());
    }

    private static String loadRuntimeSource(Path path) {
        ResourceManager resources = (ResourceManager) Proxy.newProxyInstance(
                ResourceManager.class.getClassLoader(), new Class<?>[]{ResourceManager.class},
                (proxy, method, args) -> {
                    if (!method.getName().equals("getResource")) throw new UnsupportedOperationException(method.getName());
                    Identifier id = (Identifier) args[0];
                    for (String root : new String[]{"src/dev/resources", "src/main/resources"}) {
                        Path resource = Path.of(root, "assets", id.getNamespace(), id.getPath());
                        if (Files.isRegularFile(resource)) {
                            return Optional.of(new Resource(null, () -> Files.newInputStream(resource)));
                        }
                    }
                    return Optional.empty();
                });
        String relative = Path.of("src/dev/resources/assets/combatant").relativize(path).toString().replace('\\', '/');
        Identifier id = Identifier.fromNamespaceAndPath("combatant", relative);
        String source = relative.endsWith(".comp")
                ? CombatantShaderSources.loadNativeStage(resources, id, ".comp")
                : CombatantShaderSources.load(resources, id);
        assertFalse(source.contains("#moj_import"), relative);
        assertFalse(source.contains("#include"), relative);
        return source;
    }

    private static String expand(String source) throws IOException {
        Matcher matcher = INCLUDE.matcher(source);
        StringBuilder expanded = new StringBuilder();
        while (matcher.find()) {
            String resource = "assets/" + matcher.group(1) + "/shaders/include/" + matcher.group(2);
            try (var stream = DeferredGbufferShaderCompileTest.class.getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(stream, resource);
                matcher.appendReplacement(expanded, Matcher.quoteReplacement(expand(
                        new String(stream.readAllBytes(), StandardCharsets.UTF_8))));
            }
        }
        matcher.appendTail(expanded);
        return expanded.toString();
    }
}
