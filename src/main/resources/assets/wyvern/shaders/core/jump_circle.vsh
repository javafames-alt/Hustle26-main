#version 330
#extension GL_ARB_separate_shader_objects : require

// Quick's jump circle mesh: positions relative to the circle's centre, UV0 = (floor weight, 0 floor / 1 wall).
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <wyvern:jump_circle.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;

layout(location = 0) out vec3 localPos;
layout(location = 1) out vec2 surface;
layout(location = 2) out vec4 centreClip;

void main() {
    localPos = Position;
    surface = UV0;
    centreClip = ProjMat * ModelViewMat * vec4(Centre.xyz, 1.0);
    gl_Position = ProjMat * ModelViewMat * vec4(Centre.xyz + Position, 1.0);
}
