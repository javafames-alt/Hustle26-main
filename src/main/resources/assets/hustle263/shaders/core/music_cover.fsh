#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};
uniform sampler2D Sampler0;
layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;
layout(location = 0) out vec4 fragColor;

void main() {
    // Entire artwork UVs, so the rounded mask is independent of cover resolution.
    vec2 q = abs(texCoord0 - 0.5) - 0.36;
    float distance = length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - 0.14;
    float aa = max(fwidth(distance), 1e-4);
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    color.a *= 1.0 - smoothstep(-aa * 0.5, aa * 0.5, distance);
    if (color.a <= 0.001) discard;
    fragColor = color;
}
