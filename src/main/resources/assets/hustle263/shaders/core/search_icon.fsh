#version 330
#extension GL_ARB_separate_shader_objects : require
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat; mat4 TextureMat; vec4 ColorModulator; vec3 ModelOffset;
};
layout(location=0) in vec2 texCoord0;
layout(location=1) in vec4 vertexColor;
layout(location=0) out vec4 fragColor;
void main() {
    vec2 p = texCoord0 - 0.5;
    float ring = abs(length(p - vec2(-0.10)) - 0.235) - 0.052;
    vec2 a = vec2(0.075), b = vec2(0.35);
    vec2 pa = p - a, ba = b - a;
    float handle = length(pa - ba * clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0)) - 0.057;
    float distance = min(ring, handle);
    float aa = max(fwidth(distance), 0.0001);
    float coverage = 1.0 - smoothstep(-aa * 0.5, aa * 0.5, distance);
    fragColor = vertexColor * ColorModulator;
    fragColor.a *= coverage;
    if (fragColor.a <= 0.001) discard;
}
