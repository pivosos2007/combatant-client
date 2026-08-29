/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rig.mesh;

import combatant.client.render.engine.rig.deform.RigDeformFlags;

/**
 * One-time cuboid subdivision compiler. Side faces are split only along the requested longitudinal axis;
 * end caps remain single quads. Position and UV interpolation use the same face parameters, preserving
 * the original face mapping exactly while introducing the vertices required for GPU deformation.
 */
public final class RigCuboidSubdivision {
    private RigCuboidSubdivision() {
    }

    public static RigMeshPart compile(String partName,
                                      RigCuboid cuboid,
                                      RigAxis deformationAxis,
                                      int longitudinalSections,
                                      RigSkinBinding skin,
                                      int deformId) {
        return compile(partName, cuboid, deformationAxis, longitudinalSections, skin, deformId,
                deformId >= 0 ? RigDeformFlags.ALL : RigDeformFlags.NONE);
    }

    public static RigMeshPart compile(String partName,
                                      RigCuboid cuboid,
                                      RigAxis deformationAxis,
                                      int longitudinalSections,
                                      RigSkinBinding skin,
                                      int deformId,
                                      int deformFlags) {
        if (partName == null || partName.isBlank()) throw new IllegalArgumentException("Rig mesh part name must not be blank");
        if (cuboid == null) throw new IllegalArgumentException("Rig cuboid must not be null");
        if (deformationAxis == null) throw new IllegalArgumentException("Rig deformation axis must not be null");
        if (longitudinalSections < 1) throw new IllegalArgumentException("Rig cuboid subdivision count must be >= 1");
        if (skin == null) throw new IllegalArgumentException("Rig skin binding must not be null");
        RigVertexEncoding.packDeformMeta(deformId, deformFlags);

        int visibleFaces = 0;
        int sideFaces = 0;
        for (RigFace face : RigFace.values()) {
            if (!cuboid.hasFace(face)) continue;
            visibleFaces++;
            if (face.spansOnS(cuboid, deformationAxis) || face.spansOnT(cuboid, deformationAxis)) sideFaces++;
        }
        int capFaces = visibleFaces - sideFaces;
        int expectedVertices = sideFaces * ((longitudinalSections + 1) * 2) + capFaces * 4;
        int expectedIndices = sideFaces * longitudinalSections * 6 + capFaces * 6;
        RigMeshPart.Builder output = new RigMeshPart.Builder(partName, expectedVertices, expectedIndices);
        RigSkinBinding.Sample skinSample = new RigSkinBinding.Sample();

        for (RigFace face : RigFace.values()) {
            RigFaceUv uv = cuboid.faceUv(face);
            if (uv == null) continue;

            int sSegments = face.spansOnS(cuboid, deformationAxis) ? longitudinalSections : 1;
            int tSegments = face.spansOnT(cuboid, deformationAxis) ? longitudinalSections : 1;
            int base = output.vertexCount();

            for (int tIndex = 0; tIndex <= tSegments; tIndex++) {
                float t = (float) tIndex / tSegments;
                for (int sIndex = 0; sIndex <= sSegments; sIndex++) {
                    float s = (float) sIndex / sSegments;
                    float x = face.x(cuboid, s, t);
                    float y = face.y(cuboid, s, t);
                    float z = face.z(cuboid, s, t);
                    float longitudinal = deformationAxis.normalized(cuboid, x, y, z);
                    skin.sample(longitudinal, skinSample);

                    output.addVertex(new RigVertex(
                            x, y, z,
                            uv.u(s, t), uv.v(s, t),
                            face.normalX(), face.normalY(), face.normalZ(),
                            skinSample.bone(0), skinSample.bone(1), skinSample.bone(2), skinSample.bone(3),
                            skinSample.weight(0), skinSample.weight(1), skinSample.weight(2), skinSample.weight(3),
                            longitudinal,
                            deformationAxis.lateral(cuboid, x, y, z),
                            deformationAxis.depth(cuboid, x, y, z),
                            0f,
                            deformId,
                            deformFlags
                    ));
                }
            }

            int row = sSegments + 1;
            for (int t = 0; t < tSegments; t++) {
                for (int s = 0; s < sSegments; s++) {
                    int a = base + t * row + s;
                    int b = a + 1;
                    int d = a + row;
                    int c = d + 1;
                    output.triangle(a, b, c);
                    output.triangle(a, c, d);
                }
            }
        }

        return output.build();
    }
}
