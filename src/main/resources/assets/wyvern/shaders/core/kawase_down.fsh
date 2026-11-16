#version 330
#extension GL_ARB_separate_shader_objects : require

// Dual Kawase blur, downsample step: 5 taps, renders into a target half the size of the input.
uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    vec2 halfPixel = 0.5 / vec2(textureSize(InSampler, 0));
    vec2 o = halfPixel * BLUR_OFFSET;

    vec4 sum = texture(InSampler, texCoord) * 4.0;
    sum += texture(InSampler, texCoord - o);
    sum += texture(InSampler, texCoord + o);
    sum += texture(InSampler, texCoord + vec2(o.x, -o.y));
    sum += texture(InSampler, texCoord - vec2(o.x, -o.y));
    fragColor = vec4((sum / 8.0).rgb, 1.0);
}
