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
package combatant.client.render.engine.asset.model.animation;

import java.util.List;
import java.util.Objects;

public final class AnimationClip {
    private final String name;
    private final List<AnimationChannel> channels;
    private final float duration;

    public AnimationClip(String name, List<AnimationChannel> channels) {
        this.name = name == null ? "" : name;
        Objects.requireNonNull(channels, "channels");
        this.channels = List.copyOf(channels);
        if (this.channels.isEmpty()) throw new IllegalArgumentException("A model animation must contain at least one channel");
        this.duration = channels.stream().map(AnimationChannel::sampler)
            .map(AnimationSampler::endTime).max(Float::compare).orElse(0.0f);
    }

    public String name() { return name; }
    public List<AnimationChannel> channels() { return channels; }
    public float duration() { return duration; }
}
