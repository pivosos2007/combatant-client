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
package combatant.client.render.engine.asset.gltf.io;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.javagl.jgltf.model.*;
import de.javagl.jgltf.model.AnimationModel.Channel;
import de.javagl.jgltf.model.io.GltfAssetReader;
import de.javagl.jgltf.model.io.GltfReferenceResolver;
import de.javagl.jgltf.model.v2.MaterialModelV2;
import net.minecraft.resources.Identifier;
import combatant.client.render.engine.asset.model.io.ModelResourcePaths;
import combatant.client.render.engine.asset.model.animation.*;
import combatant.client.render.engine.asset.model.data.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/** Converts JglTF's mutable glTF document to the common imported-model representation. */
public final class JgltfAssetLoader {
    public static final long DEFAULT_MAX_RESOURCE_BYTES = 256L * 1024L * 1024L;
    private static final int MAX_NODES = 100_000;
    private static final int MAX_MESHES = 100_000;
    private static final int MAX_PRIMITIVES = 250_000;
    private static final int MAX_TEXTURES = 16_384;
    private static final int MAX_ANIMATIONS = 16_384;
    private static final int MAX_ANIMATION_CHANNELS = 250_000;
    private static final long MAX_RUNTIME_ARRAY_BYTES = 256L * 1024L * 1024L;
    private static final Set<String> SUPPORTED_REQUIRED_EXTENSIONS = Set.of(
        "KHR_mesh_quantization",
        "KHR_texture_transform",
        "KHR_materials_unlit",
        "KHR_materials_emissive_strength"
    );
    private static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final int GLB_MAGIC = 0x46546C67;
    private static final int GLB_VERSION_2 = 2;
    private static final int GLB_JSON_CHUNK = 0x4E4F534A;
    private static final int MAX_JSON_NESTING = 512;

    private final long maxResourceBytes;

    public JgltfAssetLoader() {
        this(DEFAULT_MAX_RESOURCE_BYTES);
    }

    public JgltfAssetLoader(long maxResourceBytes) {
        if (maxResourceBytes < 1024) throw new IllegalArgumentException("Resource limit is too small");
        this.maxResourceBytes = maxResourceBytes;
    }

    public ModelAsset load(Identifier id, GltfResourceResolver resolver) throws IOException {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(resolver, "resolver");
        ReadBudget budget = new ReadBudget(maxResourceBytes);
        try (InputStream opened = requireStream(resolver.open(id), id)) {
            byte[] sourceBytes = readAll(opened, budget, id.toString());
            byte[] normalizedBytes = normalizeDataUris(sourceBytes, budget);
            ObjectNode jsonRoot = readJsonRoot(normalizedBytes);
            GltfAssetReader reader = new GltfAssetReader();
            de.javagl.jgltf.model.io.GltfAsset parsed = reader.readWithoutReferences(
                new ByteArrayInputStream(normalizedBytes));
            if (!(parsed.getGltf() instanceof de.javagl.jgltf.impl.v2.GlTF)) {
                throw new GltfLoadException(id, "Only glTF 2.0 assets are supported");
            }
            GltfReferenceResolver.resolveAll(parsed.getReferences(), uri -> {
                try {
                    if (isDataUri(uri)) return decodeDataUri(uri, budget);
                    Identifier child = resolveRelative(id, uri);
                    try (InputStream external = requireStream(resolver.open(child), child)) {
                        return ByteBuffer.wrap(readAll(external, budget, child.toString())).order(ByteOrder.LITTLE_ENDIAN);
                    }
                } catch (IOException | RuntimeException exception) {
                    throw new ReferenceResolutionException(exception);
                }
            });
            de.javagl.jgltf.impl.v2.GlTF gltf = (de.javagl.jgltf.impl.v2.GlTF) parsed.getGltf();
            int defaultScene = gltf.getScene() == null ? 0 : gltf.getScene();
            return convert(id, GltfModels.create(parsed), defaultScene, jsonRoot);
        } catch (ReferenceResolutionException exception) {
            Throwable cause = exception.getCause();
            String message = message(cause);
            if (cause instanceof IOException io) throw new GltfLoadException(id, message, io);
            throw new GltfLoadException(id, message, cause);
        } catch (GltfLoadException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new GltfLoadException(id, message(exception), exception);
        }
    }

    private ModelAsset convert(Identifier id, GltfModel source, int defaultScene,
                              ObjectNode jsonRoot) throws GltfLoadException {
        List<NodeModel> sourceNodes = source.getNodeModels();
        List<MeshModel> sourceMeshes = source.getMeshModels();
        List<TextureModel> sourceTextures = source.getTextureModels();
        List<AnimationModel> sourceAnimations = source.getAnimationModels();
        requireCount(id, "nodes", sourceNodes.size(), MAX_NODES);
        requireCount(id, "meshes", sourceMeshes.size(), MAX_MESHES);
        requireCount(id, "textures", sourceTextures.size(), MAX_TEXTURES);
        requireCount(id, "animations", sourceAnimations.size(), MAX_ANIMATIONS);
        long primitiveCount = sourceMeshes.stream().mapToLong(mesh -> mesh.getMeshPrimitiveModels().size()).sum();
        if (primitiveCount > MAX_PRIMITIVES) {
            throw new GltfLoadException(id, "Asset contains too many mesh primitives: " + primitiveCount);
        }
        long channelCount = sourceAnimations.stream().mapToLong(animation -> animation.getChannels().size()).sum();
        if (channelCount > MAX_ANIMATION_CHANNELS) {
            throw new GltfLoadException(id, "Asset contains too many animation channels: " + channelCount);
        }
        validateRuntimeArrayBudget(id, source);

        ExtensionsModel extensions = source.getExtensionsModel();
        List<String> used = extensions == null ? List.of() : extensions.getExtensionsUsed();
        List<String> required = extensions == null ? List.of() : extensions.getExtensionsRequired();
        if (required != null) {
            for (String extension : required) {
                if (!SUPPORTED_REQUIRED_EXTENSIONS.contains(extension)) {
                    throw new GltfLoadException(id, "Required extension is not supported: " + extension);
                }
            }
        }

        IdentityHashMap<NodeModel, Integer> nodeIds = index(sourceNodes);
        IdentityHashMap<MeshModel, Integer> meshIds = index(sourceMeshes);
        IdentityHashMap<SkinModel, Integer> skinIds = index(source.getSkinModels());
        IdentityHashMap<TextureModel, Integer> textureIds = index(sourceTextures);
        IdentityHashMap<MaterialModel, Integer> materialIds = index(source.getMaterialModels());

        List<ModelTexture> textures = convertTextures(sourceTextures);
        List<ModelMaterial> materials = convertMaterials(
            source.getMaterialModels(),
            textureIds,
            jsonRoot.get("materials")
        );
        int defaultMaterial = materials.size();
        materials.add(ModelMaterial.defaultMaterial());
        List<ModelMesh> meshes = convertMeshes(sourceMeshes, materialIds, defaultMaterial);
        List<ModelNode> nodes = convertNodes(sourceNodes, nodeIds, meshIds, skinIds);
        List<ModelScene> scenes = convertScenes(source.getSceneModels(), nodeIds, nodes);
        List<ModelSkin> skins = convertSkins(source.getSkinModels(), nodeIds);
        List<AnimationClip> animations = convertAnimations(sourceAnimations, nodeIds);
        if (!scenes.isEmpty() && (defaultScene < 0 || defaultScene >= scenes.size())) {
            throw new GltfLoadException(id, "Default scene index is out of range: " + defaultScene);
        }
        return new ModelAsset(id, scenes, nodes, meshes, materials, textures, skins,
            animations, used == null ? List.of() : used, required == null ? List.of() : required,
            scenes.isEmpty() ? 0 : defaultScene);
    }

    /** glTF topology codes are a format-specific concern, never part of the shared model enum. */
    private static PrimitiveMode primitiveMode(int code) {
        return switch (code) {
            case 0 -> PrimitiveMode.POINTS;
            case 1 -> PrimitiveMode.LINES;
            case 2 -> PrimitiveMode.LINE_LOOP;
            case 3 -> PrimitiveMode.LINE_STRIP;
            case 4 -> PrimitiveMode.TRIANGLES;
            case 5 -> PrimitiveMode.TRIANGLE_STRIP;
            case 6 -> PrimitiveMode.TRIANGLE_FAN;
            default -> throw new IllegalArgumentException("Unsupported glTF primitive mode: " + code);
        };
    }

    private static List<ModelTexture> convertTextures(List<TextureModel> sources) {
        List<ModelTexture> result = new ArrayList<>(sources.size());
        for (int index = 0; index < sources.size(); index++) {
            TextureModel texture = sources.get(index);
            ImageModel image = texture.getImageModel();
            if (image == null || image.getImageData() == null) {
                throw new IllegalArgumentException("Texture " + index + " has no decodable image source");
            }
            String mime = image.getMimeType();
            if (mime == null) mime = guessMime(image.getUri());
            result.add(new ModelTexture(texture.getName(), mime, image.getImageData(),
                value(texture.getMagFilter(), 9729), value(texture.getMinFilter(), 9987),
                value(texture.getWrapS(), 10497), value(texture.getWrapT(), 10497)));
        }
        return result;
    }

    private static List<ModelMaterial> convertMaterials(
        List<MaterialModel> sources,
        IdentityHashMap<TextureModel, Integer> textureIds,
        JsonNode materialNodes
    ) {
        List<ModelMaterial> result = new ArrayList<>(sources.size() + 1);
        for (int materialIndex = 0; materialIndex < sources.size(); materialIndex++) {
            MaterialModel source = sources.get(materialIndex);
            if (source instanceof MaterialModelV2 material) {
                combatant.client.render.engine.asset.model.data.AlphaMode alphaMode = material.getAlphaMode() == null
                    ? combatant.client.render.engine.asset.model.data.AlphaMode.OPAQUE
                    : combatant.client.render.engine.asset.model.data.AlphaMode.valueOf(material.getAlphaMode().name());
                JsonNode materialNode = arrayElement(materialNodes, materialIndex);
                JsonNode pbrNode = child(materialNode, "pbrMetallicRoughness");
                JsonNode extensions = child(materialNode, "extensions");
                JsonNode emissiveStrengthNode = child(extensions, "KHR_materials_emissive_strength");
                result.add(new ModelMaterial(
                    material.getName(),
                    material.getBaseColorFactor(),
                    textureInfo(
                        child(pbrNode, "baseColorTexture"),
                        id(textureIds, material.getBaseColorTexture()),
                        value(material.getBaseColorTexcoord(), 0)
                    ),
                    material.getMetallicFactor(),
                    material.getRoughnessFactor(),
                    textureInfo(
                        child(pbrNode, "metallicRoughnessTexture"),
                        id(textureIds, material.getMetallicRoughnessTexture()),
                        value(material.getMetallicRoughnessTexcoord(), 0)
                    ),
                    textureInfo(
                        child(materialNode, "normalTexture"),
                        id(textureIds, material.getNormalTexture()),
                        value(material.getNormalTexcoord(), 0)
                    ),
                    material.getNormalScale(),
                    textureInfo(
                        child(materialNode, "occlusionTexture"),
                        id(textureIds, material.getOcclusionTexture()),
                        value(material.getOcclusionTexcoord(), 0)
                    ),
                    material.getOcclusionStrength(),
                    material.getEmissiveFactor(),
                    textureInfo(
                        child(materialNode, "emissiveTexture"),
                        id(textureIds, material.getEmissiveTexture()),
                        value(material.getEmissiveTexcoord(), 0)
                    ),
                    number(emissiveStrengthNode, "emissiveStrength", 1.0f),
                    alphaMode,
                    material.getAlphaCutoff(),
                    material.isDoubleSided(),
                    child(extensions, "KHR_materials_unlit") != null
                ));
            } else {
                result.add(ModelMaterial.defaultMaterial());
            }
        }
        return result;
    }

    private static ModelTextureInfo textureInfo(JsonNode source, int texture, int texCoord) {
        if (source == null) return new ModelTextureInfo(texture, texCoord);
        int effectiveTexture = integer(source, "index", texture);
        int effectiveTexCoord = integer(source, "texCoord", texCoord);
        float offsetU = 0.0f;
        float offsetV = 0.0f;
        float scaleU = 1.0f;
        float scaleV = 1.0f;
        float rotation = 0.0f;
        JsonNode transform = child(child(source, "extensions"), "KHR_texture_transform");
        if (transform != null) {
            float[] offset = vector(transform, "offset", 2, new float[]{0.0f, 0.0f});
            float[] scale = vector(transform, "scale", 2, new float[]{1.0f, 1.0f});
            offsetU = offset[0];
            offsetV = offset[1];
            scaleU = scale[0];
            scaleV = scale[1];
            rotation = number(transform, "rotation", 0.0f);
            effectiveTexCoord = integer(transform, "texCoord", effectiveTexCoord);
        }
        return new ModelTextureInfo(
            effectiveTexture,
            effectiveTexCoord,
            offsetU,
            offsetV,
            scaleU,
            scaleV,
            rotation
        );
    }

    private static JsonNode arrayElement(JsonNode array, int index) {
        if (array == null) return null;
        if (!array.isArray()) throw new IllegalArgumentException("glTF materials must be an array");
        return index < array.size() ? array.get(index) : null;
    }

    private static JsonNode child(JsonNode parent, String field) {
        if (parent == null) return null;
        JsonNode child = parent.get(field);
        return child == null || child.isNull() ? null : child;
    }

    private static int integer(JsonNode parent, String field, int fallback) {
        JsonNode value = child(parent, field);
        if (value == null) return fallback;
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException(field + " must be a 32-bit integer");
        }
        return value.intValue();
    }

    private static float number(JsonNode parent, String field, float fallback) {
        JsonNode value = child(parent, field);
        if (value == null) return fallback;
        if (!value.isNumber()) throw new IllegalArgumentException(field + " must be a number");
        float result = value.floatValue();
        if (!Float.isFinite(result)) throw new IllegalArgumentException(field + " must be finite");
        return result;
    }

    private static float[] vector(JsonNode parent, String field, int length, float[] fallback) {
        JsonNode value = child(parent, field);
        if (value == null) return fallback.clone();
        if (!value.isArray() || value.size() != length) {
            throw new IllegalArgumentException(field + " must contain " + length + " numbers");
        }
        float[] result = new float[length];
        for (int index = 0; index < length; index++) {
            if (!value.get(index).isNumber()) {
                throw new IllegalArgumentException(field + " must contain numbers");
            }
            result[index] = value.get(index).floatValue();
            if (!Float.isFinite(result[index])) {
                throw new IllegalArgumentException(field + " contains a non-finite value");
            }
        }
        return result;
    }

    private static List<ModelMesh> convertMeshes(List<MeshModel> sources,
                                                 IdentityHashMap<MaterialModel, Integer> materialIds,
                                                 int defaultMaterial) {
        List<ModelMesh> result = new ArrayList<>(sources.size());
        for (MeshModel mesh : sources) {
            List<ModelPrimitive> primitives = new ArrayList<>();
            for (MeshPrimitiveModel primitive : mesh.getMeshPrimitiveModels()) {
                Map<String, AccessorModel> attributes = primitive.getAttributes();
                AccessorModel positions = attributes.get("POSITION");
                if (positions == null) throw new IllegalArgumentException("Primitive has no POSITION attribute");
                requireElementType(positions, "POSITION", ElementType.VEC3);
                requireOptionalElementType(attributes.get("NORMAL"), "NORMAL", ElementType.VEC3);
                requireOptionalElementType(attributes.get("TANGENT"), "TANGENT", ElementType.VEC4);
                requireOptionalElementType(attributes.get("TEXCOORD_0"), "TEXCOORD_0", ElementType.VEC2);
                requireOptionalElementType(attributes.get("TEXCOORD_1"), "TEXCOORD_1", ElementType.VEC2);
                requireOptionalElementType(attributes.get("COLOR_0"), "COLOR_0", ElementType.VEC3, ElementType.VEC4);
                requireOptionalElementType(attributes.get("JOINTS_0"), "JOINTS_0", ElementType.VEC4);
                requireOptionalElementType(attributes.get("WEIGHTS_0"), "WEIGHTS_0", ElementType.VEC4);
                requireOptionalElementType(primitive.getIndices(), "indices", ElementType.SCALAR);
                List<MorphTarget> targets = new ArrayList<>();
                for (Map<String, AccessorModel> target : primitive.getTargets()) {
                    requireOptionalElementType(target.get("POSITION"), "morph POSITION", ElementType.VEC3);
                    requireOptionalElementType(target.get("NORMAL"), "morph NORMAL", ElementType.VEC3);
                    requireOptionalElementType(target.get("TANGENT"), "morph TANGENT", ElementType.VEC3);
                    targets.add(new MorphTarget(optionalFloats(target.get("POSITION")),
                        optionalFloats(target.get("NORMAL")), optionalFloats(target.get("TANGENT"))));
                }
                int material = primitive.getMaterialModel() == null ? defaultMaterial
                    : id(materialIds, primitive.getMaterialModel());
                primitives.add(new ModelPrimitive(primitiveMode(primitive.getMode()), material,
                    positions.getCount(), AccessorReader.floats(positions),
                    optionalFloats(attributes.get("NORMAL")), optionalFloats(attributes.get("TANGENT")),
                    optionalFloats(attributes.get("TEXCOORD_0")), optionalFloats(attributes.get("TEXCOORD_1")),
                    optionalFloats(attributes.get("COLOR_0")), optionalIntegers(attributes.get("JOINTS_0")),
                    optionalFloats(attributes.get("WEIGHTS_0")), optionalIntegers(primitive.getIndices()), targets));
            }
            result.add(new ModelMesh(mesh.getName(), primitives, mesh.getWeights()));
        }
        return result;
    }

    private static List<ModelNode> convertNodes(List<NodeModel> sources,
                                                IdentityHashMap<NodeModel, Integer> nodeIds,
                                                IdentityHashMap<MeshModel, Integer> meshIds,
                                                IdentityHashMap<SkinModel, Integer> skinIds) {
        List<ModelNode> result = new ArrayList<>(sources.size());
        for (NodeModel node : sources) {
            result.add(new ModelNode(node.getName(), id(nodeIds, node.getParent()),
                ids(node.getChildren(), nodeIds), ids(node.getMeshModels(), meshIds),
                id(skinIds, node.getSkinModel()), node.getMatrix(), node.getTranslation(),
                node.getRotation(), node.getScale(), node.getWeights()));
        }
        return result;
    }

    private static List<ModelScene> convertScenes(List<SceneModel> sources,
                                                  IdentityHashMap<NodeModel, Integer> nodeIds,
                                                  List<ModelNode> nodes) {
        if (sources.isEmpty() && !nodes.isEmpty()) {
            int count = 0;
            for (ModelNode node : nodes) if (node.parent() < 0) count++;
            int[] roots = new int[count];
            int write = 0;
            for (int i = 0; i < nodes.size(); i++) if (nodes.get(i).parent() < 0) roots[write++] = i;
            return List.of(new ModelScene("default", roots));
        }
        List<ModelScene> result = new ArrayList<>(sources.size());
        for (SceneModel scene : sources) result.add(new ModelScene(scene.getName(), ids(scene.getNodeModels(), nodeIds)));
        return result;
    }

    private static List<ModelSkin> convertSkins(List<SkinModel> sources,
                                                IdentityHashMap<NodeModel, Integer> nodeIds) {
        List<ModelSkin> result = new ArrayList<>(sources.size());
        for (SkinModel skin : sources) {
            requireOptionalElementType(skin.getInverseBindMatrices(), "inverse bind matrices", ElementType.MAT4);
            int[] joints = ids(skin.getJoints(), nodeIds);
            if (joints.length > ModelSkin.MAX_JOINTS) {
                throw new IllegalArgumentException("Skin has " + joints.length
                    + " joints; Combatant importer accepts at most " + ModelSkin.MAX_JOINTS + " joints per skin");
            }
            float[] inverse = new float[joints.length * 16];
            for (int i = 0; i < joints.length; i++) {
                float[] matrix = skin.getInverseBindMatrices() == null ? identity() : skin.getInverseBindMatrix(i, null);
                System.arraycopy(matrix, 0, inverse, i * 16, 16);
            }
            result.add(new ModelSkin(skin.getName(), id(nodeIds, skin.getSkeleton()), joints, inverse));
        }
        return result;
    }

    private static List<AnimationClip> convertAnimations(List<AnimationModel> sources,
                                                          IdentityHashMap<NodeModel, Integer> nodeIds) {
        List<AnimationClip> result = new ArrayList<>(sources.size());
        for (AnimationModel animation : sources) {
            List<AnimationChannel> channels = new ArrayList<>();
            for (Channel channel : animation.getChannels()) {
                AnimationPath path = AnimationPath.fromGltf(channel.getPath());
                requireElementType(channel.getSampler().getInput(), "animation input", ElementType.SCALAR);
                ElementType outputType = switch (path) {
                    case TRANSLATION, SCALE -> ElementType.VEC3;
                    case ROTATION -> ElementType.VEC4;
                    case WEIGHTS -> ElementType.SCALAR;
                };
                requireElementType(channel.getSampler().getOutput(), "animation output", outputType);
                float[] times = AccessorReader.floats(channel.getSampler().getInput());
                float[] values = AccessorReader.floats(channel.getSampler().getOutput());
                AnimationModel.Interpolation sourceInterpolation = channel.getSampler().getInterpolation();
                Interpolation interpolation = sourceInterpolation == null ? Interpolation.LINEAR : switch (sourceInterpolation) {
                    case STEP -> Interpolation.STEP;
                    case LINEAR -> Interpolation.LINEAR;
                    case CUBICSPLINE -> Interpolation.CUBIC_SPLINE;
                };
                int multiplier = interpolation == Interpolation.CUBIC_SPLINE ? 3 : 1;
                int components = path.components() > 0 ? path.components()
                    : values.length / Math.max(1, times.length * multiplier);
                channels.add(new AnimationChannel(id(nodeIds, channel.getNodeModel()), path,
                    new AnimationSampler(times, values, components, interpolation)));
            }
            result.add(new AnimationClip(animation.getName(), channels));
        }
        return result;
    }


    private static void validateRuntimeArrayBudget(Identifier id, GltfModel source) throws GltfLoadException {
        long components = 0;
        try {
            for (MeshModel mesh : source.getMeshModels()) {
                for (MeshPrimitiveModel primitive : mesh.getMeshPrimitiveModels()) {
                    Map<String, AccessorModel> attributes = primitive.getAttributes();
                    for (String semantic : List.of("POSITION", "NORMAL", "TANGENT", "TEXCOORD_0",
                        "TEXCOORD_1", "COLOR_0", "JOINTS_0", "WEIGHTS_0")) {
                        components = addAccessorComponents(components, attributes.get(semantic));
                    }
                    components = addAccessorComponents(components, primitive.getIndices());
                    for (Map<String, AccessorModel> target : primitive.getTargets()) {
                        components = addAccessorComponents(components, target.get("POSITION"));
                        components = addAccessorComponents(components, target.get("NORMAL"));
                        components = addAccessorComponents(components, target.get("TANGENT"));
                    }
                }
            }
            for (SkinModel skin : source.getSkinModels()) {
                components = addAccessorComponents(components, skin.getInverseBindMatrices());
            }
            for (AnimationModel animation : source.getAnimationModels()) {
                for (Channel channel : animation.getChannels()) {
                    components = addAccessorComponents(components, channel.getSampler().getInput());
                    components = addAccessorComponents(components, channel.getSampler().getOutput());
                }
            }
            long bytes = Math.multiplyExact(components, Integer.BYTES);
            if (bytes > MAX_RUNTIME_ARRAY_BYTES) {
                throw new GltfLoadException(id, "Decoded glTF attribute and animation arrays require " + bytes
                    + " bytes; safety limit is " + MAX_RUNTIME_ARRAY_BYTES);
            }
        } catch (ArithmeticException overflow) {
            throw new GltfLoadException(id, "Decoded glTF array size overflow", overflow);
        }
    }

    private static long addAccessorComponents(long total, AccessorModel accessor) {
        if (accessor == null) return total;
        int count = accessor.getAccessorData().getTotalNumComponents();
        if (count < 0) throw new IllegalArgumentException("Accessor reports a negative component count");
        return Math.addExact(total, count);
    }

    private static void requireOptionalElementType(AccessorModel accessor, String label, ElementType... allowed) {
        if (accessor != null) requireElementType(accessor, label, allowed);
    }

    private static void requireElementType(AccessorModel accessor, String label, ElementType... allowed) {
        Objects.requireNonNull(accessor, label);
        ElementType actual = accessor.getElementType();
        for (ElementType candidate : allowed) if (actual == candidate) return;
        throw new IllegalArgumentException(label + " accessor has type " + actual
            + ", expected " + Arrays.toString(allowed));
    }

    private static float[] optionalFloats(AccessorModel accessor) {
        return accessor == null ? null : AccessorReader.floats(accessor);
    }

    private static int[] optionalIntegers(AccessorModel accessor) {
        return accessor == null ? null : AccessorReader.integers(accessor);
    }

    private static <T> IdentityHashMap<T, Integer> index(List<T> values) {
        IdentityHashMap<T, Integer> result = new IdentityHashMap<>();
        for (int i = 0; i < values.size(); i++) result.put(values.get(i), i);
        return result;
    }

    private static <T> int id(IdentityHashMap<T, Integer> ids, T value) {
        if (value == null) return -1;
        Integer id = ids.get(value);
        if (id == null) throw new IllegalArgumentException("Referenced object is not part of the asset");
        return id;
    }

    private static <T> int[] ids(List<T> values, IdentityHashMap<T, Integer> ids) {
        if (values == null || values.isEmpty()) return new int[0];
        int[] result = new int[values.size()];
        for (int i = 0; i < result.length; i++) result[i] = id(ids, values.get(i));
        return result;
    }

    private static int value(Integer value, int fallback) { return value == null ? fallback : value; }

    private static String guessMime(String uri) {
        if (uri == null) return "application/octet-stream";
        String lower = uri.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }

    private static ObjectNode readJsonRoot(byte[] source) throws IOException {
        byte[] jsonBytes = source;
        if (source.length >= 12
            && ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN).getInt(0) == GLB_MAGIC) {
            ByteBuffer input = ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN);
            input.position(12);
            jsonBytes = null;
            while (input.hasRemaining()) {
                if (input.remaining() < 8) throw new IOException("Truncated GLB chunk header");
                int chunkLength = input.getInt();
                int chunkType = input.getInt();
                if (chunkLength < 0 || chunkLength > input.remaining()) {
                    throw new IOException("Invalid GLB chunk length");
                }
                byte[] chunk = new byte[chunkLength];
                input.get(chunk);
                if (chunkType == GLB_JSON_CHUNK && jsonBytes == null) {
                    jsonBytes = trimJsonPadding(chunk);
                }
            }
            if (jsonBytes == null) throw new IOException("GLB has no JSON chunk");
        }
        JsonNode parsed = JSON.readTree(jsonBytes);
        if (!(parsed instanceof ObjectNode root)) {
            throw new IOException("glTF JSON root must be an object");
        }
        return root;
    }



    private static byte[] normalizeDataUris(byte[] source, ReadBudget budget) throws IOException {
        if (source.length >= 12 && ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN).getInt(0) == GLB_MAGIC) {
            return normalizeGlbDataUris(source, budget);
        }
        return normalizeJsonDataUris(source, budget);
    }

    private static byte[] normalizeGlbDataUris(byte[] source, ReadBudget budget) throws IOException {
        ByteBuffer input = ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN);
        if (input.remaining() < 12) throw new IOException("Truncated GLB header");
        int magic = input.getInt();
        int version = input.getInt();
        long declaredLength = Integer.toUnsignedLong(input.getInt());
        if (magic != GLB_MAGIC) throw new IOException("Invalid GLB magic");
        if (version != GLB_VERSION_2) throw new IOException("Only GLB version 2 is supported");
        if (declaredLength != source.length) {
            throw new IOException("GLB declared length " + declaredLength + " does not match input length " + source.length);
        }

        List<GlbChunk> chunks = new ArrayList<>();
        boolean normalizedJson = false;
        while (input.hasRemaining()) {
            if (input.remaining() < 8) throw new IOException("Truncated GLB chunk header");
            long chunkLengthLong = Integer.toUnsignedLong(input.getInt());
            int chunkType = input.getInt();
            if (chunkLengthLong > input.remaining() || chunkLengthLong > Integer.MAX_VALUE) {
                throw new IOException("Invalid GLB chunk length: " + chunkLengthLong);
            }
            int chunkLength = (int) chunkLengthLong;
            byte[] data = new byte[chunkLength];
            input.get(data);
            if (chunkType == GLB_JSON_CHUNK) {
                if (normalizedJson) throw new IOException("GLB contains multiple JSON chunks");
                data = normalizeJsonDataUris(trimJsonPadding(data), budget);
                normalizedJson = true;
            }
            chunks.add(new GlbChunk(chunkType, data));
        }
        if (!normalizedJson) throw new IOException("GLB has no JSON chunk");

        long totalLength = 12L;
        for (GlbChunk chunk : chunks) {
            totalLength = Math.addExact(totalLength, 8L + align4(chunk.data.length));
        }
        if (totalLength > Integer.MAX_VALUE) throw new IOException("Normalized GLB is too large");
        ByteBuffer output = ByteBuffer.allocate((int) totalLength).order(ByteOrder.LITTLE_ENDIAN);
        output.putInt(GLB_MAGIC).putInt(GLB_VERSION_2).putInt((int) totalLength);
        for (GlbChunk chunk : chunks) {
            int paddedLength = align4(chunk.data.length);
            output.putInt(paddedLength).putInt(chunk.type).put(chunk.data);
            byte padding = chunk.type == GLB_JSON_CHUNK ? (byte) 0x20 : 0;
            while ((output.position() & 3) != 0) output.put(padding);
        }
        return output.array();
    }

    private static byte[] trimJsonPadding(byte[] data) {
        int length = data.length;
        while (length > 0 && (data[length - 1] == 0 || data[length - 1] == 0x20
            || data[length - 1] == '\t' || data[length - 1] == '\r' || data[length - 1] == '\n')) {
            length--;
        }
        return length == data.length ? data : Arrays.copyOf(data, length);
    }

    private static int align4(int length) throws IOException {
        try {
            return Math.addExact(length, 3) & ~3;
        } catch (ArithmeticException exception) {
            throw new IOException("GLB chunk length overflow", exception);
        }
    }

    private static byte[] normalizeJsonDataUris(byte[] jsonBytes, ReadBudget budget) throws IOException {
        validateJsonNesting(jsonBytes);
        JsonNode parsed = JSON.readTree(jsonBytes);
        if (!(parsed instanceof ObjectNode root)) throw new IOException("glTF JSON root must be an object");
        normalizeUriArray(root.get("buffers"), budget);
        normalizeUriArray(root.get("images"), budget);
        return JSON.writeValueAsBytes(root);
    }

    private static void validateJsonNesting(byte[] jsonBytes) throws IOException {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (byte raw : jsonBytes) {
            int value = raw & 0xff;
            if (inString) {
                if (escaped) escaped = false;
                else if (value == '\\') escaped = true;
                else if (value == '"') inString = false;
                continue;
            }
            if (value == '"') {
                inString = true;
            } else if (value == '{' || value == '[') {
                if (++depth > MAX_JSON_NESTING) {
                    throw new IOException("glTF JSON nesting exceeds safety limit " + MAX_JSON_NESTING);
                }
            } else if (value == '}' || value == ']') {
                if (--depth < 0) throw new IOException("Malformed glTF JSON nesting");
            }
        }
        if (depth != 0) throw new IOException("Malformed glTF JSON nesting");
    }

    private static void normalizeUriArray(JsonNode value, ReadBudget budget) throws IOException {
        if (value == null) return;
        if (!(value instanceof ArrayNode array)) throw new IOException("glTF URI collection must be an array");
        for (JsonNode element : array) {
            if (!(element instanceof ObjectNode object)) continue;
            JsonNode uriNode = object.get("uri");
            if (uriNode == null) continue;
            if (!uriNode.isTextual()) throw new IOException("glTF resource URI must be a string");
            String uri = uriNode.textValue();
            if (!isDataUri(uri)) continue;
            object.put("uri", canonicalDataUri(uri, budget));
        }
    }

    private static String canonicalDataUri(String rawUri, ReadBudget budget) throws IOException {
        int comma = rawUri.indexOf(',', 5);
        if (comma < 0) throw new IOException("Malformed data URI: missing comma");
        String metadata = rawUri.substring(5, comma);
        byte[] decoded = decodeDataUriBytes(rawUri, budget);
        String[] parts = metadata.split(";", -1);
        List<String> retained = new ArrayList<>(parts.length);
        if (parts.length > 0 && !parts[0].isEmpty()) retained.add(parts[0]);
        for (int index = 1; index < parts.length; index++) {
            if (!parts[index].equalsIgnoreCase("base64") && !parts[index].isEmpty()) retained.add(parts[index]);
        }
        String cleanMetadata = String.join(";", retained);
        return "data:" + cleanMetadata + ";base64," + Base64.getEncoder().encodeToString(decoded);
    }

    private static boolean isDataUri(String rawUri) throws IOException {
        if (rawUri == null) return false;
        URI uri;
        try {
            uri = URI.create(rawUri);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid URI " + rawUri, exception);
        }
        return "data".equalsIgnoreCase(uri.getScheme());
    }

    static ByteBuffer decodeDataUri(String rawUri, ReadBudget budget) throws IOException {
        return ByteBuffer.wrap(decodeDataUriBytes(rawUri, budget)).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static byte[] decodeDataUriBytes(String rawUri, ReadBudget budget) throws IOException {
        Objects.requireNonNull(budget, "budget");
        if (rawUri == null || !rawUri.regionMatches(true, 0, "data:", 0, 5)) {
            throw new IOException("Not a data URI");
        }
        int comma = rawUri.indexOf(',', 5);
        if (comma < 0) throw new IOException("Malformed data URI: missing comma");
        String metadata = rawUri.substring(5, comma);
        boolean base64 = false;
        if (!metadata.isEmpty()) {
            String[] parts = metadata.split(";", -1);
            for (int i = 1; i < parts.length; i++) {
                if (parts[i].equalsIgnoreCase("base64")) {
                    if (base64) throw new IOException("Malformed data URI: duplicate base64 marker");
                    base64 = true;
                }
            }
        }
        byte[] encoded = percentDecode(rawUri, comma + 1);
        byte[] decoded;
        if (base64) {
            try {
                decoded = Base64.getDecoder().decode(encoded);
            } catch (IllegalArgumentException exception) {
                throw new IOException("Malformed base64 data URI", exception);
            }
        } else {
            decoded = encoded;
        }
        budget.consumeExact(decoded.length, "data URI");
        return decoded;
    }

    private static byte[] percentDecode(String value, int start) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.max(0, value.length() - start));
        for (int index = start; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '%') {
                if (index + 2 >= value.length()) throw new IOException("Malformed percent escape in data URI");
                int high = Character.digit(value.charAt(index + 1), 16);
                int low = Character.digit(value.charAt(index + 2), 16);
                if (high < 0 || low < 0) throw new IOException("Malformed percent escape in data URI");
                output.write((high << 4) | low);
                index += 2;
            } else {
                if (character > 0x7f) throw new IOException("Data URI contains an unescaped non-ASCII character");
                output.write((byte) character);
            }
        }
        return output.toByteArray();
    }

    static Identifier resolveRelative(Identifier base, String rawUri) throws IOException {
        return ModelResourcePaths.sibling(base, rawUri);
    }

    private static byte[] readAll(InputStream stream, ReadBudget budget, String label) throws IOException {
        try (LimitedInputStream limited = new LimitedInputStream(requireStream(stream, null), budget, label);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            limited.transferTo(output);
            return output.toByteArray();
        }
    }

    private static InputStream requireStream(InputStream stream, Identifier location) throws IOException {
        if (stream != null) return stream;
        throw new IOException(location == null ? "Resolver returned a null stream" : "Resolver returned a null stream for " + location);
    }

    private static void requireCount(Identifier id, String label, int count, int maximum) throws GltfLoadException {
        if (count > maximum) throw new GltfLoadException(id, "Asset contains too many " + label + ": " + count + " (limit " + maximum + ")");
    }

    private static String message(Throwable throwable) {
        if (throwable == null) return "Unknown loading error";
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private static float[] identity() {
        return new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    }

    private static final class ReferenceResolutionException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        ReferenceResolutionException(Throwable cause) { super(cause); }
    }

    private record GlbChunk(int type, byte[] data) {
        private GlbChunk {
            data = data.clone();
        }
    }

    private static final class ReadBudget {
        private long remaining;

        ReadBudget(long limit) {
            if (limit < 0) throw new IllegalArgumentException("Negative size limit");
            this.remaining = limit;
        }

        int allowed(int requested) {
            return (int) Math.min(requested, remaining);
        }

        void consume(long count) {
            remaining -= count;
        }

        void consumeExact(long count, String label) throws IOException {
            if (count < 0 || count > remaining) {
                throw new IOException("Combined glTF resource data exceeds configured size limit while reading " + label);
            }
            remaining -= count;
        }

        boolean exhausted() {
            return remaining == 0;
        }
    }

    private static final class LimitedInputStream extends java.io.FilterInputStream {
        private final ReadBudget budget;
        private final String label;

        LimitedInputStream(InputStream delegate, ReadBudget budget, String label) {
            super(Objects.requireNonNull(delegate, "delegate"));
            this.budget = Objects.requireNonNull(budget, "budget");
            this.label = Objects.requireNonNullElse(label, "resource");
        }

        @Override
        public int read() throws IOException {
            if (budget.exhausted()) {
                if (super.read() < 0) return -1;
                throw exceeded();
            }
            int value = super.read();
            if (value >= 0) budget.consume(1);
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, buffer.length);
            if (length == 0) return 0;
            if (budget.exhausted()) {
                if (super.read() < 0) return -1;
                throw exceeded();
            }
            int count = super.read(buffer, offset, budget.allowed(length));
            if (count > 0) budget.consume(count);
            return count;
        }

        @Override
        public long skip(long count) throws IOException {
            if (count <= 0 || budget.exhausted()) return 0;
            long skipped = super.skip(Math.min(count, budget.remaining));
            budget.consume(skipped);
            return skipped;
        }

        private IOException exceeded() {
            return new IOException("Combined glTF resource data exceeds configured size limit while reading " + label);
        }
    }

}
