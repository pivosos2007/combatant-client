/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are adapted from GFBS-glTF 1.5.1
 * (commit 8906d4083c23b5d5bbf5191a0555d149a5272900).
 * Copyright (c) 2026 LytharaLab. Original portions are licensed under the MIT License.
 * See THIRD_PARTY_LICENSES/GFBS-glTF-MIT.txt.
 *
 * Combatant modifications are distributed under GNU GPL v3.0 as part of Combatant.
 */
package combatant.client.render.engine.asset.model.data;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Set;

public final class ModelTexture {
    private static final Set<Integer> MAG_FILTERS = Set.of(9728, 9729);
    private static final Set<Integer> MIN_FILTERS = Set.of(9728, 9729, 9984, 9985, 9986, 9987);
    private static final Set<Integer> WRAP_MODES = Set.of(33071, 33648, 10497);

    private final String name;
    private final String mimeType;
    private final byte[] encodedImage;
    private final int magFilter;
    private final int minFilter;
    private final int wrapS;
    private final int wrapT;

    public ModelTexture(String name, String mimeType, ByteBuffer encodedImage,
                       int magFilter, int minFilter, int wrapS, int wrapT) {
        this.name = name == null ? "" : name;
        this.mimeType = Objects.requireNonNullElse(mimeType, "application/octet-stream");
        ByteBuffer source = Objects.requireNonNull(encodedImage, "encodedImage").duplicate();
        if (!source.hasRemaining()) throw new IllegalArgumentException("Texture image is empty");
        this.encodedImage = new byte[source.remaining()];
        source.get(this.encodedImage);
        if (!MAG_FILTERS.contains(magFilter)) throw new IllegalArgumentException("Unsupported magnification filter: " + magFilter);
        if (!MIN_FILTERS.contains(minFilter)) throw new IllegalArgumentException("Unsupported minification filter: " + minFilter);
        if (!WRAP_MODES.contains(wrapS) || !WRAP_MODES.contains(wrapT)) {
            throw new IllegalArgumentException("Unsupported texture wrap mode");
        }
        this.magFilter = magFilter;
        this.minFilter = minFilter;
        this.wrapS = wrapS;
        this.wrapT = wrapT;
    }

    public String name() { return name; }
    public String mimeType() { return mimeType; }
    public ByteBuffer encodedImage() { return ByteBuffer.wrap(encodedImage).asReadOnlyBuffer(); }
    public int encodedImageSize() { return encodedImage.length; }
    public int magFilter() { return magFilter; }
    public int minFilter() { return minFilter; }
    public int wrapS() { return wrapS; }
    public int wrapT() { return wrapT; }
}
