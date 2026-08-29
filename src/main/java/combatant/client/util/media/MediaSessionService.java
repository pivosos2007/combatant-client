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

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import combatant.client.util.logging.DebugLog;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Locale;

public final class MediaSessionService {

    private static final long SESSION_GRACE_MS = 1500L;
    private static final Identifier ARTWORK_ID = Identifier.fromNamespaceAndPath("combatant", "media_player/artwork");
    private static final MediaSessionService INSTANCE = new MediaSessionService();

    private final Minecraft mc = Minecraft.getInstance();
    private final MediaPlayerInfo mediaPlayerInfo = MediaPlayerInfo.system();
    private volatile List<IMediaSession> mediaSessions = List.of();
    private volatile boolean pollingRunning;
    private Thread pollingThread;

    private String lastTrackKey;
    private IMediaSession currentSession;
    private MediaInfo currentMedia;
    private Identifier artworkTexture;
    private long lastSessionSeenMs;
    private int lastArtworkSize;

    private long lastRawPosition;
    private long lastRawDuration;
    private boolean lastRawPlaying;
    private long lastRawUpdateMs;
    private long lastDebugLogMs;
    private String lastDebugLogKey;

    private Snapshot snapshot = Snapshot.empty(isMediaAvailable());

    private MediaSessionService() {
    }

    public static MediaSessionService get() {
        return INSTANCE;
    }

    private static NativeImage getArtworkAsNativeImage(MediaInfo mediaInfo) {
        byte[] bytes = mediaInfo.artworkPng();
        if (bytes == null || bytes.length == 0) return null;
        try {
            return NativeImage.read(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            DebugLog.errorOnce("media-artwork-native-image", "[MediaSessionService] Failed to read artwork NativeImage", e);
            return null;
        }
    }

    private static String safe(String value) {
        if (value == null) return "";
        String v = value.trim();
        return v.isEmpty() ? "" : v;
    }

    public static boolean isMediaAvailable() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.startsWith("windows") || os.equals("linux");
    }

    public synchronized void init() {
        if (pollingThread != null && pollingThread.isAlive()) return;
        pollingRunning = true;
        pollingThread = new Thread(this::pollMediaSessions, "CombatantMediaInfoPollingThread");
        pollingThread.setDaemon(true);
        pollingThread.start();
    }

    public synchronized void shutdown() {
        pollingRunning = false;
        Thread thread = pollingThread;
        if (thread == null) {
            closeMediaBackend();
            mediaSessions = List.of();
            return;
        }

        thread.interrupt();
        if (thread != Thread.currentThread()) {
            try {
                thread.join(3000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        pollingThread = null;
        mediaSessions = List.of();
        currentSession = null;
        currentMedia = null;
    }

    public synchronized Snapshot snapshot() {
        refresh();
        return snapshot;
    }

    public synchronized void onResourceReload() {
        artworkTexture = null;
        lastArtworkSize = 0;
        lastTrackKey = null;
        currentSession = null;
        currentMedia = null;
        lastSessionSeenMs = 0L;
        lastDebugLogKey = null;
        snapshot = Snapshot.empty(isMediaAvailable());
    }

    public synchronized void playPause() {
        if (currentSession == null) return;
        MediaInfo media = resolveCurrentMedia();
        if (media == null) return;
        try {
            if (media.playing()) {
                applyLocalPlaybackState(false, media);
                currentSession.pause();
            } else {
                applyLocalPlaybackState(true, media);
                currentSession.play();
            }
        } catch (Throwable ignored) {
        }
    }

    public synchronized void play() {
        if (currentSession == null) return;
        MediaInfo media = resolveCurrentMedia();
        if (media == null) return;
        try {
            applyLocalPlaybackState(true, media);
            currentSession.play();
        } catch (Throwable ignored) {
        }
    }

    public synchronized void pause() {
        if (currentSession == null) return;
        MediaInfo media = resolveCurrentMedia();
        if (media == null) return;
        try {
            applyLocalPlaybackState(false, media);
            currentSession.pause();
        } catch (Throwable ignored) {
        }
    }

    public synchronized void stop() {
        if (currentSession == null) return;
        MediaInfo media = resolveCurrentMedia();
        if (media == null) return;
        try {
            applyLocalPlaybackState(false, media);
            currentSession.stop();
        } catch (Throwable ignored) {
        }
    }

    public synchronized void previous() {
        if (currentSession == null) return;
        try {
            currentSession.previous();
            resetPredictedAfterSkip();
        } catch (Throwable ignored) {
        }
    }

    public synchronized void next() {
        if (currentSession == null) return;
        try {
            currentSession.next();
            resetPredictedAfterSkip();
        } catch (Throwable ignored) {
        }
    }

    public synchronized boolean supportsShuffle() {
        return currentSession != null && currentSession.supportsShuffle();
    }

    public synchronized boolean isShuffleActive() {
        return currentSession != null && currentSession.isShuffleActive();
    }

    public synchronized void setShuffle(boolean active) {
        if (currentSession == null || !currentSession.supportsShuffle()) return;
        try {
            currentSession.setShuffle(active);
        } catch (Throwable ignored) {
        }
    }

    public synchronized void toggleShuffle() {
        if (currentSession == null || !currentSession.supportsShuffle()) return;
        try {
            currentSession.setShuffle(!currentSession.isShuffleActive());
        } catch (Throwable ignored) {
        }
    }

    public synchronized boolean supportsRepeat() {
        return currentSession != null && currentSession.supportsRepeat();
    }

    public synchronized RepeatMode getRepeatMode() {
        if (currentSession == null || !currentSession.supportsRepeat()) {
            return RepeatMode.UNKNOWN;
        }
        try {
            return currentSession.getRepeatMode();
        } catch (Throwable ignored) {
            return RepeatMode.UNKNOWN;
        }
    }

    public synchronized void setRepeatMode(RepeatMode mode) {
        if (currentSession == null || mode == null || !currentSession.supportsRepeat()) return;
        try {
            currentSession.setRepeatMode(mode);
        } catch (Throwable ignored) {
        }
    }

    public synchronized RepeatMode cycleRepeatMode() {
        RepeatMode current = getRepeatMode();
        RepeatMode next = switch (current) {
            case OFF, UNKNOWN -> RepeatMode.ALL;
            case ALL -> RepeatMode.ONE;
            case ONE -> RepeatMode.OFF;
        };
        setRepeatMode(next);
        return next;
    }

    public synchronized boolean supportsSeek() {
        return currentSession != null && currentSession.supportsSeek();
    }

    public synchronized void seekTo(long positionSeconds) {
        if (currentSession == null || !currentSession.supportsSeek()) return;
        try {
            currentSession.seekTo(positionSeconds);
            MediaInfo media = resolveCurrentMedia();
            if (media != null) {
                rememberLocalSeek(positionSeconds, media);
            }
        } catch (Throwable ignored) {
        }
    }

    private void refresh() {
        boolean mediaAvailable = isMediaAvailable();
        IMediaSession session = resolveSession();
        MediaInfo media = currentMedia;
        if (session != null && media == null) {
            media = resolveCurrentMedia();
            currentMedia = media;
        }

        boolean hasSession = session != null && media != null;
        long predictedPosition = hasSession ? getPredictedPosition(media) : 0L;
        long duration = hasSession ? lastRawDuration : 0L;
        snapshot = new Snapshot(mediaAvailable, session, media, artworkTexture, predictedPosition, duration, hasSession);
    }

    private IMediaSession resolveSession() {
        List<IMediaSession> sessions = mediaSessions;
        long now = Util.getMillis();
        if (sessions.isEmpty()) {
            if (currentSession != null && now - lastSessionSeenMs < SESSION_GRACE_MS) {
                return currentSession;
            }
            lastTrackKey = null;
            artworkTexture = null;
            lastArtworkSize = 0;
            currentSession = null;
            currentMedia = null;
            return null;
        }

        IMediaSession best = null;
        MediaInfo bestMedia = null;
        for (IMediaSession session : sessions) {
            if (session == null) continue;
            MediaInfo media;
            try {
                media = session.getMedia();
            } catch (Throwable ignored) {
                continue;
            }
            if (best == null) {
                best = session;
                bestMedia = media;
            }
            if (media.playing()) {
                best = session;
                bestMedia = media;
                break;
            }
        }
        if (best == null) return null;

        currentSession = best;
        lastSessionSeenMs = now;
        MediaInfo media = bestMedia;
        currentMedia = media;
        if (media == null) {
            return best;
        }

        String key = buildTrackKey(best, media);
        boolean keyChanged = !key.equals(lastTrackKey);
        boolean forceReset = shouldForceTrackReset(media);
        if (keyChanged || forceReset) {
            resetPredictedClock(media);
            if (keyChanged) {
                lastTrackKey = key;
            }
        }

        maybeUpdateArtwork(media, keyChanged || forceReset);
        debugLogMedia(best, media);
        return best;
    }

    private MediaInfo resolveCurrentMedia() {
        if (currentSession == null) return null;
        try {
            return currentSession.getMedia();
        } catch (Throwable ignored) {
            return currentMedia;
        }
    }

    private void updateArtwork(MediaInfo media) {
        artworkTexture = null;
        if (mc == null || media == null || media.artworkPng() == null || media.artworkPng().length == 0) return;
        NativeImage image = getArtworkAsNativeImage(media);
        if (image == null) return;
        DynamicTexture texture = new DynamicTexture(() -> "combatant_media_artwork", image);
        mc.getTextureManager().register(ARTWORK_ID, texture);
        artworkTexture = ARTWORK_ID;
    }

    private void pollMediaSessions() {
        try {
            while (pollingRunning) {
                try {
                    mediaSessions = List.copyOf(mediaPlayerInfo.getMediaSessions());
                } catch (Throwable t) {
                    DebugLog.errorOnce("media-session-poll", "[MediaSessionService] Failed to fetch media sessions", t);
                    mediaSessions = List.of();
                }

                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    pollingRunning = false;
                }
            }
        } finally {
            closeMediaBackend();
        }
    }

    private void closeMediaBackend() {
        try {
            mediaPlayerInfo.close();
        } catch (Throwable t) {
            DebugLog.errorOnce("media-session-shutdown", "[MediaSessionService] Failed to close media backend", t);
        }
    }

    private void maybeUpdateArtwork(MediaInfo media, boolean trackChanged) {
        if (media == null) {
            if (trackChanged) {
                artworkTexture = null;
                lastArtworkSize = 0;
            }
            return;
        }

        byte[] artwork = media.artworkPng();
        int size = artwork != null ? artwork.length : 0;
        if (size == 0) {
            if (trackChanged) {
                artworkTexture = null;
                lastArtworkSize = 0;
            }
            return;
        }

        if (trackChanged || artworkTexture == null || size != lastArtworkSize) {
            updateArtwork(media);
            lastArtworkSize = size;
        }
    }

    private long getPredictedPosition(MediaInfo media) {
        if (media == null) return 0L;
        long now = Util.getMillis();
        long rawPos = Math.max(0L, media.position());
        long rawDur = Math.max(0L, media.duration());
        long[] normalized = normalizeTime(rawPos, rawDur);
        rawPos = normalized[0];
        rawDur = normalized[1];
        boolean rawPlaying = media.playing();

        boolean changed = rawPos != lastRawPosition || rawDur != lastRawDuration || rawPlaying != lastRawPlaying;
        if (changed) {
            lastRawPosition = rawPos;
            lastRawDuration = rawDur;
            lastRawPlaying = rawPlaying;
            lastRawUpdateMs = now;
        }

        long predicted = lastRawPosition;
        if (lastRawPlaying) {
            long deltaMs = Math.max(0L, now - lastRawUpdateMs);
            predicted += deltaMs / 1000L;
        }
        if (lastRawDuration > 0L && predicted > lastRawDuration) {
            predicted = lastRawDuration;
        }
        return predicted;
    }

    private void resetPredictedAfterSkip() {
        long now = Util.getMillis();
        lastRawPosition = 0L;
        lastRawDuration = 0L;
        lastRawPlaying = true;
        lastRawUpdateMs = now;
    }

    private void applyLocalPlaybackState(boolean playing, MediaInfo media) {
        if (media == null) return;
        long now = Util.getMillis();
        long predicted = getPredictedPosition(media);
        long rawDur = Math.max(0L, media.duration());
        long[] normalized = normalizeTime(predicted, rawDur);
        lastRawPosition = normalized[0];
        lastRawDuration = normalized[1];
        lastRawPlaying = playing;
        lastRawUpdateMs = now;
    }

    private void rememberLocalSeek(long positionSeconds, MediaInfo media) {
        long now = Util.getMillis();
        long rawDur = Math.max(0L, media.duration());
        long[] normalized = normalizeTime(Math.max(0L, positionSeconds), rawDur);
        lastRawPosition = normalized[0];
        lastRawDuration = normalized[1];
        lastRawPlaying = media.playing();
        lastRawUpdateMs = now;
    }

    private void resetPredictedClock(MediaInfo media) {
        long now = Util.getMillis();
        if (media == null) {
            lastRawPosition = 0L;
            lastRawDuration = 0L;
            lastRawPlaying = false;
            lastRawUpdateMs = now;
            return;
        }
        long rawPos = Math.max(0L, media.position());
        long rawDur = Math.max(0L, media.duration());
        long[] normalized = normalizeTime(rawPos, rawDur);
        long pos = normalized[0];
        long dur = normalized[1];
        if (dur > 0L && pos > dur + 5L) {
            pos = 0L;
        }
        lastRawPosition = pos;
        lastRawDuration = dur;
        lastRawPlaying = media.playing();
        lastRawUpdateMs = now;
    }

    private boolean shouldForceTrackReset(MediaInfo media) {
        if (media == null) return false;
        long rawPos = Math.max(0L, media.position());
        if (rawPos > lastRawPosition) return false;
        long backJump = lastRawPosition - rawPos;
        return rawPos <= 2L && backJump >= 8L;
    }

    private long[] normalizeTime(long position, long duration) {
        long pos = Math.max(0L, position);
        long dur = Math.max(0L, duration);
        if (dur == 0L) return new long[]{pos, 0L};

        for (int i = 0; i < 6; i++) {
            if (pos > dur * 100L && pos >= 1000L) {
                pos /= 1000L;
                continue;
            }
            if (pos > 0L && dur > pos * 100L && dur >= 1000L) {
                dur /= 1000L;
                continue;
            }
            break;
        }
        return new long[]{pos, dur};
    }

    private String buildTrackKey(IMediaSession session, MediaInfo media) {
        String owner = session != null ? safe(session.getOwner()) : "";
        String title = media != null ? safe(media.title()) : "";
        String artist = media != null ? safe(media.artist()) : "";
        String durationKey = "";
        if (media != null) {
            long dur = Math.max(0L, media.duration());
            if (dur > 0L) durationKey = Long.toString(dur);
        }
        return owner + "\u0000" + title + "\u0000" + artist + "\u0000" + durationKey;
    }

    private void debugLogMedia(IMediaSession session, MediaInfo media) {
        if (!DebugLog.isEnabled()) return;
        long now = Util.getMillis();
        if (now - lastDebugLogMs < 1000L) return;
        lastDebugLogMs = now;
        String owner = session != null ? safe(session.getOwner()) : "";
        String title = media != null ? safe(media.title()) : "";
        String artist = media != null ? safe(media.artist()) : "";
        long pos = media != null ? media.position() : -1L;
        long dur = media != null ? media.duration() : -1L;
        boolean playing = media != null && media.playing();
        String debugKey = owner + "\u0000" + title + "\u0000" + artist + "\u0000" + dur + "\u0000" + playing;
        if (debugKey.equals(lastDebugLogKey)) return;
        lastDebugLogKey = debugKey;
        DebugLog.info("[MediaSessionService] owner=%s title=%s artist=%s pos=%d dur=%d playing=%s",
                owner, title, artist, pos, dur, playing);
    }

    public record Snapshot(boolean mediaAvailable,
                           IMediaSession session,
                           MediaInfo media,
                           Identifier artworkTexture,
                           long predictedPositionSeconds,
                           long durationSeconds,
                           boolean hasSession) {
        public static Snapshot empty(boolean mediaAvailable) {
            return new Snapshot(mediaAvailable, null, null, null, 0L, 0L, false);
        }

        public boolean isPlaying() {
            return media != null && media.playing();
        }

        public String owner() {
            return session != null ? safe(session.getOwner()) : "";
        }

        public String title() {
            return media != null ? safe(media.title()) : "";
        }

        public String artist() {
            return media != null ? safe(media.artist()) : "";
        }

        public boolean supportsShuffle() {
            return session != null && session.supportsShuffle();
        }

        public boolean isShuffleActive() {
            return session != null && session.isShuffleActive();
        }

        public boolean supportsRepeat() {
            return session != null && session.supportsRepeat();
        }

        public RepeatMode repeatMode() {
            if (session == null || !session.supportsRepeat()) {
                return RepeatMode.UNKNOWN;
            }
            try {
                return session.getRepeatMode();
            } catch (Throwable ignored) {
                return RepeatMode.UNKNOWN;
            }
        }

        public boolean supportsSeek() {
            return session != null && session.supportsSeek();
        }
    }
}
