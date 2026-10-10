#version 330 core

in vec2 v_TexCoord;
in vec4 v_Color;
in vec4 v_Params;
in vec4 v_Params2;
in vec3 v_ViewPosition;
in vec3 v_ViewNormal;
out vec4 color;

const float TAU = 6.283185307179586;

float sat(float x) {
    return clamp(x, 0.0, 1.0);
}

void main() {
    float u = fract(v_TexCoord.x);
    float radial = clamp(v_TexCoord.y, 0.0, 1.0);
    float time = v_Params.x;
    float track = v_Params.y;
    float speed = max(v_Params.z, 0.05);

    vec3 viewDir = normalize(-v_ViewPosition);
    vec3 diskNormal = normalize(v_ViewNormal);
    float facing = abs(dot(viewDir, diskNormal));
    float grazing = pow(1.0 - sat(facing), 1.6);

    if (track > 2.5) {
        float edge = pow(1.0 - sat(facing), 2.1);
        float breathe = 0.5 + 0.5 * sin(time * 0.36 + u * TAU);
        vec3 horizon = vec3(0.0025, 0.0045, 0.0080);
        horizon += vec3(0.018, 0.026, 0.050) * edge * (0.58 + breathe * 0.16);
        float horizonAlpha = v_Color.a * (0.72 - edge * 0.20 + breathe * 0.025);
        color = vec4(horizon, sat(horizonAlpha));
        return;
    }

    float innerHot = pow(1.0 - radial, 2.35);
    float innerFade = smoothstep(0.02, 0.18, radial);
    float body = innerFade * smoothstep(0.0, 0.055, radial) * (1.0 - smoothstep(0.76, 1.0, radial));
    float outerMist = smoothstep(0.52, 0.76, radial) * (1.0 - smoothstep(0.78, 1.0, radial));

    float spiralA = 0.5 + 0.5 * sin(u * TAU * 5.0 - radial * 13.0 - time * speed * 1.35);
    float spiralB = 0.5 + 0.5 * sin(u * TAU * 9.0 + radial * 22.0 - time * speed * 0.63);
    float filament = smoothstep(0.62, 0.94, spiralA * 0.68 + spiralB * 0.32);

    float doppler = 0.5 + 0.5 * cos(u * TAU);
    float mainAlpha = body * (0.30 + innerHot * 0.40 + filament * 0.16) + outerMist * 0.09;

    float photon = track > 0.5 && track < 1.5 ? 1.0 : 0.0;
    float echo = track > 1.5 ? 1.0 : 0.0;
    float alpha = mix(mainAlpha, 0.52 + innerHot * 0.26, photon);
    alpha = mix(alpha, (0.12 + innerHot * 0.14 + filament * 0.06) * (0.56 + grazing * 0.34), echo);
    alpha *= v_Color.a;
    if (alpha <= 0.002) discard;

    vec3 base = max(v_Color.rgb, vec3(0.001));
    vec3 warm = mix(base, vec3(1.0, 0.72, 0.34), 0.20 + innerHot * 0.16);
    vec3 cool = mix(base, vec3(0.34, 0.58, 1.0), 0.14 + (1.0 - innerHot) * 0.06);
    vec3 whiteHot = mix(base, vec3(1.0), 0.70);

    vec3 diskColor = mix(cool, warm, doppler);
    diskColor = mix(diskColor, whiteHot, innerHot * (0.32 + filament * 0.20));
    diskColor += whiteHot * filament * (0.08 + innerHot * 0.16);
    diskColor *= 0.52 + innerHot * 0.74 + body * 0.18;

    vec3 photonColor = mix(base, vec3(1.0), 0.84) * (1.02 + filament * 0.10);
    vec3 echoColor = mix(base, vec3(0.78, 0.88, 1.0), 0.24) * (0.46 + innerHot * 0.18);
    vec3 outColor = mix(diskColor, photonColor, photon);
    outColor = mix(outColor, echoColor, echo);
    outColor = outColor / (1.0 + outColor * 0.10);

    color = vec4(outColor, sat(alpha));
}
