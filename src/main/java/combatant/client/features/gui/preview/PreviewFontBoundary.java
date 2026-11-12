/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.preview;

import combatant.client.features.gui.preview.api.PreviewFrame;
import combatant.client.render.engine.text.BuiltinFontCatalog;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.util.logging.DebugLog;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Isolates leaked text begin/end pairs in sample HUD renderers without changing
 * the live rendering path. Already-open renderers belong to the caller and are
 * never closed here; only begin calls made by the current sample can be balanced.
 */
public final class PreviewFontBoundary implements AutoCloseable {
    private static final PreviewFontBoundary DISABLED = new PreviewFontBoundary(List.of());
    private static long cachedGeneration = Long.MIN_VALUE;
    private static List<TextRenderer> cachedRenderers = List.of();
    private final List<TextRenderer> idleAtEntry;

    private PreviewFontBoundary(List<TextRenderer> idleAtEntry) {
        this.idleAtEntry = idleAtEntry;
    }

    public static PreviewFontBoundary capture() {
        if (!PreviewFrame.isSample()) return DISABLED;
        List<TextRenderer> renderers = renderers();
        List<TextRenderer> idle = new ArrayList<>(renderers.size());
        for (TextRenderer renderer : renderers) {
            if (!renderer.isBuilding()) idle.add(renderer);
        }
        return new PreviewFontBoundary(idle);
    }

    private static List<TextRenderer> renderers() {
        long generation = Fonts.generation();
        if (generation == cachedGeneration && !cachedRenderers.isEmpty()) return cachedRenderers;
        var unique = new IdentityHashMap<TextRenderer, Boolean>();
        List<TextRenderer> faces = new ArrayList<>();
        TextRenderer defaultRenderer = TextRenderer.get();
        if (defaultRenderer != null && unique.put(defaultRenderer, Boolean.TRUE) == null)
            faces.add(defaultRenderer);
        for (BuiltinFontCatalog font : BuiltinFontCatalog.values()) {
            TextRenderer renderer = font.renderer();
            if (renderer != null && unique.put(renderer, Boolean.TRUE) == null) faces.add(renderer);
        }
        cachedRenderers = List.copyOf(faces);
        cachedGeneration = generation;
        return cachedRenderers;
    }

    @Override
    public void close() {
        for (TextRenderer renderer : idleAtEntry) {
            if (!renderer.isBuilding()) continue;
            try {
                renderer.end();
            } catch (RuntimeException failure) {
                DebugLog.warnOnce("hud-preview-font-scope-" + System.identityHashCode(renderer),
                        "A HUD preview left a text renderer open", failure);
            }
        }
    }
}
