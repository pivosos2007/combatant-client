/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

/* HoldMyItems Recode; original project by sapling, CC0-1.0. */
package combatant.client.features.hmi_recode.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.resources.model.geometry.BakedQuad;

import java.util.AbstractList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.RandomAccess;

/**
 * Carries the per-quad HMI model pose snapshot through Minecraft's deferred item submit pipeline.
 *
 * <p>The wrapper is intentionally still a normal {@link List}: Fabric Renderer API can keep its
 * own submit redirect and extended mesh path. HMI consumes the metadata later, when vanilla
 * {@code ItemFeatureRenderer} emits each baked quad.</p>
 *
 * <p>Quad lookup and transformed poses are cached. The old implementation linearly searched the
 * entire quad list and rebuilt a temporary {@link PoseStack} on every main/outline/foil lookup,
 * which turned model animation into an avoidable O(n^2) hot path.</p>
 */
public final class HmiModelQuadList extends AbstractList<BakedQuad> implements RandomAccess {
    private final List<BakedQuad> delegate;
    private final List<HmiModelCommand> commands;
    private final IdentityHashMap<BakedQuad, Integer> indices;
    private final PoseStack.Pose[] sourcePoses;
    private final PoseStack.Pose[] transformedPoses;

    public HmiModelQuadList(List<BakedQuad> delegate, List<HmiModelCommand> commands) {
        this.delegate = delegate;
        this.commands = List.copyOf(commands);
        this.indices = new IdentityHashMap<>(Math.max(4, delegate.size() * 2));
        this.sourcePoses = new PoseStack.Pose[delegate.size()];
        this.transformedPoses = new PoseStack.Pose[delegate.size()];

        // Match the previous reference lookup semantics: if an identical quad object occurs more
        // than once, the first index wins.
        for (int i = 0; i < delegate.size(); i++) {
            indices.putIfAbsent(delegate.get(i), i);
        }
    }

    @Override
    public BakedQuad get(int index) {
        return delegate.get(index);
    }

    @Override
    public int size() {
        return delegate.size();
    }

    public PoseStack.Pose transformPose(PoseStack.Pose original, BakedQuad quad) {
        if (original == null || commands.isEmpty()) return original;

        Integer index = indices.get(quad);
        if (index == null) return original;

        PoseStack.Pose cached = transformedPoses[index];
        if (cached != null && sourcePoses[index] == original) return cached;

        PoseStack stack = new PoseStack();
        stack.last().set(original);
        HmiModelCommand.apply(commands, index, stack);
        cached = stack.last().copy();
        sourcePoses[index] = original;
        transformedPoses[index] = cached;
        return cached;
    }

    public static PoseStack.Pose transform(List<BakedQuad> quads, PoseStack.Pose original, BakedQuad quad) {
        return quads instanceof HmiModelQuadList hmi ? hmi.transformPose(original, quad) : original;
    }
}
