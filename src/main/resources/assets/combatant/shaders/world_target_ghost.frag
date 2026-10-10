#version 330 core

in vec2 v_TexCoord;
in vec4 v_Color;
in vec4 v_Params;
in vec4 v_Params2;
in vec3 v_ViewPosition;
in vec3 v_ViewNormal;
out vec4 color;

const float TAU = 6.283185307179586;

void main() {
    float u = clamp(v_TexCoord.x, 0.0, 1.0);
    float v = v_TexCoord.y;
    float time = v_Params.x;
    float seed = v_Params2.w * TAU;
    float envelope = smoothstep(0.0, 0.12, u) * (1.0 - smoothstep(0.82, 1.0, u));
    float edge = pow(clamp(1.0 - abs(v * 2.0 - 1.0), 0.0, 1.0), 0.7);
    float flow = u * 18.0 - time * 2.1 + seed;
    float lane = (v - 0.5) * 9.0;
    float waveA = sin(flow + sin(lane * 1.4 + time * 0.65) * 1.8);
    float waveB = sin(flow * 1.34 - lane * 1.6 + time * 0.9);
    float focus = max(0.0, 1.0 - abs(waveA - waveB) * 0.83);
    float caustic = pow(focus, 6.0) * (0.7 + 0.3 * sin(lane * 1.2 - time));
    caustic = clamp(caustic, 0.0, 1.0);
    float alpha = v_Color.a * envelope * edge * caustic * clamp(v_Params.w, 0.0, 1.5);
    if (alpha < 0.002) discard;
    color = vec4(v_Color.rgb * (0.7 + 0.3 * caustic), alpha);
}
