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

/** Immutable CPU/GPU shared struct layout following GLSL std430 alignment rules. */
public final class Std430StructLayout {
    public record Member(String name,
                         Std430Type type,
                         int offset,
                         int arrayLength,
                         int arrayStride,
                         int occupiedBytes) {
        public boolean array() {
            return arrayLength > 1;
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

    public static final class Builder {
        private final ArrayList<MemberSpec> specs = new ArrayList<>();

        public Builder member(String name, Std430Type type) {
            return array(name, type, 1);
        }

        public Builder array(String name, Std430Type type, int length) {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("name");
            if (type == null) throw new IllegalArgumentException("type");
            if (length < 1) throw new IllegalArgumentException("length");
            for (MemberSpec spec : specs) {
                if (spec.name.equals(name)) throw new IllegalArgumentException("Duplicate std430 member: " + name);
            }
            specs.add(new MemberSpec(name, type, length));
            return this;
        }

        public Std430StructLayout build() {
            if (specs.isEmpty()) return new Std430StructLayout(List.of(), 1, 0);
            ArrayList<Member> members = new ArrayList<>(specs.size());
            int cursor = 0;
            int structAlignment = 1;
            for (MemberSpec spec : specs) {
                int alignment = spec.type.alignment();
                int stride = spec.type.arrayStride();
                int occupied = spec.length == 1 ? spec.type.size() : stride * spec.length;
                cursor = Std430Type.align(cursor, alignment);
                members.add(new Member(spec.name, spec.type, cursor, spec.length, stride, occupied));
                cursor += occupied;
                structAlignment = Math.max(structAlignment, alignment);
            }
            return new Std430StructLayout(
                    members,
                    structAlignment,
                    Std430Type.align(cursor, structAlignment)
            );
        }

        private record MemberSpec(String name, Std430Type type, int length) {
        }
    }
}
