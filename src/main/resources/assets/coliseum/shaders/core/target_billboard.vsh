#version 330
#extension GL_ARB_separate_shader_objects : require

layout(location=0) in vec3 Position;
layout(location=1) in vec2 UV0;
layout(location=2) in vec4 Color;

layout(std140) uniform BillboardData {
    mat4 MVP;
};

layout(location=0) out vec2 vUv;
layout(location=1) out vec4 vColor;

void main() {
    gl_Position = MVP * vec4(Position, 1.0);
    vUv = UV0;
    vColor = Color;
}
