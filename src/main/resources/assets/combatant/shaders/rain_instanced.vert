#version 430 core
layout(location = 0) in vec3 Position;
layout(std430, binding = 3) readonly buffer RainFrame {
    mat4 u_ViewProjection;
    vec4 u_Frame;
    vec4 u_WindScreen;
    vec4 u_Camera;
    vec4 u_NearAnchor;
    vec4 u_MidAnchor;
    vec4 u_FarAnchor;
    vec4 u_Extra;
};
struct Drop { vec4 center; vec4 motion; };
layout(std430, binding = 4) readonly buffer RainVisible { Drop visible[]; };
layout(location = 0) out vec2 v_UV;
layout(location = 1) out vec4 v_Optics;
layout(location = 2) out float v_Fade;
void main() {
#ifdef VULKAN
    uint instance = uint(gl_InstanceIndex);
#else
    uint instance = uint(gl_InstanceID);
#endif
    Drop drop = visible[instance];
    vec3 center = drop.center.xyz;
    float lod = floor(drop.center.w / 256.0);
    float dist = drop.center.w - lod * 256.0;
    vec2 wind = drop.motion.xy;
    float speed = drop.motion.z;
    float trail = drop.motion.w;
    vec3 tail = center + vec3(wind.x, -speed, wind.y) * (trail / max(speed, 0.01));
    vec4 headClip = u_ViewProjection * vec4(center, 1.0);
    vec4 tailClip = u_ViewProjection * vec4(tail, 1.0);
    vec2 screen = max(u_WindScreen.zw, vec2(1.0));
    vec2 streakPx = (headClip.xy / headClip.w - tailClip.xy / tailClip.w) * 0.5 * screen;
    vec2 tangent = length(streakPx) > 0.1 ? normalize(streakPx) : vec2(0.0, 1.0);
    vec2 perpendicular = vec2(tangent.y, -tangent.x);
    // Physical radius varies with the candidate, not a constant rectangle impersonating water.
    float randomRadius = fract(sin(dot(center, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
    float physicalRadius = mix(0.007, 0.016, randomRadius);
    float radiusPixels = physicalRadius * abs(u_ViewProjection[1][1]) * screen.y * 0.5 / max(headClip.w, 0.1);
    float halfWidth = clamp(radiusPixels, 0.55, lod < 0.5 ? 4.5 : 2.0);
    float x = Position.x;
    float t = Position.y * 0.5 + 0.5;
    vec4 clip = mix(tailClip, headClip, t);
    clip.xy += perpendicular * (x * halfWidth) * (2.0 / screen) * clip.w;
    gl_Position = clip;
    v_UV = vec2(x * 0.5 + 0.5, t);
    float maxDist = min(u_Frame.w, lod < 0.5 ? 25.0 : (lod < 1.5 ? 71.0 : 119.0));
    float minDist = lod < 0.5 ? 0.2 : (lod < 1.5 ? 18.0 : 60.0);
    v_Fade = clamp((maxDist - dist) / max(8.0, maxDist * 0.18), 0.0, 1.0)
        * clamp((dist - minDist) / max(5.0, minDist * 0.25), 0.0, 1.0) * u_Frame.y;
    v_Optics = vec4(0.0, halfWidth, dist, lod);
}
