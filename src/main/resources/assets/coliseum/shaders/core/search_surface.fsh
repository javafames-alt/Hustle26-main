#version 330
#extension GL_ARB_separate_shader_objects : require
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat; mat4 TextureMat; vec4 ColorModulator; vec3 ModelOffset;
};
layout(location=0) in vec2 texCoord0;
layout(location=1) in vec4 vertexColor;
layout(location=0) out vec4 fragColor;
void main() {
    vec2 extent = 1.0 / max(fwidth(texCoord0), vec2(0.00001));
    float padding = extent.y * (3.0 / 22.0);
    float radius = extent.y * (4.0 / 22.0);
    vec2 p = (texCoord0 - 0.5) * extent;
    vec2 q = abs(p) - (extent * 0.5 - vec2(padding + radius));
    float distance = length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
    float sigma = max(padding * 0.48, 0.5);
    float outside = max(distance, 0.0);
    float coverage = exp(-0.5 * outside * outside / (sigma * sigma));
    coverage *= 1.0 - smoothstep(padding * 0.65, padding, outside);
    fragColor = vertexColor * ColorModulator;
    fragColor.a *= coverage;
    if (fragColor.a <= 0.001) discard;
}
