/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.map;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Version graph for a four-child spatial LOD pyramid. It schedules downsample work without
 * owning provider pixels or a concrete GPU backend.
 */
public final class MapTileLodPyramid {
    private final int maxLod;
    private final Map<MapTileCoordinate, Node> nodes = new HashMap<>();
    private long requestSequence;

    public MapTileLodPyramid(int maxLod) {
        if (maxLod < 0 || maxLod > MapTileCoordinate.MAX_LOD) {
            throw new IllegalArgumentException("maxLod is outside the supported range.");
        }
        this.maxLod = maxLod;
    }

    public synchronized boolean putLeaf(MapTileCoordinate coordinate, long revision) {
        requireLeaf(coordinate);
        Node node = nodes.computeIfAbsent(coordinate, ignored -> new Node());
        if (node.present && node.revision == revision && !node.dirty) return false;
        node.present = true;
        node.revision = revision;
        node.dirty = false;
        node.inFlight = false;
        dirtyAncestors(coordinate);
        return true;
    }

    public synchronized void removeLeaf(MapTileCoordinate coordinate) {
        requireLeaf(coordinate);
        if (nodes.remove(coordinate) != null) {
            dirtyAncestors(coordinate);
        }
    }

    public synchronized List<DownsampleRequest> pollBuilds(int limit) {
        if (limit <= 0) return List.of();
        List<MapTileCoordinate> candidates = nodes.entrySet().stream()
                .filter(entry -> entry.getKey().lod() > 0 && entry.getValue().dirty && !entry.getValue().inFlight)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        List<DownsampleRequest> result = new ArrayList<>(Math.min(limit, candidates.size()));
        for (MapTileCoordinate parent : candidates) {
            if (result.size() >= limit) break;
            List<MapTileCoordinate> children = parent.children();
            long[] revisions = new long[4];
            boolean ready = true;
            for (int i = 0; i < children.size(); i++) {
                Node child = nodes.get(children.get(i));
                if (child == null || !child.present || child.dirty || child.inFlight) {
                    ready = false;
                    break;
                }
                revisions[i] = child.revision;
            }
            if (!ready) continue;
            Node node = nodes.get(parent);
            node.inFlight = true;
            result.add(new DownsampleRequest(++requestSequence, parent, children, revisions));
        }
        return List.copyOf(result);
    }

    /** Completes only if the four input revisions still match the scheduled request. */
    public synchronized boolean complete(DownsampleRequest request, long outputRevision) {
        if (request == null) return false;
        Node parent = nodes.get(request.parent());
        if (parent == null || !parent.inFlight) return false;
        parent.inFlight = false;
        for (int i = 0; i < request.children().size(); i++) {
            Node child = nodes.get(request.children().get(i));
            if (child == null || !child.present || child.dirty
                    || child.revision != request.childRevision(i)) {
                parent.dirty = true;
                return false;
            }
        }
        parent.present = true;
        parent.dirty = false;
        parent.revision = outputRevision;
        dirtyAncestors(request.parent());
        return true;
    }

    public synchronized void cancel(DownsampleRequest request) {
        if (request == null) return;
        Node parent = nodes.get(request.parent());
        if (parent != null) {
            parent.inFlight = false;
            parent.dirty = true;
        }
    }

    public synchronized TileState state(MapTileCoordinate coordinate) {
        Node node = nodes.get(coordinate);
        return node == null
                ? TileState.MISSING
                : new TileState(node.present, node.revision, node.dirty, node.inFlight);
    }

    public synchronized int size() {
        return nodes.size();
    }

    private void dirtyAncestors(MapTileCoordinate coordinate) {
        MapTileCoordinate current = coordinate;
        while (current.lod() < maxLod) {
            current = current.parent();
            Node parent = nodes.computeIfAbsent(current, ignored -> new Node());
            parent.dirty = true;
            parent.inFlight = false;
        }
    }

    private static void requireLeaf(MapTileCoordinate coordinate) {
        if (coordinate == null) throw new NullPointerException("coordinate");
        if (coordinate.lod() != 0) throw new IllegalArgumentException("Expected a leaf LOD 0 tile.");
    }

    private static final class Node {
        private boolean present;
        private long revision;
        private boolean dirty;
        private boolean inFlight;
    }

    public record TileState(boolean present, long revision, boolean dirty, boolean inFlight) {
        public static final TileState MISSING = new TileState(false, 0L, false, false);
    }

    public static final class DownsampleRequest {
        private final long id;
        private final MapTileCoordinate parent;
        private final List<MapTileCoordinate> children;
        private final long[] childRevisions;

        private DownsampleRequest(long id,
                                  MapTileCoordinate parent,
                                  List<MapTileCoordinate> children,
                                  long[] childRevisions) {
            this.id = id;
            this.parent = parent;
            this.children = List.copyOf(children);
            this.childRevisions = childRevisions.clone();
        }

        public long id() {
            return id;
        }

        public MapTileCoordinate parent() {
            return parent;
        }

        public List<MapTileCoordinate> children() {
            return children;
        }

        public long childRevision(int index) {
            return childRevisions[index];
        }

        public long[] childRevisions() {
            return childRevisions.clone();
        }

        @Override
        public String toString() {
            return "DownsampleRequest[id=" + id + ", parent=" + parent
                    + ", children=" + children + ", childRevisions=" + Arrays.toString(childRevisions) + ']';
        }
    }
}
