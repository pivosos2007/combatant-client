// Identical ray reconstruction in the atmosphere, cloud, and full-resolution composite passes.
layout(std140) uniform DeferredSky {
    vec4 u_View;       // framebuffer width, height, vertical FOV radians, aspect
    vec4 u_Rotation;   // yaw, pitch, moon phase index, dimension type (0 overworld, 1 end, 2 nether)
    vec4 u_Sun;        // direction xyz, real vanilla star brightness
    vec4 u_Climate;    // rain, thunder, user cloud coverage, wind
    vec4 u_Camera;     // world xyz, animation seconds
    vec4 u_Sky;        // vanilla sky RGB, user star strength
    vec4 u_Rayleigh;   // scattering RGB in /km, scale height km
    vec4 u_Mie;        // scattering RGB in /km, scale height km
    vec4 u_Quality;    // marching steps, cloud resolution scale, sun angular radius, reserved
    vec4 u_Layer;      // cloud base, top, density scale, coverage bias
    vec4 u_CloudShape; // inverse macro/detail scales, extinction per block, max distance
    vec4 u_CloudLighting; // anisotropy, albedo, multi-scattering energy, coarse threshold
    vec4 u_CloudWeather; // base variation, thickness variation, storm response, convective boost
    vec4 u_Cirrus;    // high layer altitude, density, inverse macro, inverse detail scale
    vec4 u_PrevCamera; // previous camera xyz, previous animation seconds
    vec4 u_PrevView;   // previous yaw, pitch, vertical FOV radians, aspect
    vec4 u_Temporal;   // history validity, frame index, temporal history weight, reserved
    vec4 u_Moon;       // independent moon direction xyz and lunar angular radius
    vec4 u_Optics;     // planet radius, atmosphere radius, mie g, ozone peak altitude (km)
    vec4 u_Ozone;      // ozone extinction /km RGB, ozone width in km
    vec4 u_Stratus;    // stratiform base, thickness, density, coverage bias
    vec4 u_SunColor;   // registered sun radiance RGB, registered star intensity
    vec4 u_SunVisual;  // sun disk scale, limb darkening, star density, star size
    vec4 u_MoonColor;  // registered moon radiance RGB, disk scale
    vec4 u_MoonVisual; // moon earthshine, phase response, star horizon fade min/max
};

const float SKY_PI = 3.141592653589793;

vec3 skyRayParameters(vec2 uv, vec4 view, vec2 orientation) {
    vec2 ndc = (uv * 2.0 - 1.0) * vec2(view.w, 1.0) * tan(view.z * 0.5);
    vec3 rd = normalize(vec3(ndc, 1.0));
    float sx = sin(orientation.y), cx = cos(orientation.y);
    rd = vec3(rd.x, cx * rd.y - sx * rd.z, sx * rd.y + cx * rd.z);
    float sy = sin(orientation.x), cy = cos(orientation.x);
    return normalize(vec3(cy * rd.x - sy * rd.z, rd.y, sy * rd.x + cy * rd.z));
}

vec3 skyRay(vec2 uv) {
    return skyRayParameters(uv, u_View, u_Rotation.xy);
}

float skyHash(vec3 p) {
    vec3 q = fract(p * vec3(0.1031, 0.1030, 0.0973));
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

float skyNoise(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = skyHash(i), n100 = skyHash(i + vec3(1, 0, 0));
    float n010 = skyHash(i + vec3(0, 1, 0)), n110 = skyHash(i + vec3(1, 1, 0));
    float n001 = skyHash(i + vec3(0, 0, 1)), n101 = skyHash(i + vec3(1, 0, 1));
    float n011 = skyHash(i + vec3(0, 1, 1)), n111 = skyHash(i + vec3(1, 1, 1));
    return mix(mix(mix(n000, n100, f.x), mix(n010, n110, f.x), f.y),
               mix(mix(n001, n101, f.x), mix(n011, n111, f.x), f.y), f.z);
}
