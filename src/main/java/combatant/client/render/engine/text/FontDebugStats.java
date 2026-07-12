/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.text;

import java.util.concurrent.atomic.AtomicInteger;

public enum FontDebugStats {
    ;
    private static final AtomicInteger MSDF_ATTEMPTS = new AtomicInteger();
    private static final AtomicInteger MSDF_SUCCESS = new AtomicInteger();
    private static final AtomicInteger MSDF_FALLBACKS = new AtomicInteger();
    private static final AtomicInteger BITMAP_ATLAS_PAGES = new AtomicInteger();
    private static final AtomicInteger MSDF_ATLAS_PAGES = new AtomicInteger();

    private static volatile String lastMsdfAttempt = "";
    private static volatile String lastMsdfSuccess = "";
    private static volatile String lastMsdfFallback = "";
    private static volatile String lastMsdfFallbackReason = "";

    public static void noteMsdfAttempt(FontInfo info) {
        MSDF_ATTEMPTS.incrementAndGet();
        lastMsdfAttempt = info != null ? info.toString() : "";
    }

    public static void noteMsdfSuccess(FontInfo info) {
        MSDF_SUCCESS.incrementAndGet();
        MSDF_ATLAS_PAGES.incrementAndGet();
        lastMsdfSuccess = info != null ? info.toString() : "";
    }

    public static void noteMsdfFallback(FontInfo info, String reason) {
        MSDF_FALLBACKS.incrementAndGet();
        lastMsdfFallback = info != null ? info.toString() : "";
        lastMsdfFallbackReason = reason != null ? reason : "";
    }

    public static void noteBitmapAtlasPage() {
        BITMAP_ATLAS_PAGES.incrementAndGet();
    }

    public static int getMsdfAttempts() {
        return MSDF_ATTEMPTS.get();
    }

    public static int getMsdfSuccess() {
        return MSDF_SUCCESS.get();
    }

    public static int getMsdfFallbacks() {
        return MSDF_FALLBACKS.get();
    }

    public static int getBitmapAtlasPages() {
        return BITMAP_ATLAS_PAGES.get();
    }

    public static int getMsdfAtlasPages() {
        return MSDF_ATLAS_PAGES.get();
    }

    public static String getLastMsdfAttempt() {
        return lastMsdfAttempt;
    }

    public static String getLastMsdfSuccess() {
        return lastMsdfSuccess;
    }

    public static String getLastMsdfFallback() {
        return lastMsdfFallback;
    }

    public static String getLastMsdfFallbackReason() {
        return lastMsdfFallbackReason;
    }
}
