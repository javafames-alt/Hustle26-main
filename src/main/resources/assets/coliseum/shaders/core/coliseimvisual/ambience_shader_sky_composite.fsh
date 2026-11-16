#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D SkySampler;
uniform sampler2D DepthSampler;

layout(location = 0) in vec2 TexCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    float depth = texture(DepthSampler, TexCoord).r;
    if (depth > 0.000001) {
        discard;
    }

    vec4 sky = texture(SkySampler, TexCoord);
    if (sky.a <= 0.001) {
        discard;
    }

    fragColor = sky;
}
