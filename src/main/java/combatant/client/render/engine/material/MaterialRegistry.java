/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.material;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Resource-pack-backed surface material registry.
 *
 * <p>Resolution order is explicit descriptor -> producer tags/classification -> LabPBR metadata
 * -> vanilla fallback -> unknown. IDs are derived from the exact Sodium sprite plus semantic
 * domain; no material semantics are inferred from rendered color/depth/texture index.</p>
 */
public final class MaterialRegistry {
    private static final MaterialRegistry GLOBAL = new MaterialRegistry();
    private static final int DEFAULT_ID = 0;
    private static final String DESCRIPTOR_ROOT = "combatant/materials";

    private volatile Snapshot snapshot = Snapshot.EMPTY;
    private volatile ResourceManager resources;
    private volatile Map<Identifier, Integer> gpuMapAvailability = Map.of();

    public static MaterialRegistry global() {
        return GLOBAL;
    }

    public void reload(ResourceManager resources) {
        if (resources == null) return;
        this.resources = resources;
        this.gpuMapAvailability = Map.of();

        Set<Identifier> available = new HashSet<>();
        for (String namespace : resources.getNamespaces()) {
            available.addAll(resources.listResources("textures", id -> id.getPath().endsWith(".png")).keySet());
        }

        Map<DescriptorKey, ExplicitDescriptor> explicit = loadExplicitDescriptors(resources);
        snapshot = new Snapshot(Set.copyOf(available), Map.copyOf(explicit), new HashMap<>(), new HashMap<>(), new HashMap<>());
        DebugLog.renderThread("[Materials] registry reload: textures=%d explicitDescriptors=%d",
                available.size(), explicit.size());
    }

    public ResourceManager resources() {
        return resources;
    }

    /** Installed by the companion-atlas builder after validating dimensions/animation layout. */
    public void installGpuMapAvailability(Map<Identifier, Integer> availability) {
        this.gpuMapAvailability = availability == null ? Map.of() : Map.copyOf(availability);
    }

    public int gpuPresenceMask(MaterialSurfaceDescriptor descriptor) {
        if (descriptor == null) return 0;
        Integer usable = gpuMapAvailability.get(descriptor.spriteId());
        return usable == null ? 0 : descriptor.gpuPresenceMask8() & usable;
    }

    /** Compatibility entry point for callers that only know the render-pass fallback. */
    public MaterialSurfaceDescriptor resolve(TextureAtlasSprite sprite, MaterialDomain domain) {
        return resolve(sprite, MaterialClassifier.fallback(domain, MaterialResolutionSource.UNKNOWN));
    }

    public MaterialSurfaceDescriptor resolve(TextureAtlasSprite sprite, MaterialClassification classification) {
        MaterialClassification producer = classification == null ? MaterialClassification.UNKNOWN : classification;
        if (sprite == null || sprite.contents() == null || sprite.contents().name() == null) {
            return fallback(producer);
        }

        Identifier spriteId = sprite.contents().name();
        Snapshot state = snapshot;
        CacheKey key = new CacheKey(spriteId, producer);
        synchronized (state.cache) {
            MaterialSurfaceDescriptor cached = state.cache.get(key);
            if (cached != null) return cached;
            MaterialSurfaceDescriptor resolved = build(state, spriteId, producer);
            state.cache.put(key, resolved);
            return resolved;
        }
    }

    /** Map-only descriptor used by the shared companion atlas; domain-specific scalar policy remains per vertex. */
    public MaterialSurfaceDescriptor resolveAtlas(TextureAtlasSprite sprite) {
        return resolve(sprite, MaterialClassification.UNKNOWN);
    }

    private static MaterialSurfaceDescriptor build(Snapshot state,
                                                   Identifier spriteId,
                                                   MaterialClassification producer) {
        EnumMap<MaterialTextureSemantic, Identifier> maps = discoverMaps(state.available, spriteId);
        ExplicitDescriptor explicit = state.explicit.get(new DescriptorKey(spriteId, producer.domain()));
        if (explicit == null) explicit = state.explicit.get(new DescriptorKey(spriteId, null));

        MaterialClassification classification = producer;
        MaterialResolutionSource source;
        Scalars scalars;
        MaterialTessellationProfile tessellation;
        MaterialWeatherResponse weatherResponse;
        MaterialTemporalPolicy temporalPolicy;

        if (explicit != null) {
            classification = explicit.applyClassification(producer);
            explicit.applyTextureOverrides(state.available, maps);
            scalars = explicit.applyScalars(defaultScalars(classification));
            tessellation = explicit.tessellation != null
                    ? explicit.tessellation
                    : defaultTessellation(classification.domain());
            weatherResponse = explicit.weatherResponse != null
                    ? explicit.weatherResponse
                    : MaterialWeatherResponse.NONE;
            temporalPolicy = explicit.temporalPolicy != null
                    ? explicit.temporalPolicy
                    : defaultTemporalPolicy(classification);
            source = MaterialResolutionSource.EXPLICIT_DESCRIPTOR;
        } else {
            scalars = defaultScalars(classification);
            tessellation = defaultTessellation(classification.domain());
            weatherResponse = MaterialWeatherResponse.NONE;
            temporalPolicy = defaultTemporalPolicy(classification);
            boolean labPbr = maps.containsKey(MaterialTextureSemantic.LABPBR_NORMAL)
                    || maps.containsKey(MaterialTextureSemantic.LABPBR_SPECULAR);
            if (classification.source() == MaterialResolutionSource.TAG) {
                source = MaterialResolutionSource.TAG;
            } else if (labPbr) {
                source = MaterialResolutionSource.LABPBR_METADATA;
            } else if (classification.source() != MaterialResolutionSource.UNKNOWN) {
                source = classification.source();
            } else if (classification.domain() != MaterialDomain.UNKNOWN) {
                source = MaterialResolutionSource.VANILLA_FALLBACK;
            } else {
                source = MaterialResolutionSource.UNKNOWN;
            }
        }

        int stableId = stableId32(spriteId, classification);
        if (stableId == DEFAULT_ID) stableId = 1;
        MaterialSurfaceDescriptor descriptor = new MaterialSurfaceDescriptor(
                stableId,
                spriteId,
                classification.domain(),
                classification.traitMask(),
                classification.route(),
                source,
                new MaterialTextureSet(maps),
                scalars.ao,
                scalars.roughness,
                scalars.metallic,
                scalars.f0,
                scalars.emission,
                scalars.heightScale,
                scalars.transmission,
                scalars.subsurface,
                scalars.clearcoat,
                scalars.clearcoatRoughness,
                scalars.porosity,
                scalars.thickness,
                temporalPolicy,
                weatherResponse,
                tessellation
        );
        synchronized (state.weatherResponses) {
            state.weatherResponses.put(stableId, weatherResponse);
        }
        synchronized (state.temporalPolicies) {
            state.temporalPolicies.put(stableId, temporalPolicy);
        }
        return descriptor;
    }

    /** Exact weather-response descriptors keyed by the stable material ID written to G-buffer. */
    public Map<Integer, MaterialWeatherResponse> weatherResponsesSnapshot() {
        Snapshot state = snapshot;
        synchronized (state.weatherResponses) {
            return Map.copyOf(state.weatherResponses);
        }
    }

    /** Exact reactive policy keyed by the stable material ID written to the G-buffer. */
    public Map<Integer, MaterialTemporalPolicy> temporalPoliciesSnapshot() {
        Snapshot state = snapshot;
        synchronized (state.temporalPolicies) {
            return Map.copyOf(state.temporalPolicies);
        }
    }

    private static MaterialSurfaceDescriptor fallback(MaterialClassification classification) {
        MaterialClassification resolved = classification == null ? MaterialClassification.UNKNOWN : classification;
        Scalars scalars = defaultScalars(resolved);
        return new MaterialSurfaceDescriptor(
                DEFAULT_ID,
                Identifier.fromNamespaceAndPath("combatant", "unknown"),
                resolved.domain(),
                resolved.traitMask(),
                resolved.route(),
                MaterialResolutionSource.UNKNOWN,
                new MaterialTextureSet(null),
                scalars.ao,
                scalars.roughness,
                scalars.metallic,
                scalars.f0,
                scalars.emission,
                scalars.heightScale,
                scalars.transmission,
                scalars.subsurface,
                scalars.clearcoat,
                scalars.clearcoatRoughness,
                scalars.porosity,
                scalars.thickness,
                defaultTemporalPolicy(resolved),
                MaterialWeatherResponse.NONE,
                defaultTessellation(resolved.domain())
        );
    }

    private static Scalars defaultScalars(MaterialClassification classification) {
        MaterialDomain domain = classification == null ? MaterialDomain.UNKNOWN : classification.domain();
        int traits = classification == null ? 0 : classification.traitMask();
        return switch (domain) {
            case WATER -> new Scalars(1.0f, 0.045f, 0.0f, 0.02f, 0.0f,
                    0.12f, 1.0f, 0.0f, 0.0f, 0.1f, 0.0f, 1.0f);
            case LAVA -> new Scalars(1.0f, 0.5f, 0.0f, 0.04f, 1.0f,
                    0.0f, 0.0f, 0.0f, 0.0f, 0.25f, 0.0f, 1.0f);
            case GLASS -> new Scalars(1.0f, 0.12f, 0.0f, 0.04f, 0.0f,
                    0.0f, 0.96f, 0.0f, 0.0f, 0.08f, 0.0f, 0.15f);
            case PORTAL -> new Scalars(1.0f, 0.32f, 0.0f, 0.04f, 1.0f,
                    0.0f, 0.0f, 0.0f, 0.0f, 0.2f, 0.0f, 0.25f);
            default -> new Scalars(1.0f, 0.72f, 0.0f, 0.04f,
                    (traits & MaterialTrait.EMISSIVE.bit()) != 0 ? 1.0f : 0.0f,
                    0.0f,
                    (traits & MaterialTrait.TRANSMISSIVE.bit()) != 0 ? 1.0f : 0.0f,
                    (traits & MaterialTrait.FOLIAGE.bit()) != 0 ? 0.35f : 0.0f,
                    0.0f, 0.25f, 0.5f, 1.0f);
        };
    }

    private static MaterialTemporalPolicy defaultTemporalPolicy(MaterialClassification classification) {
        MaterialDomain domain = classification == null ? MaterialDomain.UNKNOWN : classification.domain();
        int traits = classification == null ? 0 : classification.traitMask();
        if (domain == MaterialDomain.PORTAL || domain == MaterialDomain.WATER) {
            return MaterialTemporalPolicy.REJECT_HISTORY;
        }
        if (domain == MaterialDomain.TRANSLUCENT || domain == MaterialDomain.GLASS || domain == MaterialDomain.LAVA
                || (traits & MaterialTrait.EMISSIVE.bit()) != 0) {
            return MaterialTemporalPolicy.RESPONSIVE;
        }
        return MaterialTemporalPolicy.STABLE;
    }

    /** Height maps alone never opt a surface into patch topology. */
    private static MaterialTessellationProfile defaultTessellation(MaterialDomain domain) {
        return MaterialTessellationProfile.NONE;
    }

    private static EnumMap<MaterialTextureSemantic, Identifier> discoverMaps(Set<Identifier> available,
                                                                             Identifier spriteId) {
        EnumMap<MaterialTextureSemantic, Identifier> maps = new EnumMap<>(MaterialTextureSemantic.class);
        putFirstPresent(available, maps, MaterialTextureSemantic.ALBEDO,
                texture(spriteId, "_albedo"), texture(spriteId, "_diffuse"), texture(spriteId, "_basecolor"));
        putFirstPresent(available, maps, MaterialTextureSemantic.NORMAL,
                texture(spriteId, "_normal"), texture(spriteId, "_norm"));
        putIfPresent(available, maps, MaterialTextureSemantic.AMBIENT_OCCLUSION, texture(spriteId, "_ao"));
        putIfPresent(available, maps, MaterialTextureSemantic.ROUGHNESS, texture(spriteId, "_roughness"));
        putIfPresent(available, maps, MaterialTextureSemantic.METALLIC, texture(spriteId, "_metallic"));
        putIfPresent(available, maps, MaterialTextureSemantic.SPECULAR, texture(spriteId, "_specular"));
        putIfPresent(available, maps, MaterialTextureSemantic.EMISSIVE, texture(spriteId, "_emissive"));
        putFirstPresent(available, maps, MaterialTextureSemantic.HEIGHT,
                texture(spriteId, "_height"), texture(spriteId, "_displacement"));
        putIfPresent(available, maps, MaterialTextureSemantic.ORM, texture(spriteId, "_orm"));
        putIfPresent(available, maps, MaterialTextureSemantic.LABPBR_NORMAL, texture(spriteId, "_n"));
        putIfPresent(available, maps, MaterialTextureSemantic.LABPBR_SPECULAR, texture(spriteId, "_s"));
        return maps;
    }

    private static Map<DescriptorKey, ExplicitDescriptor> loadExplicitDescriptors(ResourceManager resources) {
        Map<DescriptorKey, ExplicitDescriptor> descriptors = new HashMap<>();
        Map<Identifier, Resource> found = resources.listResources(DESCRIPTOR_ROOT, id -> id.getPath().endsWith(".json"));
        for (Map.Entry<Identifier, Resource> entry : found.entrySet()) {
            try (var reader = entry.getValue().openAsReader()) {
                JsonElement root = JsonParser.parseReader(reader);
                if (!root.isJsonObject()) throw new IllegalArgumentException("root must be an object");
                ExplicitDescriptor descriptor = parseExplicit(entry.getKey(), root.getAsJsonObject());
                DescriptorKey key = new DescriptorKey(descriptor.spriteId, descriptor.matchDomain);
                ExplicitDescriptor previous = descriptors.put(key, descriptor);
                if (previous != null) {
                    DebugLog.warnOnce("combatant.material.descriptor.duplicate." + key,
                            "[Materials] duplicate explicit descriptor for %s domain=%s; %s replaces %s",
                            key.spriteId, key.domain, entry.getKey(), previous.sourceResource);
                }
            } catch (Throwable error) {
                DebugLog.warnOnce("combatant.material.descriptor.invalid." + entry.getKey(),
                        "[Materials] invalid descriptor %s: %s: %s",
                        entry.getKey(), error.getClass().getSimpleName(), error.getMessage());
            }
        }
        return descriptors;
    }

    private static ExplicitDescriptor parseExplicit(Identifier source, JsonObject json) {
        Identifier sprite = parseId(requiredString(json, "sprite"));
        MaterialDomain domain = json.has("domain") ? parseEnum(MaterialDomain.class, json.get("domain").getAsString()) : null;
        MaterialDomain matchDomain = json.has("matchDomain")
                ? parseEnum(MaterialDomain.class, json.get("matchDomain").getAsString()) : null;
        MaterialSubmissionRoute route = json.has("route")
                ? parseEnum(MaterialSubmissionRoute.class, json.get("route").getAsString()) : null;
        int traitMask = 0;
        boolean traitsDeclared = json.has("traits");
        if (traitsDeclared) {
            for (JsonElement element : json.getAsJsonArray("traits")) {
                traitMask |= parseEnum(MaterialTrait.class, element.getAsString()).bit();
            }
        }

        EnumMap<MaterialTextureSemantic, Identifier> textureOverrides = new EnumMap<>(MaterialTextureSemantic.class);
        if (json.has("textures")) {
            JsonObject textures = json.getAsJsonObject("textures");
            for (Map.Entry<String, JsonElement> entry : textures.entrySet()) {
                MaterialTextureSemantic semantic = parseSemantic(entry.getKey());
                textureOverrides.put(semantic, parseId(entry.getValue().getAsString()));
            }
        }

        MaterialTessellationProfile tess = null;
        if (json.has("tessellation")) {
            JsonObject obj = json.getAsJsonObject("tessellation");
            MaterialTessellationMode mode = parseEnum(MaterialTessellationMode.class, requiredString(obj, "mode"));
            if (mode == MaterialTessellationMode.NONE) {
                tess = MaterialTessellationProfile.NONE;
            } else {
                float scale = floatOr(obj, "displacementScale", mode == MaterialTessellationMode.WATER_SURFACE ? 0.12f : 0.04f);
                tess = new MaterialTessellationProfile(
                        mode,
                        scale,
                        floatOr(obj, "minFactor", 1.0f),
                        floatOr(obj, "maxFactor", mode == MaterialTessellationMode.WATER_SURFACE ? 12.0f : 8.0f),
                        floatOr(obj, "distanceFadeStart", mode == MaterialTessellationMode.WATER_SURFACE ? 8.0f : 6.0f),
                        floatOr(obj, "distanceFadeEnd", mode == MaterialTessellationMode.WATER_SURFACE ? 96.0f : 64.0f)
                );
            }
        }

        MaterialTemporalPolicy temporalPolicy = json.has("temporalPolicy")
                ? parseEnum(MaterialTemporalPolicy.class, json.get("temporalPolicy").getAsString())
                : null;

        MaterialWeatherResponse weatherResponse = null;
        if (json.has("weatherResponse")) {
            JsonObject weather = json.getAsJsonObject("weatherResponse");
            weatherResponse = new MaterialWeatherResponse(
                    floatOr(weather, "wetLayerStrength", 0.0f),
                    floatOr(weather, "absorptionRate", 0.0f),
                    floatOr(weather, "dryingRate", 0.0f),
                    floatOr(weather, "runoffRate", 0.0f),
                    floatOr(weather, "puddleCapacity", 0.0f),
                    floatOr(weather, "snowRetention", 0.0f),
                    floatOr(weather, "particulateRetention", 0.0f),
                    floatOr(weather, "particulateWashOffRate", 1.0f)
            );
        }

        return new ExplicitDescriptor(
                source, sprite, domain, matchDomain, route, traitMask, traitsDeclared, textureOverrides,
                optionalFloat(json, "ambientOcclusion"), optionalFloat(json, "roughness"),
                optionalFloat(json, "metallic"), optionalFloat(json, "dielectricF0"),
                optionalFloat(json, "emission"), optionalFloat(json, "heightScale"),
                optionalFloat(json, "transmission"), optionalFloat(json, "subsurface"),
                optionalFloat(json, "clearcoat"), optionalFloat(json, "clearcoatRoughness"),
                optionalFloat(json, "porosity"), optionalFloat(json, "thickness"), temporalPolicy, weatherResponse, tess
        );
    }

    private static MaterialTextureSemantic parseSemantic(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (normalized.equals("BASE_COLOR") || normalized.equals("BASECOLOR") || normalized.equals("DIFFUSE")) {
            return MaterialTextureSemantic.ALBEDO;
        }
        if (normalized.equals("AO")) return MaterialTextureSemantic.AMBIENT_OCCLUSION;
        return MaterialTextureSemantic.valueOf(normalized);
    }

    private static Identifier parseId(String value) {
        Identifier id = Identifier.tryParse(value);
        if (id == null) throw new IllegalArgumentException("invalid identifier: " + value);
        return id;
    }

    private static String requiredString(JsonObject object, String name) {
        if (!object.has(name)) throw new IllegalArgumentException("missing '" + name + "'");
        return object.get(name).getAsString();
    }

    private static Float optionalFloat(JsonObject object, String name) {
        return object.has(name) ? object.get(name).getAsFloat() : null;
    }

    private static float floatOr(JsonObject object, String name, float fallback) {
        return object.has(name) ? object.get(name).getAsFloat() : fallback;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }

    private static void putFirstPresent(Set<Identifier> available,
                                        Map<MaterialTextureSemantic, Identifier> target,
                                        MaterialTextureSemantic semantic,
                                        Identifier... ids) {
        for (Identifier id : ids) {
            if (available.contains(id)) {
                target.put(semantic, id);
                return;
            }
        }
    }

    private static void putIfPresent(Set<Identifier> available,
                                     Map<MaterialTextureSemantic, Identifier> target,
                                     MaterialTextureSemantic semantic,
                                     Identifier id) {
        if (available.contains(id)) target.put(semantic, id);
    }

    private static Identifier texture(Identifier sprite, String suffix) {
        return Identifier.fromNamespaceAndPath(sprite.getNamespace(), "textures/" + sprite.getPath() + suffix + ".png");
    }

    /** Stable 32-bit FNV-1a ID derived from the exact sprite plus semantic producer contract. */
    private static int stableId32(Identifier id, MaterialClassification classification) {
        MaterialClassification c = classification == null ? MaterialClassification.UNKNOWN : classification;
        byte[] bytes = (id + "|" + c.domain().name() + "|" + Integer.toUnsignedString(c.traitMask())
                + "|" + c.route().name()).getBytes(StandardCharsets.UTF_8);
        int hash = 0x811C9DC5;
        for (byte b : bytes) {
            hash ^= b & 0xFF;
            hash *= 0x01000193;
        }
        return hash;
    }

    private record CacheKey(Identifier spriteId, MaterialClassification classification) {
    }

    private record DescriptorKey(Identifier spriteId, MaterialDomain domain) {
    }

    private record Scalars(float ao, float roughness, float metallic, float f0, float emission,
                           float heightScale, float transmission, float subsurface, float clearcoat,
                           float clearcoatRoughness, float porosity, float thickness) {
    }

    private record ExplicitDescriptor(
            Identifier sourceResource,
            Identifier spriteId,
            MaterialDomain domain,
            MaterialDomain matchDomain,
            MaterialSubmissionRoute route,
            int traitMask,
            boolean traitsDeclared,
            Map<MaterialTextureSemantic, Identifier> textureOverrides,
            Float ao,
            Float roughness,
            Float metallic,
            Float f0,
            Float emission,
            Float heightScale,
            Float transmission,
            Float subsurface,
            Float clearcoat,
            Float clearcoatRoughness,
            Float porosity,
            Float thickness,
            MaterialTemporalPolicy temporalPolicy,
            MaterialWeatherResponse weatherResponse,
            MaterialTessellationProfile tessellation
    ) {
        MaterialClassification applyClassification(MaterialClassification producer) {
            MaterialClassification base = producer == null ? MaterialClassification.UNKNOWN : producer;
            MaterialDomain resolvedDomain = domain != null ? domain : base.domain();
            int resolvedTraits = traitsDeclared ? traitMask : base.traitMask();
            MaterialSubmissionRoute resolvedRoute = route != null ? route : defaultRoute(resolvedDomain, base.route());
            return new MaterialClassification(resolvedDomain, resolvedTraits, resolvedRoute,
                    MaterialResolutionSource.EXPLICIT_DESCRIPTOR);
        }

        void applyTextureOverrides(Set<Identifier> available,
                                   EnumMap<MaterialTextureSemantic, Identifier> target) {
            for (Map.Entry<MaterialTextureSemantic, Identifier> entry : textureOverrides.entrySet()) {
                if (available.contains(entry.getValue())) {
                    target.put(entry.getKey(), entry.getValue());
                } else {
                    DebugLog.warnOnce("combatant.material.descriptor.texture." + sourceResource + "." + entry.getKey(),
                            "[Materials] descriptor %s references missing texture %s for %s",
                            sourceResource, entry.getValue(), entry.getKey());
                }
            }
        }

        Scalars applyScalars(Scalars defaults) {
            return new Scalars(
                    ao != null ? ao : defaults.ao,
                    roughness != null ? roughness : defaults.roughness,
                    metallic != null ? metallic : defaults.metallic,
                    f0 != null ? f0 : defaults.f0,
                    emission != null ? emission : defaults.emission,
                    heightScale != null ? heightScale : defaults.heightScale,
                    transmission != null ? transmission : defaults.transmission,
                    subsurface != null ? subsurface : defaults.subsurface,
                    clearcoat != null ? clearcoat : defaults.clearcoat,
                    clearcoatRoughness != null ? clearcoatRoughness : defaults.clearcoatRoughness,
                    porosity != null ? porosity : defaults.porosity,
                    thickness != null ? thickness : defaults.thickness
            );
        }

        private static MaterialSubmissionRoute defaultRoute(MaterialDomain domain, MaterialSubmissionRoute producerRoute) {
            if (domain == MaterialDomain.WATER) return MaterialSubmissionRoute.FORWARD_SPECIAL;
            if (domain == MaterialDomain.LAVA || domain == MaterialDomain.GLASS || domain == MaterialDomain.PORTAL) {
                return MaterialSubmissionRoute.FORWARD_SPECIAL;
            }
            return producerRoute == null ? MaterialSubmissionRoute.COMPATIBILITY : producerRoute;
        }
    }

    private record Snapshot(Set<Identifier> available,
                            Map<DescriptorKey, ExplicitDescriptor> explicit,
                            Map<CacheKey, MaterialSurfaceDescriptor> cache,
                            Map<Integer, MaterialWeatherResponse> weatherResponses,
                            Map<Integer, MaterialTemporalPolicy> temporalPolicies) {
        private static final Snapshot EMPTY = new Snapshot(Set.of(), Map.of(), new HashMap<>(), new HashMap<>(), new HashMap<>());
    }
}
