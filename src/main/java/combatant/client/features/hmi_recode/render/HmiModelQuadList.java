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
import net.minecraft.client.resources.model.geometry.ItemQuads;

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
 * <p>Quad indices and transformed poses are cached for O(1) lookup during main/outline/foil rendering.</p>
 */
public final class HmiModelQuadList extends AbstractList<BakedQuad> implements RandomAccess {
    private final List<BakedQuad> delegate;
    private final List<HmiModelCommand> commands;
    private final IdentityHashMap<BakedQuad, Integer> indices;
    private final IdentityHashMap<BakedQuad, Integer> cacheIndices;
    private final PoseStack.Pose[] sourcePoses;
    private final PoseStack.Pose[] transformedPoses;

    public HmiModelQuadList(List<BakedQuad> delegate, List<HmiModelCommand> commands) {
        this(delegate, commands, index(delegate));
    }

    private HmiModelQuadList(List<BakedQuad> delegate,
                             List<HmiModelCommand> commands,
                             IdentityHashMap<BakedQuad, Integer> indices) {
        this.delegate = delegate;
        this.commands = List.copyOf(commands);
        this.indices = indices;
        this.cacheIndices = index(delegate);
        this.sourcePoses = new PoseStack.Pose[delegate.size()];
        this.transformedPoses = new PoseStack.Pose[delegate.size()];
    }

    private static IdentityHashMap<BakedQuad, Integer> index(List<BakedQuad> quads) {
        IdentityHashMap<BakedQuad, Integer> indices = new IdentityHashMap<>(Math.max(4, quads.size() * 2));
        for (int i = 0; i < quads.size(); i++) {
            indices.putIfAbsent(quads.get(i), i);
        }
        return indices;
    }

    public static ItemQuads wrap(ItemQuads quads, List<HmiModelCommand> commands) {
        IdentityHashMap<BakedQuad, Integer> indices = index(quads.all());
        return new ItemQuads(
                new HmiModelQuadList(quads.all(), commands, indices),
                new HmiModelQuadList(quads.solid(), commands, indices),
                new HmiModelQuadList(quads.translucent(), commands, indices));
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

        Integer cacheIndex = cacheIndices.get(quad);
        if (cacheIndex == null) return original;
        PoseStack.Pose cached = transformedPoses[cacheIndex];
        if (cached != null && sourcePoses[cacheIndex] == original) return cached;

        PoseStack stack = new PoseStack();
        stack.last().set(original);
        HmiModelCommand.apply(commands, index, stack);
        cached = stack.last().copy();
        sourcePoses[cacheIndex] = original;
        transformedPoses[cacheIndex] = cached;
        return cached;
    }

    public static PoseStack.Pose transform(List<BakedQuad> quads, PoseStack.Pose original, BakedQuad quad) {
        return quads instanceof HmiModelQuadList hmi ? hmi.transformPose(original, quad) : original;
    }
}
