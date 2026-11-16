#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform Lightning {
    mat4 Projection;
    mat4 View;
    vec4 Color;
};

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;

layout(location = 0) out vec2 BoltUv;
layout(location = 1) out vec4 BoltColor;

void main() {
    BoltUv = UV0;
    BoltColor = Color;
    gl_Position = Projection * View * vec4(Position, 1.0);
}
