#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>

layout(location = 0) in vec4 vertexColor;
layout(location = 1) noperspective in float edge;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 color = vertexColor * ColorModulator;

    // 1 in the middle, 0 at the sides: one pixel of fade there
    float dist = 1.0 - abs(edge);
    float aa = fwidth(edge);
    float coverage = aa > 0.0 ? clamp(dist / aa, 0.0, 1.0) : 1.0;

    fragColor = vec4(color.rgb, color.a * coverage);
}
