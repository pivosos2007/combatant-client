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

/**
 * Modified Java port for Combatant by combatant.client.
 * Based on Redstonecrafter0/MediaPlayerInfo.
 */
public interface IMediaSession {
    String getOwner();

    MediaInfo getMedia();

    void play();

    void pause();

    void playPause();

    void stop();

    void next();

    void previous();

    default boolean supportsShuffle() {
        return false;
    }

    default boolean isShuffleActive() {
        return false;
    }

    default void setShuffle(boolean active) {
    }

    default boolean supportsRepeat() {
        return false;
    }

    default RepeatMode getRepeatMode() {
        return RepeatMode.OFF;
    }

    default void setRepeatMode(RepeatMode mode) {
    }

    default boolean supportsSeek() {
        return false;
    }

    default void seekTo(long positionSeconds) {
    }
}
