/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rig.mesh;

import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3fc;
import combatant.client.render.engine.rig.deform.RigDeformFlags;

/**
 * Converts vanilla {@link ModelPart.Cube} geometry into the CPU-side rig mesh format.
 *
 * <p>The compiler deliberately consumes the final vanilla polygon vertices instead of reconstructing
 * the classic box UV layout. That preserves texture scaling, mirror winding, dilation and the visible-face
 * mask exactly as Minecraft baked them. Generated vertices are subdivided only along the requested
 * deformation axis and remain in ModelPart world units ({@code 1 px == 1/16 block}).</p>
 */
public final class VanillaRigCubeCompiler {
    private static final float EPSILON = 1.0e-7f;

    private VanillaRigCubeCompiler() {
    }

    public static RigMeshData compileData(String partName,
                                          ModelPart.Cube cube,
                                          RigAxis deformationAxis,
                                          int longitudinalSections,
                                          RigSkinBinding skin,
                                          int deformId) {
        return compileData(partName, cube, deformationAxis, longitudinalSections, skin, deformId,
                deformId >= 0 ? RigDeformFlags.ALL : RigDeformFlags.NONE);
    }

    public static RigMeshData compileData(String partName,
                                          ModelPart.Cube cube,
                                          RigAxis deformationAxis,
                                          int longitudinalSections,
                                          RigSkinBinding skin,
                                          int deformId,
                                          int deformFlags) {
        return RigMeshData.builder()
                .part(compile(partName, cube, deformationAxis, longitudinalSections, skin, deformId, deformFlags))
                .build();
    }

    public static RigMeshPart compile(String partName,
                                      ModelPart.Cube cube,
                                      RigAxis deformationAxis,
                                      int longitudinalSections,
                                      RigSkinBinding skin,
                                      int deformId) {
        return compile(partName, cube, deformationAxis, longitudinalSections, skin, deformId,
                deformId >= 0 ? RigDeformFlags.ALL : RigDeformFlags.NONE);
    }

    public static RigMeshPart compile(String partName,
                                      ModelPart.Cube cube,
                                      RigAxis deformationAxis,
                                      int longitudinalSections,
                                      RigSkinBinding skin,
                                      int deformId,
                                      int deformFlags) {
        if (partName == null || partName.isBlank()) throw new IllegalArgumentException("Rig mesh part name must not be blank");
        if (cube == null) throw new IllegalArgumentException("Vanilla ModelPart.Cube must not be null");
        if (deformationAxis == null) throw new IllegalArgumentException("Rig deformation axis must not be null");
        if (longitudinalSections < 1) throw new IllegalArgumentException("Rig cuboid subdivision count must be >= 1");
        if (skin == null) throw new IllegalArgumentException("Rig skin binding must not be null");
        RigVertexEncoding.packDeformMeta(deformId, deformFlags);

        ModelPart.Polygon[] polygons = cube.polygons;
        if (polygons == null || polygons.length == 0) {
            throw new IllegalArgumentException("Vanilla ModelPart.Cube contains no visible polygons");
        }

        Bounds bounds = bounds(cube, polygons);
        int expectedVertices = 0;
        int expectedIndices = 0;
        for (ModelPart.Polygon polygon : polygons) {
            ModelPart.Vertex[] vertices = requireQuad(polygon);
            int sSegments = spansAxis(vertices[0], vertices[1], deformationAxis) ? longitudinalSections : 1;
            int tSegments = spansAxis(vertices[0], vertices[3], deformationAxis) ? longitudinalSections : 1;
            expectedVertices = Math.addExact(expectedVertices, (sSegments + 1) * (tSegments + 1));
            expectedIndices = Math.addExact(expectedIndices, sSegments * tSegments * 6);
        }

        RigMeshPart.Builder output = new RigMeshPart.Builder(partName, expectedVertices, expectedIndices);
        for (ModelPart.Polygon polygon : polygons) {
            appendPolygon(output, polygon, bounds, deformationAxis, longitudinalSections, skin, deformId, deformFlags);
        }
        return output.build();
    }

    private static void appendPolygon(RigMeshPart.Builder output,
                                      ModelPart.Polygon polygon,
                                      Bounds bounds,
                                      RigAxis deformationAxis,
                                      int longitudinalSections,
                                      RigSkinBinding skin,
                                      int deformId,
                                      int deformFlags) {
        ModelPart.Vertex[] vertices = requireQuad(polygon);
        ModelPart.Vertex a = vertices[0];
        ModelPart.Vertex b = vertices[1];
        ModelPart.Vertex c = vertices[2];
        ModelPart.Vertex d = vertices[3];
        Vector3fc normal = polygon.normal();
        if (normal == null) throw new IllegalArgumentException("Vanilla polygon has no normal");

        int sSegments = spansAxis(a, b, deformationAxis) ? longitudinalSections : 1;
        int tSegments = spansAxis(a, d, deformationAxis) ? longitudinalSections : 1;
        int base = output.vertexCount();

        for (int tIndex = 0; tIndex <= tSegments; tIndex++) {
            float t = (float) tIndex / tSegments;
            for (int sIndex = 0; sIndex <= sSegments; sIndex++) {
                float s = (float) sIndex / sSegments;
                float x = bilerp(a.worldX(), b.worldX(), c.worldX(), d.worldX(), s, t);
                float y = bilerp(a.worldY(), b.worldY(), c.worldY(), d.worldY(), s, t);
                float z = bilerp(a.worldZ(), b.worldZ(), c.worldZ(), d.worldZ(), s, t);
                float u = bilerp(a.u(), b.u(), c.u(), d.u(), s, t);
                float v = bilerp(a.v(), b.v(), c.v(), d.v(), s, t);

                float longitudinal = bounds.normalized(deformationAxis, x, y, z);
                float secondWeight = skin.secondWeight(longitudinal);
                float firstWeight = 1.0f - secondWeight;
                int secondBone = secondWeight > 0.0f ? skin.secondBone() : RigVertex.UNUSED_BONE;

                output.addVertex(new RigVertex(
                        x, y, z,
                        u, v,
                        normal.x(), normal.y(), normal.z(),
                        skin.firstBone(), secondBone, RigVertex.UNUSED_BONE, RigVertex.UNUSED_BONE,
                        firstWeight, secondWeight, 0.0f, 0.0f,
                        longitudinal,
                        bounds.lateral(deformationAxis, x, y, z),
                        bounds.depth(deformationAxis, x, y, z),
                        0.0f,
                        deformId,
                        deformFlags
                ));
            }
        }

        int row = sSegments + 1;
        for (int t = 0; t < tSegments; t++) {
            for (int s = 0; s < sSegments; s++) {
                int i0 = base + t * row + s;
                int i1 = i0 + 1;
                int i3 = i0 + row;
                int i2 = i3 + 1;
                // ModelPart.Polygon's final vertex order already contains vanilla mirror winding.
                output.triangle(i0, i1, i2);
                output.triangle(i0, i2, i3);
            }
        }
    }

    private static ModelPart.Vertex[] requireQuad(ModelPart.Polygon polygon) {
        if (polygon == null) throw new IllegalArgumentException("Vanilla cube contains a null polygon");
        ModelPart.Vertex[] vertices = polygon.vertices();
        if (vertices == null || vertices.length != 4) {
            throw new IllegalArgumentException("Vanilla ModelPart.Cube polygon must contain exactly four vertices");
        }
        return vertices;
    }

    private static boolean spansAxis(ModelPart.Vertex a, ModelPart.Vertex b, RigAxis axis) {
        return Math.abs(coordinate(a, axis) - coordinate(b, axis)) > EPSILON;
    }

    private static float coordinate(ModelPart.Vertex vertex, RigAxis axis) {
        return switch (axis) {
            case X -> vertex.worldX();
            case Y -> vertex.worldY();
            case Z -> vertex.worldZ();
        };
    }

    private static Bounds bounds(ModelPart.Cube cube, ModelPart.Polygon[] polygons) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        for (ModelPart.Polygon polygon : polygons) {
            for (ModelPart.Vertex vertex : requireQuad(polygon)) {
                float x = vertex.worldX();
                float y = vertex.worldY();
                float z = vertex.worldZ();
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                maxZ = Math.max(maxZ, z);
            }
        }

        // ModelPart.Cube public extents are the pre-dilation box, while Polygon vertices are already baked
        // with CubeDeformation. A visible-face subset can leave one axis constant, so reconstruct the missing
        // opposite baked plane from that visible plane plus vanilla's symmetric per-axis dilation instead of
        // silently falling back to the smaller pre-dilation extent.
        if (!(maxX - minX > EPSILON)) {
            AxisBounds inferred = inferBakedAxis(minX, cube.minX / 16.0f, cube.maxX / 16.0f);
            minX = inferred.min();
            maxX = inferred.max();
        }
        if (!(maxY - minY > EPSILON)) {
            AxisBounds inferred = inferBakedAxis(minY, cube.minY / 16.0f, cube.maxY / 16.0f);
            minY = inferred.min();
            maxY = inferred.max();
        }
        if (!(maxZ - minZ > EPSILON)) {
            AxisBounds inferred = inferBakedAxis(minZ, cube.minZ / 16.0f, cube.maxZ / 16.0f);
            minZ = inferred.min();
            maxZ = inferred.max();
        }
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static AxisBounds inferBakedAxis(float visiblePlane, float rawA, float rawB) {
        float rawMin = Math.min(rawA, rawB);
        float rawMax = Math.max(rawA, rawB);
        if (!Float.isFinite(visiblePlane)) return new AxisBounds(rawMin, rawMax);

        float distanceToMin = Math.abs(visiblePlane - rawMin);
        float distanceToMax = Math.abs(visiblePlane - rawMax);
        if (distanceToMin <= distanceToMax) {
            float dilation = rawMin - visiblePlane;
            return ordered(visiblePlane, rawMax + dilation);
        }

        float dilation = visiblePlane - rawMax;
        return ordered(rawMin - dilation, visiblePlane);
    }

    private static AxisBounds ordered(float a, float b) {
        return a <= b ? new AxisBounds(a, b) : new AxisBounds(b, a);
    }

    private static float bilerp(float a, float b, float c, float d, float s, float t) {
        float top = a + (b - a) * s;
        float bottom = d + (c - d) * s;
        return top + (bottom - top) * t;
    }

    private record AxisBounds(float min, float max) {
    }

    private record Bounds(float minX, float minY, float minZ,
                          float maxX, float maxY, float maxZ) {
        float normalized(RigAxis axis, float x, float y, float z) {
            float min = min(axis);
            float range = max(axis) - min;
            if (!(range > EPSILON)) return 0.0f;
            float value = switch (axis) {
                case X -> x;
                case Y -> y;
                case Z -> z;
            };
            return clamp01((value - min) / range);
        }

        float lateral(RigAxis axis, float x, float y, float z) {
            return switch (axis) {
                case X -> y - (minY + maxY) * 0.5f;
                case Y, Z -> x - (minX + maxX) * 0.5f;
            };
        }

        float depth(RigAxis axis, float x, float y, float z) {
            return switch (axis) {
                case X, Y -> z - (minZ + maxZ) * 0.5f;
                case Z -> y - (minY + maxY) * 0.5f;
            };
        }

        private float min(RigAxis axis) {
            return switch (axis) {
                case X -> minX;
                case Y -> minY;
                case Z -> minZ;
            };
        }

        private float max(RigAxis axis) {
            return switch (axis) {
                case X -> maxX;
                case Y -> maxY;
                case Z -> maxZ;
            };
        }

        private static float clamp01(float value) {
            if (value <= 0.0f) return 0.0f;
            if (value >= 1.0f) return 1.0f;
            return value;
        }
    }
}
