/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.framegraph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Compiles ordered pass contracts into explicit resource hazards.
 *
 * <p>This first graph slice deliberately preserves producer order. It makes resource hazards and
 * lifetime declarations explicit now, while later graph lowering can insert barriers, alias
 * transient allocations and cull passes without changing producer contracts.</p>
 */
public final class FrameGraphContractCompiler {
    private FrameGraphContractCompiler() {
    }

    public static CompiledFrameGraph compile(List<FrameGraphPassContract> passes) {
        List<FrameGraphPassContract> ordered = passes == null ? List.of() : List.copyOf(passes);
        ArrayList<CompiledFrameGraph.Dependency> dependencies = new ArrayList<>();
        HashMap<FrameGraphResourceKey, Integer> lastWriter = new HashMap<>();
        HashMap<FrameGraphResourceKey, LinkedHashSet<Integer>> readersSinceWrite = new HashMap<>();
        HashSet<String> labels = new HashSet<>();

        for (int index = 0; index < ordered.size(); index++) {
            FrameGraphPassContract pass = Objects.requireNonNull(ordered.get(index), "passes contains null");
            if (!labels.add(pass.label())) {
                throw new IllegalArgumentException("Duplicate frame-graph pass label: " + pass.label());
            }

            for (FrameGraphResourceUse use : pass.resources()) {
                FrameGraphResourceKey resource = use.resource();
                Integer writer = lastWriter.get(resource);

                if (use.access().reads()) {
                    if (writer != null) {
                        dependencies.add(new CompiledFrameGraph.Dependency(
                                writer, index, resource, CompiledFrameGraph.Hazard.READ_AFTER_WRITE));
                    } else if (resource.lifetime() == FrameGraphResourceLifetime.TRANSIENT) {
                        throw new IllegalStateException("Transient resource '" + resource.name()
                                + "' is read before its first write by pass '" + pass.label() + "'");
                    }
                    readersSinceWrite.computeIfAbsent(resource, ignored -> new LinkedHashSet<>()).add(index);
                }

                if (use.access().writes()) {
                    if (writer != null) {
                        dependencies.add(new CompiledFrameGraph.Dependency(
                                writer, index, resource, CompiledFrameGraph.Hazard.WRITE_AFTER_WRITE));
                    }
                    LinkedHashSet<Integer> readers = readersSinceWrite.get(resource);
                    if (readers != null) {
                        for (int reader : readers) {
                            if (reader != index) {
                                dependencies.add(new CompiledFrameGraph.Dependency(
                                        reader, index, resource, CompiledFrameGraph.Hazard.WRITE_AFTER_READ));
                            }
                        }
                        readers.clear();
                    }
                    lastWriter.put(resource, index);
                }
            }
        }

        return new CompiledFrameGraph(ordered, dependencies);
    }
}
