#version 330
#extension GL_ARB_separate_shader_objects : require

// Dual Kawase blur, upsample step: 8-tap tent, renders into a target twice the size of the input.
uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    vec2 halfPixel = 0.5 / vec2(textureSize(InSampler, 0));
    vec2 o = halfPixel * BLUR_OFFSET;

    vec4 sum = texture(InSampler, texCoord + vec2(-o.x * 2.0, 0.0));
    sum += texture(InSampler, texCoord + vec2(-o.x, o.y)) * 2.0;
    sum += texture(InSampler, texCoord + vec2(0.0, o.y * 2.0));
    sum += texture(InSampler, texCoord + vec2(o.x, o.y)) * 2.0;
    sum += texture(InSampler, texCoord + vec2(o.x * 2.0, 0.0));
    sum += texture(InSampler, texCoord + vec2(o.x, -o.y)) * 2.0;
    sum += texture(InSampler, texCoord + vec2(0.0, -o.y * 2.0));
    sum += texture(InSampler, texCoord + vec2(-o.x, -o.y)) * 2.0;
    fragColor = vec4((sum / 12.0).rgb, 1.0);
}
