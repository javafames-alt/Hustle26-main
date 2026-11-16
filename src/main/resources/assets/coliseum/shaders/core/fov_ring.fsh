#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>
layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;
layout(location = 0) out vec4 fragColor;
void main() {
    vec2 extent = 1.0 / max(fwidth(texCoord0), vec2(0.000001));
    float radius = min(extent.x, extent.y) * 0.5 - 2.0;
    float distance = abs(length((texCoord0 - 0.5) * extent) - radius);
    float coverage = 1.0 - smoothstep(0.55, 1.45, distance);
    fragColor = vec4(vertexColor.rgb, vertexColor.a * coverage) * ColorModulator;
    if (fragColor.a <= 0.001) discard;
}
