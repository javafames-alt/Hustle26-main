#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform SaturationData {
    vec4 uParams;
};
uniform sampler2D Sampler0;
layout(location = 0) in vec2 TexCoord;
layout(location = 0) out vec4 fragColor;

const vec3 lumaWeights = vec3(0.2125, 0.7154, 0.0721);

void main() {
    vec3 color = texture(Sampler0, TexCoord).rgb;
    color *= uParams.w;
    color = (color - 0.5) * uParams.z + 0.5;
    float luma = dot(color, lumaWeights);
    float highest = max(color.r, max(color.g, color.b));
    float lowest = min(color.r, min(color.g, color.b));
    color = mix(vec3(luma), color, 1.0 + uParams.y * (1.0 - (highest - lowest)));
    luma = dot(color, lumaWeights);
    color = mix(vec3(luma), color, uParams.x);
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
