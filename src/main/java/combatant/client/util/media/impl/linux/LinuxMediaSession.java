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

package combatant.client.util.media.impl.linux;

import org.freedesktop.dbus.ObjectPath;
import org.freedesktop.dbus.types.Variant;
import combatant.client.util.media.IMediaSession;
import combatant.client.util.media.MediaInfo;
import combatant.client.util.media.RepeatMode;
import combatant.client.util.media.impl.linux.dbus.Player;

import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Modified Java port for Combatant by combatant.client.
 * Based on Redstonecrafter0/MediaPlayerInfo.
 */
public final class LinuxMediaSession implements IMediaSession {
    private static final long SEEK_OVERRIDE_TOLERANCE_SECONDS = 2L;
    private static final long SEEK_OVERRIDE_TTL_MS = 15_000L;

    private static final ConcurrentHashMap<String, String> LAST_TRACK_ID_BY_OWNER = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> LAST_FALLBACK_KEY_BY_OWNER = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> PENDING_TRACK_BY_OWNER = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> LAST_NUDGED_TRACK_ID_BY_OWNER = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, SeekOverrideState> SEEK_OVERRIDE_BY_OWNER = new ConcurrentHashMap<>();

    private final Player dbus;
    private final String owner;
    private final MediaInfo media;
    private String trackId;

    LinuxMediaSession(Player dbus, String owner) {
        this.dbus = dbus;
        this.owner = owner;
        this.media = generateMediaInfo();
    }

    private static void rememberSeekOverride(String owner, String trackId, long targetPosition, boolean playing, long duration) {
        SEEK_OVERRIDE_BY_OWNER.put(owner, new SeekOverrideState(trackId, targetPosition, System.currentTimeMillis(), playing, duration));
    }

    private static void clearSeekOverride(String owner) {
        SEEK_OVERRIDE_BY_OWNER.remove(owner);
    }

    private static long resolveVisiblePosition(String owner,
                                               String trackId,
                                               long rawPosition,
                                               boolean playing,
                                               long duration,
                                               boolean pending) {
        if (pending) return 0L;

        long now = System.currentTimeMillis();
        SeekOverrideState override = SEEK_OVERRIDE_BY_OWNER.get(owner);
        if (override == null) return clampPosition(rawPosition, duration);
        if (override.trackId != null && trackId != null && !override.trackId.equals(trackId)) {
            clearSeekOverride(owner);
            return clampPosition(rawPosition, duration);
        }
        if (now - override.appliedAtMs > SEEK_OVERRIDE_TTL_MS) {
            clearSeekOverride(owner);
            return clampPosition(rawPosition, duration);
        }

        long expected = clampPosition(override.expectedPosition(now), duration);
        long clampedRaw = clampPosition(rawPosition, duration);
        long diff = Math.abs(clampedRaw - expected);
        if (diff <= SEEK_OVERRIDE_TOLERANCE_SECONDS) {
            clearSeekOverride(owner);
            return clampedRaw;
        }
        if (clampedRaw > expected + SEEK_OVERRIDE_TOLERANCE_SECONDS) {
            clearSeekOverride(owner);
            return clampedRaw;
        }
        if (!playing && clampedRaw >= expected) {
            clearSeekOverride(owner);
            return clampedRaw;
        }
        return expected;
    }

    private static long clampPosition(long position, long duration) {
        if (position < 0L) return 0L;
        if (duration > 0L) return Math.min(position, duration);
        return position;
    }

    @Override
    public String getOwner() {
        return owner;
    }

    @Override
    public MediaInfo getMedia() {
        return media;
    }

    @Override
    public void play() {
        dbus.Play();
    }

    @Override
    public void pause() {
        dbus.Pause();
    }

    @Override
    public void playPause() {
        dbus.PlayPause();
    }

    @Override
    public void stop() {
        dbus.Stop();
    }

    @Override
    public void next() {
        dbus.Next();
    }

    @Override
    public void previous() {
        dbus.Previous();
    }

    @Override
    public boolean supportsShuffle() {
        return getShuffleValue() != null;
    }

    @Override
    public boolean isShuffleActive() {
        Boolean value = getShuffleValue();
        return value != null && value;
    }

    @Override
    public void setShuffle(boolean active) {
        LinuxMediaPlayerInfo.INSTANCE.setProperty(owner, "Shuffle", active);
    }

    @Override
    public boolean supportsRepeat() {
        return getLoopStatus() != null;
    }

    @Override
    public RepeatMode getRepeatMode() {
        return RepeatMode.fromMpris(getLoopStatus());
    }

    @Override
    public void setRepeatMode(RepeatMode mode) {
        String value = switch (mode) {
            case OFF, UNKNOWN -> "None";
            case ONE -> "Track";
            case ALL -> "Playlist";
        };
        LinuxMediaPlayerInfo.INSTANCE.setProperty(owner, "LoopStatus", value);
    }

    @Override
    public boolean supportsSeek() {
        return getCanSeek();
    }

    @Override
    public void seekTo(long positionSeconds) {
        if (!getCanSeek()) return;
        long targetSeconds = Math.max(0L, positionSeconds);
        long targetUs = targetSeconds * 1_000_000L;
        BaseState state = readBaseState();
        String id = state.trackId != null ? state.trackId : trackId;
        trackId = id;

        try {
            if (id != null) {
                String source = "org.mpris.MediaPlayer2." + owner;
                dbus.SetPosition(new ObjectPath(source, id), targetUs);
                rememberSeekOverride(owner, id, targetSeconds, state.playing, state.duration);
                return;
            }
        } catch (Exception ignored) {
        }

        try {
            long currentUs = Math.max(0L, resolveSeekPosition(state)) * 1_000_000L;
            dbus.Seek(targetUs - currentUs);
            rememberSeekOverride(owner, id, targetSeconds, state.playing, state.duration);
        } catch (Exception ignored) {
        }
    }

    private MediaInfo generateMediaInfo() {
        BaseState state = readBaseState();
        String title = state.title;
        String artist = state.artist;
        long durationRaw = state.duration;
        String nextTrackId = state.trackId;
        String fallbackKey = buildFallbackKey(title, artist, durationRaw);
        boolean trackChanged = trackChanged(owner, nextTrackId, fallbackKey);
        long positionRaw = state.positionRaw;

        if (trackChanged) {
            PENDING_TRACK_BY_OWNER.put(owner, true);
            clearSeekOverride(owner);
        }
        if (Boolean.TRUE.equals(PENDING_TRACK_BY_OWNER.get(owner)) && positionRaw <= 2L) {
            PENDING_TRACK_BY_OWNER.remove(owner);
        }
        if (trackChanged && nextTrackId != null && positionRaw > 5L) {
            String lastNudged = LAST_NUDGED_TRACK_ID_BY_OWNER.get(owner);
            if (!Objects.equals(lastNudged, nextTrackId) && getCanSeek()) {
                try {
                    String source = "org.mpris.MediaPlayer2." + owner;
                    dbus.SetPosition(new ObjectPath(source, nextTrackId), 0L);
                    positionRaw = 0L;
                    LAST_NUDGED_TRACK_ID_BY_OWNER.put(owner, nextTrackId);
                    rememberSeekOverride(owner, nextTrackId, 0L, state.playing, durationRaw);
                } catch (Exception ignored) {
                }
            }
        }

        boolean pending = Boolean.TRUE.equals(PENDING_TRACK_BY_OWNER.get(owner));
        long position = resolveVisiblePosition(owner, nextTrackId, positionRaw, state.playing, durationRaw, pending);
        trackId = nextTrackId;
        byte[] artwork = readArtworkBytes(state.metadata);
        return new MediaInfo(title, artist, artwork, position, durationRaw, state.playing);
    }

    @SuppressWarnings("unchecked")
    private BaseState readBaseState() {
        Map<String, Object> metadata;
        try {
            Object raw = LinuxMediaPlayerInfo.INSTANCE.getProperty(owner, "Metadata");
            metadata = raw instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        } catch (Exception ignored) {
            metadata = Map.of();
        }

        boolean playing;
        try {
            playing = "Playing".equals(LinuxMediaPlayerInfo.INSTANCE.getProperty(owner, "PlaybackStatus"));
        } catch (Exception ignored) {
            playing = false;
        }

        return new BaseState(
                metadata,
                playing,
                readTitle(metadata),
                readArtist(metadata),
                readDurationSeconds(metadata),
                extractTrackId(metadata),
                readPositionSeconds()
        );
    }

    private boolean getCanSeek() {
        try {
            return switch (unwrap(LinuxMediaPlayerInfo.INSTANCE.getProperty(owner, "CanSeek"))) {
                case Boolean value -> value;
                case Number value -> value.intValue() != 0;
                case String value -> value.equalsIgnoreCase("true") || value.equals("1");
                case null, default -> false;
            };
        } catch (Exception ignored) {
            return false;
        }
    }

    private String extractTrackId(Map<String, Object> metadata) {
        Object raw = metadata.get("mpris:trackid");
        Object value = unwrap(raw);
        return value != null ? value.toString() : null;
    }

    private Boolean getShuffleValue() {
        try {
            Object value = unwrap(LinuxMediaPlayerInfo.INSTANCE.getProperty(owner, "Shuffle"));
            if (value instanceof Boolean bool) return bool;
            if (value instanceof Number number) return number.doubleValue() >= 0.5;
            if (value instanceof String string) {
                try {
                    return Double.parseDouble(string) >= 0.5;
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String getLoopStatus() {
        try {
            Object value = unwrap(LinuxMediaPlayerInfo.INSTANCE.getProperty(owner, "LoopStatus"));
            return value != null ? value.toString() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Object unwrap(Object value) {
        return value instanceof Variant<?> variant ? variant.getValue() : value;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String string) {
            try {
                return Long.parseLong(string);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private long readPositionSeconds() {
        Object raw;
        try {
            raw = LinuxMediaPlayerInfo.INSTANCE.getProperty(owner, "Position");
        } catch (Exception ignored) {
            return 0L;
        }
        Long positionUs = toLong(unwrap(raw));
        return positionUs != null ? positionUs / 1_000_000L : 0L;
    }

    private long readDurationSeconds(Map<String, Object> metadata) {
        Long durationUs = toLong(unwrap(metadata.get("mpris:length")));
        return durationUs != null ? durationUs / 1_000_000L : 0L;
    }

    private String readTitle(Map<String, Object> metadata) {
        Object raw = unwrap(metadata.get("xesam:title"));
        return raw instanceof String string ? string : "";
    }

    private String readArtist(Map<String, Object> metadata) {
        Object raw = unwrap(metadata.get("xesam:artist"));
        if (raw instanceof String string) return string;
        if (raw instanceof Object[] array) return joinObjects(Arrays.asList(array));
        if (raw instanceof List<?> list) return joinObjects(list);
        return "";
    }

    private String joinObjects(List<?> values) {
        StringBuilder builder = new StringBuilder();
        for (Object value : values) {
            if (value == null) continue;
            if (!builder.isEmpty()) builder.append(", ");
            builder.append(value);
        }
        return builder.toString();
    }

    private byte[] readArtworkBytes(Map<String, Object> metadata) {
        Object raw = unwrap(metadata.get("mpris:artUrl"));
        if (!(raw instanceof String url) || url.isBlank()) return new byte[0];
        try {
            URL connectionUrl = new URL(url);
            var connection = connectionUrl.openConnection();
            connection.setConnectTimeout(1500);
            connection.setReadTimeout(1500);
            try (InputStream stream = connection.getInputStream()) {
                return stream.readAllBytes();
            }
        } catch (Exception ignored) {
            return new byte[0];
        }
    }

    private String buildFallbackKey(String title, String artist, long duration) {
        if (title.isBlank() && artist.isBlank() && duration <= 0L) return null;
        return title + "\u0000" + artist + "\u0000" + duration;
    }

    private long resolveSeekPosition(BaseState state) {
        return resolveVisiblePosition(owner, state.trackId, state.positionRaw, state.playing, state.duration, false);
    }

    private boolean trackChanged(String owner, String trackId, String fallbackKey) {
        if (trackId != null) {
            String prev = LAST_TRACK_ID_BY_OWNER.put(owner, trackId);
            if (fallbackKey != null) {
                LAST_FALLBACK_KEY_BY_OWNER.put(owner, fallbackKey);
            }
            return prev != null && !prev.equals(trackId);
        }
        if (fallbackKey == null) return false;
        String prev = LAST_FALLBACK_KEY_BY_OWNER.put(owner, fallbackKey);
        return prev != null && !prev.equals(fallbackKey);
    }

    private record BaseState(Map<String, Object> metadata,
                             boolean playing,
                             String title,
                             String artist,
                             long duration,
                             String trackId,
                             long positionRaw) {
    }

    private record SeekOverrideState(String trackId,
                                     long targetPositionSeconds,
                                     long appliedAtMs,
                                     boolean playing,
                                     long duration) {
        long expectedPosition(long nowMs) {
            long elapsedSeconds = playing ? Math.max(0L, nowMs - appliedAtMs) / 1000L : 0L;
            long position = targetPositionSeconds + elapsedSeconds;
            return duration > 0L ? Math.min(position, duration) : position;
        }
    }
}
