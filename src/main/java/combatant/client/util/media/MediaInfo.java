/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * This file belongs to Combatant's MediaPlayerInfo integration, based on
 * https://github.com/Redstonecrafter0/MediaPlayerInfo.
 * Copyright (c) Redstonecrafter0 and contributors.
 *
 * Licensed under the GNU Affero General Public License v3.0.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.util.media;

import java.util.Arrays;
import java.util.Objects;

/**
 * Modified Java port for Combatant by combatant.client.
 * Based on Redstonecrafter0/MediaPlayerInfo.
 */
public record MediaInfo(String title, String artist, byte[] artworkPng, long position, long duration, boolean playing) {
    private static final byte[] EMPTY_ARTWORK = new byte[0];

    public MediaInfo(String title, String artist, byte[] artworkPng, long position, long duration, boolean playing) {
        this.title = title != null ? title : "";
        this.artist = artist != null ? artist : "";
        this.artworkPng = artworkPng != null ? artworkPng : EMPTY_ARTWORK;
        this.position = position;
        this.duration = duration;
        this.playing = playing;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MediaInfo mediaInfo)) return false;
        return position == mediaInfo.position
                && duration == mediaInfo.duration
                && playing == mediaInfo.playing
                && Objects.equals(title, mediaInfo.title)
                && Objects.equals(artist, mediaInfo.artist);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, artist, position, duration, playing);
    }

    @Override
    public String toString() {
        return "MediaInfo(title='" + title + "', artist='" + artist + "', position=" + position
                + ", duration=" + duration + ", playing=" + playing + ")";
    }

    public byte[] copyArtworkPng() {
        return Arrays.copyOf(artworkPng, artworkPng.length);
    }
}
