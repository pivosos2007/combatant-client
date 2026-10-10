/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.player.navigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Bounded, synchronous local path search. The world adapter owns collision and hazard policy;
 * no Minecraft state is retained by the search. This is a local orbit planner, not a replacement
 * for long-distance Baritone pathfinding.
 */
public final class LocalWalkPathfinder {
    public record Node(int x, int y, int z) {
        public double centerX() { return x + 0.5; }
        public double centerZ() { return z + 0.5; }
    }

    public interface Terrain {
        boolean standable(Node node);
        boolean traversable(Node from, Node to);
    }

    public record Goal(double x, double y, double z, double radius, double preferredAngle,
                       int direction, boolean orbit) {}

    private record QueueEntry(Node node, double cost) {}
    private record Parent(Node previous, double cost) {}

    private static final int[][] OFFSETS = {
            {1, 0}, {0, 1}, {-1, 0}, {0, -1},
            {1, 1}, {-1, 1}, {-1, -1}, {1, -1}
    };

    private LocalWalkPathfinder() {}

    public static List<Node> search(Node start, Goal goal, Terrain terrain,
                                    int range, int maxVisited, boolean allowJump) {
        if (!terrain.standable(start)) return List.of();
        Map<Node, Parent> parents = new HashMap<>();
        PriorityQueue<QueueEntry> queue = new PriorityQueue<>(Comparator.comparingDouble(QueueEntry::cost));
        parents.put(start, new Parent(null, 0.0));
        queue.add(new QueueEntry(start, 0.0));

        double originAngle = Math.atan2(start.centerZ() - goal.z(), start.centerX() - goal.x());
        double originRadius = Math.hypot(start.centerX() - goal.x(), start.centerZ() - goal.z());
        Node best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        int examined = 0;

        while (!queue.isEmpty() && examined < maxVisited) {
            QueueEntry current = queue.poll();
            Parent currentParent = parents.get(current.node());
            if (currentParent == null || current.cost() > currentParent.cost() + 1.0e-5) continue;
            examined++;
            if (current.cost() * 0.47 >= bestScore) break;
            Node node = current.node();

            double r = Math.hypot(node.centerX() - goal.x(), node.centerZ() - goal.z());
            double radialError = Math.abs(r - goal.radius());
            if (radialError <= Math.max(0.65, goal.radius() * 0.20)
                    && Math.abs(node.y() - goal.y()) <= 1.25) {
                double angle = Math.atan2(node.centerZ() - goal.z(), node.centerX() - goal.x());
                double progress = positiveAngle(goal.direction() * (angle - originAngle));
                // When already orbiting, do not select the start/current cell as the next goal.
                // Approaching from farther away is allowed to head radially to the near-side ring.
                if (!goal.orbit() || originRadius > goal.radius() + 0.85 || progress >= 0.27) {
                    double anglePenalty = goal.orbit()
                            ? Math.abs(wrapAngle(angle - goal.preferredAngle())) * Math.max(0.9, goal.radius())
                            : 0.0;
                    double score = current.cost() * 0.47 + radialError * 6.0 + anglePenalty * 1.35
                            + Math.abs(node.y() - goal.y()) * 1.6;
                    if (score < bestScore) {
                        bestScore = score;
                        best = node;
                    }
                }
            }

            for (int[] offset : OFFSETS) {
                int x = node.x() + offset[0];
                int z = node.z() + offset[1];
                if (Math.abs(x - start.x()) > range || Math.abs(z - start.z()) > range) continue;
                boolean diagonal = offset[0] != 0 && offset[1] != 0;
                for (int dy : (allowJump ? new int[]{0, 1, -1} : new int[]{0, -1})) {
                    if (Math.abs(node.y() + dy - start.y()) > 2) continue;
                    Node next = new Node(x, node.y() + dy, z);
                    if (!terrain.standable(next) || !terrain.traversable(node, next)) continue;
                    // No diagonal corner-cutting past walls or missing support.
                    if (diagonal && (!terrain.traversable(node,
                            new Node(node.x() + offset[0], node.y(), node.z()))
                            || !terrain.traversable(node,
                            new Node(node.x(), node.y(), node.z() + offset[1])))) continue;
                    double cost = current.cost() + (diagonal ? 1.41421356237 : 1.0)
                            + (dy > 0 ? 1.1 : dy < 0 ? 0.5 : 0.0);
                    Parent previous = parents.get(next);
                    if (previous == null || cost + 1.0e-5 < previous.cost()) {
                        parents.put(next, new Parent(node, cost));
                        queue.add(new QueueEntry(next, cost));
                    }
                    // At the same x/z take the first feasible height; don't fall through
                    // to a lower platform when the same-level traversal is available.
                    break;
                }
            }
        }

        if (best == null) return List.of();
        List<Node> result = new ArrayList<>();
        for (Node node = best; node != null; node = parents.get(node).previous()) result.add(node);
        Collections.reverse(result);
        return List.copyOf(result);
    }

    private static double wrapAngle(double angle) {
        return Math.atan2(Math.sin(angle), Math.cos(angle));
    }

    private static double positiveAngle(double angle) {
        double a = angle % (Math.PI * 2.0);
        return a < 0.0 ? a + Math.PI * 2.0 : a;
    }
}
