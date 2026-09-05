/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui;

import combatant.client.render.engine.command.UiCommand;
import combatant.client.render.engine.command.UiCommandBuffer;
import combatant.client.render.engine.command.UiEffectRegionCommand;
import combatant.client.render.engine.renderer.ui.draw.UiBackdropRequest;
import combatant.client.render.engine.renderer.ui.draw.UiRect;

import java.util.ArrayList;
import java.util.List;

/** Compiler-visible groups of adjacent compatible backdrop requests. */
public record UiBackdropPlan(List<Group> groups, int requestCount) {
    public static final UiBackdropPlan EMPTY = new UiBackdropPlan(List.of(), 0);

    public UiBackdropPlan {
        groups = groups == null || groups.isEmpty() ? List.of() : List.copyOf(groups);
        requestCount = Math.max(0, requestCount);
    }

    public static UiBackdropPlan compile(UiCommandBuffer commands) {
        if (commands == null || commands.size() == 0) return EMPTY;

        ArrayList<Group> groups = new ArrayList<>();
        Group current = null;
        int requests = 0;
        int commandIndex = 0;
        for (UiCommand command : commands.commands()) {
            UiBackdropRequest request = backdropOf(command);
            if (request == null || request.equals(UiBackdropRequest.NONE)) {
                current = null;
                commandIndex++;
                continue;
            }

            requests++;
            if (current != null
                    && current.lastCommandIndex() + 1 == commandIndex
                    && current.request().compatibleInputs(request)) {
                current = current.append(request.captureBounds(), commandIndex);
                groups.set(groups.size() - 1, current);
            } else {
                current = new Group(request, request.captureBounds(), commandIndex, commandIndex, 1);
                groups.add(current);
            }
            commandIndex++;
        }
        return groups.isEmpty() ? EMPTY : new UiBackdropPlan(groups, requests);
    }

    private static UiBackdropRequest backdropOf(UiCommand command) {
        if (command instanceof UiEffectRegionCommand effectCommand
                && effectCommand.effect() != null) {
            return effectCommand.effect().backdrop();
        }
        return null;
    }

    public record Group(UiBackdropRequest request,
                        UiRect captureBounds,
                        int firstCommandIndex,
                        int lastCommandIndex,
                        int requestCount) {
        public Group {
            request = request != null ? request : UiBackdropRequest.NONE;
            firstCommandIndex = Math.max(0, firstCommandIndex);
            lastCommandIndex = Math.max(firstCommandIndex, lastCommandIndex);
            requestCount = Math.max(1, requestCount);
        }

        private Group append(UiRect bounds, int commandIndex) {
            return new Group(request, union(captureBounds, bounds), firstCommandIndex,
                    commandIndex, requestCount + 1);
        }
    }

    private static UiRect union(UiRect a, UiRect b) {
        if (a == null || b == null) return null;
        float minX = Math.min(a.x(), b.x());
        float minY = Math.min(a.y(), b.y());
        float maxX = Math.max(a.x() + a.width(), b.x() + b.width());
        float maxY = Math.max(a.y() + a.height(), b.y() + b.height());
        return new UiRect(minX, minY, maxX - minX, maxY - minY);
    }
}
