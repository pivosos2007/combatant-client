#version 330 core

out vec4 color;

uniform sampler2D u_Texture;

in vec2 v_TexCoord;
in vec4 v_Color;

void main() {
    vec4 value = texture(u_Texture, v_TexCoord);
    if (value == vec4(0.0)) discard;
    color = vec4(value.rgb * v_Color.rgb * max(value.a, v_Color.a), 1.0);
}
