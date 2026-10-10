/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.shader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable CPU/GPU shared struct layout following GLSL std430 alignment rules. */
public final class Std430StructLayout {
    public record Member(String name,
                         Std430Type type,
                         int offset,
                         int arrayLength,
                         int arrayStride,
                         int occupiedBytes,
                         boolean array,
                         Std430StructLayout structLayout) {
        /** Compatibility constructor for older callers creating scalar members. */
        public Member(String name, Std430Type type, int offset, int arrayLength, int arrayStride, int occupiedBytes) {
            this(name, type, offset, arrayLength, arrayStride, occupiedBytes, arrayLength > 1, null);
        }

        public Member {
            if (type == null && structLayout == null) throw new IllegalArgumentException("Missing std430 member type");
            if (type != null && structLayout != null) throw new IllegalArgumentException("Ambiguous std430 member type");
        }

        public int elementOffset(int index) {
            if (index < 0 || index >= arrayLength) {
                throw new IndexOutOfBoundsException(name + "[" + index + "] length=" + arrayLength);
            }
            return offset + index * arrayStride;
        }
    }

    private final List<Member> members;
    private final Map<String, Member> byName;
    private final int alignment;
    private final int size;

    private Std430StructLayout(List<Member> members, int alignment, int size) {
        this.members = List.copyOf(members);
        LinkedHashMap<String, Member> index = new LinkedHashMap<>();
        for (Member member : members) index.put(member.name(), member);
        this.byName = Map.copyOf(index);
        this.alignment = alignment;
        this.size = size;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<Member> members() {
        return members;
    }

    public Member member(String name) {
        Member member = byName.get(name);
        if (member == null) throw new IllegalArgumentException("Unknown std430 member: " + name);
        return member;
    }

    public int alignment() {
        return alignment;
    }

    public int size() {
        return size;
    }

    public int arrayStride() {
        return Std430Type.align(size, alignment);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Std430StructLayout layout
                && alignment == layout.alignment && size == layout.size && members.equals(layout.members);
    }

    @Override
    public int hashCode() {
        return Objects.hash(members, alignment, size);
    }

    public static final class Builder {
        private final ArrayList<MemberSpec> specs = new ArrayList<>();

        public Builder member(String name, Std430Type type) {
            return add(name, type, null, 1, false);
        }

        public Builder array(String name, Std430Type type, int length) {
            return add(name, type, null, length, true);
        }

        public Builder struct(String name, Std430StructLayout layout) {
            return add(name, null, layout, 1, false);
        }

        public Builder structArray(String name, Std430StructLayout layout, int length) {
            return add(name, null, layout, length, true);
        }

        private Builder add(String name, Std430Type type, Std430StructLayout nested, int length, boolean array) {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("name");
            if (type == null && nested == null) throw new IllegalArgumentException("type");
            if (length < 1) throw new IllegalArgumentException("length");
            if (specs.stream().anyMatch(spec -> spec.name.equals(name))) {
                throw new IllegalArgumentException("Duplicate std430 member: " + name);
            }
            specs.add(new MemberSpec(name, type, nested, length, array));
            return this;
        }

        public Std430StructLayout build() {
            if (specs.isEmpty()) return new Std430StructLayout(List.of(), 1, 0);
            ArrayList<Member> members = new ArrayList<>(specs.size());
            int cursor = 0;
            int structAlignment = 1;
            for (MemberSpec spec : specs) {
                int alignment = spec.type != null ? spec.type.alignment() : spec.nested.alignment();
                int occupiedSize = spec.type != null ? spec.type.size() : spec.nested.size();
                int stride = Std430Type.align(occupiedSize, alignment);
                int occupied = spec.array ? Math.multiplyExact(stride, spec.length) : occupiedSize;
                cursor = Std430Type.align(cursor, alignment);
                members.add(new Member(spec.name, spec.type, cursor, spec.length, stride, occupied, spec.array, spec.nested));
                cursor = Math.addExact(cursor, occupied);
                structAlignment = Math.max(structAlignment, alignment);
            }
            return new Std430StructLayout(members, structAlignment, Std430Type.align(cursor, structAlignment));
        }

        private record MemberSpec(String name, Std430Type type, Std430StructLayout nested, int length, boolean array) { }
    }
}
