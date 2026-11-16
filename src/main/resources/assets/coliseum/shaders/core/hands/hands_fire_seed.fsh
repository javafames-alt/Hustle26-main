#version 330
#extension GL_ARB_separate_shader_objects : require
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 OutColor;
uniform sampler2D MaskSampler;
uniform sampler2D AppearanceSampler;
layout(std140) uniform FireSeed { vec4 Params; };
void main() {
    float mask = texture(MaskSampler, texCoord).r;
    vec3 color = texture(AppearanceSampler, texCoord).rgb;
    float peak = max(color.r, max(color.g, color.b));
    vec3 hue = color / max(peak, 0.001);
    vec3 itemColor = mix(vec3(0.45), hue * (0.78 + 0.22 * sqrt(peak)), smoothstep(0.005, 0.08, peak));
    OutColor = Params.x > 0.5 ? vec4(itemColor * mask, mask) : vec4(mask);
}
