#version 330 core
#extension GL_ARB_separate_shader_objects : require
#include <combatant:reimagined_sky_common.glsl>

layout(location = 0) in vec2 v_TexCoord;
layout(location = 0) out vec4 color;

uniform sampler2D u_Atmosphere;
uniform sampler2D u_Clouds;
// Directly derived from IterEclipse shaders/texture/moon.png and moonN.png.
// Real photographic albedo + tangent-space normal map, not procedural crater noise.
uniform sampler2D u_LunarAlbedo;
uniform sampler2D u_LunarNormal;

float starField(vec3 rd) {
    vec3 cell = floor(rd * 480.0);
    vec3 local = fract(rd * 480.0) - 0.5;
    float seed = skyHash(cell);
    vec3 centre = vec3(skyHash(cell + 11.0), skyHash(cell + 17.0),
                       skyHash(cell + 29.0)) - 0.5;
    float disc = 1.0 - smoothstep(0.018, max(0.024, u_SunVisual.w * 0.62),
                                  length(local - centre * 0.36));
    float threshold = mix(0.9998, 0.993, clamp(u_SunVisual.z * 5.0, 0.0, 1.0));
    float brightness = mix(0.48, 1.8, skyHash(cell + 47.0));
    return disc * smoothstep(threshold, min(threshold + 0.002, 0.99999), seed) * brightness;
}

// Same optical coefficients and scale heights used by the registered atmospheric model.
// RGB attenuation is shared by the sun and moon, instead of arbitrarily tinting disks.
vec3 bodyTransmittance(float elevation) {
    float h = max(elevation, 0.0);
    float airMass = 1.0 / max(0.045, h + 0.15 * pow(1.0 - h, 1.2));
    vec3 opticalDepth = u_Rayleigh.rgb * u_Rayleigh.w +
                        u_Mie.rgb * u_Mie.w * 1.11 +
                        u_Ozone.rgb * (u_Ozone.w * 0.65);
    return exp(-airMass * opticalDepth);
}

// Normalized disk coordinates in the screen basis. For the moon, this is also
// the latitude-longitude projection input used in IterEclipse's sphereMap().
void skyDiskFrame(vec3 rd, vec3 direction, float angularRadius,
                  out vec2 xy, out vec3 east, out vec3 north) {
    vec3 referenceUp = abs(direction.y) > 0.97 ? vec3(0.0, 0.0, 1.0) : vec3(0.0, 1.0, 0.0);
    east = normalize(cross(direction, referenceUp));
    north = normalize(-cross(direction, east));
    xy = vec2(dot(rd, east), dot(rd, north)) / max(sin(angularRadius), 0.0001);
}

float diskCoverage(vec2 xy) {
    // A one-pixel coverage transition at any FOV, including zoomed views.
    float r = length(xy);
    float aa = max(fwidth(r), 0.0005);
    return 1.0 - smoothstep(1.0 - aa, 1.0 + aa, r);
}

vec3 texturedMoon(vec3 rd, out float occlusion) {
    occlusion = 0.0;
    vec3 moonDir = normalize(u_Moon.xyz);
    float radius = max(u_Moon.w, 0.004);
    // Reject nearly the entire sky BEFORE sampling either high-resolution atlas.
    if (dot(rd, moonDir) < cos(radius * 1.65)) return vec3(0.0);

    vec2 xy;
    vec3 east, north;
    skyDiskFrame(rd, moonDir, radius, xy, east, north);
    float shape = diskCoverage(xy);
    if (shape <= 0.0) return vec3(0.0);
    float rr = dot(xy, xy);
    vec3 sphere = vec3(xy, sqrt(max(1.0 - rr, 0.0)));

    // Original IterEclipse equirectangular UV: longitude=atan(x,z),
    // latitude=acos(y), see shaders/lib/sky_gradient.glsl sphereMap().
    float longitude = atan(sphere.x, sphere.z);
    vec2 lunarUv = vec2((longitude + SKY_PI) / (2.0 * SKY_PI),
                        acos(clamp(sphere.y, -1.0, 1.0)) / SKY_PI);
    vec3 albedoSrgb = texture(u_LunarAlbedo, lunarUv).rgb;
    vec3 normalTex = normalize(texture(u_LunarNormal, lunarUv).xyz * 2.0 - 1.0);

    // The spherical surface points toward the observer; the light direction
    // points from the moon toward the light. This avoids inverted crater normals.
    vec3 surfaceNormal = normalize(east * sphere.x + north * sphere.y - moonDir * sphere.z);
    vec3 longitudinalTangent = normalize(east * cos(longitude) + moonDir * sin(longitude));
    vec3 latitudinalTangent = normalize(cross(surfaceNormal, longitudinalTangent));
    vec3 microNormal = normalize(longitudinalTangent * normalTex.x +
                                 latitudinalTangent * normalTex.y +
                                 surfaceNormal * max(normalTex.z, 0.02));

    // Minecraft provides eight moon phases but its moon sky direction normally
    // remains opposite the sun on EVERY day. Physically deriving phases from
    // u_Sun alone would therefore always render a full moon. Rotate the local
    // solar illumination using the actual EnvironmentAttributes.MOON_PHASE index.
    float phase = mod(u_Rotation.z, 8.0) * (SKY_PI * 0.25);
    vec3 lunarSun = normalize(-moonDir * cos(phase) - east * sin(phase));
    float macroLight = dot(surfaceNormal, lunarSun);
    float normalLight = max(dot(microNormal, lunarSun), 0.0);
    float terminator = smoothstep(-0.014, 0.02, macroLight);
    float lambert = pow(normalLight, max(u_MoonVisual.y, 0.05));
    float earthshine = max(u_MoonVisual.x, 0.0) * (1.0 - smoothstep(0.0, 0.12, macroLight));
    float illumination = lambert * terminator + earthshine;

    // Moon colour is sampled in sRGB, radiance in linear/HDR. The existing
    // celestial profile supplies energy and surface-response parameters.
    vec3 reflectance = pow(max(albedoSrgb, vec3(0.0)), vec3(2.2));
    vec3 lunarLight = u_MoonColor.rgb * (240.0 * u_MoonColor.w);
    vec3 transmitted = bodyTransmittance(moonDir.y);
    float horizon = smoothstep(-0.012, 0.015, rd.y);
    occlusion = shape * horizon;
    return reflectance * lunarLight * illumination * transmitted * occlusion;
}

vec3 texturedSun(vec3 rd, out float occlusion) {
    occlusion = 0.0;
    vec3 sunDir = normalize(u_Sun.xyz);
    float radius = max(u_Quality.z, 0.0025);
    if (dot(rd, sunDir) < cos(radius * 1.65)) return vec3(0.0);

    vec2 xy;
    vec3 east, north;
    skyDiskFrame(rd, sunDir, radius, xy, east, north);
    float shape = diskCoverage(xy);
    if (shape <= 0.0) return vec3(0.0);

    // Solar photosphere: the centre is brighter than its edge (limb darkening).
    // No high-frequency procedural sun texture or fake solar 'caustic' pattern.
    float mu = sqrt(max(1.0 - dot(xy, xy), 0.0));
    float limb = 1.0 - clamp(u_SunVisual.y, 0.0, 0.95) * (1.0 - mu);
    vec3 spectrum = vec3(1.0, 0.965, 0.91) * u_SunColor.rgb;
    vec3 optical = bodyTransmittance(sunDir.y);
    float horizon = smoothstep(-0.012, 0.015, rd.y);
    float weather = 1.0 - clamp(u_Climate.x * 0.55 + u_Climate.y * 0.3, 0.0, 0.85);
    occlusion = shape * horizon;
    return spectrum * optical * (32.0 * u_SunVisual.x * limb * weather) * occlusion;
}

vec3 celestial(vec3 rd) {
    if (u_Rotation.w > 0.5) {
        if (u_Rotation.w < 1.5) {
            return vec3(0.57, 0.52, 0.75) * starField(rd) * u_Sky.w * 1.15;
        }
        return vec3(0.0);
    }

    float moonOcclusion;
    float sunOcclusion;
    vec3 sun = texturedSun(rd, sunOcclusion);
    vec3 moon = texturedMoon(rd, moonOcclusion);
    float day = smoothstep(-0.13, 0.07, u_Sun.y);
    float visibility = u_Sun.w * u_Sky.w * (1.0 - day * 0.90);
    visibility *= smoothstep(u_MoonVisual.z, u_MoonVisual.w, rd.y);
    visibility *= 1.0 - clamp(sunOcclusion + moonOcclusion, 0.0, 1.0);
    if (visibility < 0.0005) return sun + moon;
    float stars = starField(rd) * visibility * max(u_SunColor.w * 30.0, 0.1);
    return sun + moon + vec3(0.69, 0.79, 1.0) * stars;
}

void main() {
    vec3 rd = skyRay(v_TexCoord);
    vec3 atmosphere = texture(u_Atmosphere, v_TexCoord).rgb + celestial(rd);
    vec4 clouds = texture(u_Clouds, v_TexCoord);
    // Premultiplied volumetric radiance over physical atmosphere and both disks.
    vec3 hdr = max(atmosphere, vec3(0.0)) * (1.0 - clamp(clouds.a, 0.0, 1.0)) +
               max(clouds.rgb, vec3(0.0));
    vec3 mapped = hdr / (vec3(1.0) + hdr);
    color = vec4(clamp(mapped, 0.0, 1.0), 1.0);
}
