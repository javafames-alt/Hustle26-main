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
    // A continuous rounded ring inside the 19 GUI-pixel selection, with a clear centre.
    vec2 p = (texCoord0 - 0.5) * 19.0;
    float radius = 5.0;
    vec2 q = abs(p) - vec2(9.5 - 0.6 - radius);
    float distance = length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
    float aa = max(fwidth(distance), 0.001);
    float coverage = 1.0 - smoothstep(0.45 - aa * 0.5, 0.45 + aa * 0.5, abs(distance + 0.45));
    vec4 color = vertexColor * ColorModulator;
    color.a *= coverage;
    if (color.a <= 0.001) discard;
    fragColor = color;
}
