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

package combatant.client.util.media.impl.win;

import combatant.client.util.media.IMediaSession;
import combatant.client.util.media.MediaInfo;
import combatant.client.util.media.RepeatMode;

/**
 * Modified Java/native port for Combatant by combatant.client.
 * Based on Redstonecrafter0/MediaPlayerInfo.
 */
public final class WindowsMediaSession implements IMediaSession {
    private final MediaInfo media;
    private final String owner;
    private final String sessionId;
    private final boolean supportsShuffleSnapshot;
    private final boolean shuffleActiveSnapshot;
    private final boolean supportsRepeatSnapshot;
    private final int repeatModeSnapshot;
    private final boolean supportsSeekSnapshot;

    public WindowsMediaSession(MediaInfo media,
                               String owner,
                               String sessionId,
                               boolean supportsShuffleSnapshot,
                               boolean shuffleActiveSnapshot,
                               boolean supportsRepeatSnapshot,
                               int repeatModeSnapshot,
                               boolean supportsSeekSnapshot) {
        this.media = media;
        this.owner = owner;
        this.sessionId = sessionId;
        this.supportsShuffleSnapshot = supportsShuffleSnapshot;
        this.shuffleActiveSnapshot = shuffleActiveSnapshot;
        this.supportsRepeatSnapshot = supportsRepeatSnapshot;
        this.repeatModeSnapshot = repeatModeSnapshot;
        this.supportsSeekSnapshot = supportsSeekSnapshot;
    }

    @Override
    public MediaInfo getMedia() {
        return media;
    }

    @Override
    public String getOwner() {
        return owner;
    }

    @Override
    public native void play();

    @Override
    public native void pause();

    @Override
    public native void playPause();

    @Override
    public native void stop();

    @Override
    public native void next();

    @Override
    public native void previous();

    private native boolean nativeSupportsShuffle();

    private native boolean nativeIsShuffleActive();

    private native void nativeSetShuffle(boolean active);

    private native boolean nativeSupportsRepeat();

    private native int nativeGetRepeatMode();

    private native void nativeSetRepeatMode(int mode);

    private native boolean nativeSupportsSeek();

    private native void nativeSeekTo(long positionSeconds);

    @Override
    public boolean supportsShuffle() {
        return supportsShuffleSnapshot;
    }

    @Override
    public boolean isShuffleActive() {
        return shuffleActiveSnapshot;
    }

    @Override
    public void setShuffle(boolean active) {
        nativeSetShuffle(active);
    }

    @Override
    public boolean supportsRepeat() {
        return supportsRepeatSnapshot;
    }

    @Override
    public RepeatMode getRepeatMode() {
        return switch (repeatModeSnapshot) {
            case 1 -> RepeatMode.ONE;
            case 2 -> RepeatMode.ALL;
            case 0 -> RepeatMode.OFF;
            default -> RepeatMode.UNKNOWN;
        };
    }

    @Override
    public void setRepeatMode(RepeatMode mode) {
        int value = switch (mode) {
            case ONE -> 1;
            case ALL -> 2;
            case OFF, UNKNOWN -> 0;
        };
        nativeSetRepeatMode(value);
    }

    @Override
    public boolean supportsSeek() {
        return supportsSeekSnapshot;
    }

    @Override
    public void seekTo(long positionSeconds) {
        nativeSeekTo(positionSeconds);
    }
}
