#version 330 core
#extension GL_ARB_separate_shader_objects : require

#include <combatant:ui_glass_surface.glsl>

void main() {
    combatantDrawGlass();
}
