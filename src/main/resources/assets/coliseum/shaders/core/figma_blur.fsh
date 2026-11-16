#version 330
#extension GL_ARB_separate_shader_objects : require
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 OutColor;
uniform sampler2D InputSampler;
layout(std140) uniform BlurStyle { vec4 Blur; };

void main() {
    // Thirty-one taps spanning three sigma, at half resolution for both passes.
    vec4 sum = vec4(0.0);
    float total = 0.0;
    for (int i = -15; i <= 15; ++i) {
        float t = float(i) * 0.2;
        float weight = exp(-0.5 * t * t);
        sum += texture(InputSampler, texCoord + Blur.xy * Blur.z * t) * weight;
        total += weight;
    }
    OutColor = sum / total;
}
