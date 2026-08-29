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

import org.lwjgl.BufferUtils;
import combatant.client.util.media.IMediaSession;
import combatant.client.util.media.MediaInfo;
import combatant.client.util.media.MediaPlayerInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Modified Java/native port for Combatant by combatant.client.
 * Based on Redstonecrafter0/MediaPlayerInfo.
 */
public final class WindowsMediaPlayerInfo implements MediaPlayerInfo {
    public static final WindowsMediaPlayerInfo INSTANCE = new WindowsMediaPlayerInfo();

    private static final int MAX_SESSIONS = 16;
    private static final int SESSION_ID_BYTES = 256;
    private static final int OWNER_BYTES = 256;
    private static final int TITLE_BYTES = 512;
    private static final int ARTIST_BYTES = 512;

    private static final int SESSION_ID_OFFSET = 0;
    private static final int OWNER_OFFSET = SESSION_ID_OFFSET + SESSION_ID_BYTES;
    private static final int TITLE_OFFSET = OWNER_OFFSET + OWNER_BYTES;
    private static final int ARTIST_OFFSET = TITLE_OFFSET + TITLE_BYTES;
    private static final int POSITION_OFFSET = ARTIST_OFFSET + ARTIST_BYTES;
    private static final int DURATION_OFFSET = POSITION_OFFSET + Long.BYTES;
    private static final int PLAYING_OFFSET = DURATION_OFFSET + Long.BYTES;
    private static final int SUPPORTS_SHUFFLE_OFFSET = PLAYING_OFFSET + Integer.BYTES;
    private static final int SHUFFLE_ACTIVE_OFFSET = SUPPORTS_SHUFFLE_OFFSET + Integer.BYTES;
    private static final int SUPPORTS_REPEAT_OFFSET = SHUFFLE_ACTIVE_OFFSET + Integer.BYTES;
    private static final int REPEAT_MODE_OFFSET = SUPPORTS_REPEAT_OFFSET + Integer.BYTES;
    private static final int SUPPORTS_SEEK_OFFSET = REPEAT_MODE_OFFSET + Integer.BYTES;
    private static final int RECORD_SIZE = SUPPORTS_SEEK_OFFSET + Integer.BYTES;
    private static final int MAX_ARTWORK_CACHE_SIZE = 8;

    private final ByteBuffer fallbackSnapshotBuffer =
            BufferUtils.createByteBuffer(RECORD_SIZE * MAX_SESSIONS).order(ByteOrder.nativeOrder());
    private final Map<String, String> trackKeyBySessionId = new LinkedHashMap<>();
    private final Map<String, byte[]> artworkByTrackKey = new LinkedHashMap<>(MAX_ARTWORK_CACHE_SIZE, 0.75f, true);
    private boolean closed;

    private WindowsMediaPlayerInfo() {
        loadNativeLibrary();
    }

    public static int snapshotBufferSize() {
        return RECORD_SIZE * MAX_SESSIONS;
    }

    private static String readString(ByteBuffer buffer, int offset, int maxBytes) {
        int length = 0;
        while (length < maxBytes && buffer.get(offset + length) != 0) {
            length++;
        }
        if (length == 0) return "";
        byte[] bytes = new byte[length];
        ByteBuffer duplicate = buffer.duplicate();
        duplicate.position(offset);
        duplicate.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String buildTrackKey(String sessionId, String title, String artist, long duration) {
        return sessionId + "\u0000" + title + "\u0000" + artist + "\u0000" + duration;
    }

    private static void loadNativeLibrary() {
        String resource = "/mediaplayerinfo/natives/win/MediaPlayerInfo.dll";
        try (InputStream stream = WindowsMediaPlayerInfo.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new UnsatisfiedLinkError("Missing native resource: " + resource);
            }
            Path dir = Files.createTempDirectory("combatant-mediainfo-");
            Path dll = dir.resolve("MediaPlayerInfo.dll");
            Files.copy(stream, dll);
            dll.toFile().deleteOnExit();
            dir.toFile().deleteOnExit();
            System.load(dll.toAbsolutePath().toString());
        } catch (IOException e) {
            UnsatisfiedLinkError error = new UnsatisfiedLinkError("Failed to load MediaPlayerInfo.dll");
            error.initCause(e);
            throw error;
        }
    }

    @Override
    public synchronized List<IMediaSession> getMediaSessions() {
        if (closed) return List.of();
        return getMediaSessions(fallbackSnapshotBuffer);
    }

    public synchronized List<IMediaSession> getMediaSessions(ByteBuffer buffer) {
        if (closed) return List.of();
        ByteBuffer snapshotBuffer = buffer.order(ByteOrder.nativeOrder());
        int maxSessions = Math.max(0, Math.min(MAX_SESSIONS, snapshotBuffer.capacity() / RECORD_SIZE));
        int count = Math.max(0, Math.min(maxSessions, fillSessionSnapshotBuffer(snapshotBuffer, maxSessions)));
        if (count == 0) return List.of();

        List<IMediaSession> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int offset = index * RECORD_SIZE;
            String sessionId = readString(snapshotBuffer, offset + SESSION_ID_OFFSET, SESSION_ID_BYTES);
            String owner = readString(snapshotBuffer, offset + OWNER_OFFSET, OWNER_BYTES);
            String title = readString(snapshotBuffer, offset + TITLE_OFFSET, TITLE_BYTES);
            String artist = readString(snapshotBuffer, offset + ARTIST_OFFSET, ARTIST_BYTES);
            long position = snapshotBuffer.getLong(offset + POSITION_OFFSET);
            long duration = snapshotBuffer.getLong(offset + DURATION_OFFSET);
            boolean playing = snapshotBuffer.getInt(offset + PLAYING_OFFSET) != 0;
            boolean supportsShuffle = snapshotBuffer.getInt(offset + SUPPORTS_SHUFFLE_OFFSET) != 0;
            boolean shuffleActive = snapshotBuffer.getInt(offset + SHUFFLE_ACTIVE_OFFSET) != 0;
            boolean supportsRepeat = snapshotBuffer.getInt(offset + SUPPORTS_REPEAT_OFFSET) != 0;
            int repeatMode = snapshotBuffer.getInt(offset + REPEAT_MODE_OFFSET);
            boolean supportsSeek = snapshotBuffer.getInt(offset + SUPPORTS_SEEK_OFFSET) != 0;
            String trackKey = buildTrackKey(sessionId, title, artist, duration);
            byte[] artwork = resolveArtwork(sessionId, trackKey);
            MediaInfo media = new MediaInfo(title, artist, artwork, position, duration, playing);
            result.add(new WindowsMediaSession(media, owner, sessionId, supportsShuffle, shuffleActive,
                    supportsRepeat, repeatMode, supportsSeek));
        }
        return result;
    }

    private native int fillSessionSnapshotBuffer(ByteBuffer buffer, int maxSessions);

    private native byte[] getArtworkPng(String sessionId);

    private native void nativeShutdown();

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        trackKeyBySessionId.clear();
        artworkByTrackKey.clear();
        try {
            nativeShutdown();
        } catch (Throwable ignored) {
        }
    }

    private byte[] resolveArtwork(String sessionId, String trackKey) {
        if (trackKey.equals(trackKeyBySessionId.get(sessionId))) {
            return artworkByTrackKey.getOrDefault(trackKey, new byte[0]);
        }

        trackKeyBySessionId.put(sessionId, trackKey);
        byte[] artwork;
        try {
            artwork = getArtworkPng(sessionId);
        } catch (Exception ignored) {
            artwork = new byte[0];
        }
        artworkByTrackKey.put(trackKey, artwork);
        while (artworkByTrackKey.size() > MAX_ARTWORK_CACHE_SIZE) {
            String oldest = artworkByTrackKey.keySet().iterator().next();
            artworkByTrackKey.remove(oldest);
        }
        return artwork;
    }
}
