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

float circularDistance(float a, float b) {
    return abs(fract(a - b + 0.5) - 0.5);
}

float pulseAt(float u, float phase, float width) {
    return 1.0 - smoothstep(width * 0.28, width, circularDistance(u, phase));
}

void main() {
    float u = fract(v_TexCoord.x);
    float tube = fract(v_TexCoord.y) * TAU;
    float time = v_Params.x;
    float layer = v_Params.y;
    float speed = max(v_Params.z, 0.05);
    float intensity = max(v_Params.w, 0.0);

    vec3 normal = normalize(v_ViewNormal);
    vec3 viewDir = normalize(-v_ViewPosition);
    float fresnel = pow(1.0 - sat(abs(dot(normal, viewDir))), 2.1);

    float tubeCore = pow(max(cos(tube), 0.0), 4.0) + pow(max(-cos(tube), 0.0), 4.0) * 0.24;
    float tubeSide = pow(max(sin(tube), 0.0), 4.0) + pow(max(-sin(tube), 0.0), 4.0);
    float tubeGlow = sat(0.34 + fresnel * 0.92 + tubeSide * 0.28);

    float scan = fract(time * 0.115 * speed);
    float p0 = pulseAt(u, scan, 0.075);
    float p1 = pulseAt(u, fract(scan + 0.337), 0.052);
    float p2 = pulseAt(u, fract(1.0 - scan * 0.71 + 0.612), 0.040);
    float pulse = sat(p0 + p1 * 0.72 + p2 * 0.54);

    float arc0 = pulseAt(u, fract(scan - 0.11), 0.19) * (1.0 - pulseAt(u, fract(scan - 0.11), 0.085));
    float arc1 = pulseAt(u, fract(scan + 0.43), 0.13) * (1.0 - pulseAt(u, fract(scan + 0.43), 0.055));
    float fracture = sat(arc0 * 0.72 + arc1 * 0.52);

    float gateCarrier = 0.5 + 0.5 * sin(u * TAU * 6.0 - time * 0.31 * speed);
    float gates = smoothstep(0.76, 0.97, gateCarrier);
    float bodyGate = mix(0.78, 1.0, gates);

    float layerFade = layer < 0.5 ? 1.0 : 0.42;
    float body = (0.24 + tubeCore * 0.58 + tubeGlow * 0.34) * bodyGate;
    float electric = pulse * (0.56 + tubeGlow * 0.44) + fracture * (0.28 + fresnel * 0.46);
    float alpha = (body + electric) * v_Color.a * intensity * layerFade;
    if (alpha <= 0.002) discard;

    vec3 base = max(v_Color.rgb, vec3(0.001));
    vec3 hot = mix(base, vec3(1.0), 0.68);
    vec3 shifted = mix(base, base.gbr, 0.14);
    vec3 outColor = base * (0.48 + tubeCore * 0.68 + fresnel * 0.30);
    outColor += hot * pulse * (0.62 + tubeCore * 0.40);
    outColor += shifted * fracture * 0.42;
    outColor += mix(base, hot, 0.22) * gates * 0.12;
    outColor *= intensity * layerFade;
    outColor = outColor / (1.0 + outColor * 0.16);

    color = vec4(outColor, sat(alpha));
}
