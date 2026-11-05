/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.hud.nondraggable.debug;

import combatant.client.mixininterface.IGlBackendInfo;
import combatant.client.render.engine.rhi.backend.gl.GlBackendAccess;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Add one capability line next to vanilla's graphics information, without rewriting it. */
final class GraphicsDebugHudProducer {
    private static final Pattern VANILLA_CONTEXT_LINE = Pattern.compile(
            "^[0-9]+\\.[0-9]+(?:\\.[0-9]+)?\\s+(?:core|compatibility)(?:\\s.*)?$"
    );
    private GraphicsDebugHudProducer() {
    }

    static void insert(List<String> vanilla, DebugHudLineRegistry.VanillaLineSink lines) {
        // When the vanilla right column is absent (F3 hidden), there is no graphics
        // section to annotate. Inserting at index -1 would create a stray HUD line.
        if (vanilla == null || vanilla.isEmpty()) return;
        // No RhiCapabilities allocations and no native GL calls when F3 is drawn.
        // Vulkan does not expose an IGlBackendInfo, so no misleading OpenGL line is added.
        IGlBackendInfo gl = GlBackendAccess.current();
        if (gl == null) return;
        String level = gl.combatant$availableCapabilitiesLevel();
        if (level == null || level.isBlank()) return;

        int lineIndex = graphicsLineIndex(vanilla, gl);
        if (lineIndex < 0) return;
        lines.after(lineIndex, "Available capabilities level: " + level);
    }

    /**
     * Prefer an actual version line; next prefer the display/GPU block. If a mod changes
     * the right-column format entirely, append at the end instead of modifying vanilla data.
     */
    static int graphicsLineIndex(List<String> vanilla, IGlBackendInfo gl) {
        int display = -1;
        int adapter = -1;
        int version = -1;
        int lastVisibleLine = -1;
        String rawVersion = gl.combatant$glVersion();
        String renderer = gl.combatant$renderer();
        for (int i = 0; i < vanilla.size(); ++i) {
            String line = vanilla.get(i);
            if (line == null) continue;
            String normalized = line.strip().toLowerCase(Locale.ROOT);
            if (!normalized.isBlank()) lastVisibleLine = i;
            if (normalized.startsWith("display:") || normalized.startsWith("graphics:")) {
                display = i;
            }
            if (!renderer.isBlank() && line.contains(renderer)) adapter = i;
            if (!rawVersion.isBlank() && line.contains(rawVersion)) {
                version = i;
            } else if (normalized.startsWith("opengl ") || normalized.startsWith("gl version:")
                    || VANILLA_CONTEXT_LINE.matcher(normalized).matches()) {
                version = i;
            }
        }
        if (version >= 0) return version;
        if (adapter >= 0) return adapter;
        if (display >= 0) return display;
        // A blank-only column is not a visible F3 graphics section.
        return lastVisibleLine;
    }
}
