#ifndef COMBATANT_CLOUD_WEATHER_BINDING
#error COMBATANT_CLOUD_WEATHER_BINDING must be defined before importing deferred_cloud_density.glsl
#endif
#ifndef COMBATANT_CLOUD_MACRO_WEATHER_BINDING
#error COMBATANT_CLOUD_MACRO_WEATHER_BINDING must be defined before importing deferred_cloud_density.glsl
#endif
#ifndef COMBATANT_CLOUD_DOMAIN_BINDING
#error COMBATANT_CLOUD_DOMAIN_BINDING must be defined before importing deferred_cloud_density.glsl
#endif

const int COMBATANT_CLOUD_MAX_DOMAINS = 8;
const int COMBATANT_CLOUD_GROUP_ALL = -1;

struct WeatherCell {
    vec4 climate;
    vec4 windFront;
};
layout(std430, binding = COMBATANT_CLOUD_WEATHER_BINDING) readonly buffer CloudWeatherData {
    WeatherCell cells[];
} u_Weather;
layout(std430, binding = COMBATANT_CLOUD_MACRO_WEATHER_BINDING) readonly buffer CloudMacroWeatherData {
    WeatherCell cells[];
} u_MacroWeather;

struct CloudDomain {
    vec4 envelope;
    vec4 development;
    vec4 scaleShape;
    vec4 weatherOptics;
    vec4 coverageShape;
    vec4 scatteringPolicy;
    vec4 windPolicy;
    vec4 familyShape;
    vec4 domainPolicy;
    vec4 densityPolicy;
};
layout(std430, binding = COMBATANT_CLOUD_DOMAIN_BINDING) readonly buffer CloudDomainData {
    CloudDomain domains[];
} u_Domains;

struct CloudOptics {
    float density;
    float extinction;
    float anisotropy;
    float albedo;
    float multiEnergy;
    float multiExtinction;
    float multiAnisotropy;
};

float combatant_cloud_hash31(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33 + u_Data.noiseDomain.z * 17.0);
    return fract((p.x + p.y) * p.z);
}

float combatant_cloud_value_noise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = combatant_cloud_hash31(i + vec3(0, 0, 0));
    float n100 = combatant_cloud_hash31(i + vec3(1, 0, 0));
    float n010 = combatant_cloud_hash31(i + vec3(0, 1, 0));
    float n110 = combatant_cloud_hash31(i + vec3(1, 1, 0));
    float n001 = combatant_cloud_hash31(i + vec3(0, 0, 1));
    float n101 = combatant_cloud_hash31(i + vec3(1, 0, 1));
    float n011 = combatant_cloud_hash31(i + vec3(0, 1, 1));
    float n111 = combatant_cloud_hash31(i + vec3(1, 1, 1));
    float x00 = mix(n000, n100, f.x);
    float x10 = mix(n010, n110, f.x);
    float x01 = mix(n001, n101, f.x);
    float x11 = mix(n011, n111, f.x);
    return mix(mix(x00, x10, f.y), mix(x01, x11, f.y), f.z);
}

float combatant_cloud_coarse_noise(vec3 p) {
    float a = combatant_cloud_value_noise(p);
    float b = combatant_cloud_value_noise(p * 2.013 + vec3(13.0, 29.0, 43.0));
    return a * 0.68 + b * 0.32;
}

float combatant_cloud_fbm(vec3 p) {
    float sum = 0.0;
    float weight = 0.5;
    for (int i = 0; i < 4; ++i) {
        sum += combatant_cloud_value_noise(p) * weight;
        p = p * 2.031 + vec3(17.0, 31.0, 47.0);
        weight *= 0.5;
    }
    return sum / 0.9375;
}

WeatherCell combatant_cloud_empty_weather() {
    WeatherCell empty;
    empty.climate = vec4(0.0);
    empty.windFront = vec4(0.0);
    return empty;
}

WeatherCell combatant_cloud_weather_cell_from_local(int x, int z) {
    int width = max(1, int(u_Data.grid.y));
    int depth = max(1, int(u_Data.grid.z));
    x = clamp(x, 0, width - 1);
    z = clamp(z, 0, depth - 1);
    int index = z * width + x;
    int sampleCount = max(0, int(u_Data.grid.w));
    if (index < 0 || index >= sampleCount) return combatant_cloud_empty_weather();
    WeatherCell cell = u_Weather.cells[index];
    return cell.windFront.w > 0.5 ? cell : combatant_cloud_empty_weather();
}

WeatherCell combatant_cloud_macro_weather_cell(int x, int z) {
    int width = max(1, int(u_Data.macroGrid.y));
    int depth = max(1, int(u_Data.macroGrid.z));
    x = clamp(x, 0, width - 1);
    z = clamp(z, 0, depth - 1);
    int index = z * width + x;
    int sampleCount = max(0, int(u_Data.macroGrid.w));
    if (index < 0 || index >= sampleCount) return combatant_cloud_empty_weather();
    WeatherCell cell = u_MacroWeather.cells[index];
    return cell.windFront.w > 0.5 ? cell : combatant_cloud_empty_weather();
}

WeatherCell combatant_cloud_sample_weather(vec2 localXZ) {
    float spacing = max(u_Data.grid.x, 1.0);
    vec2 p = localXZ / spacing;
    ivec2 i = ivec2(floor(p));
    vec2 f = fract(p);
    WeatherCell a = combatant_cloud_weather_cell_from_local(i.x, i.y);
    WeatherCell b = combatant_cloud_weather_cell_from_local(i.x + 1, i.y);
    WeatherCell c = combatant_cloud_weather_cell_from_local(i.x, i.y + 1);
    WeatherCell d = combatant_cloud_weather_cell_from_local(i.x + 1, i.y + 1);
    WeatherCell result;
    result.climate = mix(mix(a.climate, b.climate, f.x), mix(c.climate, d.climate, f.x), f.y);
    result.windFront = mix(mix(a.windFront, b.windFront, f.x), mix(c.windFront, d.windFront, f.x), f.y);
    return result;
}

WeatherCell combatant_cloud_sample_macro_weather(vec2 localXZ) {
    float spacing = max(u_Data.macroGrid.x, 1.0);
    vec2 p = (localXZ - u_Data.macroOrigin.xy) / spacing;
    ivec2 i = ivec2(floor(p));
    vec2 f = fract(p);
    WeatherCell a = combatant_cloud_macro_weather_cell(i.x, i.y);
    WeatherCell b = combatant_cloud_macro_weather_cell(i.x + 1, i.y);
    WeatherCell c = combatant_cloud_macro_weather_cell(i.x, i.y + 1);
    WeatherCell d = combatant_cloud_macro_weather_cell(i.x + 1, i.y + 1);
    WeatherCell result;
    result.climate = mix(mix(a.climate, b.climate, f.x), mix(c.climate, d.climate, f.x), f.y);
    result.windFront = mix(mix(a.windFront, b.windFront, f.x), mix(c.windFront, d.windFront, f.x), f.y);
    return result;
}

bool combatant_cloud_domain_in_group(CloudDomain domain, int group) {
    return group < 0 || int(domain.domainPolicy.x + 0.5) == group;
}

vec2 combatant_cloud_domain_wind(CloudDomain domain, WeatherCell localWeather, WeatherCell macroWeather, float height01) {
    vec2 weatherWind = mix(macroWeather.windFront.xy, localWeather.windFront.xy, 0.45);
    float multiplier = mix(domain.windPolicy.x, domain.windPolicy.y, clamp(height01, 0.0, 1.0));
    vec2 wind = weatherWind * multiplier;
    float lengthWind = length(weatherWind);
    if (lengthWind > 1e-5) {
        vec2 perpendicular = vec2(-weatherWind.y, weatherWind.x) / lengthWind;
        wind += perpendicular * domain.windPolicy.z * (height01 - 0.5) * lengthWind;
    }
    return wind;
}

void combatant_cloud_domain_altitudes(CloudDomain domain,
                                       vec3 localPosition,
                                       WeatherCell macroWeather,
                                       out float baseY,
                                       out float topY) {
    float macroScale = max(domain.scaleShape.x * max(domain.domainPolicy.y, 0.1), 1.0);
    vec2 worldXZ = localPosition.xz + u_Data.noiseDomain.xy;
    float baseNoise = combatant_cloud_coarse_noise(vec3(worldXZ.x / macroScale, 0.37, worldXZ.y / macroScale)) * 2.0 - 1.0;
    float thicknessNoise = combatant_cloud_coarse_noise(vec3(worldXZ.x / (macroScale * 1.71), 4.13,
            worldXZ.y / (macroScale * 1.71))) * 2.0 - 1.0;
    float humidityLift = (macroWeather.climate.x - 0.5) * 0.35;
    float convective = clamp(max(macroWeather.climate.z, macroWeather.windFront.z), 0.0, 1.0);

    baseY = domain.envelope.z + domain.envelope.w * clamp(baseNoise + humidityLift, -1.0, 1.0);
    baseY = clamp(baseY, domain.envelope.x, domain.envelope.y - 1.0);

    float thickness = domain.development.x
            + domain.development.y * thicknessNoise
            + domain.development.z * convective * max(domain.familyShape.z, 0.0);
    thickness = max(thickness, 1.0);
    topY = clamp(baseY + thickness, baseY + 1.0, domain.envelope.y);
}

float combatant_cloud_vertical_profile(CloudDomain domain, float height01, float convective) {
    float h = clamp(height01, 0.0, 1.0);
    float bottom = pow(clamp(smoothstep(0.0, 0.22, h), 0.0, 1.0), max(domain.familyShape.x, 0.25));
    float top = pow(clamp(1.0 - smoothstep(0.62, 1.0, h), 0.0, 1.0), max(domain.familyShape.y, 0.25));
    float vertical = bottom * top;
    float anvil = max(domain.familyShape.w, 0.0) * convective;
    if (anvil > 1e-4) {
        float cap = smoothstep(0.62, 0.82, h) * (1.0 - smoothstep(0.92, 1.0, h));
        vertical = max(vertical, cap * anvil);
    }
    return vertical;
}

float combatant_cloud_weather_drive(CloudDomain domain, WeatherCell localWeather, WeatherCell macroWeather) {
    vec3 responses = max(domain.weatherOptics.xyz, vec3(0.0));
    float responseSum = max(responses.x + responses.y + responses.z, 1e-4);
    vec3 localSignals = vec3(localWeather.climate.x, localWeather.climate.z, localWeather.windFront.z);
    vec3 macroSignals = vec3(macroWeather.climate.x, macroWeather.climate.z, macroWeather.windFront.z);
    vec3 signals = mix(macroSignals, localSignals, 0.55);
    return dot(signals, responses) / responseSum;
}

float combatant_cloud_domain_coarse_density(CloudDomain domain,
                                             vec3 localPosition,
                                             WeatherCell localWeather,
                                             WeatherCell macroWeather,
                                             out float baseY,
                                             out float topY,
                                             out vec2 domainWind) {
    combatant_cloud_domain_altitudes(domain, localPosition, macroWeather, baseY, topY);
    if (localPosition.y < baseY || localPosition.y > topY) {
        domainWind = vec2(0.0);
        return 0.0;
    }

    float height01 = (localPosition.y - baseY) / max(topY - baseY, 1.0);
    float convective = clamp(max(macroWeather.climate.z, macroWeather.windFront.z), 0.0, 1.0);
    float vertical = combatant_cloud_vertical_profile(domain, height01, convective);
    if (vertical <= 0.0) {
        domainWind = vec2(0.0);
        return 0.0;
    }

    domainWind = combatant_cloud_domain_wind(domain, localWeather, macroWeather, height01);
    vec2 worldXZ = localPosition.xz + u_Data.noiseDomain.xy;
    vec2 advectedXZ = worldXZ - domainWind * u_Data.cameraTime.w;
    float macroScale = max(domain.scaleShape.x * max(domain.domainPolicy.y, 0.1), 1.0);
    float verticalScale = max(domain.domainPolicy.z, 0.1);
    float macro = combatant_cloud_coarse_noise(vec3(
            advectedXZ.x / macroScale,
            localPosition.y / (macroScale * verticalScale),
            advectedXZ.y / macroScale));

    float weatherDrive = combatant_cloud_weather_drive(domain, localWeather, macroWeather);
    float coverage = clamp(weatherDrive + domain.coverageShape.x, 0.0, 1.0);
    float threshold = mix(domain.coverageShape.y, domain.coverageShape.z, coverage);
    float shape = smoothstep(threshold - 0.10, threshold + 0.10, macro);
    return max(0.0, shape * vertical * domain.development.w);
}

float combatant_cloud_domain_full_density(CloudDomain domain,
                                           vec3 localPosition,
                                           WeatherCell localWeather,
                                           WeatherCell macroWeather,
                                           out vec2 domainWind) {
    float baseY;
    float topY;
    float coarse = combatant_cloud_domain_coarse_density(domain, localPosition, localWeather, macroWeather,
            baseY, topY, domainWind);
    if (coarse <= max(domain.densityPolicy.x, 0.0)) return 0.0;

    float detailScale = max(domain.scaleShape.y, 1.0);
    vec2 worldXZ = localPosition.xz + u_Data.noiseDomain.xy;
    vec2 detailWind = domainWind * max(domain.windPolicy.w, 0.0);
    vec2 advectedXZ = worldXZ - detailWind * u_Data.cameraTime.w;
    float detail = combatant_cloud_fbm(vec3(advectedXZ.x / detailScale,
            localPosition.y / max(detailScale * domain.domainPolicy.z, 1.0),
            advectedXZ.y / detailScale));
    float detailStrength = clamp(domain.densityPolicy.z, 0.0, 2.0);
    float erosion = clamp(domain.scaleShape.z, 0.0, 1.0);
    float detailMask = mix(1.0, smoothstep(erosion, 1.0, detail),
            clamp(detailStrength + erosion * 0.35, 0.0, 1.0));
    return max(0.0, coarse * detailMask);
}

float combatant_cloud_domain_lighting_density(CloudDomain domain,
                                               vec3 localPosition,
                                               WeatherCell localWeather,
                                               WeatherCell macroWeather) {
    float baseY;
    float topY;
    vec2 domainWind;
    float coarse = combatant_cloud_domain_coarse_density(domain, localPosition, localWeather, macroWeather,
            baseY, topY, domainWind);
    if (coarse <= max(domain.densityPolicy.x, 0.0)) return 0.0;
    float fraction = clamp(domain.densityPolicy.y, 0.0, 1.0);
    if (fraction <= 1e-4) return coarse;
    float detailScale = max(domain.scaleShape.y * 1.8, 1.0);
    vec2 worldXZ = localPosition.xz + u_Data.noiseDomain.xy;
    vec2 advectedXZ = worldXZ - domainWind * u_Data.cameraTime.w;
    float detail = combatant_cloud_coarse_noise(vec3(advectedXZ.x / detailScale,
            localPosition.y / max(detailScale * domain.domainPolicy.z, 1.0),
            advectedXZ.y / detailScale));
    return coarse * mix(1.0, smoothstep(domain.scaleShape.z, 1.0, detail), fraction);
}

CloudOptics combatant_cloud_optics_at_mode(vec3 localPosition, int group, bool lightingDensity) {
    WeatherCell localWeather = combatant_cloud_sample_weather(localPosition.xz);
    WeatherCell macroWeather = combatant_cloud_sample_macro_weather(localPosition.xz);
    CloudOptics result = CloudOptics(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

    float weightedExtinction = 0.0;
    float weightedAnisotropy = 0.0;
    float weightedAlbedo = 0.0;
    float weightedMultiEnergy = 0.0;
    float weightedMultiExtinction = 0.0;
    float weightedMultiAnisotropy = 0.0;
    int domainCount = clamp(int(u_Data.counts.x), 0, COMBATANT_CLOUD_MAX_DOMAINS);
    for (int i = 0; i < COMBATANT_CLOUD_MAX_DOMAINS; ++i) {
        if (i >= domainCount) break;
        CloudDomain domain = u_Domains.domains[i];
        if (!combatant_cloud_domain_in_group(domain, group)) continue;
        vec2 wind;
        float density = lightingDensity
                ? combatant_cloud_domain_lighting_density(domain, localPosition, localWeather, macroWeather)
                : combatant_cloud_domain_full_density(domain, localPosition, localWeather, macroWeather, wind);
        result.density += density;
        weightedExtinction += density * domain.weatherOptics.w;
        weightedAnisotropy += density * domain.scaleShape.w;
        weightedAlbedo += density * domain.scatteringPolicy.x;
        weightedMultiEnergy += density * domain.scatteringPolicy.y;
        weightedMultiExtinction += density * domain.scatteringPolicy.z;
        weightedMultiAnisotropy += density * domain.scatteringPolicy.w;
    }
    if (result.density > 1e-6) {
        float inverseDensity = 1.0 / result.density;
        result.extinction = max(0.0, weightedExtinction * inverseDensity);
        result.anisotropy = clamp(weightedAnisotropy * inverseDensity, -0.9, 0.9);
        result.albedo = clamp(weightedAlbedo * inverseDensity, 0.0, 1.0);
        result.multiEnergy = clamp(weightedMultiEnergy * inverseDensity, 0.0, 0.98);
        result.multiExtinction = clamp(weightedMultiExtinction * inverseDensity, 0.01, 1.0);
        result.multiAnisotropy = clamp(weightedMultiAnisotropy * inverseDensity, 0.0, 1.0);
    }
    return result;
}

CloudOptics combatant_cloud_optics_at(vec3 localPosition) {
    return combatant_cloud_optics_at_mode(localPosition, COMBATANT_CLOUD_GROUP_ALL, false);
}

CloudOptics combatant_cloud_optics_at_group(vec3 localPosition, int group) {
    return combatant_cloud_optics_at_mode(localPosition, group, false);
}

CloudOptics combatant_cloud_lighting_optics_at(vec3 localPosition) {
    return combatant_cloud_optics_at_mode(localPosition, COMBATANT_CLOUD_GROUP_ALL, true);
}

float combatant_cloud_density_at(vec3 localPosition, out float extinction, out float anisotropy) {
    CloudOptics optics = combatant_cloud_optics_at(localPosition);
    extinction = optics.extinction;
    anisotropy = optics.anisotropy;
    return optics.density;
}

float combatant_cloud_optical_density_at(vec3 localPosition) {
    CloudOptics optics = combatant_cloud_lighting_optics_at(localPosition);
    return optics.density * max(optics.extinction, 0.0);
}

vec2 combatant_cloud_group_wind(vec3 localPosition, int group) {
    WeatherCell localWeather = combatant_cloud_sample_weather(localPosition.xz);
    WeatherCell macroWeather = combatant_cloud_sample_macro_weather(localPosition.xz);
    vec2 weightedWind = vec2(0.0);
    float weightSum = 0.0;
    int domainCount = clamp(int(u_Data.counts.x), 0, COMBATANT_CLOUD_MAX_DOMAINS);
    for (int i = 0; i < COMBATANT_CLOUD_MAX_DOMAINS; ++i) {
        if (i >= domainCount) break;
        CloudDomain domain = u_Domains.domains[i];
        if (!combatant_cloud_domain_in_group(domain, group)) continue;
        float baseY;
        float topY;
        vec2 wind;
        float density = combatant_cloud_domain_coarse_density(domain, localPosition, localWeather, macroWeather,
                baseY, topY, wind);
        if (density <= 1e-6) continue;
        weightedWind += wind * density;
        weightSum += density;
    }
    return weightSum > 1e-6 ? weightedWind / weightSum : vec2(0.0);
}
