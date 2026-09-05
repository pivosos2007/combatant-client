/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.backend.vulkan;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.util.shaderc.Shaderc.*;

/** Minimal shaderc owner for stages Mojang's vertex/fragment-only ShaderType cannot represent. */
final class VulkanNativeShaderCompiler implements AutoCloseable {
    private final long compiler;
    private final long options;
    private boolean closed;

    VulkanNativeShaderCompiler() {
        compiler = shaderc_compiler_initialize();
        options = shaderc_compile_options_initialize();
        if (compiler == 0L || options == 0L) {
            if (options != 0L) shaderc_compile_options_release(options);
            if (compiler != 0L) shaderc_compiler_release(compiler);
            throw new IllegalStateException("Failed to initialize shaderc for Combatant native Vulkan stages");
        }
        shaderc_compile_options_set_target_env(options, shaderc_target_env_vulkan, shaderc_env_version_vulkan_1_2);
        shaderc_compile_options_set_generate_debug_info(options);
        shaderc_compile_options_set_optimization_level(options, shaderc_optimization_level_performance);
    }

    ByteBuffer compile(String label, String source, int shaderKind) {
        if (closed) throw new IllegalStateException("Vulkan native shader compiler is closed");
        ByteBuffer sourceUtf8 = MemoryUtil.memUTF8(source, false);
        ByteBuffer nameUtf8 = MemoryUtil.memUTF8(label == null ? "combatant-native" : label);
        ByteBuffer entryUtf8 = MemoryUtil.memUTF8("main");
        long result = 0L;
        try {
            result = shaderc_compile_into_spv(compiler, sourceUtf8, shaderKind, nameUtf8, entryUtf8, options);
            if (result == 0L) throw new IllegalStateException("shaderc returned null result for " + label);
            int status = shaderc_result_get_compilation_status(result);
            if (status != shaderc_compilation_status_success) {
                throw new IllegalStateException("Vulkan native shader compile failed for " + label + ":\n"
                        + shaderc_result_get_error_message(result));
            }
            ByteBuffer bytes = shaderc_result_get_bytes(result);
            ByteBuffer copy = MemoryUtil.memAlloc(bytes.remaining());
            copy.put(bytes.duplicate()).flip();
            return copy;
        } finally {
            if (result != 0L) shaderc_result_release(result);
            MemoryUtil.memFree(entryUtf8);
            MemoryUtil.memFree(nameUtf8);
            MemoryUtil.memFree(sourceUtf8);
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        shaderc_compile_options_release(options);
        shaderc_compiler_release(compiler);
    }
}
