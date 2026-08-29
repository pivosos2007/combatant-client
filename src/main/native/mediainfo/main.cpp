// Modified native MediaPlayerInfo bridge for Combatant by pivosos2007.
// Based on Redstonecrafter0/MediaPlayerInfo.
#include "main.h"

#include <iostream>
#include <vector>
#include <fstream>
#include <unordered_map>
#include <string>
#include <cstdlib>
#include <algorithm>
#include <cstring>
#include <mutex>
#include <thread>
#include <winrt/Windows.Foundation.Collections.h>
#include <winrt/Windows.Media.Control.h>
#include <winrt/Windows.Media.h>
#include <winrt/Windows.Storage.h>
#include <winrt/Windows.Storage.Streams.h>

using namespace winrt;
using namespace Windows::Media::Control;
using namespace Windows::Storage::Streams;

struct TrackState {
    winrt::hstring title;
    winrt::hstring artist;
    long long durationSec = 0;
    bool pending = false;
    int64_t lastTimelineUpdate = 0;
    long long lastRawPosSec = -1;
    bool nudgedThisTrack = false;
};

static std::unordered_map<std::wstring, TrackState> TRACK_STATE;
static std::mutex MANAGER_MUTEX;
static GlobalSystemMediaTransportControlsSessionManager MANAGER{ nullptr };
static std::thread::id MANAGER_OWNER_THREAD{};
static bool MANAGER_APARTMENT_INITIALIZED = false;

constexpr int SESSION_ID_BYTES = 256;
constexpr int OWNER_BYTES = 256;
constexpr int TITLE_BYTES = 512;
constexpr int ARTIST_BYTES = 512;
constexpr int SESSION_ID_OFFSET = 0;
constexpr int OWNER_OFFSET = SESSION_ID_OFFSET + SESSION_ID_BYTES;
constexpr int TITLE_OFFSET = OWNER_OFFSET + OWNER_BYTES;
constexpr int ARTIST_OFFSET = TITLE_OFFSET + TITLE_BYTES;
constexpr int POSITION_OFFSET = ARTIST_OFFSET + ARTIST_BYTES;
constexpr int DURATION_OFFSET = POSITION_OFFSET + sizeof(int64_t);
constexpr int PLAYING_OFFSET = DURATION_OFFSET + sizeof(int64_t);
constexpr int SUPPORTS_SHUFFLE_OFFSET = PLAYING_OFFSET + sizeof(int32_t);
constexpr int SHUFFLE_ACTIVE_OFFSET = SUPPORTS_SHUFFLE_OFFSET + sizeof(int32_t);
constexpr int SUPPORTS_REPEAT_OFFSET = SHUFFLE_ACTIVE_OFFSET + sizeof(int32_t);
constexpr int REPEAT_MODE_OFFSET = SUPPORTS_REPEAT_OFFSET + sizeof(int32_t);
constexpr int SUPPORTS_SEEK_OFFSET = REPEAT_MODE_OFFSET + sizeof(int32_t);
constexpr int RECORD_SIZE = SUPPORTS_SEEK_OFFSET + sizeof(int32_t);

static GlobalSystemMediaTransportControlsSessionManager getManager() {
    std::lock_guard<std::mutex> lock(MANAGER_MUTEX);
    if (MANAGER != nullptr) return MANAGER;

    bool apartmentInitialized = false;
    try {
        winrt::init_apartment(winrt::apartment_type::multi_threaded);
        apartmentInitialized = true;
    } catch (...) {
    }

    try {
        MANAGER = GlobalSystemMediaTransportControlsSessionManager::RequestAsync().get();
        MANAGER_OWNER_THREAD = std::this_thread::get_id();
        MANAGER_APARTMENT_INITIALIZED = apartmentInitialized;
        return MANAGER;
    } catch (...) {
        if (apartmentInitialized) {
            try {
                winrt::uninit_apartment();
            } catch (...) {
            }
        }
        throw;
    }
}

static void shutdownManager() {
    bool uninitApartment = false;
    {
        std::lock_guard<std::mutex> lock(MANAGER_MUTEX);
        TRACK_STATE.clear();
        MANAGER = nullptr;
        if (MANAGER_APARTMENT_INITIALIZED && MANAGER_OWNER_THREAD == std::this_thread::get_id()) {
            uninitApartment = true;
        }
        MANAGER_APARTMENT_INITIALIZED = false;
        MANAGER_OWNER_THREAD = std::thread::id{};
    }

    if (uninitApartment) {
        try {
            winrt::uninit_apartment();
        } catch (...) {
        }
    }
}

static void writeInt(char* record, int offset, int32_t value) {
    std::memcpy(record + offset, &value, sizeof(value));
}

static void writeLong(char* record, int offset, int64_t value) {
    std::memcpy(record + offset, &value, sizeof(value));
}

static void writeString(char* record, int offset, int maxBytes, const std::string& value) {
    std::memset(record + offset, 0, maxBytes);
    if (value.empty() || maxBytes <= 1) return;
    const size_t bytesToCopy = std::min(value.size(), static_cast<size_t>(maxBytes - 1));
    std::memcpy(record + offset, value.data(), bytesToCopy);
}

static int repeatModeToInt(GlobalSystemMediaTransportControlsSessionPlaybackInfo const& playbackInfo) {
    try {
        auto ref = playbackInfo.AutoRepeatMode();
        if (!ref) return -1;
        switch (ref.Value()) {
            case Windows::Media::MediaPlaybackAutoRepeatMode::Track:
                return 1;
            case Windows::Media::MediaPlaybackAutoRepeatMode::List:
                return 2;
            case Windows::Media::MediaPlaybackAutoRepeatMode::None:
            default:
                return 0;
        }
    } catch (...) {
        return -1;
    }
}

static GlobalSystemMediaTransportControlsSession findSessionById(const std::string& sessionId) {
    auto sessions = getManager().GetSessions();
    auto target = winrt::to_hstring(sessionId);
    for (uint32_t i = 0; i < sessions.Size(); ++i) {
        auto session = sessions.GetAt(i);
        if (session.SourceAppUserModelId() == target) {
            return session;
        }
    }
    return nullptr;
}

static GlobalSystemMediaTransportControlsSession sessionFromObject(JNIEnv* env, jobject obj) {
    jfieldID sessionIdField = env->GetFieldID(env->GetObjectClass(obj), "sessionId", "Ljava/lang/String;");
    auto jSessionId = static_cast<jstring>(env->GetObjectField(obj, sessionIdField));
    if (jSessionId == nullptr) return nullptr;
    const char* raw = env->GetStringUTFChars(jSessionId, nullptr);
    std::string sessionId = raw == nullptr ? std::string() : std::string(raw);
    if (raw != nullptr) {
        env->ReleaseStringUTFChars(jSessionId, raw);
    }
    return findSessionById(sessionId);
}

jint Java_combatant_client_util_media_impl_win_WindowsMediaPlayerInfo_fillSessionSnapshotBuffer(JNIEnv* env, jobject obj, jobject buffer, jint maxSessions) {
    if (buffer == nullptr || maxSessions <= 0) return 0;
    auto* base = static_cast<char*>(env->GetDirectBufferAddress(buffer));
    if (base == nullptr) return 0;

    auto sessions = getManager().GetSessions();
    int count = std::min(static_cast<int>(sessions.Size()), static_cast<int>(maxSessions));
    for (int i = 0; i < count; ++i) {
        char* record = base + (i * RECORD_SIZE);
        std::memset(record, 0, RECORD_SIZE);

        auto session = sessions.GetAt(i);
        auto mediaProperties = session.TryGetMediaPropertiesAsync().get();
        auto timeline = session.GetTimelineProperties();
        auto playbackInfo = session.GetPlaybackInfo();
        auto controls = playbackInfo.Controls();

        auto sessionId = to_string(session.SourceAppUserModelId());
        auto title = to_string(mediaProperties.Title());
        auto artist = to_string(mediaProperties.Artist());
        int64_t rawPosSec = std::chrono::duration_cast<std::chrono::seconds>(timeline.Position()).count();
        int64_t durationSec = std::chrono::duration_cast<std::chrono::seconds>(timeline.EndTime() - timeline.StartTime()).count();
        int64_t timelineUpdate = timeline.LastUpdatedTime().time_since_epoch().count();
        bool playing = playbackInfo.PlaybackStatus() == GlobalSystemMediaTransportControlsSessionPlaybackStatus::Playing;

        std::wstring ownerKey = session.SourceAppUserModelId().c_str();
        TrackState& state = TRACK_STATE[ownerKey];
        long long prevRawPosSec = state.lastRawPosSec;
        int64_t prevTimelineUpdate = state.lastTimelineUpdate;
        bool titleChanged = (state.title != mediaProperties.Title()) || (state.artist != mediaProperties.Artist());
        bool durationChanged = (state.durationSec > 0 && durationSec > 0 && llabs(durationSec - state.durationSec) >= 2);
        bool trackChanged = titleChanged || (durationChanged && rawPosSec <= 2);
        if (trackChanged) {
            bool hadTrack = (state.title.size() > 0) || (state.artist.size() > 0);
            state.title = mediaProperties.Title();
            state.artist = mediaProperties.Artist();
            state.durationSec = durationSec;
            state.pending = hadTrack;
            state.lastTimelineUpdate = timelineUpdate;
            state.nudgedThisTrack = false;
        } else if (state.durationSec == 0 && durationSec > 0) {
            state.durationSec = durationSec;
        }

        if (trackChanged && !state.nudgedThisTrack && rawPosSec > 5) {
            bool positionBeyondDuration = (durationSec > 0 && rawPosSec > (durationSec + 2));
            bool timelineStale = (prevTimelineUpdate != 0 && timelineUpdate == prevTimelineUpdate);
            bool positionUnchanged = (prevRawPosSec > 0 && rawPosSec == prevRawPosSec);
            bool shouldNudge = positionBeyondDuration || (timelineStale && positionUnchanged);
            try {
                if (shouldNudge && controls.IsPlaybackPositionEnabled()) {
                    session.TryChangePlaybackPositionAsync(0).get();
                    rawPosSec = 0;
                    state.nudgedThisTrack = true;
                }
            } catch (...) {
            }
        }

        int64_t positionSec = rawPosSec;
        if (playing && !state.pending) {
            try {
                positionSec = std::chrono::duration_cast<std::chrono::seconds>(
                        winrt::clock::now() - timeline.LastUpdatedTime() + timeline.Position()
                ).count();
            } catch (...) {
                positionSec = rawPosSec;
            }
        }
        if (durationSec > 0 && positionSec > durationSec) positionSec = durationSec;
        if (positionSec < 0) positionSec = 0;

        if (state.pending) {
            if (timelineUpdate != state.lastTimelineUpdate
                    && rawPosSec >= 0
                    && (durationSec == 0 || rawPosSec <= durationSec)) {
                state.pending = false;
            } else {
                positionSec = 0;
            }
        }
        state.lastTimelineUpdate = timelineUpdate;
        state.lastRawPosSec = rawPosSec;

        writeString(record, SESSION_ID_OFFSET, SESSION_ID_BYTES, sessionId);
        writeString(record, OWNER_OFFSET, OWNER_BYTES, sessionId);
        writeString(record, TITLE_OFFSET, TITLE_BYTES, title);
        writeString(record, ARTIST_OFFSET, ARTIST_BYTES, artist);
        writeLong(record, POSITION_OFFSET, positionSec);
        writeLong(record, DURATION_OFFSET, durationSec);
        writeInt(record, PLAYING_OFFSET, playing ? 1 : 0);
        writeInt(record, SUPPORTS_SHUFFLE_OFFSET, controls.IsShuffleEnabled() ? 1 : 0);
        try {
            auto shuffle = playbackInfo.IsShuffleActive();
            writeInt(record, SHUFFLE_ACTIVE_OFFSET, (shuffle && shuffle.Value()) ? 1 : 0);
        } catch (...) {
            writeInt(record, SHUFFLE_ACTIVE_OFFSET, 0);
        }
        writeInt(record, SUPPORTS_REPEAT_OFFSET, controls.IsRepeatEnabled() ? 1 : 0);
        writeInt(record, REPEAT_MODE_OFFSET, repeatModeToInt(playbackInfo));
        writeInt(record, SUPPORTS_SEEK_OFFSET, controls.IsPlaybackPositionEnabled() ? 1 : 0);
    }
    return count;
}

jbyteArray Java_combatant_client_util_media_impl_win_WindowsMediaPlayerInfo_getArtworkPng(JNIEnv* env, jobject obj, jstring sessionId) {
    if (sessionId == nullptr) return env->NewByteArray(0);
    const char* raw = env->GetStringUTFChars(sessionId, nullptr);
    std::string id = raw == nullptr ? std::string() : std::string(raw);
    if (raw != nullptr) {
        env->ReleaseStringUTFChars(sessionId, raw);
    }

    try {
        auto session = findSessionById(id);
        if (session == nullptr) return env->NewByteArray(0);
        auto mediaProperties = session.TryGetMediaPropertiesAsync().get();
        auto thumbnail = mediaProperties.Thumbnail();
        if (!thumbnail) return env->NewByteArray(0);

        auto thumbnailStream = thumbnail.OpenReadAsync().get();
        auto reader = DataReader(thumbnailStream.GetInputStreamAt(0));
        reader.LoadAsync(thumbnailStream.Size()).get();
        std::vector<uint8_t> bytes(thumbnailStream.Size());
        auto bufferView = array_view<uint8_t>(bytes);
        reader.ReadBytes(bufferView);
        reader.Close();
        thumbnailStream.Close();

        auto result = env->NewByteArray(static_cast<jsize>(bytes.size()));
        env->SetByteArrayRegion(result, 0, static_cast<jsize>(bytes.size()), reinterpret_cast<const jbyte*>(bytes.data()));
        return result;
    } catch (...) {
        return env->NewByteArray(0);
    }
}

void Java_combatant_client_util_media_impl_win_WindowsMediaPlayerInfo_nativeShutdown(JNIEnv*, jobject) {
    shutdownManager();
}

jobject Java_combatant_client_util_media_impl_win_WindowsMediaPlayerInfo_getMediaSessions(JNIEnv* env, jobject obj) {
    jclass listClass = env->FindClass("java/util/ArrayList");
    jmethodID listConstructor = env->GetMethodID(listClass, "<init>", "(I)V");
    jmethodID listAdd = env->GetMethodID(listClass, "add", "(Ljava/lang/Object;)Z");
    jclass mediaSessionClass = env->FindClass("combatant/client/util/media/impl/win/WindowsMediaSession");
    jmethodID mediaSessionConstructor = env->GetMethodID(mediaSessionClass, "<init>", "(Lcombatant/client/util/media/MediaInfo;Ljava/lang/String;Ljava/lang/String;ZZZIZ)V");
    jclass mediaInfoClass = env->FindClass("combatant/client/util/media/MediaInfo");
    jmethodID mediaInfoConstructor = env->GetMethodID(mediaInfoClass, "<init>", "(Ljava/lang/String;Ljava/lang/String;[BJJZ)V");

    auto sessions = getManager().GetSessions();
    jobject list = env->NewObject(listClass, listConstructor, static_cast<jint>(sessions.Size()));
    for (int i = 0; i < sessions.Size(); ++i) {
        auto session = sessions.GetAt(i);
        auto mediaProperties = session.TryGetMediaPropertiesAsync().get();
        auto timeline = session.GetTimelineProperties();

        jbyteArray jArtwork = env->NewByteArray(0);

        auto titleH = mediaProperties.Title();
        auto artistH = mediaProperties.Artist();
        jstring jTitle = env->NewStringUTF(to_string(titleH).c_str());
        jstring jArtist = env->NewStringUTF(to_string(artistH).c_str());
        jlong jPosition;
        jboolean jPlaying = session.GetPlaybackInfo().PlaybackStatus() == GlobalSystemMediaTransportControlsSessionPlaybackStatus::Playing;
        long long rawPosSec = std::chrono::duration_cast<std::chrono::seconds>(timeline.Position()).count();
        jlong jDuration = std::chrono::duration_cast<std::chrono::seconds>(timeline.EndTime() - timeline.StartTime()).count();
        int64_t timelineUpdate = timeline.LastUpdatedTime().time_since_epoch().count();

        std::wstring ownerKey = session.SourceAppUserModelId().c_str();
        TrackState& state = TRACK_STATE[ownerKey];
        long long prevRawPosSec = state.lastRawPosSec;
        int64_t prevTimelineUpdate = state.lastTimelineUpdate;
        bool titleChanged = (state.title != titleH) || (state.artist != artistH);
        bool durationChanged = (state.durationSec > 0 && jDuration > 0 && llabs(jDuration - state.durationSec) >= 2);
        bool trackChanged = titleChanged || (durationChanged && rawPosSec <= 2);
        if (trackChanged) {
            bool hadTrack = (state.title.size() > 0) || (state.artist.size() > 0);
            state.title = titleH;
            state.artist = artistH;
            state.durationSec = jDuration;
            state.pending = hadTrack;
            state.lastTimelineUpdate = timelineUpdate;
            state.nudgedThisTrack = false;
        } else if (state.durationSec == 0 && jDuration > 0) {
            state.durationSec = jDuration;
        }

        if (trackChanged && !state.nudgedThisTrack && rawPosSec > 5) {
            bool positionBeyondDuration = (jDuration > 0 && rawPosSec > (jDuration + 2));
            bool timelineStale = (prevTimelineUpdate != 0 && timelineUpdate == prevTimelineUpdate);
            bool positionUnchanged = (prevRawPosSec > 0 && rawPosSec == prevRawPosSec);
            bool shouldNudge = positionBeyondDuration || (timelineStale && positionUnchanged);
            try {
                if (shouldNudge) {
                    auto controls = session.GetPlaybackInfo().Controls();
                    if (controls.IsPlaybackPositionEnabled()) {
                        // Nudge only when timeline is clearly stale to avoid audible rewinds.
                        session.TryChangePlaybackPositionAsync(0).get();
                        rawPosSec = 0;
                        state.nudgedThisTrack = true;
                    }
                }
            } catch (...) {
            }
        }

        jlong predPosSec = rawPosSec;
        if (jPlaying && !state.pending) {
            try {
                predPosSec = std::chrono::duration_cast<std::chrono::seconds>(
                        winrt::clock::now() - timeline.LastUpdatedTime() + timeline.Position()
                ).count();
            } catch (...) {
                predPosSec = rawPosSec;
            }
        }
        if (jDuration > 0 && predPosSec > jDuration) predPosSec = jDuration;
        if (predPosSec < 0) predPosSec = 0;

        jPosition = predPosSec;
        if (state.pending) {
            if (timelineUpdate != state.lastTimelineUpdate
                    && rawPosSec >= 0
                    && (jDuration == 0 || rawPosSec <= jDuration)) {
                state.pending = false;
            } else {
                jPosition = 0;
            }
        }
        state.lastTimelineUpdate = timelineUpdate;
        state.lastRawPosSec = rawPosSec;

        jobject mediaInfo = env->NewObject(mediaInfoClass, mediaInfoConstructor, jTitle, jArtist, jArtwork, jPosition, jDuration, jPlaying);

        auto playbackInfo = session.GetPlaybackInfo();
        auto controls = playbackInfo.Controls();
        jstring jOwner = env->NewStringUTF(to_string(session.SourceAppUserModelId()).c_str());
        jstring jSessionId = env->NewStringUTF(to_string(session.SourceAppUserModelId()).c_str());
        int repeatMode = repeatModeToInt(playbackInfo);
        bool shuffleActive = false;
        try {
            auto shuffle = playbackInfo.IsShuffleActive();
            shuffleActive = shuffle && shuffle.Value();
        } catch (...) {
        }

        jobject mediaSession = env->NewObject(
            mediaSessionClass,
            mediaSessionConstructor,
            mediaInfo,
            jOwner,
            jSessionId,
            controls.IsShuffleEnabled() ? JNI_TRUE : JNI_FALSE,
            shuffleActive ? JNI_TRUE : JNI_FALSE,
            controls.IsRepeatEnabled() ? JNI_TRUE : JNI_FALSE,
            static_cast<jint>(repeatMode),
            controls.IsPlaybackPositionEnabled() ? JNI_TRUE : JNI_FALSE
        );
        env->CallBooleanMethod(list, listAdd, mediaSession);
    }

    return list;
}

void Java_combatant_client_util_media_impl_win_WindowsMediaSession_play(JNIEnv* env, jobject obj) {
    auto session = sessionFromObject(env, obj);
    if (session != nullptr) {
        session.TryPlayAsync();
    }
}

void Java_combatant_client_util_media_impl_win_WindowsMediaSession_pause(JNIEnv* env, jobject obj) {
    auto session = sessionFromObject(env, obj);
    if (session != nullptr) {
        session.TryPauseAsync();
    }
}

void Java_combatant_client_util_media_impl_win_WindowsMediaSession_playPause(JNIEnv* env, jobject obj) {
    auto session = sessionFromObject(env, obj);
    if (session != nullptr) {
        session.TryTogglePlayPauseAsync();
    }
}

void Java_combatant_client_util_media_impl_win_WindowsMediaSession_stop(JNIEnv* env, jobject obj) {
    auto session = sessionFromObject(env, obj);
    if (session != nullptr) {
        session.TryStopAsync();
    }
}

void Java_combatant_client_util_media_impl_win_WindowsMediaSession_next(JNIEnv* env, jobject obj) {
    auto session = sessionFromObject(env, obj);
    if (session != nullptr) {
        session.TrySkipNextAsync();
    }
}

void Java_combatant_client_util_media_impl_win_WindowsMediaSession_previous(JNIEnv* env, jobject obj) {
    auto session = sessionFromObject(env, obj);
    if (session != nullptr) {
        session.TrySkipPreviousAsync();
    }
}

jboolean Java_combatant_client_util_media_impl_win_WindowsMediaSession_nativeSupportsShuffle(JNIEnv* env, jobject obj) {
    auto session = sessionFromObject(env, obj);
    if (session == nullptr) return JNI_FALSE;
    try {
        auto controls = session.GetPlaybackInfo().Controls();
        return controls.IsShuffleEnabled() ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        return JNI_FALSE;
    }
}

jboolean Java_combatant_client_util_media_impl_win_WindowsMediaSession_nativeIsShuffleActive(JNIEnv* env, jobject obj) {
    auto session = sessionFromObject(env, obj);
    if (session == nullptr) return JNI_FALSE;
    try {
        auto ref = session.GetPlaybackInfo().IsShuffleActive();
        if (!ref) return JNI_FALSE;
        return ref.Value() ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        return JNI_FALSE;
    }
}

void Java_combatant_client_util_media_impl_win_WindowsMediaSession_nativeSetShuffle(JNIEnv* env, jobject obj, jboolean active) {
    auto session = sessionFromObject(env, obj);
    if (session == nullptr) return;
    try {
        session.TryChangeShuffleActiveAsync(active == JNI_TRUE).get();
    } catch (...) {
    }
}

jboolean Java_combatant_client_util_media_impl_win_WindowsMediaSession_nativeSupportsRepeat(JNIEnv* env, jobject obj) {
    auto session = sessionFromObject(env, obj);
    if (session == nullptr) return JNI_FALSE;
    try {
        auto controls = session.GetPlaybackInfo().Controls();
        return controls.IsRepeatEnabled() ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        return JNI_FALSE;
    }
}

jint Java_combatant_client_util_media_impl_win_WindowsMediaSession_nativeGetRepeatMode(JNIEnv* env, jobject obj) {
    auto session = sessionFromObject(env, obj);
    if (session == nullptr) return 0;
    try {
        auto ref = session.GetPlaybackInfo().AutoRepeatMode();
        if (!ref) return -1;
        switch (ref.Value()) {
            case Windows::Media::MediaPlaybackAutoRepeatMode::Track:
                return 1;
            case Windows::Media::MediaPlaybackAutoRepeatMode::List:
                return 2;
            case Windows::Media::MediaPlaybackAutoRepeatMode::None:
            default:
                return 0;
        }
    } catch (...) {
        return -1;
    }
}

void Java_combatant_client_util_media_impl_win_WindowsMediaSession_nativeSetRepeatMode(JNIEnv* env, jobject obj, jint mode) {
    auto session = sessionFromObject(env, obj);
    if (session == nullptr) return;
    try {
        Windows::Media::MediaPlaybackAutoRepeatMode target =
            Windows::Media::MediaPlaybackAutoRepeatMode::None;
        if (mode == 1) {
            target = Windows::Media::MediaPlaybackAutoRepeatMode::Track;
        } else if (mode == 2) {
            target = Windows::Media::MediaPlaybackAutoRepeatMode::List;
        }
        session.TryChangeAutoRepeatModeAsync(target).get();
    } catch (...) {
    }
}

jboolean Java_combatant_client_util_media_impl_win_WindowsMediaSession_nativeSupportsSeek(JNIEnv* env, jobject obj) {
    auto session = sessionFromObject(env, obj);
    if (session == nullptr) return JNI_FALSE;
    try {
        auto controls = session.GetPlaybackInfo().Controls();
        return controls.IsPlaybackPositionEnabled() ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        return JNI_FALSE;
    }
}

void Java_combatant_client_util_media_impl_win_WindowsMediaSession_nativeSeekTo(JNIEnv* env, jobject obj, jlong positionSeconds) {
    auto session = sessionFromObject(env, obj);
    if (session == nullptr) return;
    try {
        int64_t pos100ns = static_cast<int64_t>(positionSeconds) * 10000000LL;
        session.TryChangePlaybackPositionAsync(pos100ns).get();
    } catch (...) {
    }
}

int main() {
    GlobalSystemMediaTransportControlsSessionManager smtc = getManager();
    auto sessions = smtc.GetSessions();
    for (int i = 0; i < sessions.Size(); ++i) {
        auto session = sessions.GetAt(i);
        auto mediaProps = session.TryGetMediaPropertiesAsync().get();
        auto title = to_string(mediaProps.Title());
        auto artist = to_string(mediaProps.Artist());
        auto thumbnail = mediaProps.Thumbnail().OpenReadAsync().get();
        auto reader = DataReader(thumbnail.GetInputStreamAt(0));
        reader.LoadAsync(thumbnail.Size()).get();
        std::vector<uint8_t> buffer(thumbnail.Size());
        auto bufferView = array_view<uint8_t>(buffer);
        reader.ReadBytes(bufferView);
        reader.Close();
        std::ofstream file("thumbnail.png", std::ios::out | std::ios::binary);
        file.write(reinterpret_cast<char*>(buffer.data()), buffer.size());
        file.close();
        auto timeline = session.GetTimelineProperties();
        long long positionFrom = (std::chrono::duration_cast<std::chrono::milliseconds>(timeline.LastUpdatedTime().time_since_epoch()).count() - 11647238400000) / 1000;
        long long now = (std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::system_clock::now().time_since_epoch()).count()) / 1000;
        long long pos = std::chrono::duration_cast<std::chrono::seconds>(timeline.Position()).count();
        std::cout << title << std::endl;
        std::cout << artist << std::endl;
        std::cout << positionFrom << std::endl;
        std::cout << now << std::endl;
        std::cout << pos << std::endl;
        std::cout << now - positionFrom + pos << std::endl;
        std::cout << std::chrono::duration_cast<std::chrono::seconds>(winrt::clock::now() - timeline.LastUpdatedTime() + timeline.Position()).count() << std::endl;
    }
    return 0;
}
