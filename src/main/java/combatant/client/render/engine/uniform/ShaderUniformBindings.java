/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.uniform;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.rhi.shader.Std430StructLayout;
import combatant.client.render.engine.rhi.shader.Std430Type;
import combatant.client.render.engine.rhi.uniform.CombatantUniformAllocator;
import org.joml.Matrix4fc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;

public final class ShaderUniformBindings {
    private static final String RESOURCE = "/combatant/uniform-bindings-v2.tsv";
    private static final Map<String, Block> BLOCKS = load();

    private ShaderUniformBindings() {
    }

    public static Block block(String name) {
        Block block = BLOCKS.get(name);
        if (block == null) throw new IllegalArgumentException("Uniform block is absent from the manifest: " + name);
        return block;
    }

    private static Map<String, Block> load() {
        InputStream stream = ShaderUniformBindings.class.getResourceAsStream(RESOURCE);
        if (stream == null) throw new ExceptionInInitializerError("Missing generated uniform metadata " + RESOURCE);
        LinkedHashMap<String, Block> blocks = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (!"COMBATANT_UNIFORM_BINDINGS\t2".equals(header)) {
                throw new IOException("Unsupported uniform metadata header: " + header);
            }
            BlockBuilder current = null;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] fields = line.split("\\t", -1);
                switch (fields[0]) {
                    case "block" -> {
                        if (fields.length != 5 || current != null) throw new IOException("Malformed block row: " + line);
                        if (!fields[1].equals(fields[2]) && !fields[1].equals(fields[2] + "@std430")) {
                            throw new IOException("Invalid block key: " + line);
                        }
                        current = new BlockBuilder(fields[1], fields[2], fields[3], Integer.parseInt(fields[4]));
                    }
                    case "member" -> {
                        if (fields.length != 6 || current == null) throw new IOException("Malformed member row: " + line);
                        current.add(new Member(fields[1], fields[2], Integer.parseInt(fields[3]),
                                Integer.parseInt(fields[4]), Integer.parseInt(fields[5])));
                    }
                    case "end" -> {
                        if (fields.length != 1 || current == null) throw new IOException("Malformed block terminator: " + line);
                        Block block = current.build();
                        if (blocks.putIfAbsent(current.key, block) != null) {
                            throw new IOException("Duplicate uniform block " + current.key);
                        }
                        current = null;
                    }
                    default -> throw new IOException("Unknown uniform metadata row: " + line);
                }
            }
            if (current != null) throw new IOException("Unterminated uniform block " + current.name);
        } catch (IOException | RuntimeException exception) {
            throw new ExceptionInInitializerError(exception);
        }
        return Map.copyOf(blocks);
    }

    public record Block(String name, String standard, int size, Map<String, Member> members) {
        public Block {
            if (!"std140".equals(standard) && !"std430".equals(standard)) {
                throw new IllegalArgumentException("Unsupported uniform standard " + standard + " for " + name);
            }
            if (size <= 0) throw new IllegalArgumentException("Invalid uniform size for " + name + ": " + size);
            members = Collections.unmodifiableMap(new LinkedHashMap<>(members));
        }

        public Writer writer() {
            return new Writer(this);
        }

        public Member member(String name) {
            Member member = members.get(name);
            if (member == null) throw new IllegalArgumentException("Unknown member " + this.name + "." + name);
            return member;
        }

        public Std430StructLayout storageLayout() {
            if (!"std430".equals(standard)) throw new IllegalStateException(name + " is not std430");
            Std430StructLayout.Builder builder = Std430StructLayout.builder();
            for (Member member : members.values()) {
                Std430Type type = Std430Type.valueOf(member.type().toUpperCase());
                if (member.stride() > 0) builder.array(member.name(), type, member.count());
                else builder.member(member.name(), type);
            }
            Std430StructLayout layout = builder.build();
            if (layout.size() != size) {
                throw new IllegalStateException(name + " metadata size " + size + " != std430 layout " + layout.size());
            }
            return layout;
        }
    }

    public record Member(String name, String type, int offset, int count, int stride) {
        int offset(int index) {
            if (index < 0 || index >= count) throw new IndexOutOfBoundsException(name + "[" + index + "] length=" + count);
            return offset + (count > 1 ? index * stride : 0);
        }
    }

    public static final class Writer implements CombatantUniformAllocator.UniformWriter {
        private final Block block;
        private final ByteBuffer data;
        private final ByteBuffer readView;

        private Writer(Block block) {
            this.block = block;
            this.data = ByteBuffer.allocateDirect(block.size()).order(ByteOrder.nativeOrder());
            this.readView = data.asReadOnlyBuffer().order(data.order());
        }

        public int byteSize() {
            return block.size();
        }

        public ByteBuffer buffer() {
            return readView.position(0).limit(block.size());
        }

        public Writer vec4(String name, float x, float y, float z, float w) {
            return vec4(name, 0, x, y, z, w);
        }

        public Writer vec4(String name, int index, float x, float y, float z, float w) {
            Member member = require(name, "vec4");
            int position = member.offset(index);
            data.putFloat(position, x);
            data.putFloat(position + 4, y);
            data.putFloat(position + 8, z);
            data.putFloat(position + 12, w);
            return this;
        }

        public Writer mat4(String name, int index, Matrix4fc value) {
            if (value == null) throw new IllegalArgumentException(name + " matrix is null");
            Member member = require(name, "mat4");
            int position = member.offset(index);
            data.putFloat(position, value.m00());
            data.putFloat(position + 4, value.m01());
            data.putFloat(position + 8, value.m02());
            data.putFloat(position + 12, value.m03());
            data.putFloat(position + 16, value.m10());
            data.putFloat(position + 20, value.m11());
            data.putFloat(position + 24, value.m12());
            data.putFloat(position + 28, value.m13());
            data.putFloat(position + 32, value.m20());
            data.putFloat(position + 36, value.m21());
            data.putFloat(position + 40, value.m22());
            data.putFloat(position + 44, value.m23());
            data.putFloat(position + 48, value.m30());
            data.putFloat(position + 52, value.m31());
            data.putFloat(position + 56, value.m32());
            data.putFloat(position + 60, value.m33());
            return this;
        }

        public GpuBufferSlice upload(int expectedWritesPerFrame) {
            if (!"std140".equals(block.standard())) throw new IllegalStateException(block.name() + " is not a UBO");
            return CombatantRenderSystem.uniforms().write(
                    "Combatant - " + block.name() + " UBO", block.size(), expectedWritesPerFrame, this);
        }

        @Override
        public void write(ByteBuffer target) {
            if (target.remaining() < block.size()) {
                throw new IllegalArgumentException("GPU destination smaller than " + block.size());
            }
            int position = target.position();
            target.put(position, data, 0, block.size());
            target.position(position + block.size());
        }

        private Member require(String name, String type) {
            Member member = block.member(name);
            if (!type.equals(member.type())) {
                throw new IllegalArgumentException(block.name() + "." + name + " is " + member.type() + ", not " + type);
            }
            return member;
        }
    }

    private static final class BlockBuilder {
        private final String key;
        private final String name;
        private final String standard;
        private final int size;
        private final LinkedHashMap<String, Member> members = new LinkedHashMap<>();

        private BlockBuilder(String key, String name, String standard, int size) {
            this.key = key;
            this.name = name;
            this.standard = standard;
            this.size = size;
        }

        private void add(Member member) throws IOException {
            if (members.putIfAbsent(member.name(), member) != null) {
                throw new IOException("Duplicate member " + name + "." + member.name());
            }
        }

        private Block build() {
            if (members.isEmpty()) throw new IllegalArgumentException("Empty uniform block " + name);
            for (Member member : members.values()) {
                if (member.offset() < 0 || member.count() < 1 || member.stride() < 0) {
                    throw new IllegalArgumentException("Invalid metadata for " + name + "." + member.name());
                }
            }
            return new Block(name, standard, size, members);
        }
    }
}
