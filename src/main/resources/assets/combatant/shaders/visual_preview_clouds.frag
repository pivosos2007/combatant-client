#version 330 core

/*
 * Protean clouds by nimitz (twitter: @stormoid)
 * https://www.shadertoy.com/view/3l23Rh
 * License Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported License
 * Contact the author for other licensing options
 * This adapted shader remains subject to the upstream CC BY-NC-SA 3.0 license stated above.
 */

out vec4 color;
in vec2 v_TexCoord;

layout (std140) uniform VisualPreviewBackground {
    vec4 u_Viewport;   // width, height, time, pad
    vec4 u_Camera;     // camera translation xyz
    vec4 u_Rotation;   // yaw, pitch, pad, pad
    vec4 u_Accent;
    vec4 u_Background;
};

const mat3 M3 = mat3(
     0.33338,  0.56034, -0.71817,
    -0.87887,  0.32651, -0.15323,
     0.15162,  0.69596,  0.61339
) * 1.93;

mat2 rot(float a) {
    float c = cos(a);
    float s = sin(a);
    return mat2(c, s, -s, c);
}

float mag2(vec2 p) {
    return dot(p, p);
}

float linstep(float mn, float mx, float x) {
    return clamp((x - mn) / (mx - mn), 0.0, 1.0);
}

vec2 disp(float t) {
    return vec2(sin(t * 0.22), cos(t * 0.175)) * 2.0;
}

vec2 mapVolume(vec3 p, float time, float morph) {
    vec3 p2 = p;
    p2.xy -= disp(p.z);
    p.xy *= rot(sin(p.z + time) * (0.1 + morph * 0.05) + time * 0.09);
    float cl = mag2(p2.xy);
    float d = 0.0;
    p *= 0.61;
    float z = 1.0;
    float track = 1.0;
    float displacement = 0.1 + morph * 0.2;
    for (int octave = 0; octave < 5; octave++) {
        p += sin(p.zxy * 0.75 * track + time * track * 0.8) * displacement;
        d -= abs(dot(cos(p), sin(p.yzx)) * z);
        z *= 0.57;
        track *= 1.4;
        p = p * M3;
    }
    d = abs(d + morph * 3.0) + morph * 0.3 - 2.5;
    return vec2(d + cl * 0.2 + 0.25, cl);
}

vec4 renderVolume(vec3 ro, vec3 rd, float time, float morph) {
    vec4 result = vec4(0.0);
    float distanceAlongRay = 1.0;
    float previousFog = 0.0;

    for (int stepIndex = 0; stepIndex < 52; stepIndex++) {
        if (result.a > 0.99) break;

        vec3 pos = ro + distanceAlongRay * rd;
        vec2 volume = mapVolume(pos, time, morph);
        float density = clamp(volume.x - 0.3, 0.0, 1.0) * 1.12;
        float stepDensity = clamp(volume.x + 2.0, 0.0, 3.0);
        vec4 sampleColor = vec4(0.0);

        if (volume.x > 0.52) {
            sampleColor = vec4(
                sin(vec3(5.0, 0.4, 0.2) + volume.y * 0.1 + sin(pos.z * 0.4) * 0.5 + 1.8) * 0.5 + 0.5,
                0.105
            );
            sampleColor *= density * density * density;
            sampleColor.rgb *= linstep(4.0, -2.5, volume.x) * 2.65;
            float diffuse = clamp((density - mapVolume(pos + vec3(0.8), time, morph).x) / 9.0, 0.001, 1.0);
            diffuse += clamp((density - mapVolume(pos + vec3(0.35), time, morph).x) / 2.5, 0.001, 1.0);
            sampleColor.rgb *= density * (vec3(0.008, 0.065, 0.095) + 1.75 * vec3(0.04, 0.09, 0.045) * diffuse);
            sampleColor.rgb = mix(sampleColor.rgb, sampleColor.rgb * u_Accent.rgb * 2.2, 0.34);
        }

        float fog = exp(distanceAlongRay * 0.2 - 2.2);
        vec3 fogTint = mix(vec3(0.075, 0.14, 0.16), max(u_Accent.rgb, vec3(0.08)), 0.22);
        sampleColor += vec4(fogTint, 0.12) * clamp(fog - previousFog, 0.0, 1.0);
        previousFog = fog;
        result += sampleColor * (1.0 - result.a);
        distanceAlongRay += clamp(0.5 - stepDensity * stepDensity * 0.05, 0.09, 0.3);
    }
    return clamp(result, 0.0, 1.0);
}

float saturation(vec3 c) {
    float lo = min(min(c.x, c.y), c.z);
    float hi = max(max(c.x, c.y), c.z);
    return (hi - lo) / (hi + 1e-7);
}

vec3 saturationLerp(vec3 a, vec3 b, float x) {
    vec3 mixed = mix(a, b, x) + vec3(1e-6, 0.0, 0.0);
    float delta = abs(saturation(mixed) - mix(saturation(a), saturation(b), x));
    vec3 direction = normalize(vec3(
        2.0 * mixed.x - mixed.y - mixed.z,
        2.0 * mixed.y - mixed.x - mixed.z,
        2.0 * mixed.z - mixed.y - mixed.x
    ));
    float light = dot(vec3(1.0), mixed);
    float facing = dot(direction, normalize(mixed));
    return clamp(mixed + 1.5 * direction * delta * facing * light, 0.0, 1.0);
}

void main() {
    vec2 resolution = max(u_Viewport.xy, vec2(1.0));
    vec2 q = v_TexCoord;
    vec2 p = (v_TexCoord * resolution - 0.5 * resolution) / resolution.y;
    float time = u_Viewport.z * 0.55;
    float morph = smoothstep(-0.4, 0.4, sin(time * 0.3));

    // Fixed origin: unlike the original fly-through, time is never added to ro.z.
    vec3 ro = vec3(0.0, 1.7, 0.0) + u_Camera.xyz;
    // Wide, distant panorama projection. Subject dolly/orbit never feeds this ray camera.
    vec3 rd = normalize(vec3(p.x * 1.58, -p.y * 1.58, 1.0));
    rd.yz *= rot(u_Rotation.y);
    rd.xz *= rot(u_Rotation.x);

    vec4 scene = renderVolume(ro, rd, time, morph);
    vec3 base = max(u_Background.rgb, vec3(0.018, 0.035, 0.048));
    vec3 cloud = saturationLerp(scene.bgr, scene.rgb, clamp(1.0 - morph, 0.05, 1.0));
    cloud = pow(max(cloud, vec3(0.0)), vec3(0.55, 0.65, 0.60)) * vec3(1.0, 0.97, 0.9);
    vec3 result = mix(base, cloud + base * 0.24, max(scene.a, 0.12));
    result += u_Accent.rgb * scene.a * 0.055;

    float vignette = pow(max(16.0 * q.x * q.y * (1.0 - q.x) * (1.0 - q.y), 0.0), 0.12) * 0.7 + 0.3;
    color = vec4(clamp(result * vignette, 0.0, 1.0), 1.0);
}
