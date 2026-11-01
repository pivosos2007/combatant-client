/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.blockbench.io;

import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.GeoQuad;
import com.geckolib.cache.model.GeoVertex;
import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.geckolib.cache.model.cuboid.GeoCube;
import com.geckolib.loading.definition.geometry.Geometry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import combatant.client.render.engine.asset.model.data.AlphaMode;
import combatant.client.render.engine.asset.model.data.ModelAsset;
import combatant.client.render.engine.asset.model.data.ModelMaterial;
import combatant.client.render.engine.asset.model.data.ModelMesh;
import combatant.client.render.engine.asset.model.data.ModelNode;
import combatant.client.render.engine.asset.model.data.ModelPrimitive;
import combatant.client.render.engine.asset.model.data.ModelScene;
import combatant.client.render.engine.asset.model.data.ModelTexture;
import combatant.client.render.engine.asset.model.data.PrimitiveMode;
import combatant.client.render.engine.asset.model.io.ModelImporter;
import combatant.client.render.engine.asset.model.io.ModelResourceReader;
import combatant.client.render.engine.asset.model.io.ModelResourceResolver;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import combatant.client.render.engine.asset.model.io.ModelLoadException;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Imports Blockbench's GeckoLib 5 Bedrock geometry export (*.geo.json).
 * GeckoLib owns decoding, UV baking, mirroring, cube inflation and bone hierarchy construction.
 * Combatant owns the immutable primitive representation, scene instancing and RHI GPU upload.
 *
 * <p>Images are resolved either via an optional root-level "combatant:texture" resource identifier,
 * or as matching PNGs next to the model / in GeckoLib's textures directory. A model without a
 * discoverable texture uses the standard white material. The native Blockbench *.bbmodel project
 * format is intentionally not an export format and is not accepted here.</p>
 */
public final class BlockbenchGeckoLibImporter implements ModelImporter {
    private static final int MAX_GEOMETRY_BYTES = 32 * 1024 * 1024;
    private static final int MAX_TEXTURE_BYTES = 64 * 1024 * 1024;
    private static final int MAX_BONES = 65_536;
    private static final int MAX_QUADS = 150_000;

    @Override
    public List<String> extensions() {
        return List.of("geo.json");
    }

    @Override
    public ModelAsset load(Identifier source, ModelResourceResolver resolver) throws IOException {
        final JsonObject json;
        try (InputStream input = resolver.open(source)) {
            byte[] bytes = ModelResourceReader.read(input, MAX_GEOMETRY_BYTES, "GeckoLib geometry " + source);
            json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new ModelLoadException("Invalid GeckoLib geometry JSON: " + source, exception);
        }

        final BakedGeoModel baked;
        try {
            Geometry geometry = Geometry.GSON.fromJson(json, Geometry.class);
            int bones = 0;
            int cubes = 0;
            for (var definition : geometry.definitions()) {
                if (definition.bones() == null) continue;
                bones = Math.addExact(bones, definition.bones().length);
                for (var bone : definition.bones()) {
                    if (bone.cubes() != null) cubes = Math.addExact(cubes, bone.cubes().length);
                }
            }
            if (bones > MAX_BONES || cubes > MAX_QUADS / 6) {
                throw new ModelLoadException("GeckoLib model exceeds bone/cube limits: " + source);
            }
            baked = geometry.bake(source);
        } catch (ModelLoadException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ModelLoadException("GeckoLib failed to bake geometry: " + source, exception);
        }

        List<ModelTexture> textures = loadTexture(source, json, resolver);
        List<ModelMaterial> materials = List.of(new ModelMaterial(
                "geckolib", new float[]{1, 1, 1, 1},
                textures.isEmpty() ? -1 : 0, 0,
                new float[]{0, 0, 0}, -1, 0,
                textures.isEmpty() ? AlphaMode.OPAQUE : AlphaMode.MASK,
                0.1f, true));

        GeometryBuilder builder = new GeometryBuilder();
        ArrayList<Integer> roots = new ArrayList<>();
        for (GeoBone root : baked.topLevelBones()) {
            roots.add(builder.addBone(root, -1));
        }
        if (builder.meshes.isEmpty()) {
            throw new ModelLoadException("GeckoLib model has no renderable cubes: " + source);
        }
        int[] rootIds = roots.stream().mapToInt(Integer::intValue).toArray();
        return new ModelAsset(source,
                List.of(new ModelScene(baked.properties().identifier(), rootIds)),
                builder.nodes, builder.meshes, materials, textures,
                List.of(), List.of(), List.of(), List.of(), 0);
    }

    private static List<ModelTexture> loadTexture(Identifier source, JsonObject json,
                                                 ModelResourceResolver resolver) throws IOException {
        List<Identifier> candidates = new ArrayList<>();
        if (json.has("combatant:texture")) {
            String path = json.get("combatant:texture").getAsString();
            Identifier explicit = Identifier.tryParse(path);
            if (explicit == null) throw new ModelLoadException("Invalid GeckoLib texture id: " + path);
            candidates.add(explicit);
        } else {
            String modelPath = source.getPath();
            String png = modelPath.endsWith(".geo.json")
                    ? modelPath.substring(0, modelPath.length() - ".geo.json".length()) + ".png"
                    : modelPath.substring(0, modelPath.length() - ".json".length()) + ".png";
            candidates.add(Identifier.fromNamespaceAndPath(source.getNamespace(), png));
            // GeckoLib 5 uses geckolib/models and Minecraft's ordinary textures folder.
            String prefix = "geckolib/models/";
            if (png.startsWith(prefix)) {
                candidates.add(Identifier.fromNamespaceAndPath(source.getNamespace(),
                        "textures/" + png.substring(prefix.length())));
            }
            if (png.startsWith("models/")) {
                candidates.add(Identifier.fromNamespaceAndPath(source.getNamespace(),
                        "textures/" + png.substring("models/".length())));
            }
        }
        for (Identifier candidate : candidates) {
            try (InputStream input = resolver.open(candidate)) {
                byte[] bytes = ModelResourceReader.read(input, MAX_TEXTURE_BYTES, "GeckoLib texture " + candidate);
                return List.of(new ModelTexture(candidate.toString(), "image/png", ByteBuffer.wrap(bytes),
                        9728, 9984, 10497, 10497));
            } catch (FileNotFoundException missing) {
                if (json.has("combatant:texture")) throw missing;
                // A GeckoLib geometry file has no built-in texture URI; try the next convention.
            }
        }
        return List.of();
    }

    /** Incrementally creates parent-first nodes so hierarchy validation and scene layout stay generic. */
    private static final class GeometryBuilder {
        final List<ModelNode> nodes = new ArrayList<>();
        final List<ModelMesh> meshes = new ArrayList<>();
        int quadCount;

        int addBone(GeoBone bone, int parent) throws IOException {
            if (nodes.size() >= MAX_BONES) throw new ModelLoadException("GeckoLib bone count exceeds " + MAX_BONES);
            int index = nodes.size();
            nodes.add(null);
            ArrayList<Integer> children = new ArrayList<>();
            ArrayList<Integer> localMeshes = new ArrayList<>();
            if (bone instanceof CuboidGeoBone cuboid) {
                ModelPrimitive primitive = bakePrimitive(cuboid);
                if (primitive != null) {
                    localMeshes.add(meshes.size());
                    meshes.add(new ModelMesh(bone.name(), List.of(primitive), null));
                }
            }
            for (GeoBone child : bone.children()) children.add(addBone(child, index));
            // GeckoLib pivots are in model pixels; its matrices rotate around this pivot.
            float px = bone.pivotX() / 16.0f;
            float py = bone.pivotY() / 16.0f;
            float pz = bone.pivotZ() / 16.0f;
            Quaternionf rotation = new Quaternionf().rotateZYX(
                    bone.baseRotZ(), bone.baseRotY(), bone.baseRotX());
            Vector3f pivot = new Vector3f(px, py, pz);
            Vector3f translation = new Vector3f(pivot).sub(new Vector3f(pivot).rotate(rotation));
            nodes.set(index, new ModelNode(bone.name(), parent,
                    children.stream().mapToInt(Integer::intValue).toArray(),
                    localMeshes.stream().mapToInt(Integer::intValue).toArray(),
                    -1, null,
                    new float[]{translation.x, translation.y, translation.z},
                    new float[]{rotation.x, rotation.y, rotation.z, rotation.w},
                    null, null));
            return index;
        }

        ModelPrimitive bakePrimitive(CuboidGeoBone bone) throws IOException {
            ArrayList<Float> positions = new ArrayList<>();
            ArrayList<Float> normals = new ArrayList<>();
            ArrayList<Float> uvs = new ArrayList<>();
            ArrayList<Integer> indices = new ArrayList<>();
            for (GeoCube cube : bone.cubes) {
                if (cube == null || cube.quads() == null) continue;
                var pivot = cube.pivot();
                Matrix4f transform = new Matrix4f().translate((float)pivot.x / 16, (float)pivot.y / 16, (float)pivot.z / 16)
                        .rotateZYX((float)cube.rotation().z, (float)cube.rotation().y, (float)cube.rotation().x)
                        .translate((float)-pivot.x / 16, (float)-pivot.y / 16, (float)-pivot.z / 16);
                for (GeoQuad quad : cube.quads()) {
                    if (quad == null) continue;
                    if (++quadCount > MAX_QUADS) throw new ModelLoadException("GeckoLib quad count exceeds " + MAX_QUADS);
                    GeoVertex[] vertices = quad.vertices();
                    if (vertices.length != 4) throw new ModelLoadException("GeckoLib cube face must have four vertices");
                    int first = positions.size() / 3;
                    Vector3f transformedNormal = transform.transformDirection(
                            new Vector3f(quad.normalX(), quad.normalY(), quad.normalZ())).normalize();
                    for (GeoVertex vertex : vertices) {
                        Vector4f point = transform.transform(new Vector4f(vertex.posX(), vertex.posY(), vertex.posZ(), 1));
                        positions.add(point.x()); positions.add(point.y()); positions.add(point.z());
                        normals.add(transformedNormal.x); normals.add(transformedNormal.y); normals.add(transformedNormal.z);
                        uvs.add(vertex.texU()); uvs.add(vertex.texV());
                    }
                    // GeckoLib's quad vertices are already in render order, but validate winding
                    // against the baked normal to keep face culling consistent in the RHI backend.
                    float ax = positions.get(3 * (first + 1)) - positions.get(3 * first);
                    float ay = positions.get(3 * (first + 1) + 1) - positions.get(3 * first + 1);
                    float az = positions.get(3 * (first + 1) + 2) - positions.get(3 * first + 2);
                    float bx = positions.get(3 * (first + 2)) - positions.get(3 * first);
                    float by = positions.get(3 * (first + 2) + 1) - positions.get(3 * first + 1);
                    float bz = positions.get(3 * (first + 2) + 2) - positions.get(3 * first + 2);
                    float facing = (ay * bz - az * by) * transformedNormal.x
                            + (az * bx - ax * bz) * transformedNormal.y
                            + (ax * by - ay * bx) * transformedNormal.z;
                    if (facing >= 0) {
                        indices.add(first); indices.add(first + 1); indices.add(first + 2);
                        indices.add(first); indices.add(first + 2); indices.add(first + 3);
                    } else {
                        indices.add(first); indices.add(first + 2); indices.add(first + 1);
                        indices.add(first); indices.add(first + 3); indices.add(first + 2);
                    }
                }
            }
            if (indices.isEmpty()) return null;
            return new ModelPrimitive(PrimitiveMode.TRIANGLES, 0, positions.size() / 3,
                    toFloats(positions), toFloats(normals), null, toFloats(uvs), null,
                    null, null, null, indices.stream().mapToInt(Integer::intValue).toArray(), List.of());
        }

        private static float[] toFloats(List<Float> list) {
            float[] values = new float[list.size()];
            for (int i = 0; i < values.length; i++) values[i] = list.get(i);
            return values;
        }
    }
}
