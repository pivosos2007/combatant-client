/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.events.impl;

import combatant.client.events.Event;
import combatant.client.render.engine.svg.SvgRegistry;
import combatant.client.render.engine.text.FontInfo;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Collects render resources that are known to be needed before the first GUI/HUD draw.
 * Producers should add only resources implied by current runtime state/config: enabled
 * modules, enabled HUD elements, and UI surfaces that can be opened immediately.
 */
public final class RenderPrewarmCollectEvent extends Event {
    private final String reason;
    private final LinkedHashSet<FontInfo> fonts = new LinkedHashSet<>();
    private final LinkedHashSet<Identifier> svgMsdfIcons = new LinkedHashSet<>();

    public RenderPrewarmCollectEvent(String reason) {
        this.reason = reason != null ? reason : "render prewarm";
    }

    public String reason() {
        return reason;
    }

    public RenderPrewarmCollectEvent font(String family) {
        return font(family, FontInfo.Type.Regular);
    }

    public RenderPrewarmCollectEvent font(String family, FontInfo.Type type) {
        if (family == null || family.isBlank()) return this;
        fonts.add(new FontInfo(family.trim(), type != null ? type : FontInfo.Type.Regular));
        return this;
    }

    public RenderPrewarmCollectEvent font(@Nullable FontInfo info) {
        if (info != null && info.family() != null && !info.family().isBlank()) {
            fonts.add(info);
        }
        return this;
    }

    public RenderPrewarmCollectEvent svg(String nameOrPath) {
        Identifier id = SvgRegistry.resolve(nameOrPath);
        if (id != null) {
            svgMsdfIcons.add(id);
        }
        return this;
    }

    public RenderPrewarmCollectEvent svg(@Nullable Identifier id) {
        if (id != null) {
            svgMsdfIcons.add(id);
        }
        return this;
    }

    public Set<FontInfo> fonts() {
        return Collections.unmodifiableSet(fonts);
    }

    public Set<Identifier> svgMsdfIcons() {
        return Collections.unmodifiableSet(svgMsdfIcons);
    }

    public boolean isEmpty() {
        return fonts.isEmpty() && svgMsdfIcons.isEmpty();
    }
}
