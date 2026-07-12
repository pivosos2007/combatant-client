/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.text.backend;

import combatant.client.render.engine.core.RenderFrameContext;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.text.TextCommandStats;

import java.util.ArrayList;
import java.util.List;

public final class TextBackendRouter {
    private final List<TextBackend> backends = new ArrayList<>();
    private final TextCommandStats stats;
    private TextBackend lastBackend;

    public TextBackendRouter() {
        this(null);
    }

    public TextBackendRouter(TextCommandStats stats) {
        this.stats = stats;
    }

    private static boolean sameRenderer(TextDrawCommand a, TextDrawCommand b) {
        TextRendererRef ar = new TextRendererRef(a);
        TextRendererRef br = new TextRendererRef(b);
        return ar.renderer == br.renderer;
    }

    public void add(TextBackend backend) {
        if (backend != null && !backends.contains(backend)) {
            backends.add(backend);
            lastBackend = null;
        }
    }

    public void draw(TextDrawCommand command, RenderFrameContext context, CombatantRhi rhi) {
        if (command == null || command.text() == null || command.text().isEmpty()) return;
        TextPlacementTransform placement = TextPlacementResolver.resolve(command, context);
        if (stats != null && placement.world()) stats.worldPlacement();
        TextBackend backend = selectBackend(command, placement);
        if (backend != null) {
            backend.draw(command, placement, context, rhi);
        }
    }

    public int drawAdjacent(List<TextDrawCommand> commands, int start, RenderFrameContext context, CombatantRhi rhi) {
        if (commands == null || start < 0 || start >= commands.size()) return 1;
        TextDrawCommand first = commands.get(start);
        if (first == null || first.text() == null || first.text().isEmpty()) return 1;

        TextPlacementTransform placement = TextPlacementResolver.resolve(first, context);
        if (stats != null && placement.world()) stats.worldPlacement();
        TextBackend backend = selectBackend(first, placement);
        if (backend == null) return 1;

        int end = start + 1;
        if (canStartAdjacentBatch(first, placement)) {
            while (end < commands.size()) {
                TextDrawCommand next = commands.get(end);
                if (!canJoinAdjacentBatch(first, next, backend, context)) break;
                end++;
            }
        }

        if (end - start > 1) {
            if (stats != null) stats.adjacentTextBatch(end - start);
            backend.drawBatch(commands, start, end, context, rhi);
        } else {
            backend.draw(first, placement, context, rhi);
        }
        return end - start;
    }

    private TextBackend selectBackend(TextDrawCommand command, TextPlacementTransform placement) {
        TextBackend cached = lastBackend;
        if (cached != null && cached.supports(command, placement)) {
            if (stats != null) stats.routerCacheHit();
            return cached;
        }
        if (stats != null) stats.routerCacheMiss();
        for (TextBackend backend : backends) {
            if (backend.supports(command, placement)) {
                lastBackend = backend;
                return backend;
            }
        }
        lastBackend = null;
        if (stats != null) stats.routerFallbackMiss();
        return null;
    }

    private boolean canStartAdjacentBatch(TextDrawCommand command, TextPlacementTransform placement) {
        return command != null
                && placement != null
                && !placement.world()
                && !command.customEffects()
                && (command.effect() == null || !command.effect().enabled())
                && (command.clip() == null || !command.clip().enabled());
    }

    private boolean canJoinAdjacentBatch(TextDrawCommand first,
                                         TextDrawCommand next,
                                         TextBackend backend,
                                         RenderFrameContext context) {
        if (first == null || next == null || backend == null) return false;
        if (next.text() == null || next.text().isEmpty()) return false;
        TextPlacementTransform placement = TextPlacementResolver.resolve(next, context);
        if (!canStartAdjacentBatch(next, placement)) return false;
        if (!backend.supports(next, placement)) return false;
        return sameRenderer(first, next)
                && Float.compare(first.size(), next.size()) == 0
                && first.big() == next.big()
                && first.placement() == next.placement()
                && first.preference() == next.preference();
    }

    public List<TextBackend> backends() {
        return List.copyOf(backends);
    }

    private record TextRendererRef(TextRenderer renderer) {
        private TextRendererRef(TextDrawCommand command) {
            this(command.renderer() != null ? command.renderer() : TextRenderer.get());
        }
    }
}
