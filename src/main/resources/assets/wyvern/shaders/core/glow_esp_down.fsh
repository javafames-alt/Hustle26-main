#version 330
#extension GL_ARB_separate_shader_objects : require

// GlowESP blur, downsample step: dual Kawase like kawase_down, but the mask is premultiplied colour + coverage,
// so alpha is blurred along with the colour instead of being forced to 1.
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
    fragColor = sum / 8.0;
}
