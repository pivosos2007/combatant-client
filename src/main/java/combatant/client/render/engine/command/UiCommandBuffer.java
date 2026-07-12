/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.command;

import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.RenderFrameContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * High-level normalized UI command stream.
 */
public final class UiCommandBuffer implements RenderCommandBuffer {
    private final List<UiCommand> commands = new ArrayList<>();
    private final UiCommandStats stats = new UiCommandStats();

    public void beginFrame(RenderFrameContext context) {
        if (context != null) {
            stats.beginFrame(context.frameId());
        }
    }

    public void add(UiCommand command) {
        if (command == null) return;
        RenderFrameContext ctx = CombatantRenderSystem.currentContext();
        if (ctx != null) stats.beginFrame(ctx.frameId());
        commands.add(command);
        stats.record(command);
    }

    public List<UiCommand> commands() {
        return commands;
    }

    public List<UiCommand> snapshot() {
        return Collections.unmodifiableList(commands);
    }

    public UiCommandStats stats() {
        return stats;
    }

    public UiStatsSnapshot statsSnapshot() {
        return stats.snapshot();
    }

    @Override
    public void clear() {
        commands.clear();
    }

    @Override
    public int size() {
        return commands.size();
    }

    @Override
    public void submit(RenderFrameContext context) {
        beginFrame(context);
    }
}
