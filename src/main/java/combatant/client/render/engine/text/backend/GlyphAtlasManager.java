/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.text.backend;

import combatant.client.render.engine.Texture;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Owner for glyph atlas metadata. Existing Font/MsdfFont atlas textures can be registered here while Stage 11 moves
 * texture lifetime into RenderResourceManager.
 */
public final class GlyphAtlasManager {
    private final Map<String, GlyphAtlasPage> pages = new LinkedHashMap<>();
    private int glyphCount;
    private long uploadedBytes;
    private int dirtyUploads;

    public GlyphAtlasPage registerPage(String id, Texture texture, int width, int height, boolean msdf) {
        String key = id != null ? id : "glyph_page_" + pages.size();
        GlyphAtlasPage page = new GlyphAtlasPage(key, texture, width, height, msdf);
        pages.put(key, page);
        return page;
    }

    public void onPageCreated() {
        registerPage("legacy_page_" + pages.size(), null, 0, 0, false);
    }

    public void onGlyphUploaded(int bytes) {
        glyphCount++;
        uploadedBytes += Math.max(0, bytes);
        dirtyUploads++;
    }

    public void onDirtyRectUpload(int glyphs, int bytes) {
        glyphCount += Math.max(0, glyphs);
        uploadedBytes += Math.max(0, bytes);
        dirtyUploads++;
    }

    public GlyphAtlasStats stats() {
        return new GlyphAtlasStats(pages.size(), glyphCount, dirtyUploads, uploadedBytes);
    }

    public record GlyphAtlasStats(int pageCount, int glyphCount, int dirtyUploads, long uploadedBytes) {
    }
}
