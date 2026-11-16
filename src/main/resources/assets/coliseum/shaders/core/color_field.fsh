#version 330
#extension GL_ARB_separate_shader_objects : require
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};
layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;
layout(location = 0) out vec4 fragColor;
void main() {
    vec2 extent = 1.0 / max(fwidth(texCoord0), vec2(0.00001));
    float radius = min(extent.x, extent.y) * 0.06;
    vec2 q = abs((texCoord0 - 0.5) * extent) - (extent * 0.5 - radius);
    float distance = length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
    float coverage = 1.0 - smoothstep(-0.75, 0.75, distance);
    vec3 color = mix(vec3(1.0), vertexColor.rgb, clamp(texCoord0.x, 0.0, 1.0)) * (1.0 - clamp(texCoord0.y, 0.0, 1.0));
    fragColor = vec4(color, vertexColor.a * coverage) * ColorModulator;
    if (fragColor.a <= 0.001) discard;
}
