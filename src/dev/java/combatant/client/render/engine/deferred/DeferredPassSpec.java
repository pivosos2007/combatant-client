/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import combatant.client.render.engine.framegraph.FrameGraphAccess;
import combatant.client.render.engine.framegraph.FrameGraphPassContract;
import combatant.client.render.engine.framegraph.FrameGraphExecutionDomain;
import combatant.client.render.engine.framegraph.FrameGraphResourceUse;
import combatant.client.render.engine.rhi.shader.RhiShaderStage;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Immutable pass declaration used by the world graph before any GPU work is submitted. */
public record DeferredPassSpec(
        String id,
        DeferredStage stage,
        int priority,
        List<FrameGraphResourceUse> resources,
        FrameGraphExecutionDomain executionDomain,
        Set<RhiShaderStage> requiredShaderStages,
        Set<DeferredResource> optionalReads,
        DeferredFeature feature,
        boolean externallyDriven,
        DeferredPassCondition condition,
        DeferredPassExecutor executor
) {
    public DeferredPassSpec {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Deferred pass id must not be blank");
        id = id.trim();
        if (stage == null) throw new IllegalArgumentException("stage");
        resources = resources == null ? List.of() : List.copyOf(resources);
        requiredShaderStages = requiredShaderStages == null || requiredShaderStages.isEmpty()
                ? Set.of()
                : Set.copyOf(requiredShaderStages);
        optionalReads = optionalReads == null || optionalReads.isEmpty() ? Set.of() : Set.copyOf(optionalReads);
        executionDomain = executionDomain == null
                ? (requiredShaderStages.contains(RhiShaderStage.COMPUTE)
                ? FrameGraphExecutionDomain.COMPUTE : FrameGraphExecutionDomain.GRAPHICS)
                : executionDomain;
        if (executionDomain == FrameGraphExecutionDomain.TRANSFER && !requiredShaderStages.isEmpty()) {
            throw new IllegalArgumentException("Transfer pass cannot require shader stages: " + id);
        }
        condition = condition == null ? DeferredPassCondition.ALWAYS : condition;
        if (!externallyDriven && executor == null) {
            throw new IllegalArgumentException("Executable deferred pass requires an executor: " + id);
        }
    }

    public static Builder builder(String id, DeferredStage stage) {
        return new Builder(id, stage);
    }

    public FrameGraphPassContract contract() {
        return new FrameGraphPassContract(stage.renderPhase(), id, executionDomain, resources, externallyDriven);
    }

    public static final class Builder {
        private final String id;
        private final DeferredStage stage;
        private final EnumMap<DeferredResource, FrameGraphAccess> resources = new EnumMap<>(DeferredResource.class);
        private final EnumSet<RhiShaderStage> requiredStages = EnumSet.noneOf(RhiShaderStage.class);
        private final EnumSet<DeferredResource> optionalReads = EnumSet.noneOf(DeferredResource.class);
        private final EnumSet<DeferredResource> requiredReads = EnumSet.noneOf(DeferredResource.class);
        private FrameGraphExecutionDomain executionDomain;
        private int priority;
        private DeferredFeature feature;
        private boolean externallyDriven;
        private DeferredPassCondition condition = DeferredPassCondition.ALWAYS;
        private DeferredPassExecutor executor;

        private Builder(String id, DeferredStage stage) {
            this.id = id;
            this.stage = stage;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder read(DeferredResource... values) {
            return use(FrameGraphAccess.READ, false, values);
        }

        /**
         * Declares a fallback-capable read. The dependency still participates in planning/barriers
         * when a producer exists, but runtime produced-state is not required for the pass to run.
         */
        public Builder optionalRead(DeferredResource... values) {
            return use(FrameGraphAccess.READ, true, values);
        }

        public Builder write(DeferredResource... values) {
            return use(FrameGraphAccess.WRITE, false, values);
        }

        public Builder readWrite(DeferredResource... values) {
            return use(FrameGraphAccess.READ_WRITE, false, values);
        }

        /** Declares a native copy/clear/update pass with transfer-stage hazard semantics. */
        public Builder transfer() {
            executionDomain = FrameGraphExecutionDomain.TRANSFER;
            return this;
        }

        public Builder requires(RhiShaderStage... stages) {
            if (stages != null) {
                for (RhiShaderStage shaderStage : stages) if (shaderStage != null) requiredStages.add(shaderStage);
            }
            return this;
        }

        public Builder feature(DeferredFeature feature) {
            this.feature = feature;
            return this;
        }

        public Builder external() {
            externallyDriven = true;
            executor = null;
            return this;
        }

        /** Evaluated before pass GPU access; disabled producers therefore never publish logical validity. */
        public Builder when(DeferredPassCondition condition) {
            this.condition = condition == null ? DeferredPassCondition.ALWAYS : condition;
            return this;
        }

        public Builder execute(DeferredPassExecutor executor) {
            this.executor = executor;
            this.externallyDriven = false;
            return this;
        }

        public DeferredPassSpec build() {
            ArrayList<FrameGraphResourceUse> uses = new ArrayList<>(resources.size());
            for (DeferredResource resource : DeferredResource.values()) {
                FrameGraphAccess access = resources.get(resource);
                if (access != null) uses.add(new FrameGraphResourceUse(resource.key(), access));
            }
            FrameGraphExecutionDomain domain = executionDomain != null
                    ? executionDomain
                    : (requiredStages.contains(RhiShaderStage.COMPUTE)
                    ? FrameGraphExecutionDomain.COMPUTE : FrameGraphExecutionDomain.GRAPHICS);
            return new DeferredPassSpec(
                    id, stage, priority, uses, domain, requiredStages, optionalReads, feature, externallyDriven, condition, executor
            );
        }

        private Builder use(FrameGraphAccess access, boolean optionalRead, DeferredResource... values) {
            if (values == null) return this;
            for (DeferredResource resource : values) {
                if (resource == null) continue;
                FrameGraphAccess merged = resources.merge(resource, access, Builder::mergeAccess);
                if (access.reads()) {
                    if (optionalRead && !requiredReads.contains(resource) && merged == FrameGraphAccess.READ) {
                        optionalReads.add(resource);
                    } else {
                        requiredReads.add(resource);
                        optionalReads.remove(resource);
                    }
                }
                if (merged.writes()) optionalReads.remove(resource);
            }
            return this;
        }

        private static FrameGraphAccess mergeAccess(FrameGraphAccess left, FrameGraphAccess right) {
            if (left == right) return left;
            return FrameGraphAccess.READ_WRITE;
        }
    }
}
