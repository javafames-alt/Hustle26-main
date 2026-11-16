#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform WorldColor {
    vec4 ColorMultiplier;
};

layout(location = 0) in vec2 TexCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = vec4(ColorMultiplier.rgb * 0.5, 1.0);
}
