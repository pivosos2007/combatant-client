/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.model.runtime;

import combatant.client.render.engine.asset.model.data.ModelAsset;
import combatant.client.render.engine.asset.model.data.ModelBounds;
import combatant.client.render.engine.asset.model.data.ModelMesh;
import combatant.client.render.engine.asset.model.data.ModelNode;
import combatant.client.render.engine.asset.model.data.ModelPrimitive;
import combatant.client.render.engine.asset.model.data.ModelScene;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/**
 * Precomputed static node/primitive layout for one imported scene.
 *
 * <p>This is CPU metadata only. It prevents every world instance from rebuilding the immutable model
 * hierarchy and gives future geometry submission a stable node->mesh primitive placement list.</p>
 */
public final class ModelSceneLayout {
    private final int sceneIndex;
    private final ModelBounds bounds;
    private final List<ModelPrimitivePlacement> primitives;

    private ModelSceneLayout(int sceneIndex, ModelBounds bounds, List<ModelPrimitivePlacement> primitives) {
        this.sceneIndex = sceneIndex;
        this.bounds = bounds;
        this.primitives = List.copyOf(primitives);
    }

    public static ModelSceneLayout build(ModelAsset asset, int sceneIndex) {
        if (asset == null) throw new IllegalArgumentException("Imported model is required");
        if (sceneIndex < 0 || sceneIndex >= asset.scenes().size()) {
            throw new IllegalArgumentException("Imported model scene index is out of range: " + sceneIndex);
        }
        ModelScene scene = asset.scenes().get(sceneIndex);
        ArrayList<ModelPrimitivePlacement> placements = new ArrayList<>();
        BoundsAccumulator accumulator = new BoundsAccumulator();
        for (int root : scene.roots()) {
            appendNode(asset, root, new Matrix4f(), placements, accumulator);
        }
        return new ModelSceneLayout(sceneIndex, accumulator.bounds, placements);
    }

    private static void appendNode(ModelAsset asset,
                                   int nodeIndex,
                                   Matrix4f parentToAsset,
                                   ArrayList<ModelPrimitivePlacement> out,
                                   BoundsAccumulator accumulator) {
        ModelNode node = asset.nodes().get(nodeIndex);
        Matrix4f nodeToAsset = new Matrix4f(parentToAsset).mul(localMatrix(node));

        for (int meshIndex : node.meshes()) {
            ModelMesh mesh = asset.meshes().get(meshIndex);
            for (int primitiveIndex = 0; primitiveIndex < mesh.primitives().size(); primitiveIndex++) {
                ModelPrimitive primitive = mesh.primitives().get(primitiveIndex);
                ModelBounds transformed = primitive.bounds().transform(nodeToAsset);
                out.add(new ModelPrimitivePlacement(nodeIndex, meshIndex, primitiveIndex, nodeToAsset, transformed));
                accumulator.include(transformed);
            }
        }
        for (int child : node.children()) appendNode(asset, child, nodeToAsset, out, accumulator);
    }

    private static Matrix4f localMatrix(ModelNode node) {
        if (node.hasMatrix()) {
            float[] matrix = node.matrix();
            return new Matrix4f().set(matrix);
        }
        float[] translation = node.translation();
        float[] rotation = node.rotation();
        float[] scale = node.scale();
        return new Matrix4f()
                .translate(translation[0], translation[1], translation[2])
                .rotate(new Quaternionf(rotation[0], rotation[1], rotation[2], rotation[3]))
                .scale(scale[0], scale[1], scale[2]);
    }

    public int sceneIndex() { return sceneIndex; }
    public ModelBounds bounds() { return bounds; }
    public List<ModelPrimitivePlacement> primitives() { return primitives; }
    public boolean hasGeometry() { return bounds.valid() && !primitives.isEmpty(); }

    private static final class BoundsAccumulator {
        ModelBounds bounds = ModelBounds.EMPTY;
        void include(ModelBounds next) { bounds = bounds.union(next); }
    }
}
