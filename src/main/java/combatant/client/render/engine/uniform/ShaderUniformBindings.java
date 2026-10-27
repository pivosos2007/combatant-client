/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.uniform;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
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
import java.util.Locale;

public final class ShaderUniformBindings {
    private static final String RESOURCE = "/combatant/uniform-bindings-v3.tsv";
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
            if (!"COMBATANT_UNIFORM_BINDINGS\t3".equals(header)) {
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
            if (size < 0 || size == 0 && members.values().stream().noneMatch(Member::runtimeArray))
                throw new IllegalArgumentException("Invalid uniform size for " + name + ": " + size);
            members = Collections.unmodifiableMap(new LinkedHashMap<>(members));
        }

        public Writer writer() {
            if (runtimeSized()) throw new IllegalStateException(name + " requires a runtime array length");
            return new Writer(this, 0, size);
        }

        public Writer writer(int runtimeArrayLength) {
            return new Writer(this, runtimeArrayLength, size(runtimeArrayLength));
        }

        public boolean runtimeSized() {
            return members.values().stream().anyMatch(Member::runtimeArray);
        }

        public int size(int runtimeArrayLength) {
            if (runtimeArrayLength < 0) throw new IllegalArgumentException("runtimeArrayLength");
            Member tail = members.values().stream().filter(Member::runtimeArray).findFirst()
                    .orElseThrow(() -> new IllegalStateException(name + " has no runtime array"));
            return Math.addExact(tail.offset(), Math.multiplyExact(tail.stride(), runtimeArrayLength));
        }

        public Member member(String name) {
            return findMember(members, name);
        }

        public Std430StructLayout storageLayout() {
            if (!"std430".equals(standard)) throw new IllegalStateException(name + " is not std430");
            Std430StructLayout layout = storageLayoutOf(members);
            if (layout.size() != size) {
                throw new IllegalStateException(name + " metadata size " + size + " != std430 layout " + layout.size());
            }
            return layout;
        }
    }

    public record Member(String name, String type, int offset, int count, int stride, Map<String, Member> members) {
        public Member(String name, String type, int offset, int count, int stride) {
            this(name, type, offset, count, stride, Map.of());
        }

        public Member {
            members = Collections.unmodifiableMap(new LinkedHashMap<>(members));
        }

        public boolean runtimeArray() { return count == 0 && stride > 0; }

        public Member member(String path) { return findMember(members, path); }
    }

    private static Member findMember(Map<String, Member> members, String path) {
        if (path.indexOf('.') < 0) {
            Member member = members.get(path);
            if (member == null) throw new IllegalArgumentException("Unknown member " + path);
            return member;
        }
        Member member = null;
        for (String part : path.split("\\.", -1)) {
            member = members.get(part);
            if (member == null) throw new IllegalArgumentException("Unknown member " + path);
            members = member.members();
        }
        return member;
    }

    private static Std430StructLayout storageLayoutOf(Map<String, Member> members) {
        Std430StructLayout.Builder builder = Std430StructLayout.builder();
        for (Member member : members.values()) {
            if (!member.members().isEmpty()) {
                Std430StructLayout nested = storageLayoutOf(member.members());
                if (member.runtimeArray()) builder.runtimeStructArray(member.name(), nested);
                else if (member.stride() > 0) builder.structArray(member.name(), nested, member.count());
                else builder.struct(member.name(), nested);
            } else {
                Std430Type type = Std430Type.valueOf(member.type().toUpperCase(Locale.ROOT));
                if (member.runtimeArray()) builder.runtimeArray(member.name(), type);
                else if (member.stride() > 0) builder.array(member.name(), type, member.count());
                else builder.member(member.name(), type);
            }
        }
        Std430StructLayout layout = builder.build();
        for (Member member : members.values()) {
            Std430StructLayout.Member actual = layout.member(member.name());
            if (actual.offset() != member.offset() || member.stride() > 0 && actual.arrayStride() != member.stride())
                throw new IllegalArgumentException("Inconsistent std430 metadata for " + member.name());
        }
        return layout;
    }

    public static final class Writer implements CombatantUniformAllocator.UniformWriter {
        private final Block block;
        private final ByteBuffer data;
        private final ByteBuffer readView;
        private final int runtimeArrayLength;

        private Writer(Block block, int runtimeArrayLength, int byteSize) {
            this.block = block;
            this.runtimeArrayLength = runtimeArrayLength;
            this.data = ByteBuffer.allocateDirect(byteSize).order(ByteOrder.nativeOrder());
            this.readView = data.asReadOnlyBuffer().order(data.order());
        }

        public int byteSize() {
            return data.capacity();
        }

        public ByteBuffer buffer() {
            return readView.position(0).limit(byteSize());
        }

        public Writer vec4(String name, float x, float y, float z, float w) {
            return vec4(name, 0, x, y, z, w);
        }

        public Writer vec4(String name, int index, float x, float y, float z, float w) {
            require(name, "vec4");
            int position = singleIndexPosition(name, index);
            data.putFloat(position, x);
            data.putFloat(position + 4, y);
            data.putFloat(position + 8, z);
            data.putFloat(position + 12, w);
            return this;
        }

        public Writer mat4(String name, int index, Matrix4fc value) {
            if (value == null) throw new IllegalArgumentException(name + " matrix is null");
            require(name, "mat4");
            int position = singleIndexPosition(name, index);
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
            if (target.remaining() < byteSize()) {
                throw new IllegalArgumentException("GPU destination smaller than " + byteSize());
            }
            int position = target.position();
            target.put(position, data, 0, byteSize());
            target.position(position + byteSize());
        }

        public Writer floats(String path, float[] values, int... indices) {
            Member member = block.member(path);
            String type = member.type();
            int columns = 1, rows;
            if (type.equals("float")) rows = 1;
            else if (type.matches("vec[234]")) rows = type.charAt(3) - '0';
            else if (type.matches("mat[234](x[234])?")) {
                columns = type.charAt(3) - '0';
                rows = type.length() == 4 ? columns : type.charAt(5) - '0';
            } else throw new IllegalArgumentException(path + " is not a float scalar, vector or matrix");
            if (values.length != columns * rows) throw new IllegalArgumentException(path + " component count");
            int position = position(path, indices);
            int stride = columns == 1 ? 0 : block.standard().equals("std140") || rows > 2 ? 16 : 8;
            for (int column = 0; column < columns; column++)
                for (int row = 0; row < rows; row++)
                    data.putFloat(position + column * stride + row * 4, values[column * rows + row]);
            return this;
        }

        public Writer ints(String path, int[] values, int... indices) {
            String type = block.member(path).type();
            int count;
            if (type.equals("int") || type.equals("uint") || type.equals("bool")) count = 1;
            else if (type.matches("[iub]vec[234]")) count = type.charAt(4) - '0';
            else throw new IllegalArgumentException(path + " is not an integer or boolean scalar/vector");
            if (values.length != count) throw new IllegalArgumentException(path + " component count");
            int position = position(path, indices);
            for (int i = 0; i < count; i++)
                data.putInt(position + i * 4, type.startsWith("b") ? (values[i] == 0 ? 0 : 1) : values[i]);
            return this;
        }

        private int singleIndexPosition(String path, int index) {
            if (path.indexOf('.') < 0) {
                Member member = block.member(path);
                int count = member.runtimeArray() ? runtimeArrayLength : member.count();
                if (index < 0 || index >= count) throw new IndexOutOfBoundsException(path + "[" + index + "] length=" + count);
                return Math.addExact(member.offset(), Math.multiplyExact(index, member.stride()));
            }
            Map<String, Member> members = block.members();
            boolean array = false;
            for (String part : path.split("\\.", -1)) {
                Member member = members.get(part);
                if (member == null) throw new IllegalArgumentException("Unknown member " + path);
                array |= member.stride() > 0;
                members = member.members();
            }
            if (!array && index != 0) throw new IndexOutOfBoundsException(path + "[" + index + "] length=1");
            return position(path, array ? new int[]{index} : new int[0]);
        }

        private int position(String path, int... indices) {
            Map<String, Member> members = block.members();
            int position = 0, cursor = 0;
            for (String part : path.split("\\.", -1)) {
                Member member = members.get(part);
                if (member == null) throw new IllegalArgumentException("Unknown member " + path);
                position = Math.addExact(position, member.offset());
                if (member.stride() > 0) {
                    if (cursor >= indices.length) throw new IllegalArgumentException("Missing array index for " + path);
                    int index = indices[cursor++];
                    int count = member.runtimeArray() ? runtimeArrayLength : member.count();
                    if (index < 0 || index >= count) throw new IndexOutOfBoundsException(path + "[" + index + "] length=" + count);
                    position = Math.addExact(position, Math.multiplyExact(index, member.stride()));
                }
                members = member.members();
            }
            if (cursor != indices.length) throw new IllegalArgumentException("Extra array indices for " + path);
            return position;
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
                if (member.offset() < 0 || member.count() < 0 || member.stride() < 0
                        || member.count() != 1 && member.stride() == 0) {
                    throw new IllegalArgumentException("Invalid metadata for " + name + "." + member.name());
                }
            }
            Map<String, Member> tree = children("");
            for (Member member : members.values()) {
                findMember(tree, member.name());
                if (member.count() == 0 && (!standard.equals("std430") || member.name().contains(".")
                        || !member.name().equals(tree.keySet().stream().reduce((a, b) -> b).orElseThrow())))
                    throw new IllegalArgumentException("Invalid runtime array " + member.name());
            }
            Block block = new Block(name, standard, size, tree);
            if (standard.equals("std430")) block.storageLayout();
            return block;
        }

        private Map<String, Member> children(String prefix) {
            Map<String, Member> result = new LinkedHashMap<>();
            for (Member member : members.values()) {
                if (!member.name().startsWith(prefix)) continue;
                String local = member.name().substring(prefix.length());
                if (local.contains(".")) continue;
                result.put(local, new Member(local, member.type(), member.offset(), member.count(), member.stride(),
                        children(member.name() + ".")));
            }
            return result;
        }
    }
}
