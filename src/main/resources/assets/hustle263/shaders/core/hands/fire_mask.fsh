#version 330
#extension GL_ARB_separate_shader_objects : require

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 OutColor;
uniform sampler2D HandDepthSampler;

void main() {
    float depth = texture(HandDepthSampler, texCoord).r;
    float mask = smoothstep(0.0001, 0.0004, depth);

    OutColor = vec4(mask, mask, mask, mask);
}
