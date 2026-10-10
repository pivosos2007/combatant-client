#version 330 core

uniform sampler2D u_Texture;
uniform sampler2D u_Mask;

layout (std140) uniform WorldMaskedPost {
    float u_Time;
    float u_InvWidth;
    float u_InvHeight;
    float u_GlobalStrength;
};

in vec2 v_TexCoord;
out vec4 color;

void main() {
    vec2 uv = v_TexCoord;
    vec4 mask = texture(u_Mask, uv);
    if (mask.a <= 0.002 || mask.b <= 0.001 || u_GlobalStrength <= 0.0) {
        color = vec4(texture(u_Texture, uv).rgb, 1.0);
        return;
    }

    vec2 flow = mask.rg * 2.0 - 1.0;
    float flowLen = length(flow);
    if (flowLen > 1.0e-5) flow /= flowLen;

    // Secondary movement only: the world-space mask defines the volume/shape. Keep screen-space
    // wobble low-frequency so the interior reads as refracted space rather than animated caustics.
    float waveA = sin((uv.y + u_Time * 0.115) * 19.0 + uv.x * 7.0);
    float waveB = cos((uv.x - u_Time * 0.092) * 17.0 - uv.y * 6.0);
    vec2 micro = vec2(waveA, waveB) * 0.075;

    // B already carries alpha-weighted accumulated local strength from mask rasterization.
    // Multiplying by A again would square coverage and make thin/ring masks unnaturally weak.
    float envelope = mask.b * u_GlobalStrength;
    vec2 offset = (flow + micro) * (0.0135 * envelope);
    // Keep the displacement stable in pixel space. invWidth / invHeight == height / width.
    offset.x *= clamp(u_InvWidth / max(u_InvHeight, 1.0e-7), 0.25, 4.0);

    vec2 uvG = clamp(uv + offset, vec2(0.0), vec2(1.0));
    float chroma = 0.045 * envelope;
    vec2 chromaOffset = offset * chroma;
    vec2 uvR = clamp(uvG - chromaOffset, vec2(0.0), vec2(1.0));
    vec2 uvB = clamp(uvG + chromaOffset, vec2(0.0), vec2(1.0));

    vec3 refracted = vec3(
        texture(u_Texture, uvR).r,
        texture(u_Texture, uvG).g,
        texture(u_Texture, uvB).b
    );

    // A short directional gather gives the sphere interior actual optical thickness. It stays
    // bounded by the rasterized world mask, so this is still a local world-space post effect.
    vec3 cavity = refracted;
    cavity += texture(u_Texture, clamp(uvG + offset * 0.55, vec2(0.0), vec2(1.0))).rgb;
    cavity += texture(u_Texture, clamp(uvG - offset * 0.42, vec2(0.0), vec2(1.0))).rgb;
    cavity *= 1.0 / 3.0;
    float optical = clamp(envelope * mask.a, 0.0, 1.0);
    refracted = mix(refracted, cavity, optical * 0.34);

    vec3 base = texture(u_Texture, uv).rgb;
    color = vec4(mix(base, refracted, clamp(mask.a, 0.0, 1.0)), 1.0);
}
