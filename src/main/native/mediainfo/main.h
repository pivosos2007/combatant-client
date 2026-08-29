#pragma once

// Modified native MediaPlayerInfo bridge for Combatant by pivosos2007.
// Based on Redstonecrafter0/MediaPlayerInfo.

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jobject JNICALL Java_combatant_client_util_media_impl_win_WindowsMediaPlayerInfo_getMediaSessions(JNIEnv* env, jobject obj);
JNIEXPORT jint JNICALL Java_combatant_client_util_media_impl_win_WindowsMediaPlayerInfo_fillSessionSnapshotBuffer(JNIEnv* env, jobject obj, jobject buffer, jint maxSessions);
JNIEXPORT jbyteArray JNICALL Java_combatant_client_util_media_impl_win_WindowsMediaPlayerInfo_getArtworkPng(JNIEnv* env, jobject obj, jstring sessionId);
JNIEXPORT void JNICALL Java_combatant_client_util_media_impl_win_WindowsMediaPlayerInfo_nativeShutdown(JNIEnv* env, jobject obj);

JNIEXPORT void JNICALL Java_combatant_client_util_media_impl_win_WindowsMediaSession_play(JNIEnv* env, jobject obj);
JNIEXPORT void JNICALL Java_combatant_client_util_media_impl_win_WindowsMediaSession_pause(JNIEnv* env, jobject obj);
JNIEXPORT void JNICALL Java_combatant_client_util_media_impl_win_WindowsMediaSession_playPause(JNIEnv* env, jobject obj);
JNIEXPORT void JNICALL Java_combatant_client_util_media_impl_win_WindowsMediaSession_stop(JNIEnv* env, jobject obj);
JNIEXPORT void JNICALL Java_combatant_client_util_media_impl_win_WindowsMediaSession_next(JNIEnv* env, jobject obj);
JNIEXPORT void JNICALL Java_combatant_client_util_media_impl_win_WindowsMediaSession_previous(JNIEnv* env, jobject obj);

JNIEXPORT jboolean JNICALL Java_combatant_client_util_media_impl_win_WindowsMediaSession_nativeSupportsShuffle(JNIEnv* env, jobject obj);
JNIEXPORT jboolean JNICALL Java_combatant_client_util_media_impl_win_WindowsMediaSession_nativeIsShuffleActive(JNIEnv* env, jobject obj);
JNIEXPORT void JNICALL Java_combatant_client_util_media_impl_win_WindowsMediaSession_nativeSetShuffle(JNIEnv* env, jobject obj, jboolean active);

JNIEXPORT jboolean JNICALL Java_combatant_client_util_media_impl_win_WindowsMediaSession_nativeSupportsRepeat(JNIEnv* env, jobject obj);
JNIEXPORT jint JNICALL Java_combatant_client_util_media_impl_win_WindowsMediaSession_nativeGetRepeatMode(JNIEnv* env, jobject obj);
JNIEXPORT void JNICALL Java_combatant_client_util_media_impl_win_WindowsMediaSession_nativeSetRepeatMode(JNIEnv* env, jobject obj, jint mode);

JNIEXPORT jboolean JNICALL Java_combatant_client_util_media_impl_win_WindowsMediaSession_nativeSupportsSeek(JNIEnv* env, jobject obj);
JNIEXPORT void JNICALL Java_combatant_client_util_media_impl_win_WindowsMediaSession_nativeSeekTo(JNIEnv* env, jobject obj, jlong positionSeconds);

#ifdef __cplusplus
}
#endif
