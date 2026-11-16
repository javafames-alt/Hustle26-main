#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;
layout(location = 0) out vec4 fragColor;

void main() {
    vec2 code = floor(texCoord0);
    bool outline = code.y >= 128.0;
    float height = mod(code.y, 128.0);
    vec2 du = vec2(dFdx(texCoord0.x), dFdy(texCoord0.x));
    vec2 dv = vec2(dFdx(texCoord0.y), dFdy(texCoord0.y));
    vec2 paddedSize = vec2(length(dv) / max(length(du), 0.000001), 1.0) * (height + 2.0);
    vec2 halfSize = paddedSize * 0.5 - 1.0;
    float radius = min(code.x * 0.5, min(halfSize.x, halfSize.y));
    vec2 q = abs((fract(texCoord0) - 0.5) * paddedSize) - halfSize + radius;
    float distance = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
    float aa = max(fwidth(distance), 0.001) * 0.5;
    float coverage = 1.0 - smoothstep(-aa, aa, distance);
    if (outline) coverage *= smoothstep(-1.0 - aa, -1.0 + aa, distance);
    fragColor = vertexColor * ColorModulator;
    fragColor.a *= coverage;
    if (fragColor.a <= 0.001) discard;
}
