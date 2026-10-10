/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Compiles real generated Java classes and validates byte packing using only the JDK. */
public final class CodegenSmoke {
    private CodegenSmoke() { }

    public static void main(String[] args) throws Exception {
        Path workspace = Path.of("").toAbsolutePath().normalize();
        Path generator = workspace.resolve("tools/uniform-codegen/UniformCodegen.java");
        Path type = workspace.resolve("src/main/java/combatant/client/render/engine/rhi/shader/Std430Type.java");
        Path struct = workspace.resolve("src/main/java/combatant/client/render/engine/rhi/shader/Std430StructLayout.java");
        Path runtime = workspace.resolve("src/main/java/combatant/client/render/engine/uniform/ShaderUniformBindings.java");
        Path temp = Files.createTempDirectory("combatant-uniform-binding-test-");
        try {
            Path shaders = Files.createDirectories(temp.resolve("shaders"));
            Path manifest = temp.resolve("uniform-bindings.manifest");
            Path generated = Files.createDirectories(temp.resolve("generated"));
            Path runtimeGenerated = Files.createDirectories(temp.resolve("runtime-generated"));
            Path stubs = Files.createDirectories(temp.resolve("stubs"));
            Path classes = Files.createDirectories(temp.resolve("classes"));
            put(shaders.resolve("sample.frag"), """
                    #version 430 core
                    #define COUNT (1 + 2)
                    struct Child { vec3 position; float weight; vec2 uv[2]; };
                    layout(std140) uniform SmokeStd140 {
                        mat2 matrix;
                        float a[COUNT];
                        Child children[2];
                        bool valid;
                    };
                    """);
            put(shaders.resolve("sample.comp"), """
                    #version 430 core
                    struct Child { float x; vec2 uv; };
                    layout(std430, binding = 2) readonly buffer SmokeStd430 {
                        Child children[2];
                        float end;
                        bvec3 flags;
                    } b;
                    """);
            put(manifest, "sample.frag\nsample.comp\n");
            put(shaders.resolve("runtime.frag"), """
                    layout(std140) uniform RuntimeBlock {
                        vec4 uBlendParams;
                        vec4 uBlendTone0;
                        vec4 uBlendTone1;
                        vec4 uExtras[2];
                    };
                    """);
            put(shaders.resolve("runtime.comp"), """
                    layout(std430) buffer RuntimeStorage {
                        float first;
                        vec2 second;
                        float last;
                        vec3 one[1];
                    };
                    """);
            Path runtimeManifest = temp.resolve("runtime.manifest");
            put(runtimeManifest, "runtime.frag\nruntime.comp\n");
            Path testSources = Files.createDirectories(temp.resolve("test-sources"));
            Path contractSource = testSources.resolve("RuntimeUniformUse.java");
            put(contractSource, """
                    import combatant.client.render.engine.uniform.ShaderUniformBindings;
                    class RuntimeUniformUse {
                        static final ShaderUniformBindings.Block BLOCK = ShaderUniformBindings.block("RuntimeBlock");
                        static final ShaderUniformBindings.Writer WRITER = BLOCK.writer();
                        static void test() {
                            WRITER.vec4("uBlendParams", 1f, 2f, 3f, 4f).vec4("uBlendTone0", 1f, 2f, 3f, 4f);
                        }
                    }
                    """);
            put(stubs.resolve("combatant/client/render/engine/rhi/uniform/CombatantUniformAllocator.java"), """
                    package combatant.client.render.engine.rhi.uniform;
                    import com.mojang.blaze3d.buffers.GpuBufferSlice;
                    import java.nio.ByteBuffer;
                    public final class CombatantUniformAllocator {
                        public interface UniformWriter { void write(ByteBuffer buffer); }
                        public GpuBufferSlice write(String name, int size, int expected, UniformWriter writer) {
                            return new GpuBufferSlice();
                        }
                    }
                    """);
            put(stubs.resolve("com/mojang/blaze3d/buffers/GpuBufferSlice.java"), """
                    package com.mojang.blaze3d.buffers;
                    public final class GpuBufferSlice { }
                    """);
            put(stubs.resolve("combatant/client/render/engine/core/CombatantRenderSystem.java"), """
                    package combatant.client.render.engine.core;
                    import combatant.client.render.engine.rhi.uniform.CombatantUniformAllocator;
                    public final class CombatantRenderSystem {
                        private static final CombatantUniformAllocator UNIFORMS = new CombatantUniformAllocator();
                        public static CombatantUniformAllocator uniforms() { return UNIFORMS; }
                    }
                    """);
            put(stubs.resolve("org/joml/Matrix4fc.java"), """
                    package org.joml;
                    public interface Matrix4fc {
                        float m00(); float m01(); float m02(); float m03();
                        float m10(); float m11(); float m12(); float m13();
                        float m20(); float m21(); float m22(); float m23();
                        float m30(); float m31(); float m32(); float m33();
                    }
                    """);
            put(stubs.resolve("org/joml/Matrix2fc.java"), """
                    package org.joml;
                    public interface Matrix2fc {
                        float m00(); float m01(); float m10(); float m11();
                    }
                    """);
            put(stubs.resolve("BindingPackingCheck.java"), """
                    import java.nio.*;
                    import combatant.client.render.engine.uniform.generated.*;
                    public final class BindingPackingCheck {
                        private static void same(float a, float b, String description) {
                            if (Float.compare(a, b) != 0) throw new AssertionError(description + ": " + b + " expected " + a);
                        }
                        private static void check(boolean condition, String description) {
                            if (!condition) throw new AssertionError(description);
                        }
                        public static void main(String[] args) {
                            SmokeStd140Binding.Writer writer = new SmokeStd140Binding.Writer()
                                .setA(1, 9f).setChildrenUv(1, 1, 11f, 12f)
                                .setChildrenWeight(0, 13f).setValid(true)
                                .setMatrix(new org.joml.Matrix2fc() {
                                    public float m00() {return 1;} public float m01() {return 2;}
                                    public float m10() {return 3;} public float m11() {return 4;}
                                });
                            ByteBuffer dest = ByteBuffer.allocate(SmokeStd140Binding.SIZE).order(ByteOrder.nativeOrder());
                            writer.write(dest);
                            check(dest.position() == SmokeStd140Binding.SIZE, "std140 byte count");
                            same(1f, dest.getFloat(0), "mat2 column 0 x");
                            same(2f, dest.getFloat(4), "mat2 column 0 y");
                            same(0f, dest.getFloat(8), "mat2 column padding");
                            same(3f, dest.getFloat(16), "mat2 column 1 x");
                            same(9f, dest.getFloat(48), "std140 scalar array stride");
                            same(13f, dest.getFloat(SmokeStd140Binding.OFFSET_CHILDREN_WEIGHT), "vec3 + float packing");
                            same(11f, dest.getFloat(SmokeStd140Binding.OFFSET_CHILDREN_UV
                                + SmokeStd140Binding.STRIDE_CHILDREN + SmokeStd140Binding.STRIDE_CHILDREN_UV), "nested array");
                            check(dest.getInt(SmokeStd140Binding.OFFSET_VALID) == 1, "bool encoding");
                            boolean failed = false;
                            try { writer.setA(SmokeStd140Binding.COUNT_A, 1f); }
                            catch (IndexOutOfBoundsException expected) { failed = true; }
                            check(failed, "bounds-checking");
                            SmokeStd430Binding.Writer storage = new SmokeStd430Binding.Writer()
                                .setChildrenX(1, 7f).setChildrenUv(0, 8f, 9f)
                                .setFlags(true, false, true);
                            ByteBuffer dst = ByteBuffer.allocate(SmokeStd430Binding.SIZE).order(ByteOrder.nativeOrder());
                            storage.write(dst);
                            check(SmokeStd430Binding.LAYOUT.size() == SmokeStd430Binding.SIZE, "std430 descriptor size");
                            check(SmokeStd430Binding.LAYOUT.member("children").array(), "struct array metadata");
                            check(SmokeStd430Binding.LAYOUT.member("children").structLayout().member("uv").offset() == 8,
                                "nested field metadata");
                            same(7f, dst.getFloat(16), "std430 nested struct array stride");
                            same(8f, dst.getFloat(8), "std430 vec2 alignment");
                            check(dst.getInt(48) == 1 && dst.getInt(52) == 0 && dst.getInt(56) == 1,
                                "bvec3 native bool encoding");
                            var block = combatant.client.render.engine.uniform.ShaderUniformBindings.block("RuntimeBlock");
                            check(block.size() == 80 && block.member("uBlendParams").offset() == 0
                                    && block.member("uBlendTone1").offset() == 32, "runtime metadata v2 loaded");
                            var runtimeWriter = block.writer().vec4("uBlendParams", 5f, 6f, 7f, 8f);
                            runtimeWriter.vec4("uExtras", 1, 11f, 12f, 13f, 14f);
                            same(5f, runtimeWriter.buffer().getFloat(0), "runtime buffer packing");
                            same(11f, runtimeWriter.buffer().getFloat(64), "runtime vec4 array offset");
                            boolean unknownFailed = false;
                            try { runtimeWriter.vec4("u_BlendParams", 1f, 2f, 3f, 4f); }
                            catch (IllegalArgumentException expected) { unknownFailed = true; }
                            check(unknownFailed, "undeclared GLSL field rejected");
                            var storageLayout = combatant.client.render.engine.uniform.ShaderUniformBindings
                                    .block("RuntimeStorage").storageLayout();
                            check(storageLayout.member("first").offset() == 0
                                    && storageLayout.member("second").offset() == 8
                                    && storageLayout.member("last").offset() == 16, "std430 metadata preserves declaration order");
                            check(storageLayout.member("one").array()
                                    && storageLayout.member("one").arrayStride() == 16,
                                    "std430 one-element arrays retain array metadata");
                            System.out.println("byte-level std140/std430 generated and runtime binding checks passed");
                        }
                    }
                    """);

            run(workspace, javaCommand(), generator.toString(), "generate",
                    shaders.toString(), manifest.toString(), generated.toString());
            run(workspace, javaCommand(), generator.toString(), "generate",
                    shaders.toString(), runtimeManifest.toString(), runtimeGenerated.toString(), classes.toString());
            Path contract = classes.resolve("combatant/uniform-bindings-v2.tsv");
            run(workspace, javaCommand(), generator.toString(), "verify", contract.toString(), testSources.toString());
            put(contractSource, Files.readString(contractSource).replace("uBlendParams", "u_BlendParams"));
            runExpectFailure(workspace, "RuntimeBlock.u_BlendParams is missing",
                    javaCommand(), generator.toString(), "verify", contract.toString(), testSources.toString());
            List<String> compile = new ArrayList<>(List.of(javacCommand(), "--release", "17", "-d", classes.toString(),
                    type.toString(), struct.toString(), runtime.toString()));
            try (Stream<Path> sources = Files.walk(temp)) {
                sources.filter(p -> p.toString().endsWith(".java")).sorted()
                        .forEach(p -> compile.add(p.toString()));
            }
            run(workspace, compile.toArray(String[]::new));
            run(workspace, javaCommand(), "-cp", classes.toString(), "BindingPackingCheck");
            System.out.println("CodegenSmoke: generated Java compilation and buffer checks passed");
        } finally {
            try (Stream<Path> paths = Files.walk(temp)) {
                for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
            }
        }
    }

    private static void put(Path path, String contents) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, contents, StandardCharsets.UTF_8);
    }

    private static String javaCommand() { return Path.of(System.getProperty("java.home"), "bin", "java").toString(); }
    private static String javacCommand() { return Path.of(System.getProperty("java.home"), "bin", "javac").toString(); }

    private static void runExpectFailure(Path workdir, String expectedError, String... command) throws Exception {
        Process process = new ProcessBuilder(command).directory(workdir.toFile())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        if (exit == 0 || !output.contains(expectedError)) {
            throw new AssertionError("Expected verifier rejection '" + expectedError + "', got exit=" + exit + ": " + output);
        }
        System.out.println("Verified rejection of misspelled Java-to-GLSL binding");
    }

    private static void run(Path workdir, String... command) throws Exception {
        Process process = new ProcessBuilder(command).directory(workdir.toFile())
                .inheritIO().start();
        int exit = process.waitFor();
        if (exit != 0) throw new IllegalStateException("Command exited " + exit + ": " + String.join(" ", command));
    }
}
