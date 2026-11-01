/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are adapted from GFBS-glTF 1.5.1
 * (commit 8906d4083c23b5d5bbf5191a0555d149a5272900).
 * Copyright (c) 2026 LytharaLab. Original portions are licensed under the MIT License.
 * See THIRD_PARTY_LICENSES/GFBS-glTF-MIT.txt.
 *
 * Combatant modifications are distributed under GNU GPL v3.0 as part of Combatant.
 */
package combatant.client.render.engine.asset.obj.io;

import combatant.client.render.engine.asset.model.io.ModelLoadException;
import net.minecraft.resources.Identifier;
import combatant.client.render.engine.asset.model.io.ModelResourceResolver;
import combatant.client.render.engine.asset.model.io.ModelImporter;
import combatant.client.render.engine.asset.model.data.AlphaMode;
import combatant.client.render.engine.asset.model.data.ModelAsset;
import combatant.client.render.engine.asset.model.data.ModelMaterial;
import combatant.client.render.engine.asset.model.data.ModelMesh;
import combatant.client.render.engine.asset.model.data.ModelNode;
import combatant.client.render.engine.asset.model.data.ModelPrimitive;
import combatant.client.render.engine.asset.model.data.ModelScene;
import combatant.client.render.engine.asset.model.data.ModelTexture;
import combatant.client.render.engine.asset.model.data.PrimitiveMode;
import combatant.client.render.engine.asset.model.io.ModelResourcePaths;
import combatant.client.render.engine.asset.model.io.ModelResourceReader;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * OBJ/MTL importer which converts groups and materials into the normal immutable GFBS asset model.
 */
public final class ObjAssetImporter implements ModelImporter {
    private static final int MAX_TEXT_BYTES = 32 * 1024 * 1024;
    private static final int MAX_IMAGE_BYTES = 64 * 1024 * 1024;

    @Override
    public Collection<String> extensions() {
        return List.of("obj");
    }

    @Override
    public ModelAsset load(Identifier source, ModelResourceResolver resolver) throws IOException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(resolver, "resolver");
        List<float[]> positions = new ArrayList<>();
        List<float[]> texCoords = new ArrayList<>();
        List<float[]> normals = new ArrayList<>();
        Map<String, MaterialBuilder> materials = new LinkedHashMap<>();
        Map<GroupKey, GroupBuilder> groups = new LinkedHashMap<>();
        String groupName = "default";
        String materialName = "default";
        String smoothingGroup = "off";
        materials.put(materialName, new MaterialBuilder(materialName));

        try (InputStream input = resolver.open(source);
             BufferedReader reader = reader(input, MAX_TEXT_BYTES)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int comment = line.indexOf('#');
                if (comment >= 0) line = line.substring(0, comment);
                line = line.trim();
                if (line.isEmpty()) continue;
                int split = firstWhitespace(line);
                String key = split < 0 ? line : line.substring(0, split);
                String rest = split < 0 ? "" : line.substring(split).trim();
                try {
                    switch (key) {
                        case "v" -> positions.add(parseFloats(rest, 3, "position"));
                        case "vt" -> texCoords.add(parseFloats(rest, 2, "texture coordinate"));
                        case "vn" -> normals.add(parseFloats(rest, 3, "normal"));
                        case "o", "g" -> groupName = rest.isBlank() ? "group_" + groups.size() : rest;
                        case "s" -> smoothingGroup = rest.isBlank() ? "off" : rest;
                        case "usemtl" -> {
                            materialName = rest.isBlank() ? "default" : rest;
                            materials.computeIfAbsent(materialName, MaterialBuilder::new);
                        }
                        case "mtllib" -> {
                            // OBJ permits multiple material libraries on a single mtllib line.
                            for (String library : rest.split("\\s+")) {
                                if (!library.isBlank()) loadMaterialLibrary(
                                    ModelResourcePaths.sibling(source, library), resolver, materials);
                            }
                        }
                        case "f" -> groups.computeIfAbsent(
                            new GroupKey(groupName, materialName), GroupBuilder::new
                        ).addFace(rest, positions, texCoords, normals, smoothingGroup);
                        default -> {
                        }
                    }
                } catch (RuntimeException exception) {
                    throw new ModelLoadException("Invalid OBJ statement '" + key + "' in " + source, exception);
                }
            }
        }

        if (positions.isEmpty()) throw new ModelLoadException("OBJ contains no positions: " + source);
        if (groups.values().stream().noneMatch(group -> !group.indices.isEmpty())) {
            throw new ModelLoadException("OBJ contains no faces: " + source);
        }

        List<ModelTexture> textures = new ArrayList<>();
        Map<String, Integer> textureIndices = new LinkedHashMap<>();
        List<ModelMaterial> builtMaterials = new ArrayList<>();
        Map<String, Integer> materialIndices = new LinkedHashMap<>();
        for (MaterialBuilder material : materials.values()) {
            int diffuseTexture = loadTexture(material.source == null ? source : material.source, resolver, textures, textureIndices, material.diffuseMap);
            int normalTexture = loadTexture(material.source == null ? source : material.source, resolver, textures, textureIndices, material.normalMap);
            int emissiveTexture = loadTexture(material.source == null ? source : material.source, resolver, textures, textureIndices, material.emissiveMap);

            materialIndices.put(material.name, builtMaterials.size());
            builtMaterials.add(new ModelMaterial(
                material.name,
                new float[]{material.red, material.green, material.blue, material.alpha},
                diffuseTexture,
                0,
                material.metallic,
                material.roughness,
                -1,
                0,
                normalTexture,
                0,
                material.normalScale,
                -1,
                0,
                1.0f,
                new float[]{material.emissiveRed, material.emissiveGreen, material.emissiveBlue},
                emissiveTexture,
                0,
                material.alpha < 0.999f ? AlphaMode.BLEND : AlphaMode.OPAQUE,
                0.5f,
                false
            ));
        }

        List<ModelMesh> meshes = new ArrayList<>();
        List<ModelNode> nodes = new ArrayList<>();
        for (GroupBuilder group : groups.values()) {
            if (group.indices.isEmpty()) continue;
            group.finishNormals();
            int vertexCount = group.vertices.size();
            float[] meshPositions = new float[vertexCount * 3];
            float[] meshNormals = new float[vertexCount * 3];
            float[] meshTexCoords = new float[vertexCount * 2];
            for (int vertex = 0; vertex < vertexCount; vertex++) {
                Vertex data = group.vertices.get(vertex);
                System.arraycopy(data.position, 0, meshPositions, vertex * 3, 3);
                System.arraycopy(data.normal, 0, meshNormals, vertex * 3, 3);
                System.arraycopy(data.uv, 0, meshTexCoords, vertex * 2, 2);
            }
            int material = materialIndices.getOrDefault(group.key.material(), 0);
            ModelPrimitive primitive = new ModelPrimitive(
                PrimitiveMode.TRIANGLES,
                material,
                vertexCount,
                meshPositions,
                meshNormals,
                null,
                meshTexCoords,
                null,
                null,
                null,
                null,
                group.indices.stream().mapToInt(Integer::intValue).toArray(),
                List.of()
            );
            int mesh = meshes.size();
            meshes.add(new ModelMesh(group.key.displayName(), List.of(primitive), null));
            nodes.add(new ModelNode(
                group.key.displayName(),
                -1,
                new int[0],
                new int[]{mesh},
                -1,
                null,
                null,
                null,
                null,
                null
            ));
        }

        int[] roots = new int[nodes.size()];
        for (int i = 0; i < roots.length; i++) roots[i] = i;
        return new ModelAsset(
            source,
            List.of(new ModelScene("OBJ", roots)),
            nodes,
            meshes,
            builtMaterials,
            textures,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            0
        );
    }

    private static int loadTexture(Identifier source, ModelResourceResolver resolver,
                                   List<ModelTexture> textures, Map<String, Integer> textureIndices,
                                   String relativePath) throws IOException {
        if (relativePath == null || relativePath.isBlank()) return -1;
        Identifier textureLocation = ModelResourcePaths.sibling(source, relativePath);
        String cacheKey = textureLocation.toString();
        Integer existing = textureIndices.get(cacheKey);
        if (existing != null) return existing;
        byte[] encoded;
        try (InputStream image = resolver.open(textureLocation)) {
            encoded = ModelResourceReader.read(image, MAX_IMAGE_BYTES, "OBJ texture " + textureLocation);
        }
        int index = textures.size();
        textures.add(new ModelTexture(
            relativePath,
            mimeType(relativePath),
            ByteBuffer.wrap(encoded),
            9729,
            9987,
            10497,
            10497
        ));
        textureIndices.put(cacheKey, index);
        return index;
    }

    private static void loadMaterialLibrary(Identifier location, ModelResourceResolver resolver,
                                            Map<String, MaterialBuilder> materials) throws IOException {
        try (InputStream input = resolver.open(location);
             BufferedReader reader = reader(input, MAX_TEXT_BYTES)) {
            MaterialBuilder current = null;
            String line;
            while ((line = reader.readLine()) != null) {
                int comment = line.indexOf('#');
                if (comment >= 0) line = line.substring(0, comment);
                line = line.trim();
                if (line.isEmpty()) continue;
                int split = firstWhitespace(line);
                String key = split < 0 ? line : line.substring(0, split);
                String rest = split < 0 ? "" : line.substring(split).trim();
                switch (key) {
                    case "newmtl" -> {
                        current = materials.computeIfAbsent(rest, MaterialBuilder::new);
                        current.source = location;
                    }
                    case "Kd" -> {
                        if (current != null) {
                            float[] diffuse = parseFloats(rest, 3, "diffuse color");
                            current.red = unit(diffuse[0]);
                            current.green = unit(diffuse[1]);
                            current.blue = unit(diffuse[2]);
                        }
                    }
                    case "Ke" -> {
                        if (current != null) {
                            float[] emissive = parseFloats(rest, 3, "emissive color");
                            current.emissiveRed = unit(emissive[0]);
                            current.emissiveGreen = unit(emissive[1]);
                            current.emissiveBlue = unit(emissive[2]);
                        }
                    }
                    // Common PBR MTL extensions used by modern OBJ exporters.
                    case "Pm" -> {
                        if (current != null) current.metallic = unit(firstFloat(rest));
                    }
                    case "Pr" -> {
                        if (current != null) current.roughness = unit(firstFloat(rest));
                    }
                    case "d" -> {
                        if (current != null) current.alpha = unit(firstFloat(rest));
                    }
                    case "Tr" -> {
                        if (current != null) current.alpha = 1.0f - unit(firstFloat(rest));
                    }
                    case "map_Kd" -> {
                        if (current != null && !rest.isBlank()) current.diffuseMap = stripMapOptions(rest);
                    }
                    case "map_Ke" -> {
                        if (current != null && !rest.isBlank()) current.emissiveMap = stripMapOptions(rest);
                    }
                    case "map_Bump", "map_bump", "bump", "norm", "map_Kn" -> {
                        if (current != null && !rest.isBlank()) {
                            current.normalMap = stripMapOptions(rest);
                            current.normalScale = parseBumpMultiplier(rest, 1.0f);
                        }
                    }
                    default -> {
                    }
                }
            }
        }
    }

    private static BufferedReader reader(InputStream input, int limit) throws IOException {
        byte[] bytes = ModelResourceReader.read(input, limit, "OBJ/MTL text");
        return new BufferedReader(new InputStreamReader(
            new ByteArrayInputStream(bytes), StandardCharsets.UTF_8
        ));
    }

    private static int firstWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) return i;
        }
        return -1;
    }

    private static float[] parseFloats(String value, int count, String label) {
        String[] parts = value.trim().split("\\s+");
        if (parts.length < count) throw new IllegalArgumentException("Missing " + label + " components");
        float[] result = new float[count];
        for (int i = 0; i < count; i++) {
            result[i] = Float.parseFloat(parts[i]);
            if (!Float.isFinite(result[i])) throw new IllegalArgumentException("Non-finite " + label);
        }
        return result;
    }

    private static float firstFloat(String value) {
        return Float.parseFloat(value.trim().split("\\s+")[0]);
    }

    private static float unit(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static float parseBumpMultiplier(String value, float fallback) {
        String[] parts = value.trim().split("\\s+");
        for (int i = 0; i + 1 < parts.length; i++) {
            if (parts[i].equals("-bm")) {
                try {
                    float parsed = Float.parseFloat(parts[i + 1]);
                    return Float.isFinite(parsed) ? parsed : fallback;
                } catch (NumberFormatException ignored) {
                    return fallback;
                }
            }
        }
        return fallback;
    }

    private static String stripMapOptions(String value) {
        String[] parts = value.trim().split("\\s+");
        return parts.length == 0 ? "" : parts[parts.length - 1];
    }

    private static String mimeType(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }

    private record GroupKey(String group, String material) {
        private GroupKey {
            group = group == null || group.isBlank() ? "default" : group;
            material = material == null || material.isBlank() ? "default" : material;
        }

        String displayName() {
            return material.equals("default") ? group : group + "/" + material;
        }
    }

    private static final class MaterialBuilder {
        final String name;
        float red = 1.0f;
        float green = 1.0f;
        float blue = 1.0f;
        float alpha = 1.0f;
        float metallic = 0.0f;
        float roughness = 1.0f;
        float emissiveRed;
        float emissiveGreen;
        float emissiveBlue;
        float normalScale = 1.0f;
        Identifier source;
        String diffuseMap;
        String normalMap;
        String emissiveMap;

        MaterialBuilder(String name) {
            this.name = name == null || name.isBlank() ? "default" : name;
        }
    }

    private static final class Vertex {
        final float[] position = new float[3];
        final float[] uv = new float[2];
        final float[] normal = new float[3];
        boolean explicitNormal;
    }

    private static final class GroupBuilder {
        final GroupKey key;
        final List<Vertex> vertices = new ArrayList<>();
        final List<Integer> indices = new ArrayList<>();
        final Map<String, Integer> vertexCache = new LinkedHashMap<>();
        int faceId;

        GroupBuilder(GroupKey key) {
            this.key = key;
        }

        void addFace(String face, List<float[]> positions, List<float[]> texCoords,
                     List<float[]> normals, String smoothingGroup) {
            String[] tokens = face.trim().split("\\s+");
            if (tokens.length < 3) throw new IllegalArgumentException("Face has fewer than three vertices");
            int[] resolved = new int[tokens.length];
            // With smoothing disabled, vertices from adjacent faces must not be welded.
            String faceSmoothing = smoothingGroup.equalsIgnoreCase("off") || smoothingGroup.equals("0")
                    ? "face:" + faceId : "group:" + smoothingGroup;
            faceId++;
            for (int i = 0; i < tokens.length; i++) {
                resolved[i] = resolve(tokens[i], positions, texCoords, normals, faceSmoothing);
            }
            for (int i = 1; i + 1 < resolved.length; i++) {
                indices.add(resolved[0]);
                indices.add(resolved[i]);
                indices.add(resolved[i + 1]);
            }
        }

        int resolve(String token, List<float[]> positions, List<float[]> texCoords,
                    List<float[]> normals, String faceSmoothing) {
            String[] fields = token.split("/", -1);
            if (fields.length == 0 || fields[0].isEmpty()) {
                throw new IllegalArgumentException("Face vertex has no position index");
            }
            int position = index(fields[0], positions.size());
            int texCoord = fields.length > 1 && !fields[1].isEmpty() ? index(fields[1], texCoords.size()) : -1;
            int normal = fields.length > 2 && !fields[2].isEmpty() ? index(fields[2], normals.size()) : -1;
            // Negative OBJ indices are relative to the *current* array length, not the whole file.
            // Cache resolved absolute indices instead of the original token.
            String key = position + "/" + texCoord + "/" + normal
                    + (normal < 0 ? "/" + faceSmoothing : "");
            Integer cached = vertexCache.get(key);
            if (cached != null) return cached;
            Vertex vertex = new Vertex();
            System.arraycopy(positions.get(position), 0, vertex.position, 0, 3);
            if (texCoord >= 0) {
                float[] uv = texCoords.get(texCoord);
                vertex.uv[0] = uv[0];
                vertex.uv[1] = 1.0f - uv[1];
            }
            if (normal >= 0) {
                System.arraycopy(normals.get(normal), 0, vertex.normal, 0, 3);
                normalize(vertex.normal);
                vertex.explicitNormal = true;
            }
            int result = vertices.size();
            vertices.add(vertex);
            vertexCache.put(key, result);
            return result;
        }

        void finishNormals() {
            boolean needsGenerated = vertices.stream().anyMatch(vertex -> !vertex.explicitNormal);
            if (!needsGenerated) return;
            for (int i = 0; i + 2 < indices.size(); i += 3) {
                Vertex a = vertices.get(indices.get(i));
                Vertex b = vertices.get(indices.get(i + 1));
                Vertex c = vertices.get(indices.get(i + 2));
                float abX = b.position[0] - a.position[0];
                float abY = b.position[1] - a.position[1];
                float abZ = b.position[2] - a.position[2];
                float acX = c.position[0] - a.position[0];
                float acY = c.position[1] - a.position[1];
                float acZ = c.position[2] - a.position[2];
                float nx = abY * acZ - abZ * acY;
                float ny = abZ * acX - abX * acZ;
                float nz = abX * acY - abY * acX;
                addNormal(a, nx, ny, nz);
                addNormal(b, nx, ny, nz);
                addNormal(c, nx, ny, nz);
            }
            for (Vertex vertex : vertices) {
                if (!vertex.explicitNormal) normalize(vertex.normal);
            }
        }

        private static void addNormal(Vertex vertex, float x, float y, float z) {
            if (vertex.explicitNormal) return;
            vertex.normal[0] += x;
            vertex.normal[1] += y;
            vertex.normal[2] += z;
        }

        private static int index(String encoded, int size) {
            int index = Integer.parseInt(encoded);
            index = index < 0 ? size + index : index - 1;
            if (index < 0 || index >= size) throw new IllegalArgumentException("OBJ index is out of range");
            return index;
        }
    }

    private static void normalize(float[] vector) {
        double lengthSquared = (double) vector[0] * vector[0]
            + (double) vector[1] * vector[1]
            + (double) vector[2] * vector[2];
        if (!Double.isFinite(lengthSquared) || lengthSquared <= 1.0e-16) {
            vector[0] = 0.0f;
            vector[1] = 1.0f;
            vector[2] = 0.0f;
            return;
        }
        float inverse = (float) (1.0 / Math.sqrt(lengthSquared));
        vector[0] *= inverse;
        vector[1] *= inverse;
        vector[2] *= inverse;
    }
}
