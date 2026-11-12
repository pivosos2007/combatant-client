/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.hdr;

import combatant.client.render.engine.rhi.shader.*;
import net.minecraft.resources.Identifier;
import java.util.List;

/** Shared HDR exposure/bloom shader ABI; dispatch scheduling belongs to the consuming frame graph. */
public final class HdrPostKernels {
    private HdrPostKernels() { }
    public static final int LOCAL_SIZE = 8;
    public static final Identifier HISTOGRAM = id("hdr/exposure_histogram");
    public static final Identifier EXPOSURE_REDUCE = id("hdr/exposure_reduce");
    public static final Identifier BLOOM_EXTRACT = id("hdr/bloom_extract");
    public static final Identifier BLOOM_DOWNSAMPLE = id("hdr/bloom_downsample");
    public static final Identifier BLOOM_COPY = id("hdr/bloom_copy");
    public static final Identifier BLOOM_UPSAMPLE = id("hdr/bloom_upsample");
    public static final Identifier BLOOM_FINALIZE = id("hdr/bloom_finalize");
    public static final Identifier NEUTRAL_COLOR = id("hdr/post_neutral_color");

    public static final Std430StructLayout HISTOGRAM_ELEMENT = Std430StructLayout.builder()
            .member("count", Std430Type.UINT)
            .build();
    public static final Std430StructLayout EXPOSURE_STATE = Std430StructLayout.builder()
            .member("exposure", Std430Type.VEC4)
            .member("adaptation", Std430Type.VEC4)
            .member("percentiles", Std430Type.VEC4)
            .member("lifecycle", Std430Type.UVEC4)
            .build();
    public static final Std430StructLayout HISTOGRAM_PARAMS = Std430StructLayout.builder()
            .member("extentAndSampling", Std430Type.VEC4)
            .member("luminanceRange", Std430Type.VEC4)
            .member("weighting", Std430Type.VEC4)
            .build();
    public static final Std430StructLayout REDUCE_PARAMS = Std430StructLayout.builder()
            .member("histogram", Std430Type.VEC4)
            .member("percentiles", Std430Type.VEC4)
            .member("adaptation", Std430Type.VEC4)
            .member("rangeAndReset", Std430Type.VEC4)
            .member("historyAge", Std430Type.INT)
            .member("resetReason", Std430Type.INT)
            .member("resetPolicy", Std430Type.INT)
            .member("nextAge", Std430Type.INT)
            .build();
    public static final Std430StructLayout BLOOM_PARAMS = Std430StructLayout.builder()
            .member("params", Std430Type.VEC4)
            .build();

    public static final ShaderResourceLayout NEUTRAL_COLOR_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY)
    ));
    public static final ShaderResourceLayout HISTOGRAM_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_WRITE),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));
    public static final ShaderResourceLayout REDUCE_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_WRITE),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));
    public static final ShaderResourceLayout BLOOM_EXTRACT_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));
    public static final ShaderResourceLayout IMAGE_COPY_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY)
    ));
    public static final ShaderResourceLayout BLOOM_UPSAMPLE_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY)
    ));
    public static final ShaderResourceLayout BLOOM_FINALIZE_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("combatant", path);
    }
}
