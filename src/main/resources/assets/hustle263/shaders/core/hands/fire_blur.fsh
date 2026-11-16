#version 330
#extension GL_ARB_separate_shader_objects : require

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 OutColor;
uniform sampler2D InputSampler;
layout(std140) uniform HandFireBlur {
    vec4 BlurStep;
};

vec4 sampleField(vec2 uv) {
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) return vec4(0.0);
    return texture(InputSampler, uv);
}

void main() {
    vec2 stepUV = BlurStep.xy;
    vec4 result = sampleField(texCoord) * 0.2270270270;
    result += sampleField(texCoord + stepUV * 1.3846153846) * 0.3162162162;
    result += sampleField(texCoord - stepUV * 1.3846153846) * 0.3162162162;
    result += sampleField(texCoord + stepUV * 3.2307692308) * 0.0702702703;
    result += sampleField(texCoord - stepUV * 3.2307692308) * 0.0702702703;
    OutColor = result;
}
