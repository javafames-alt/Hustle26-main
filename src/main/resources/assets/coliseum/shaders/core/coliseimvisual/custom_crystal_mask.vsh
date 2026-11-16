#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:projection.glsl>
layout(location = 0) in vec3 Position;
layout(location = 2) in vec2 UV0;
layout(location = 0) out vec2 TexCoord;
void main() {
    gl_Position = ProjMat * vec4(Position, 1.0);
    TexCoord = UV0;
}
