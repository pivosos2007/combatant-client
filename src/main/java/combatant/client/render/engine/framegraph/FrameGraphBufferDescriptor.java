/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.framegraph;

import combatant.client.render.engine.rhi.shader.Std430StructLayout;
import combatant.client.render.engine.rhi.shader.StorageBufferDescriptor;

/** Exact physical requirements for a graph-owned storage/indirect buffer. */
public record FrameGraphBufferDescriptor(StorageBufferDescriptor buffer)
        implements FrameGraphPhysicalResourceDescriptor {
    public FrameGraphBufferDescriptor {
        if (buffer == null) throw new IllegalArgumentException("buffer");
    }

    @Override
    public FrameGraphResourceKind kind() {
        return FrameGraphResourceKind.BUFFER;
    }

    @Override
    public long approximateByteSize() {
        return buffer.byteSize();
    }

    @Override
    public boolean supports(FrameGraphAccess access) {
        if (access == null) return false;
        return (!access.reads() || buffer.access().readable())
                && (!access.writes() || buffer.access().writable());
    }

    @Override
    public boolean aliasCompatible(FrameGraphPhysicalResourceDescriptor other) {
        if (!(other instanceof FrameGraphBufferDescriptor value)) return false;
        StorageBufferDescriptor a = buffer;
        StorageBufferDescriptor b = value.buffer;
        return a.elementCapacity() == b.elementCapacity()
                && a.access() == b.access()
                && a.indirectSource() == b.indirectSource()
                && a.gpuOwned() == b.gpuOwned()
                && layoutCompatible(a.elementLayout(), b.elementLayout());
    }

    @Override
    public String debugDescription() {
        return "bytes=" + buffer.byteSize() + " capacity=" + buffer.elementCapacity()
                + " stride=" + buffer.elementLayout().arrayStride() + " access=" + buffer.access()
                + " indirect=" + buffer.indirectSource() + " gpuOwned=" + buffer.gpuOwned();
    }

    private static boolean layoutCompatible(Std430StructLayout a, Std430StructLayout b) {
        if (a == b) return true;
        return a.size() == b.size() && a.alignment() == b.alignment()
                && a.arrayStride() == b.arrayStride() && a.members().equals(b.members());
    }
}
